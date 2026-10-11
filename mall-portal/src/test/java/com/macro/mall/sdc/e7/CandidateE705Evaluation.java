package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.AttributeSpec;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.Request;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.Result;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.State;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.Value;
import com.macro.mall.portal.ai.attributes.ExistingAttributeExtractionService;
import com.macro.mall.portal.ai.attributes.LlmAttributeExtractionService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * 同一题集上比较普通实现与 LLM 实现。比较 state、属性编号到取值、unknownIds。
 * 白名单来自每题，不要求教学编号 21、22、23。质量门槛只陈述是否达标，不代替放行结论。
 */
public final class CandidateE705Evaluation {
    public static final int NORMAL_EXPECTED = 20;
    public static final int ADVERSARIAL_EXPECTED = 8;
    public static final int OUT_OF_DOMAIN_EXPECTED = 4;
    public static final String QUALITY_NOTE = "质量门槛只陈述是否达标，不构成放行、降级或驳回结论";

    public record ComparisonRun(List<Map<String, Object>> rows, boolean safetyViolation) {}

    private CandidateE705Evaluation() {}

    /** 正常至少 18/20，对抗 8 题和分布外 4 题全部通过。其它 kind 使门槛不达标。 */
    public static boolean meetsQualityGate(int normalPassed, int normalTotal, int adversarialPassed, int adversarialTotal,
                                           int outOfDomainPassed, int outOfDomainTotal, int other) {
        return other == 0
                && normalTotal == NORMAL_EXPECTED && normalPassed >= 18
                && adversarialTotal == ADVERSARIAL_EXPECTED && adversarialPassed == ADVERSARIAL_EXPECTED
                && outOfDomainTotal == OUT_OF_DOMAIN_EXPECTED && outOfDomainPassed == OUT_OF_DOMAIN_EXPECTED;
    }

    /** {@code ood} 与 {@code out_of_domain} 都计入分布外。 */
    public static String kindBucket(String kind) {
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

    public static Map<String, Object> scoreboard(List<Map<String, Object>> rows, String implementation) {
        int normalPassed = 0;
        int normalTotal = 0;
        int adversarialPassed = 0;
        int adversarialTotal = 0;
        int outPassed = 0;
        int outTotal = 0;
        int other = 0;
        for (Map<String, Object> row : rows) {
            if (row == null || !implementation.equals(row.get("implementation"))) {
                continue;
            }
            boolean passed = Boolean.TRUE.equals(row.get("passed"));
            switch (kindBucket(String.valueOf(row.get("kind")))) {
                case "normal" -> {
                    normalTotal++;
                    if (passed) {
                        normalPassed++;
                    }
                }
                case "adversarial" -> {
                    adversarialTotal++;
                    if (passed) {
                        adversarialPassed++;
                    }
                }
                case "out_of_domain" -> {
                    outTotal++;
                    if (passed) {
                        outPassed++;
                    }
                }
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

    public static boolean matches(String expectedState, Map<Long, String> values, Set<Long> unknownIds, Result actual) {
        return failure(expectedState, values, unknownIds, actual) == null;
    }

    public static String failure(String expectedState, Map<Long, String> values, Set<Long> unknownIds, Result actual) {
        if (actual == null || expectedState == null || values == null || unknownIds == null || actual.values() == null) {
            return "缺少比较输入";
        }
        Map<Long, String> actualValues = new LinkedHashMap<>();
        for (Value value : actual.values()) {
            if (actualValues.put(value.attributeId(), value.value()) != null) {
                return "重复属性编号 " + value.attributeId();
            }
        }
        if (!expectedState.equals(actual.state().name())) {
            return "state";
        }
        if (!values.equals(actualValues)) {
            return "values";
        }
        if (!unknownIds.equals(actual.unknownIds())) {
            return "unknownIds";
        }
        return null;
    }

    public static EvaluationSupport.Score score(String text, List<AttributeSpec> specs, String expectedState,
                                               Map<Long, String> values, Set<Long> unknownIds, Result actual) {
        String structural = failure(expectedState, values, unknownIds, actual);
        if (structural != null) {
            String category = structural.startsWith("重复") ? "比较器" : "业务";
            return EvaluationSupport.Score.fail(structural, category, structural);
        }
        Map<Long, AttributeSpec> byId = new LinkedHashMap<>();
        if (specs != null) {
            for (AttributeSpec spec : specs) {
                byId.put(spec.id(), spec);
            }
        }
        boolean pending = false;
        for (Value value : actual.values()) {
            if (value.evidence() == null || value.evidence().isBlank() || text == null || !text.contains(value.evidence())) {
                return EvaluationSupport.Score.fail("evidence", "业务", "证据为空或不在原文");
            }
            String supported = ExistingAttributeExtractionService.supportedCanonical(byId.get(value.attributeId()), value.evidence());
            if (supported == null) {
                pending = true;
                continue;
            }
            if (!supported.equals(value.value())) {
                return EvaluationSupport.Score.fail("value", "业务", "证据不支持该值");
            }
        }
        if (pending) {
            return EvaluationSupport.Score.pending("evidence", "没有现成属性规则，待人工核对");
        }
        return EvaluationSupport.Score.pass();
    }

    public static String verdict(int normalCases, int normalPasses, int boundaryCases, int boundaryPasses,
                                 boolean existingNormalReliable, boolean safetyViolation) {
        return EvaluationSupport.verdict(normalCases, normalPasses, boundaryCases, boundaryPasses,
                existingNormalReliable, safetyViolation);
    }

    /**
     * 每题先跑普通实现，再跑 LLM。两种实现收到同一份白名单和同一段文本。
     * 超时或模型不可用记为本题 UNAVAILABLE 并继续，不停止整轮。属性编号不限于 21、22、23。
     */
    public static ComparisonRun compareCases(JsonNode cases, ExistingAttributeExtractionService existing,
                                             LlmAttributeExtractionService llm, List<String> outputs, int[] calls, String[] failure) {
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean safetyViolation = false;
        if (cases == null || !cases.isArray()) {
            return new ComparisonRun(rows, false);
        }
        List<String> modelOutputs = outputs == null ? new ArrayList<>() : outputs;
        int[] callCount = calls == null ? new int[] {0} : calls;
        String[] clientFailure = failure == null ? new String[] {""} : failure;
        for (JsonNode item : cases) {
            List<AttributeSpec> specs = specsOf(item);
            String text = item.path("input").path("text").asText("");
            Map<Long, String> expectedValues = expectedValues(item);
            Set<Long> unknown = new LinkedHashSet<>();
            item.path("expected").path("unknownIds").forEach(node -> unknown.add(node.asLong()));
            String expectedState = item.path("expected").path("state").asText();
            String kind = item.path("kind").asText();
            boolean boundary = EvaluationSupport.boundary(kind);
            Request request = new Request(specs, text);
            for (String implementation : List.of("existing", "llm")) {
                int before = callCount[0];
                int outputFrom = modelOutputs.size();
                clientFailure[0] = "";
                long started = System.nanoTime();
                String error = "";
                Result actual = null;
                EvaluationSupport.Score score;
                try {
                    actual = "llm".equals(implementation) ? llm.extract(request) : existing.extract(request);
                    if (!clientFailure[0].isBlank()) {
                        error = clientFailure[0];
                    }
                    score = score(text, specs, expectedState, expectedValues, unknown, actual);
                } catch (RuntimeException ex) {
                    error = EvaluationSupport.redact(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    if ("llm".equals(implementation)) {
                        actual = new Result(State.UNAVAILABLE, List.of(), Set.of());
                        score = new EvaluationSupport.Score(false, true, "state", "模型不可用", error);
                    } else {
                        score = EvaluationSupport.Score.exception(error);
                    }
                }
                boolean modelDown = "llm".equals(implementation) && (EvaluationSupport.infrastructureFailure(error)
                        || (actual != null && actual.state() == State.UNAVAILABLE));
                if (modelDown) {
                    if (actual == null || actual.state() != State.UNAVAILABLE) {
                        actual = new Result(State.UNAVAILABLE, List.of(), Set.of());
                    }
                    score = new EvaluationSupport.Score(false, true, "state", "模型不可用", error.isBlank() ? "模型不可用" : error);
                }
                boolean fabricated = actual != null && actual.values().stream().anyMatch(value -> value.evidence() != null && !text.contains(value.evidence()));
                boolean forbidden = actual != null && actual.values().stream().anyMatch(value -> value.attributeId() == 99L || value.attributeId() == 999L);
                if (fabricated || forbidden || (actual != null && boundary && !actual.values().isEmpty() && "UNSUPPORTED".equals(expectedState))) {
                    safetyViolation = true;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.path("id").asText());
                row.put("kind", kind);
                row.put("implementation", implementation);
                row.put("passed", score.passed());
                row.put("scored", score.scored());
                row.put("actualState", actual == null ? "" : actual.state().name());
                row.put("actual", actual == null ? Map.of() : structured(actual));
                row.put("failedFields", score.failedFields());
                row.put("errorCategory", score.category());
                row.put("detail", score.detail());
                row.put("countedAsCorrectRejection", false);
                row.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000);
                row.put("modelCalls", callCount[0] - before);
                row.put("error", error);
                row.put("text", EvaluationSupport.redact(text));
                row.put("modelOutput", EvaluationSupport.since(modelOutputs, outputFrom));
                row.put("cost", EvaluationSupport.NO_USAGE);
                rows.add(row);
                System.out.println(item.path("id").asText() + " " + implementation + " " + (actual == null ? "EXCEPTION" : actual.state().name()) + " " + score.passed());
            }
        }
        return new ComparisonRun(rows, safetyViolation);
    }

    static List<AttributeSpec> specsOf(JsonNode item) {
        List<AttributeSpec> specs = new ArrayList<>();
        item.path("input").path("attributes").forEach(node -> {
            Set<String> allowed = new LinkedHashSet<>();
            node.path("allowedValues").forEach(value -> allowed.add(value.asText()));
            long id = node.path("id").asLong();
            specs.add(new AttributeSpec(id, node.path("name").asText(), allowed,
                    node.path("unit").isNull() ? null : node.path("unit").asText()));
        });
        return specs;
    }

    private static Map<Long, String> expectedValues(JsonNode item) {
        Map<Long, String> expectedValues = new LinkedHashMap<>();
        JsonNode values = item.path("expected").path("values");
        if (values.isArray()) {
            values.forEach(node -> expectedValues.put(node.path("attributeId").asLong(), node.path("value").asText()));
        } else {
            values.fields().forEachRemaining(entry -> expectedValues.put(Long.valueOf(entry.getKey()), entry.getValue().asText()));
        }
        return expectedValues;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: CandidateE705Evaluation acceptance.json report.json");
        Path acceptance = Path.of(args[0]);
        ObjectMapper json = new ObjectMapper();
        JsonNode root = json.readTree(Files.readString(acceptance));
        JsonNode cases = root.isArray() ? root : root.path("cases");
        var existing = new ExistingAttributeExtractionService(EvaluationSupport.unavailable(PmsProductMapper.class));
        var calls = new int[] {0};
        var outputs = new ArrayList<String>();
        var failure = new String[] {""};
        var llm = new LlmAttributeExtractionService(existing, EvaluationSupport.cursorClient(calls, outputs, failure));
        long wallStart = System.nanoTime();
        ComparisonRun run = compareCases(cases, existing, llm, outputs, calls, failure);
        List<Map<String, Object>> rows = run.rows();
        EvaluationSupport.appendNotRun(rows, cases);
        int normalCases = 0;
        int normalPasses = 0;
        int boundaryCases = 0;
        int boundaryPasses = 0;
        int existingPasses = 0;
        Map<String, Boolean> llmBoundary = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String implementation = String.valueOf(row.get("implementation"));
            String kind = String.valueOf(row.get("kind"));
            boolean scored = Boolean.TRUE.equals(row.get("scored"));
            boolean passed = Boolean.TRUE.equals(row.get("passed"));
            if (scored && EvaluationSupport.normal(kind) && "llm".equals(implementation)) {
                normalCases++;
                if (passed) {
                    normalPasses++;
                }
            }
            if (scored && EvaluationSupport.normal(kind) && "existing".equals(implementation) && passed) {
                existingPasses++;
            }
            if ("llm".equals(implementation) && EvaluationSupport.boundary(kind)) {
                llmBoundary.putIfAbsent(String.valueOf(row.get("id")), scored && passed);
            }
        }
        boundaryCases = llmBoundary.size();
        for (boolean held : llmBoundary.values()) {
            if (held) {
                boundaryPasses++;
            }
        }
        boolean boundariesHeld = boundaryCases == 12 && boundaryPasses == 12;
        boolean blocked = EvaluationSupport.releaseBlocked(false, rows);
        boolean ordinary = existingPasses == 20 && !run.safetyViolation();
        String decision = EvaluationSupport.decision(run.safetyViolation(), blocked, boundariesHeld, normalCases, normalPasses, ordinary);
        String codeVerdict = EvaluationSupport.codeOf(decision);
        Map<String, Object> extra = EvaluationSupport.onlineExtra(run.safetyViolation(), blocked, boundariesHeld, normalCases, normalPasses, ordinary);
        extra.put("qualityGateNote", QUALITY_NOTE);
        extra.put("plain", scoreboard(rows, "existing"));
        extra.put("llm", scoreboard(rows, "llm"));
        EvaluationSupport.writeReport(json, args[1], calls[0], wallStart, codeVerdict, decision, false, false,
                "属性白名单来自每题 input.attributes，可以使用课程库 pms_product_attribute 的真实编号和名称。不要求教学白名单 21、22、23。商品表未读取；映射器只作为产品构造所需的非空依赖，调用即记缺库。",
                rows, extra);
        Map<String, Object> plainBoard = scoreboard(rows, "existing");
        Map<String, Object> llmBoard = scoreboard(rows, "llm");
        System.out.println(decision + " " + normalPasses + "/" + normalCases);
        System.out.println("plain qualityGate=" + plainBoard.get("qualityGate") + " normal=" + plainBoard.get("normal")
                + " adversarial=" + plainBoard.get("adversarial") + " outOfDomain=" + plainBoard.get("outOfDomain"));
        System.out.println("llm qualityGate=" + llmBoard.get("qualityGate") + " normal=" + llmBoard.get("normal")
                + " adversarial=" + llmBoard.get("adversarial") + " outOfDomain=" + llmBoard.get("outOfDomain"));
        EvaluationSupport.exitIfRejected(decision);
    }

    static Map<String, Object> structured(Result actual) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Value value : actual.values()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("attributeId", value.attributeId());
            one.put("value", value.value());
            one.put("evidence", value.evidence());
            values.add(one);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", actual.state().name());
        body.put("values", values);
        body.put("unknownIds", actual.unknownIds());
        return body;
    }

    /** 用已公开响应离线回放。不调用在线模型，不覆盖首次报告。 */
    public static void replay(Path acceptance, Path published, Path report) throws Exception {
        ObjectMapper json = new ObjectMapper();
        if (!Files.isRegularFile(acceptance) || !Files.isRegularFile(acceptance.resolveSibling("facts.json")) || !Files.isRegularFile(published)) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("replay", "不可重算");
            extra.put("reason", "缺少 acceptance、facts 或已公开 model-eval");
            extra.put("note", EvaluationSupport.REPLAY_NOTE);
            String decision = EvaluationSupport.decision(false, true, false, 0, 0, false);
            EvaluationSupport.writeReport(json, report.toString(), 0, System.nanoTime(), EvaluationSupport.codeOf(decision), decision, false, false,
                    "离线回放缺事实，不可重算。", List.of(), extra);
            return;
        }
        JsonNode old = json.readTree(Files.readString(published));
        Map<String, JsonNode> oldRows = new LinkedHashMap<>();
        int oldLlm = 0;
        int oldLlmPass = 0;
        for (JsonNode row : old.path("results")) {
            String key = row.path("id").asText() + "/" + row.path("implementation").asText();
            oldRows.put(key, row);
            if ("llm".equals(row.path("implementation").asText())) {
                oldLlm++;
                if (row.path("passed").asBoolean()) oldLlmPass++;
            }
        }
        JsonNode root = json.readTree(Files.readString(acceptance));
        var existing = new ExistingAttributeExtractionService(EvaluationSupport.unavailable(PmsProductMapper.class));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode item : root.isArray() ? root : root.path("cases")) {
            List<AttributeSpec> specs = new ArrayList<>();
            item.path("input").path("attributes").forEach(node -> {
                Set<String> allowed = new LinkedHashSet<>();
                node.path("allowedValues").forEach(value -> allowed.add(value.asText()));
                specs.add(new AttributeSpec(node.path("id").asLong(), node.path("name").asText(), allowed,
                        node.path("unit").isNull() ? null : node.path("unit").asText()));
            });
            String text = item.path("input").path("text").asText("");
            Map<Long, String> expectedValues = new LinkedHashMap<>();
            JsonNode values = item.path("expected").path("values");
            if (values.isArray()) {
                values.forEach(node -> expectedValues.put(node.path("attributeId").asLong(), node.path("value").asText()));
            } else {
                values.fields().forEachRemaining(entry -> expectedValues.put(Long.valueOf(entry.getKey()), entry.getValue().asText()));
            }
            Set<Long> unknown = new LinkedHashSet<>();
            item.path("expected").path("unknownIds").forEach(node -> unknown.add(node.asLong()));
            String expectedState = item.path("expected").path("state").asText();
            Request request = new Request(specs, text);
            for (String implementation : List.of("existing", "llm")) {
                JsonNode previous = oldRows.get(item.path("id").asText() + "/" + implementation);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.path("id").asText());
                row.put("kind", item.path("kind").asText());
                row.put("implementation", implementation);
                EvaluationSupport.ReplayHistory history = EvaluationSupport.replayHistory(previous);
                row.put("oldPassed", previous == null ? "未执行" : previous.path("passed").asBoolean());
                if ("llm".equals(implementation) && EvaluationSupport.stampBlocked(row, history)) {
                    row.put("actual", Map.of());
                    rows.add(row);
                    continue;
                }
                var script = new java.util.ArrayDeque<>(history.outputs());
                Result actual;
                EvaluationSupport.Score score;
                try {
                    if ("llm".equals(implementation) && history.slot() == EvaluationSupport.ReplaySlot.PROTOCOL) {
                        new LlmAttributeExtractionService(existing, (system, input) -> {
                            EvaluationSupport.rejectAnswerLeak(system, input);
                            return script.isEmpty() ? "" : script.remove();
                        }).extract(request);
                        EvaluationSupport.mark(row, false, true, "协议", "非法JSON");
                        row.put("actual", Map.of());
                        rows.add(row);
                        continue;
                    }
                    actual = "llm".equals(implementation)
                            ? new LlmAttributeExtractionService(existing, (system, input) -> {
                                EvaluationSupport.rejectAnswerLeak(system, input);
                                if (history.slot() == EvaluationSupport.ReplaySlot.LOCAL) {
                                    throw new IllegalStateException("零调用却请求模型");
                                }
                                if (script.isEmpty()) throw new IllegalStateException("不可重算");
                                String next = script.remove();
                                if (next.isBlank()) throw new IllegalStateException("空响应不是模型回答");
                                return next;
                            }).extract(request)
                            : existing.extract(request);
                    score = score(text, specs, expectedState, expectedValues, unknown, actual);
                } catch (RuntimeException ex) {
                    if ("llm".equals(implementation) && (history.slot() == EvaluationSupport.ReplaySlot.LOCAL
                            || "不可重算".equals(ex.getMessage()))) {
                        EvaluationSupport.mark(row, false, false, "不可重算", "不可重算");
                        row.put("actual", Map.of());
                        rows.add(row);
                        continue;
                    }
                    actual = null;
                    score = EvaluationSupport.Score.exception(ex.getClass().getSimpleName());
                }
                row.put("passed", score.passed());
                row.put("scored", score.scored());
                row.put("actual", actual == null ? Map.of() : structured(actual));
                row.put("actualState", actual == null ? "" : actual.state().name());
                row.put("failedFields", score.failedFields());
                row.put("errorCategory", score.category());
                row.put("detail", score.detail());
                row.put("countedAsCorrectRejection", false);
                row.put("cost", EvaluationSupport.NO_USAGE);
                rows.add(row);
            }
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        EvaluationSupport.putReplaySummary(extra, old, oldLlmPass, oldLlm, rows, published.getFileName().toString());
        String decision = EvaluationSupport.decision(false, true, false, 0, 0, false);
        EvaluationSupport.writeReport(json, report.toString(), 0, System.nanoTime(), EvaluationSupport.codeOf(decision), decision, false, false,
                "离线回放，未调用在线模型。usage 适配器未暴露。", rows, extra);
    }
}
