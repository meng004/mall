package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.AttributeSpec;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.Request;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.Result;
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
/** 比较规范化后的属性值与未知集合。证据沿用属性 check 的别名和单位规则，期望不进入模型。 */
public final class CandidateE705Evaluation {
    private CandidateE705Evaluation() {}

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

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: CandidateE705Evaluation acceptance.json report.json");
        Path acceptance = Path.of(args[0]);
        if (!Files.isRegularFile(acceptance.resolveSibling("facts.json"))) {
            throw new IllegalStateException("缺少facts.json，不能把开发用例当作独立验收");
        }
        ObjectMapper json = new ObjectMapper();
        JsonNode root = json.readTree(Files.readString(acceptance));
        var existing = new ExistingAttributeExtractionService(EvaluationSupport.unavailable(PmsProductMapper.class));
        var calls = new int[] {0};
        var outputs = new ArrayList<String>();
        var failure = new String[] {""};
        var llm = new LlmAttributeExtractionService(existing, EvaluationSupport.cursorClient(calls, outputs, failure));
        List<Map<String, Object>> rows = new ArrayList<>();
        int normalCases = 0, normalPasses = 0, boundaryCases = 0, boundaryPasses = 0, existingCases = 0, existingPasses = 0;
        boolean safetyViolation = false;
        boolean stopped = false;
        long wallStart = System.nanoTime();
        for (JsonNode item : root.isArray() ? root : root.path("cases")) {
            if (stopped) break;
            List<AttributeSpec> specs = new ArrayList<>();
            item.path("input").path("attributes").forEach(node -> {
                Set<String> allowed = new LinkedHashSet<>();
                node.path("allowedValues").forEach(value -> allowed.add(value.asText()));
                specs.add(new AttributeSpec(node.path("id").asLong(), node.path("name").asText(), allowed,
                        node.path("unit").isNull() ? null : node.path("unit").asText()));
            });
            String text = item.path("input").path("text").asText("");
            Map<Long, String> expectedValues = new LinkedHashMap<>();
            Map<Long, String> expectedEvidence = new LinkedHashMap<>();
            JsonNode values = item.path("expected").path("values");
            if (values.isArray()) {
                values.forEach(node -> {
                    expectedValues.put(node.path("attributeId").asLong(), node.path("value").asText());
                    expectedEvidence.put(node.path("attributeId").asLong(), node.path("evidence").asText());
                });
            } else {
                values.fields().forEachRemaining(entry -> expectedValues.put(Long.valueOf(entry.getKey()), entry.getValue().asText()));
            }
            Set<Long> unknown = new LinkedHashSet<>();
            item.path("expected").path("unknownIds").forEach(node -> unknown.add(node.asLong()));
            String expectedState = item.path("expected").path("state").asText();
            String kind = item.path("kind").asText();
            boolean normal = EvaluationSupport.normal(kind);
            boolean boundary = EvaluationSupport.boundary(kind);
            boolean llmBoundary = true;
            Request request = new Request(specs, text);
            for (String implementation : List.of("existing", "llm")) {
                int before = calls[0];
                int outputFrom = outputs.size();
                failure[0] = "";
                long started = System.nanoTime();
                String error = "";
                Result actual = null;
                EvaluationSupport.Score score;
                try {
                    actual = "llm".equals(implementation) ? llm.extract(request) : existing.extract(request);
                    if (!failure[0].isBlank()) error = failure[0];
                    score = score(text, specs, expectedState, expectedValues, unknown, actual);
                    if (EvaluationSupport.infrastructureFailure(error)) {
                        score = EvaluationSupport.Score.transport(error);
                    }
                } catch (RuntimeException ex) {
                    error = EvaluationSupport.redact(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    score = EvaluationSupport.Score.exception(error);
                }
                boolean passed = score.passed();
                boolean fabricated = actual != null && actual.values().stream().anyMatch(value -> value.evidence() != null && !text.contains(value.evidence()));
                boolean forbidden = actual != null && actual.values().stream().anyMatch(value -> value.attributeId() == 99 || value.attributeId() == 999);
                if (score.scored() && normal && "llm".equals(implementation)) { normalCases++; if (passed) normalPasses++; }
                if (score.scored() && normal && "existing".equals(implementation)) { existingCases++; if (passed) existingPasses++; }
                if (boundary && "llm".equals(implementation)) llmBoundary = score.scored() && passed;
                if (fabricated || forbidden || (actual != null && boundary && !actual.values().isEmpty() && "UNSUPPORTED".equals(expectedState))) safetyViolation = true;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.path("id").asText());
                row.put("kind", kind);
                row.put("implementation", implementation);
                row.put("passed", passed);
                row.put("scored", score.scored());
                row.put("actualState", actual == null ? "" : actual.state().name());
                row.put("actual", actual == null ? Map.of() : structured(actual));
                row.put("failedFields", score.failedFields());
                row.put("errorCategory", score.category());
                row.put("detail", score.detail());
                row.put("countedAsCorrectRejection", false);
                row.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000);
                row.put("modelCalls", calls[0] - before);
                row.put("error", error);
                row.put("text", EvaluationSupport.redact(text));
                row.put("modelOutput", EvaluationSupport.since(outputs, outputFrom));
                row.put("cost", EvaluationSupport.NO_USAGE);
                rows.add(row);
                System.out.println(item.path("id").asText() + " " + implementation + " " + (actual == null ? "EXCEPTION" : actual.state().name()) + " " + passed);
                if ("llm".equals(implementation) && EvaluationSupport.infrastructureFailure(error)) { stopped = true; break; }
            }
            if (boundary) { boundaryCases++; if (llmBoundary) boundaryPasses++; }
        }
        boolean boundariesHeld = boundaryCases == 12 && boundaryPasses == 12;
        JsonNode cases = root.isArray() ? root : root.path("cases");
        EvaluationSupport.appendNotRun(rows, cases);
        boolean blocked = EvaluationSupport.releaseBlocked(stopped, rows);
        String decision = EvaluationSupport.decision(safetyViolation, blocked, boundariesHeld, normalCases, normalPasses,
                existingPasses == 20 && !safetyViolation);
        String codeVerdict = EvaluationSupport.codeOf(decision);
        EvaluationSupport.writeReport(json, args[1], calls[0], wallStart, codeVerdict, decision, stopped, false,
                "属性白名单来自题目输入。商品表未读取；映射器只作为产品构造所需的非空依赖，调用即记缺库。", rows,
                EvaluationSupport.onlineExtra(safetyViolation, blocked, boundariesHeld, normalCases, normalPasses,
                        existingPasses == 20 && !safetyViolation));
        System.out.println(decision + " " + normalPasses + "/" + normalCases);
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
