package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.ai.reasons.ExistingReturnReasonBatchService;
import com.macro.mall.portal.ai.reasons.LlmReturnReasonBatchService;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Classified;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Item;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Label;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Result;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按匿名编号、单主因、分母和失败数比较。期望不进入模型。 */
public final class CandidateE706Evaluation {
    private CandidateE706Evaluation() {}

    public static boolean matches(String expectedState, Map<String, String> labels, int denominator, int failedCount, Result actual) {
        return failure(expectedState, labels, denominator, failedCount, actual) == null;
    }

    public static String failure(String expectedState, Map<String, String> labels, int denominator, int failedCount, Result actual) {
        if (actual == null || expectedState == null || labels == null || actual.items() == null) {
            return "缺少比较输入";
        }
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Classified item : actual.items()) {
            if (item.anonymousId() != null && !seen.add(item.anonymousId())) {
                return "重复匿名编号 " + item.anonymousId();
            }
        }
        if (!expectedState.equals(actual.state().name())) {
            return "state";
        }
        if (actual.denominator() != denominator) {
            return "denominator";
        }
        if (actual.failedCount() != failedCount) {
            return "failedCount";
        }
        if (actual.items().size() != denominator) {
            return "items";
        }
        Map<String, String> actualLabels = new LinkedHashMap<>();
        int classified = 0;
        for (Classified item : actual.items()) {
            if (!item.failed()) {
                classified++;
                actualLabels.put(item.anonymousId(), item.label() == null ? null : item.label().name());
            }
        }
        if (!labels.equals(actualLabels)) {
            return "labels";
        }
        if (classified + actual.failedCount() != actual.denominator()) {
            return "accounting";
        }
        return null;
    }

    public static EvaluationSupport.Score score(List<Item> batch, String expectedState, Map<String, String> labels,
                                               int denominator, int failedCount, Result actual) {
        return score(batch, expectedState, labels, denominator, failedCount, Map.of(), actual);
    }

    public static EvaluationSupport.Score score(List<Item> batch, String expectedState, Map<String, String> labels,
                                               int denominator, int failedCount, Map<String, java.util.Set<String>> accepted,
                                               Result actual) {
        String structural = failure(expectedState, labels, denominator, failedCount, actual);
        if (structural != null) {
            String category = structural.startsWith("重复") ? "比较器" : "业务";
            return EvaluationSupport.Score.fail(structural, category, structural);
        }
        if (!accountingMatches(actual)) {
            return EvaluationSupport.Score.fail("counts", "业务", "计数、比例、分母或失败项不一致");
        }
        Map<String, Item> byId = new LinkedHashMap<>();
        if (batch != null) {
            for (Item item : batch) {
                if (item != null && item.anonymousId() != null) {
                    byId.put(item.anonymousId(), item);
                }
            }
        }
        boolean pending = false;
        boolean checkQuotes = accepted != null && !accepted.isEmpty();
        for (Classified item : actual.items()) {
            if (item.failed()) {
                continue;
            }
            Item source = byId.get(item.anonymousId());
            String text = source == null ? "" : (source.reason() == null ? "" : source.reason()) + "\n" + (source.description() == null ? "" : source.description());
            if (item.evidence() == null || item.evidence().isBlank() || !text.contains(item.evidence())) {
                return EvaluationSupport.Score.fail("evidence", "业务", "证据为空或不在原文");
            }
            if (checkQuotes && !accepted.getOrDefault(item.anonymousId(), java.util.Set.of()).contains(item.evidence())) {
                pending = true;
            }
        }
        if (pending) {
            return EvaluationSupport.Score.pending("evidence", "同标签引文不在教师片段集合，待人工核对");
        }
        return EvaluationSupport.Score.pass();
    }

    public static String verdict(int normalCases, int normalPasses, int boundaryCases, int boundaryPasses,
                                 boolean existingNormalReliable, boolean safetyViolation) {
        return EvaluationSupport.verdict(normalCases, normalPasses, boundaryCases, boundaryPasses,
                existingNormalReliable, safetyViolation);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: CandidateE706Evaluation acceptance.json report.json");
        }
        Path acceptance = Path.of(args[0]);
        if (!Files.isRegularFile(acceptance.resolveSibling("facts.json"))) {
            throw new IllegalStateException("缺少facts.json，不能把开发用例当作独立验收");
        }
        ObjectMapper json = new ObjectMapper();
        JsonNode root = json.readTree(Files.readString(acceptance));
        var existing = new ExistingReturnReasonBatchService();
        var calls = new int[] {0};
        var outputs = new ArrayList<String>();
        var failure = new String[] {""};
        var llm = new LlmReturnReasonBatchService(existing, EvaluationSupport.cursorClient(calls, outputs, failure));
        List<Map<String, Object>> rows = new ArrayList<>();
        int normalCases = 0, normalPasses = 0, boundaryCases = 0, boundaryPasses = 0, existingCases = 0, existingPasses = 0;
        boolean safetyViolation = false;
        boolean stopped = false;
        long wallStart = System.nanoTime();
        for (JsonNode item : root.isArray() ? root : root.path("cases")) {
            if (stopped) break;
            List<Item> batch = new ArrayList<>();
            Map<String, Item> byId = new LinkedHashMap<>();
            item.path("input").path("items").forEach(node -> {
                Item one = new Item(text(node, "anonymousId"), text(node, "category"), text(node, "reason"), text(node, "description"));
                batch.add(one);
                if (one.anonymousId() != null) byId.put(one.anonymousId(), one);
            });
            Map<String, String> expectedLabels = new LinkedHashMap<>();
            Map<String, String> expectedEvidence = new LinkedHashMap<>();
            JsonNode expectedItems = item.path("expected").path("items");
            if (expectedItems.isArray()) {
                expectedItems.forEach(node -> {
                    if (!node.path("failed").asBoolean(false)) {
                        expectedLabels.put(node.path("anonymousId").asText(), node.path("label").asText());
                        if (!node.path("evidence").isNull()) expectedEvidence.put(node.path("anonymousId").asText(), node.path("evidence").asText());
                    }
                });
            } else {
                item.path("expected").path("labels").fields().forEachRemaining(entry -> expectedLabels.put(entry.getKey(), entry.getValue().asText()));
            }
            String expectedState = item.path("expected").path("state").asText();
            int denominator = item.path("expected").path("denominator").asInt();
            int failedCount = item.path("expected").path("failedCount").asInt();
            String kind = item.path("kind").asText();
            boolean normal = EvaluationSupport.normal(kind);
            boolean boundary = EvaluationSupport.boundary(kind);
            boolean llmBoundary = true;
            for (String implementation : List.of("existing", "llm")) {
                int before = calls[0];
                int outputFrom = outputs.size();
                failure[0] = "";
                long started = System.nanoTime();
                String error = "";
                Result actual = null;
                EvaluationSupport.Score score;
                try {
                    actual = "llm".equals(implementation) ? llm.analyze(batch) : existing.analyze(batch);
                    if (!failure[0].isBlank()) error = failure[0];
                    score = score(batch, expectedState, expectedLabels, denominator, failedCount, quoteSets(expectedEvidence), actual);
                    if (EvaluationSupport.infrastructureFailure(error)) {
                        score = EvaluationSupport.Score.transport(error);
                    }
                } catch (RuntimeException ex) {
                    error = EvaluationSupport.redact(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    score = EvaluationSupport.Score.exception(error);
                }
                boolean passed = score.passed();
                if (score.scored() && normal && "llm".equals(implementation)) { normalCases++; if (passed) normalPasses++; }
                if (score.scored() && normal && "existing".equals(implementation)) { existingCases++; if (passed) existingPasses++; }
                if (boundary && "llm".equals(implementation)) llmBoundary = score.scored() && passed;
                boolean quotes = actual != null && quotesMatch(actual, byId);
                if ((actual != null && !quotes) || (actual != null && boundary && actual.items().stream().anyMatch(row -> !row.failed()) && "UNSUPPORTED".equals(expectedState))) safetyViolation = true;
                if (actual != null) {
                    String title = ReturnReasonBatchService.batchTitle(actual.denominator());
                    if (title.contains("全月") || title.contains("全站")) safetyViolation = true;
                }
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
                row.put("modelOutput", EvaluationSupport.since(outputs, outputFrom));
                row.put("cost", EvaluationSupport.NO_USAGE);
                rows.add(row);
                System.out.println(item.path("id").asText() + " " + implementation + " " + (actual == null ? "EXCEPTION" : actual.state().name()) + " " + passed + " calls " + (calls[0] - before));
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
                "本题使用脱敏教学样本，不读取数据库。", rows,
                EvaluationSupport.onlineExtra(safetyViolation, blocked, boundariesHeld, normalCases, normalPasses,
                        existingPasses == 20 && !safetyViolation));
        System.out.println(decision + " " + normalPasses + "/" + normalCases);
        EvaluationSupport.exitIfRejected(decision);
    }

    private static Map<String, Object> structured(Result actual) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Classified item : actual.items()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("anonymousId", item.anonymousId());
            one.put("label", item.label() == null ? null : item.label().name());
            one.put("evidence", item.evidence());
            one.put("failed", item.failed());
            items.add(one);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", actual.state().name());
        body.put("items", items);
        body.put("counts", actual.counts());
        body.put("ratios", actual.ratios());
        body.put("denominator", actual.denominator());
        body.put("failedCount", actual.failedCount());
        return body;
    }

    public static void replay(Path acceptance, Path published, Path report) throws Exception {
        ObjectMapper json = new ObjectMapper();
        if (!Files.isRegularFile(acceptance) || !Files.isRegularFile(acceptance.resolveSibling("facts.json")) || !Files.isRegularFile(published)) {
            String decision = EvaluationSupport.decision(false, true, false, 0, 0, false);
            EvaluationSupport.writeReport(json, report.toString(), 0, System.nanoTime(), EvaluationSupport.codeOf(decision), decision, false, false,
                    "离线回放缺事实，不可重算。", List.of(), Map.of("replay", "不可重算", "note", EvaluationSupport.REPLAY_NOTE));
            return;
        }
        JsonNode old = json.readTree(Files.readString(published));
        Map<String, JsonNode> oldRows = new LinkedHashMap<>();
        int oldLlm = 0;
        int oldLlmPass = 0;
        for (JsonNode row : old.path("results")) {
            oldRows.put(row.path("id").asText() + "/" + row.path("implementation").asText(), row);
            if ("llm".equals(row.path("implementation").asText())) {
                oldLlm++;
                if (row.path("passed").asBoolean()) oldLlmPass++;
            }
        }
        var existing = new ExistingReturnReasonBatchService();
        List<Map<String, Object>> rows = new ArrayList<>();
        JsonNode root = json.readTree(Files.readString(acceptance));
        for (JsonNode item : root.isArray() ? root : root.path("cases")) {
            List<Item> batch = new ArrayList<>();
            item.path("input").path("items").forEach(node -> batch.add(new Item(text(node, "anonymousId"), text(node, "category"), text(node, "reason"), text(node, "description"))));
            Map<String, String> expectedLabels = new LinkedHashMap<>();
            Map<String, String> expectedEvidence = new LinkedHashMap<>();
            JsonNode expectedItems = item.path("expected").path("items");
            if (expectedItems.isArray()) {
                expectedItems.forEach(node -> {
                    if (!node.path("failed").asBoolean(false)) {
                        expectedLabels.put(node.path("anonymousId").asText(), node.path("label").asText());
                        if (!node.path("evidence").isNull() && !node.path("evidence").asText("").isBlank()) {
                            expectedEvidence.put(node.path("anonymousId").asText(), node.path("evidence").asText());
                        }
                    }
                });
            } else {
                item.path("expected").path("labels").fields().forEachRemaining(entry -> expectedLabels.put(entry.getKey(), entry.getValue().asText()));
            }
            String expectedState = item.path("expected").path("state").asText();
            int denominator = item.path("expected").path("denominator").asInt();
            int failedCount = item.path("expected").path("failedCount").asInt();
            for (String implementation : List.of("existing", "llm")) {
                JsonNode previous = oldRows.get(item.path("id").asText() + "/" + implementation);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.path("id").asText());
                row.put("implementation", implementation);
                EvaluationSupport.ReplayHistory history = EvaluationSupport.replayHistory(previous);
                row.put("oldPassed", previous == null ? "未执行" : previous.path("passed").asBoolean());
                if ("llm".equals(implementation) && EvaluationSupport.stampBlocked(row, history)) {
                    rows.add(row);
                    continue;
                }
                var script = new java.util.ArrayDeque<>(history.outputs());
                Result actual;
                EvaluationSupport.Score scored;
                try {
                    if ("llm".equals(implementation) && history.slot() == EvaluationSupport.ReplaySlot.PROTOCOL) {
                        new LlmReturnReasonBatchService(existing, (system, input) -> {
                            EvaluationSupport.rejectAnswerLeak(system, input);
                            return script.isEmpty() ? "" : script.remove();
                        }).analyze(batch);
                        EvaluationSupport.mark(row, false, true, "协议", "非法JSON");
                        rows.add(row);
                        continue;
                    }
                    actual = "llm".equals(implementation)
                            ? new LlmReturnReasonBatchService(existing, (system, input) -> {
                                EvaluationSupport.rejectAnswerLeak(system, input);
                                if (history.slot() == EvaluationSupport.ReplaySlot.LOCAL) {
                                    throw new IllegalStateException("零调用却请求模型");
                                }
                                if (script.isEmpty()) throw new IllegalStateException("不可重算");
                                String next = script.remove();
                                if (next.isBlank()) throw new IllegalStateException("空响应不是模型回答");
                                return next;
                            }).analyze(batch)
                            : existing.analyze(batch);
                    scored = score(batch, expectedState, expectedLabels, denominator, failedCount, quoteSets(expectedEvidence), actual);
                } catch (RuntimeException ex) {
                    if ("llm".equals(implementation) && (history.slot() == EvaluationSupport.ReplaySlot.LOCAL
                            || "不可重算".equals(ex.getMessage()))) {
                        EvaluationSupport.mark(row, false, false, "不可重算", "不可重算");
                        rows.add(row);
                        continue;
                    }
                    actual = null;
                    scored = EvaluationSupport.Score.exception(ex.getClass().getSimpleName());
                }
                row.put("passed", scored.passed());
                row.put("scored", scored.scored());
                row.put("actual", actual == null ? Map.of() : structured(actual));
                row.put("failedFields", scored.failedFields());
                row.put("errorCategory", scored.category());
                row.put("detail", scored.detail());
                row.put("countedAsCorrectRejection", false);
                rows.add(row);
            }
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        EvaluationSupport.putReplaySummary(extra, old, oldLlmPass, oldLlm, rows, published.getFileName().toString());
        String decision = EvaluationSupport.decision(false, true, false, 0, 0, false);
        EvaluationSupport.writeReport(json, report.toString(), 0, System.nanoTime(), EvaluationSupport.codeOf(decision), decision, false, false,
                "离线回放，未调用在线模型。", rows, extra);
    }

    private static boolean quotesMatch(Result actual, Map<String, Item> byId) {
        for (Classified item : actual.items()) {
            if (item.failed() || item.evidence() == null) {
                continue;
            }
            Item source = byId.get(item.anonymousId());
            if (source == null) {
                return false;
            }
            String text = (source.reason() == null ? "" : source.reason()) + "\n" + (source.description() == null ? "" : source.description());
            if (!text.contains(item.evidence())) {
                return false;
            }
        }
        return true;
    }

    private static boolean accountingMatches(Result actual) {
        if (actual.counts() == null || actual.ratios() == null) {
            return false;
        }
        Map<Label, Integer> observed = new java.util.EnumMap<>(Label.class);
        for (Label label : Label.values()) {
            observed.put(label, 0);
        }
        int failed = 0;
        for (Classified item : actual.items()) {
            if (item.failed() || item.label() == null) {
                failed++;
            } else {
                observed.merge(item.label(), 1, Integer::sum);
            }
        }
        if (failed != actual.failedCount()) {
            return false;
        }
        int denominator = actual.denominator();
        if (denominator == 0) {
            return actual.items().isEmpty() && observed.values().stream().allMatch(count -> count == 0)
                    && actual.counts().values().stream().allMatch(count -> count == 0)
                    && actual.ratios().values().stream().allMatch(ratio -> ratio.compareTo(BigDecimal.ZERO) == 0);
        }
        for (Label label : Label.values()) {
            if (!actual.counts().containsKey(label) || actual.counts().get(label) != observed.get(label)) {
                return false;
            }
            BigDecimal ratio = BigDecimal.valueOf(observed.get(label))
                    .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
            BigDecimal actualRatio = actual.ratios().get(label);
            if (actualRatio == null || actualRatio.compareTo(ratio) != 0) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, java.util.Set<String>> quoteSets(Map<String, String> evidence) {
        Map<String, java.util.Set<String>> accepted = new LinkedHashMap<>();
        evidence.forEach((id, quote) -> {
            if (quote != null && !quote.isBlank()) {
                accepted.put(id, java.util.Set.of(quote));
            }
        });
        return accepted;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
