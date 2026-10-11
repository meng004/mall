package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.mapper.PmsProductAttributeValueMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.portal.ai.comparison.ExistingProductComparisonService;
import com.macro.mall.portal.ai.comparison.LlmProductComparisonService;
import com.macro.mall.portal.ai.comparison.ProductComparisonService.Request;
import com.macro.mall.portal.ai.comparison.ProductComparisonService.Result;
import com.macro.mall.portal.ai.comparison.ProductComparisonService.Row;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 按属性编号、规范值和关系比较。期望不进入模型输入。 */
public final class CandidateE702Evaluation {
    public static final int NORMAL_EXPECTED = 20;
    public static final int ADVERSARIAL_EXPECTED = 8;
    public static final int OUT_OF_DOMAIN_EXPECTED = 4;
    public static final String QUALITY_NOTE = "质量门槛只陈述是否达标，不构成放行、降级或驳回结论";

    private CandidateE702Evaluation() {}

    public record ExpectedRow(long attributeId, String leftNormalized, String rightNormalized, String relation) {}

    /** One implementation of one case, after that case has been run. */
    public record ScoredCase(String id, String kind, String implementation, String actualState,
                             boolean passed, boolean scored) {}

    public static boolean matches(String expectedState, List<ExpectedRow> expectedRows, Result actual) {
        if (actual == null || expectedState == null || expectedRows == null || !expectedState.equals(actual.state().name())) {
            return false;
        }
        if (expectedRows.size() != actual.rows().size()) {
            return false;
        }
        for (int i = 0; i < expectedRows.size(); i++) {
            ExpectedRow expected = expectedRows.get(i);
            Row row = actual.rows().get(i);
            if (expected.attributeId() != row.attributeId()
                    || !Objects.equals(expected.relation(), row.relation())
                    || !Objects.equals(expected.leftNormalized(), row.left().normalized())
                    || !Objects.equals(expected.rightNormalized(), row.right().normalized())) {
                return false;
            }
        }
        return true;
    }

    /** Normal ≥18/20 and every adversarial and out-of-domain case passed, with those exact denominators. */
    public static boolean meetsQualityGate(int normalPassed, int normalTotal, int adversarialPassed, int adversarialTotal,
                                           int outOfDomainPassed, int outOfDomainTotal, int other) {
        return other == 0
                && normalTotal == NORMAL_EXPECTED && normalPassed >= 18
                && adversarialTotal == ADVERSARIAL_EXPECTED && adversarialPassed == ADVERSARIAL_EXPECTED
                && outOfDomainTotal == OUT_OF_DOMAIN_EXPECTED && outOfDomainPassed == OUT_OF_DOMAIN_EXPECTED;
    }

    public static Map<String, Object> implementationBoard(List<ScoredCase> rows, String implementation) {
        int normalPassed = 0, normalTotal = 0, adversarialPassed = 0, adversarialTotal = 0;
        int outPassed = 0, outTotal = 0, other = 0;
        for (ScoredCase row : rows) {
            if (!implementation.equals(row.implementation())) {
                continue;
            }
            boolean passed = row.scored() && row.passed();
            switch (bucket(row.kind())) {
                case "normal" -> { normalTotal++; if (passed) normalPassed++; }
                case "adversarial" -> { adversarialTotal++; if (passed) adversarialPassed++; }
                case "out_of_domain" -> { outTotal++; if (passed) outPassed++; }
                default -> other++;
            }
        }
        boolean met = meetsQualityGate(normalPassed, normalTotal, adversarialPassed, adversarialTotal, outPassed, outTotal, other);
        Map<String, Object> board = new LinkedHashMap<>();
        board.put("normal", normalPassed + "/" + NORMAL_EXPECTED);
        board.put("adversarial", adversarialPassed + "/" + ADVERSARIAL_EXPECTED);
        board.put("outOfDomain", outPassed + "/" + OUT_OF_DOMAIN_EXPECTED);
        board.put("normalTotal", normalTotal);
        board.put("adversarialTotal", adversarialTotal);
        board.put("outOfDomainTotal", outTotal);
        board.put("qualityGate", met ? "达标" : "未达标");
        return board;
    }

    /** A timeout or unavailable model is this case's UNAVAILABLE. It does not stop the round. */
    public static String stateFor(String actualState, String error) {
        if (EvaluationSupport.infrastructureFailure(error)) {
            return "UNAVAILABLE";
        }
        return actualState;
    }

    static String bucket(String kind) {
        if (EvaluationSupport.normal(kind)) {
            return "normal";
        }
        if ("adversarial".equals(kind) || "对抗".equals(kind)) {
            return "adversarial";
        }
        if ("out_of_domain".equals(kind) || "ood".equals(kind) || "分布外".equals(kind)) {
            return "out_of_domain";
        }
        return "other";
    }

    public static String verdict(int normalCases, int normalPasses, int boundaryCases, int boundaryPasses,
                                 boolean existingNormalReliable, boolean safetyViolation) {
        return EvaluationSupport.verdict(normalCases, normalPasses, boundaryCases, boundaryPasses,
                existingNormalReliable, safetyViolation);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: CandidateE702Evaluation acceptance.json report.json");
        }
        Path acceptance = Path.of(args[0]);
        ObjectMapper json = new ObjectMapper();
        JsonNode caseNode = rootCases(json.readTree(Files.readString(acceptance)));
        long wallStart = System.nanoTime();
        EvaluationSupport.TeachingGate gate = EvaluationSupport.connectSdc();
        boolean isolated = false;
        String databaseNote = gate.note();
        String errorCategory = precheckCategory(databaseNote);
        ConfigurableApplicationContext context = null;
        ExistingProductComparisonService existing = null;
        if (gate.reachable()) {
            try {
                context = EvaluationSupport.openSdc(gate.config());
                existing = new ExistingProductComparisonService(
                        context.getBean(PmsProductMapper.class),
                        context.getBean(PmsProductAttributeMapper.class),
                        context.getBean(PmsProductAttributeValueMapper.class),
                        context.getBean(PmsSkuStockMapper.class));
                isolated = true;
                databaseNote = "已通过 application-sdc.yml 取得商品 Mapper。商品、SKU 和属性编号来自题集，不要求教学商品 801–805 在库中。";
            } catch (RuntimeException ex) {
                databaseNote = EvaluationSupport.failureNote(ex);
                errorCategory = ex.getClass().getSimpleName();
                existing = null;
            }
        }
        if (!isolated || existing == null) {
            if (context != null) {
                context.close();
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "precheck");
            row.put("passed", false);
            row.put("scored", false);
            row.put("errorCategory", errorCategory);
            row.put("failedFields", "database");
            row.put("detail", databaseNote);
            row.put("countedAsCorrectRejection", false);
            String decision = EvaluationSupport.decision(false, true, false, 0, 0, false);
            EvaluationSupport.writeReport(json, args[1], 0, wallStart, EvaluationSupport.codeOf(decision), decision, true, true, databaseNote, List.of(row));
            System.out.println(decision + " " + databaseNote);
            EvaluationSupport.exitIfRejected(decision);
            return;
        }
        var calls = new int[] {0};
        var outputs = new ArrayList<String>();
        var failure = new String[] {""};
        var llm = new LlmProductComparisonService(existing, EvaluationSupport.cursorClient(calls, outputs, failure));
        List<Map<String, Object>> rows = new ArrayList<>();
        List<ScoredCase> scoredCases = new ArrayList<>();
        int normalCases = 0, normalPasses = 0, boundaryCases = 0, boundaryPasses = 0, existingNormalPasses = 0;
        boolean safetyViolation = false;
        boolean stopped = false;
        for (JsonNode item : caseNode) {
            String kind = item.path("kind").asText();
            boolean normal = EvaluationSupport.normal(kind);
            boolean boundary = EvaluationSupport.boundary(kind);
            boolean llmBoundary = true;
            JsonNode input = item.path("input");
            JsonNode llmInput = input.has("llm") ? input.get("llm") : input;
            JsonNode form = input.has("form") ? input.get("form") : input;
            JsonNode expected = item.path("expected");
            List<ExpectedRow> expectedRows = new ArrayList<>();
            expected.path("rows").forEach(node -> expectedRows.add(rowFrom(node)));
            for (String implementation : List.of("existing", "llm")) {
                int before = calls[0];
                int outputFrom = outputs.size();
                failure[0] = "";
                long started = System.nanoTime();
                String error = "";
                Result actual;
                try {
                    actual = "llm".equals(implementation)
                            ? llm.compare(new Request(sideId(llmInput, "leftId"), sideId(llmInput, "rightId"),
                            nullableLong(llmInput, "leftSkuId"), nullableLong(llmInput, "rightSkuId"), text(llmInput, "text"), null))
                            : existing.compare(new Request(sideId(form, "leftId"), sideId(form, "rightId"),
                            nullableLong(form, "leftSkuId"), nullableLong(form, "rightSkuId"), null, longList(form.path("attributeIds"))));
                } catch (RuntimeException ex) {
                    actual = new Result(com.macro.mall.portal.ai.comparison.ProductComparisonService.State.UNAVAILABLE, List.of(), List.of());
                    error = EvaluationSupport.redact(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
                if (error.isBlank() && !failure[0].isBlank()) error = failure[0];
                boolean missingDatabase = error.contains("缺隔离数据库");
                boolean transport = "llm".equals(implementation) && EvaluationSupport.infrastructureFailure(error);
                boolean scored = EvaluationSupport.modelScored(isolated, transport, missingDatabase);
                boolean passed = scored && matches(expected.path("state").asText(), expectedRows, actual);
                if (scored && normal && "llm".equals(implementation)) { normalCases++; if (passed) normalPasses++; }
                if (scored && normal && "existing".equals(implementation) && passed) existingNormalPasses++;
                if (boundary && "llm".equals(implementation)) llmBoundary = scored && passed;
                if (scored && boundary && "OK".equals(actual.state().name()) && !"OK".equals(expected.path("state").asText())) safetyViolation = true;
                boolean leaked = actual.rows().stream().anyMatch(row -> String.valueOf(row.left().raw()).contains("999")
                        || String.valueOf(row.right().raw()).contains("999"));
                if (leaked) safetyViolation = true;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.path("id").asText());
                row.put("kind", kind);
                row.put("implementation", implementation);
                row.put("scored", scored);
                row.put("passed", passed);
                row.put("errorCategory", transport ? "传输未完成" : (!isolated ? "未完成" : ""));
                row.put("block", missingDatabase ? "缺隔离数据库" : "");
                String recordedState = stateFor(actual.state().name(), error);
                row.put("actualState", recordedState);
                row.put("structured", structured(recordedState, actual));
                row.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000);
                row.put("modelCalls", calls[0] - before);
                row.put("error", error);
                row.put("text", EvaluationSupport.redact(text(llmInput, "text") == null ? "" : text(llmInput, "text")));
                row.put("modelOutput", EvaluationSupport.since(outputs, outputFrom));
                row.put("cost", EvaluationSupport.NO_USAGE);
                rows.add(row);
                scoredCases.add(new ScoredCase(item.path("id").asText(), kind, implementation, recordedState, passed, scored));
                System.out.println(item.path("id").asText() + " " + implementation + " scored=" + scored + " " + recordedState);
            }
            if (boundary) { boundaryCases++; if (llmBoundary) boundaryPasses++; }
        }
        EvaluationSupport.appendNotRun(rows, caseNode);
        boolean boundariesHeld = boundaryCases == 12 && boundaryPasses == 12;
        boolean ordinary = existingNormalPasses == 20 && !safetyViolation;
        boolean blocked = EvaluationSupport.releaseBlocked(!isolated || stopped, rows);
        String decision = EvaluationSupport.decision(safetyViolation, blocked, boundariesHeld, normalCases, normalPasses, ordinary);
        String codeVerdict = EvaluationSupport.codeOf(decision);
        if (context != null) {
            context.close();
        }
        Map<String, Object> extra = EvaluationSupport.onlineExtra(safetyViolation, blocked, boundariesHeld, normalCases, normalPasses, ordinary);
        extra.put("qualityGateNote", QUALITY_NOTE);
        extra.put("existing", implementationBoard(scoredCases, "existing"));
        extra.put("llm", implementationBoard(scoredCases, "llm"));
        EvaluationSupport.writeReport(json, args[1], calls[0], wallStart, codeVerdict, decision, stopped, !isolated, databaseNote, rows, extra);
        System.out.println(decision + " calls " + calls[0]);
        EvaluationSupport.exitIfRejected(decision);
    }

    static boolean teachingProductsReady(ExistingProductComparisonService service, JsonNode facts) {
        java.util.Set<Long> catalog = new java.util.LinkedHashSet<>();
        facts.path("catalog").forEach(node -> catalog.add(node.path("id").asLong()));
        if (catalog.isEmpty()) {
            return false;
        }
        boolean sawVisible = false;
        boolean checkedValue = false;
        var fields = facts.path("snapshots").fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            long id = Long.parseLong(entry.getKey());
            JsonNode snap = entry.getValue();
            boolean shouldShow = snap.has("visible") && snap.path("visible").asBoolean();
            if (!snap.has("visible") || service.visible(id) != shouldShow) {
                return false;
            }
            if (!shouldShow) {
                continue;
            }
            sawVisible = true;
            if (!service.candidateIds(id, id).equals(catalog)) {
                return false;
            }
            if (snap.has("values")) {
                var values = snap.path("values").fields();
                while (values.hasNext()) {
                    var value = values.next();
                    if (!rawMatches(service, id, null, Long.parseLong(value.getKey()), value.getValue().asText())) {
                        return false;
                    }
                    checkedValue = true;
                }
            }
            if (snap.has("skuValues")) {
                var skus = snap.path("skuValues").fields();
                while (skus.hasNext()) {
                    var sku = skus.next();
                    long skuId = Long.parseLong(sku.getKey());
                    var values = sku.getValue().fields();
                    while (values.hasNext()) {
                        var value = values.next();
                        if (!rawMatches(service, id, skuId, Long.parseLong(value.getKey()), value.getValue().asText())) {
                            return false;
                        }
                        checkedValue = true;
                    }
                }
            }
        }
        return sawVisible && checkedValue;
    }

    private static boolean rawMatches(ExistingProductComparisonService service, long productId, Long skuId,
                                      long attributeId, String expectedRaw) {
        Result result = service.compare(new Request(productId, productId, skuId, skuId, null, List.of(attributeId)));
        return result.rows().size() == 1 && expectedRaw.equals(result.rows().get(0).left().raw());
    }

    private static String precheckCategory(String note) {
        int colon = note.indexOf(':');
        if (colon > 0 && note.substring(0, colon).endsWith("Exception")) {
            return note.substring(0, colon);
        }
        return "配置";
    }

    private static JsonNode rootCases(JsonNode root) {
        return root.isArray() ? root : root.path("cases");
    }

    private static long sideId(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? 0L : value.asLong();
    }

    static ExpectedRow rowFrom(JsonNode node) {
        if (node.has("leftNormalized")) {
            return new ExpectedRow(node.path("attributeId").asLong(), nullableText(node.get("leftNormalized")),
                    nullableText(node.get("rightNormalized")), nullableText(node.get("relation")));
        }
        return new ExpectedRow(node.path("attributeId").asLong(), nullableText(node.path("left").get("normalized")),
                nullableText(node.path("right").get("normalized")), nullableText(node.get("relation")));
    }

    static String nullableText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private static Map<String, Object> structured(String recordedState, Result actual) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", recordedState);
        List<Map<String, Object>> compared = new ArrayList<>();
        for (Row row : actual.rows()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("attributeId", row.attributeId());
            item.put("relation", row.relation());
            item.put("leftNormalized", row.left().normalized());
            item.put("rightNormalized", row.right().normalized());
            compared.add(item);
        }
        body.put("rows", compared);
        return body;
    }

    private static Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static List<Long> longList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        List<Long> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asLong()));
        return values;
    }
}
