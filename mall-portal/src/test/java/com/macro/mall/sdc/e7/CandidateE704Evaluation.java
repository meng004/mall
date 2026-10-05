package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.ai.comments.CommentTopicService.Result;
import com.macro.mall.portal.ai.comments.CommentTopicService.Topic;
import com.macro.mall.portal.ai.comments.ExistingCommentTopicService;
import com.macro.mall.portal.ai.comments.LlmCommentTopicService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 按主题集合比较。证据必须来自原文并命中既有主题规则。期望不进入模型。 */
public final class CandidateE704Evaluation {
    private CandidateE704Evaluation() {}

    public static boolean matches(String expectedState, Set<Topic> topics, Result actual) {
        return actual != null && expectedState != null && topics != null && actual.evidence() != null
                && expectedState.equals(actual.state().name()) && topics.equals(actual.evidence().keySet());
    }

    public static EvaluationSupport.Score score(String text, String expectedState, Set<Topic> topics, Result actual) {
        return score(text, expectedState, topics, Map.of(), actual);
    }

    public static EvaluationSupport.Score score(String text, String expectedState, Set<Topic> topics,
                                               Map<Topic, Set<String>> accepted, Result actual) {
        if (!matches(expectedState, topics, actual)) {
            String field = actual == null ? "actual" : !expectedState.equals(actual.state().name()) ? "state" : "topics";
            return EvaluationSupport.Score.fail(field, "业务", "主题集合或状态不一致");
        }
        boolean pending = false;
        boolean checkQuotes = accepted != null && !accepted.isEmpty();
        for (var entry : actual.evidence().entrySet()) {
            String quote = entry.getValue();
            if (quote == null || quote.isBlank() || text == null || !text.contains(quote)) {
                return EvaluationSupport.Score.fail("evidence", "业务", "证据为空或不在原文");
            }
            if (checkQuotes && !accepted.getOrDefault(entry.getKey(), Set.of()).contains(quote)) {
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
        if (args.length != 2) throw new IllegalArgumentException("Usage: CandidateE704Evaluation acceptance.json report.json");
        Path acceptance = Path.of(args[0]);
        if (!Files.isRegularFile(acceptance.resolveSibling("facts.json"))) {
            throw new IllegalStateException("缺少facts.json，不能把开发用例当作独立验收");
        }
        ObjectMapper json = new ObjectMapper();
        JsonNode cases = casesOf(json.readTree(Files.readString(acceptance)));
        var existing = new ExistingCommentTopicService();
        var calls = new int[] {0};
        var outputs = new ArrayList<String>();
        var failure = new String[] {""};
        var llm = new LlmCommentTopicService(existing, EvaluationSupport.cursorClient(calls, outputs, failure));
        List<Map<String, Object>> rows = new ArrayList<>();
        int normalCases = 0, normalPasses = 0, boundaryCases = 0, boundaryPasses = 0, existingCases = 0, existingPasses = 0;
        boolean safetyViolation = false;
        boolean stopped = false;
        long wallStart = System.nanoTime();
        for (JsonNode item : cases) {
            if (stopped) break;
            String text = item.path("input").path("text").asText("");
            Map<Topic, String> expectedEvidence = evidenceOf(item.path("expected").path("evidence"));
            String expectedState = item.path("expected").path("state").asText();
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
                    actual = "llm".equals(implementation) ? llm.classify(text) : existing.classify(text);
                    if (!failure[0].isBlank()) error = failure[0];
                    score = score(text, expectedState, expectedEvidence.keySet(), acceptedQuotes(expectedEvidence), actual);
                    if (EvaluationSupport.infrastructureFailure(error)) {
                        score = EvaluationSupport.Score.transport(error);
                    }
                } catch (RuntimeException ex) {
                    error = EvaluationSupport.redact(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    score = EvaluationSupport.Score.exception(error);
                }
                boolean passed = score.passed();
                boolean fabricated = actual != null && actual.evidence().values().stream().anyMatch(quote -> quote != null && !text.contains(quote));
                if (score.scored() && normal && "llm".equals(implementation)) { normalCases++; if (passed) normalPasses++; }
                if (score.scored() && normal && "existing".equals(implementation)) { existingCases++; if (passed) existingPasses++; }
                if (boundary && "llm".equals(implementation)) llmBoundary = score.scored() && passed;
                if (fabricated || (actual != null && boundary && !actual.evidence().isEmpty() && "UNSUPPORTED".equals(expectedState))) safetyViolation = true;
                Map<String, Object> row = rowOf(item, implementation, passed, actual == null ? "" : actual.state().name(), started, calls[0] - before, error, text);
                row.put("scored", score.scored());
                row.put("actual", actual == null ? Map.of() : actual.evidence());
                row.put("failedFields", score.failedFields());
                row.put("errorCategory", score.category());
                row.put("detail", score.detail());
                row.put("countedAsCorrectRejection", false);
                row.put("modelOutput", EvaluationSupport.since(outputs, outputFrom));
                rows.add(row);
                System.out.println(item.path("id").asText() + " " + implementation + " " + (actual == null ? "EXCEPTION" : actual.state().name()) + " " + passed);
                if ("llm".equals(implementation) && EvaluationSupport.infrastructureFailure(error)) {
                    stopped = true;
                    break;
                }
            }
            if (boundary) { boundaryCases++; if (llmBoundary) boundaryPasses++; }
        }
        boolean boundariesHeld = boundaryCases == 12 && boundaryPasses == 12;
        EvaluationSupport.appendNotRun(rows, cases);
        boolean blocked = EvaluationSupport.releaseBlocked(stopped, rows);
        String decision = EvaluationSupport.decision(safetyViolation, blocked, boundariesHeld, normalCases, normalPasses,
                existingPasses == 20 && !safetyViolation);
        String codeVerdict = EvaluationSupport.codeOf(decision);
        EvaluationSupport.writeReport(json, args[1], calls[0], wallStart, codeVerdict, decision, stopped, false, "本题不读取订单或商品库。", rows,
                EvaluationSupport.onlineExtra(safetyViolation, blocked, boundariesHeld, normalCases, normalPasses,
                        existingPasses == 20 && !safetyViolation));
        System.out.println(decision + " " + normalPasses + "/" + normalCases);
        EvaluationSupport.exitIfRejected(decision);
    }

    private static JsonNode casesOf(JsonNode root) {
        return root.isArray() ? root : root.path("cases");
    }

    private static Map<Topic, String> evidenceOf(JsonNode node) {
        Map<Topic, String> evidence = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> evidence.put(Topic.valueOf(entry.getKey()), entry.getValue().asText()));
        return evidence;
    }

    private static Map<Topic, Set<String>> acceptedQuotes(Map<Topic, String> evidence) {
        Map<Topic, Set<String>> accepted = new LinkedHashMap<>();
        evidence.forEach((topic, quote) -> {
            if (quote != null && !quote.isBlank()) {
                accepted.put(topic, Set.of(quote));
            }
        });
        return accepted;
    }

    private static Map<String, Object> rowOf(JsonNode item, String implementation, boolean passed, String state,
                                             long started, int modelCalls, String error, String text) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.path("id").asText());
        row.put("kind", item.path("kind").asText());
        row.put("implementation", implementation);
        row.put("passed", passed);
        row.put("actualState", state);
        row.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000);
        row.put("modelCalls", modelCalls);
        row.put("error", error);
        row.put("text", EvaluationSupport.redact(text));
        row.put("cost", EvaluationSupport.NO_USAGE);
        return row;
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
        var existing = new ExistingCommentTopicService();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode item : casesOf(json.readTree(Files.readString(acceptance)))) {
            String text = item.path("input").path("text").asText("");
            Map<Topic, String> expectedEvidence = evidenceOf(item.path("expected").path("evidence"));
            String expectedState = item.path("expected").path("state").asText();
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
                EvaluationSupport.Score score;
                try {
                    if ("llm".equals(implementation) && history.slot() == EvaluationSupport.ReplaySlot.PROTOCOL) {
                        new LlmCommentTopicService(existing, (system, input) -> {
                            EvaluationSupport.rejectAnswerLeak(system, input);
                            return script.isEmpty() ? "" : script.remove();
                        }).classify(text);
                        EvaluationSupport.mark(row, false, true, "协议", "非法JSON");
                        rows.add(row);
                        continue;
                    }
                    actual = "llm".equals(implementation)
                            ? new LlmCommentTopicService(existing, (system, input) -> {
                                EvaluationSupport.rejectAnswerLeak(system, input);
                                if (history.slot() == EvaluationSupport.ReplaySlot.LOCAL) {
                                    throw new IllegalStateException("零调用却请求模型");
                                }
                                if (script.isEmpty()) throw new IllegalStateException("不可重算");
                                String next = script.remove();
                                if (next.isBlank()) throw new IllegalStateException("空响应不是模型回答");
                                return next;
                            }).classify(text)
                            : existing.classify(text);
                    score = score(text, expectedState, expectedEvidence.keySet(), acceptedQuotes(expectedEvidence), actual);
                } catch (RuntimeException ex) {
                    if ("llm".equals(implementation) && (history.slot() == EvaluationSupport.ReplaySlot.LOCAL
                            || "不可重算".equals(ex.getMessage()))) {
                        EvaluationSupport.mark(row, false, false, "不可重算", "不可重算");
                        rows.add(row);
                        continue;
                    }
                    actual = null;
                    score = EvaluationSupport.Score.exception(ex.getClass().getSimpleName());
                }
                row.put("passed", score.passed());
                row.put("scored", score.scored());
                row.put("actual", actual == null ? Map.of() : actual.evidence());
                row.put("failedFields", score.failedFields());
                row.put("errorCategory", score.category());
                row.put("detail", score.detail());
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
}
