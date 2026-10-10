package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.llm.LlmClient;
import com.macro.mall.portal.query.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Compares expected results; independence is established by the case-set provenance, not this runner. */
public final class QueryEvaluation {
    public static final int NORMAL_EXPECTED = 20;
    public static final int ADVERSARIAL_EXPECTED = 8;
    public static final int OUT_OF_DOMAIN_EXPECTED = 4;
    public static final String QUALITY_NOTE = "质量门槛只陈述是否达标，不构成放行、降级或驳回结论";

    public record Case(String id, String kind, String text, String expected_status, List<Long> expected_ids) {}
    public record Result(String id, String kind, boolean passed, String actualStatus, List<Long> actualIds) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Observed(String actualStatus, List<Long> actualIds, boolean passed, long elapsedMillis,
                           String error, String modelText) {}

    public record Comparison(String id, String kind, Observed plain, Observed llm) {}

    private QueryEvaluation() {}

    public static Result evaluate(Case item, ProductQueryService service) {
        var answer = service.query(ProductQueryRequest.text(item.text(),1,100));
        var ids = answer.products().stream().map(ProductQueryResult.ProductSummary::id).sorted().toList();
        var expected = item.expected_ids().stream().sorted().toList();
        boolean passed = answer.status().name().equals(item.expected_status()) && ids.equals(expected);
        return new Result(item.id(),item.kind(),passed,answer.status().name(),ids);
    }

    public static String verdict(List<Result> results) {
        if (results.isEmpty()) return "INCOMPLETE";
        return results.stream().allMatch(Result::passed) ? "REGRESSION_PASSED" : "FAILED";
    }

    /** Times one case. A thrown query is a failure with the exception text, not a correct rejection. */
    public static Observed observe(Case item, ProductQueryService service, Supplier<String> modelText) {
        long started = System.nanoTime();
        Supplier<String> text = modelText == null ? () -> null : modelText;
        try {
            Result result = evaluate(item, service);
            return new Observed(result.actualStatus(), result.actualIds(), result.passed(), elapsed(started), null, text.get());
        } catch (RuntimeException ex) {
            return new Observed("", List.of(), false, elapsed(started), detail(ex), text.get());
        }
    }

    /** Normal ≥18/20 and every adversarial and out-of-domain case passed, with those exact denominators. */
    public static boolean meetsQualityGate(int normalPassed, int normalTotal, int adversarialPassed, int adversarialTotal,
                                           int outOfDomainPassed, int outOfDomainTotal, int other) {
        return other == 0
            && normalTotal == NORMAL_EXPECTED && normalPassed >= 18
            && adversarialTotal == ADVERSARIAL_EXPECTED && adversarialPassed == ADVERSARIAL_EXPECTED
            && outOfDomainTotal == OUT_OF_DOMAIN_EXPECTED && outOfDomainPassed == OUT_OF_DOMAIN_EXPECTED;
    }

    public static Map<String, Object> scoreboard(List<Comparison> rows, boolean llm) {
        int normalPassed = 0, normalTotal = 0, adversarialPassed = 0, adversarialTotal = 0;
        int outPassed = 0, outTotal = 0, other = 0;
        var results = new ArrayList<Result>();
        for (Comparison row : rows) {
            Observed observed = llm ? row.llm() : row.plain();
            results.add(new Result(row.id(), row.kind(), observed.passed(), observed.actualStatus(), observed.actualIds()));
            switch (row.kind() == null ? "" : row.kind()) {
                case "normal" -> { normalTotal++; if (observed.passed()) normalPassed++; }
                case "adversarial" -> { adversarialTotal++; if (observed.passed()) adversarialPassed++; }
                case "out_of_domain" -> { outTotal++; if (observed.passed()) outPassed++; }
                default -> other++;
            }
        }
        boolean met = meetsQualityGate(normalPassed, normalTotal, adversarialPassed, adversarialTotal, outPassed, outTotal, other);
        var board = new LinkedHashMap<String, Object>();
        board.put("verdict", verdict(results));
        board.put("normal", normalPassed + "/" + NORMAL_EXPECTED);
        board.put("adversarial", adversarialPassed + "/" + ADVERSARIAL_EXPECTED);
        board.put("outOfDomain", outPassed + "/" + OUT_OF_DOMAIN_EXPECTED);
        board.put("normalTotal", normalTotal);
        board.put("adversarialTotal", adversarialTotal);
        board.put("outOfDomainTotal", outTotal);
        board.put("qualityGate", met ? "达标" : "未达标");
        return board;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: QueryEvaluation cases.json report.json");
        var json = new ObjectMapper();
        List<Case> cases = json.readValue(Files.readString(Path.of(args[0])), new TypeReference<>() {});
        Path config = EvaluationSupport.findSdcConfig();
        if (config == null) throw new IllegalStateException("找不到 application-sdc.yml，无法连接课程库");
        String model = configuredModel();
        var rows = new ArrayList<Comparison>();
        try (var context = EvaluationSupport.openPortal("--spring.config.additional-location=" + config.toUri(),
                "--mall.llm.model=" + model)) {
            var existing = context.getBean(ExistingProductQueryService.class);
            var client = context.getBean(LlmClient.class);
            for (var item : cases) {
                Observed plain = observe(item, existing, () -> null);
                var modelText = new String[1];
                var clientError = new String[1];
                var llmService = new LlmProductQueryService(existing, (system, input) -> {
                    try {
                        String response = client.generate(system, input);
                        modelText[0] = response;
                        return response;
                    } catch (RuntimeException ex) {
                        clientError[0] = detail(ex);
                        throw ex;
                    }
                });
                Observed llm = observe(item, llmService, () -> modelText[0]);
                if (llm.error() == null && clientError[0] != null)
                    llm = new Observed(llm.actualStatus(), llm.actualIds(), llm.passed(), llm.elapsedMillis(), clientError[0], llm.modelText());
                rows.add(new Comparison(item.id(), item.kind(), plain, llm));
                System.out.printf("%s plain %s %s %dms | llm %s %s %dms%n", item.id(),
                    plain.actualStatus(), plain.passed() ? "PASS" : "FAIL", plain.elapsedMillis(),
                    llm.actualStatus(), llm.passed() ? "PASS" : "FAIL", llm.elapsedMillis());
            }
        }
        Map<String, Object> plainBoard = scoreboard(rows, false);
        Map<String, Object> llmBoard = scoreboard(rows, true);
        var report = new LinkedHashMap<String, Object>();
        report.put("evaluatedAt", Instant.now().toString());
        report.put("evidenceType", "case-set comparison; independence and model accuracy require separate provenance");
        report.put("cases", args[0]);
        report.put("pageSize", 100);
        report.put("model", model);
        report.put("verdict", llmBoard.get("verdict"));
        report.put("qualityGateNote", QUALITY_NOTE);
        report.put("plain", plainBoard);
        report.put("llm", llmBoard);
        report.put("results", rows);
        Path output = Path.of(args[1]).toAbsolutePath();
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println(llmBoard.get("verdict"));
        System.out.println("plain qualityGate=" + plainBoard.get("qualityGate") + " normal=" + plainBoard.get("normal")
            + " adversarial=" + plainBoard.get("adversarial") + " outOfDomain=" + plainBoard.get("outOfDomain"));
        System.out.println("llm qualityGate=" + llmBoard.get("qualityGate") + " normal=" + llmBoard.get("normal")
            + " adversarial=" + llmBoard.get("adversarial") + " outOfDomain=" + llmBoard.get("outOfDomain"));
        if (!"REGRESSION_PASSED".equals(llmBoard.get("verdict"))) System.exit(1);
    }

    static String configuredModel() {
        String model = System.getenv("LLM_MODEL");
        return model == null || model.isBlank() ? "grok-4.7-high-fast" : model;
    }

    private static long elapsed(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static String detail(RuntimeException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().trim();
        String text = message.isEmpty() ? ex.getClass().getSimpleName() : ex.getClass().getSimpleName() + ": " + message;
        return text.length() > 500 ? text.substring(0, 500) + "...[truncated]" : text;
    }
}
