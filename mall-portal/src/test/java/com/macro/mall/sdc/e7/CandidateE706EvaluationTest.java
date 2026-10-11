package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.ai.reasons.ExistingReturnReasonBatchService;
import com.macro.mall.portal.ai.reasons.LlmReturnReasonBatchService;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Classified;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Item;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Label;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** E7-06 评测器：按逐条标签、本批分母、数量与比例计分，历史响应离线回放，不调用模型。 */
class CandidateE706EvaluationTest {
    @Test
    void supportingQuotesScoreTheSameAndContradictionsFail() {
        Item reason = new Item("b01", "鞋", "因为质量问题", "");
        var shortReason = ExistingReturnReasonBatchService.aggregate(null, List.of(new Classified("b01", Label.QUALITY, "质量问题", false)), 1);
        var longReason = ExistingReturnReasonBatchService.aggregate(null, List.of(new Classified("b01", Label.QUALITY, "因为质量问题", false)), 1);
        var wrongReason = ExistingReturnReasonBatchService.aggregate(null, List.of(new Classified("b01", Label.LOGISTICS, "质量问题", false)), 1);
        assertTrue(CandidateE706Evaluation.score(List.of(reason), "OK", Map.of("b01", "QUALITY"), 1, 0, shortReason).passed());
        assertTrue(CandidateE706Evaluation.score(List.of(reason), "OK", Map.of("b01", "QUALITY"), 1, 0, longReason).passed());
        assertFalse(CandidateE706Evaluation.score(List.of(reason), "OK", Map.of("b01", "QUALITY"), 1, 0, wrongReason).passed());
    }

    @Test
    void publishedCounterexamplesDoNotPass() {
        var counts = new java.util.EnumMap<Label, Integer>(Label.class);
        var ratios = new java.util.EnumMap<Label, java.math.BigDecimal>(Label.class);
        for (Label label : Label.values()) {
            counts.put(label, label == Label.LOGISTICS ? 1 : 0);
            ratios.put(label, label == Label.LOGISTICS ? java.math.BigDecimal.ONE : java.math.BigDecimal.ZERO);
        }
        var corrupt = new com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Result(
                com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.State.OK,
                List.of(new Classified("a", Label.QUALITY, "质量问题", false)), counts, ratios, 1, 0);
        assertFalse(CandidateE706Evaluation.score(List.of(new Item("a", "鞋", "质量问题", "")), "OK",
                Map.of("a", "QUALITY"), 1, 0, corrupt).passed());


        var primaryCounts = new java.util.EnumMap<Label, Integer>(Label.class);
        var primaryRatios = new java.util.EnumMap<Label, java.math.BigDecimal>(Label.class);
        for (Label label : Label.values()) {
            primaryCounts.put(label, label == Label.LOGISTICS ? 1 : 0);
            primaryRatios.put(label, label == Label.LOGISTICS ? java.math.BigDecimal.ONE : java.math.BigDecimal.ZERO);
        }
        var primary = new com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Result(
                com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.State.OK,
                List.of(new Classified("a", Label.LOGISTICS, "不是质量问题，是物流问题", false)),
                primaryCounts, primaryRatios, 1, 0);
        assertTrue(CandidateE706Evaluation.score(List.of(new Item("a", "鞋", "不是质量问题，是物流问题", "")),
                "OK", Map.of("a", "LOGISTICS"), 1, 0, primary).passed());

        var quoted = CandidateE706Evaluation.score(List.of(new Item("a", "鞋", "不是质量问题，是物流问题", "")),
                "OK", Map.of("a", "LOGISTICS"), 1, 0, Map.of("a", Set.of("不是质量问题，是物流问题")), primary);
        assertTrue(quoted.passed());
        var pendingQuote = CandidateE706Evaluation.score(List.of(new Item("a", "鞋", "不是质量问题，是物流问题", "")),
                "OK", Map.of("a", "LOGISTICS"), 1, 0, Map.of("a", Set.of("物流问题")), primary);
        assertFalse(pendingQuote.passed());
        assertFalse(pendingQuote.scored());
        assertEquals("待人工核对", pendingQuote.category());
    }

    @Test
    void countsFollowItemsNotTheReportedMap() {
        Item quality = new Item("a", "鞋", "质量问题", "");
        Item logistics = new Item("b", "鞋", "物流问题", "");
        var both = new com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Result(
                com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.State.OK,
                List.of(new Classified("a", Label.QUALITY, "质量问题", false), new Classified("b", Label.LOGISTICS, "物流问题", false)),
                counts(Map.of(Label.QUALITY, 1, Label.LOGISTICS, 1)), ratios(2, Map.of(Label.QUALITY, 1, Label.LOGISTICS, 1)), 2, 0);
        assertTrue(CandidateE706Evaluation.score(List.of(quality, logistics), "OK",
                Map.of("a", "QUALITY", "b", "LOGISTICS"), 2, 0, both).passed());

        var wrongRatio = ratios(1, Map.of(Label.QUALITY, 1));
        wrongRatio.put(Label.QUALITY, new BigDecimal("0.5000"));
        var skewed = new com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Result(
                com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.State.OK,
                List.of(new Classified("a", Label.QUALITY, "质量问题", false)),
                counts(Map.of(Label.QUALITY, 1)), wrongRatio, 1, 0);
        var ratioScore = CandidateE706Evaluation.score(List.of(quality), "OK", Map.of("a", "QUALITY"), 1, 0, skewed);
        assertFalse(ratioScore.passed());
        assertTrue(ratioScore.scored());
        assertEquals("业务", ratioScore.category());

        var dropped = new com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Result(
                com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.State.OK,
                List.of(new Classified("a", Label.QUALITY, "质量问题", false)),
                counts(Map.of(Label.QUALITY, 1)), ratios(1, Map.of(Label.QUALITY, 1)), 1, 0);
        assertFalse(CandidateE706Evaluation.score(List.of(quality, logistics), "PARTIAL",
                Map.of("a", "QUALITY"), 2, 1, dropped).passed());

        assertTrue(CandidateE706Evaluation.score(List.of(), "NEEDS_INPUT", Map.of(), 0, 0,
                ExistingReturnReasonBatchService.empty()).passed());
        var dirtyEmpty = new com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Result(
                com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.State.NEEDS_INPUT,
                List.of(), counts(Map.of(Label.LOGISTICS, 1)), ratios(1, Map.of(Label.LOGISTICS, 1)), 0, 0);
        assertFalse(CandidateE706Evaluation.score(List.of(), "NEEDS_INPUT", Map.of(), 0, 0, dirtyEmpty).passed());
    }


    @Test
    void illegalShapeFailsWithoutAbortingTheReport() {

        var repeated = new com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Result(
                com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.State.OK,
                List.of(new Classified("a", Label.QUALITY, "质量问题", false), new Classified("a", Label.LOGISTICS, "物流问题", false)),
                Map.of(), Map.of(), 2, 0);
        assertDoesNotThrow(() -> CandidateE706Evaluation.matches("OK", Map.of("a", "QUALITY"), 2, 0, repeated));
        assertTrue(CandidateE706Evaluation.failure("OK", Map.of("a", "QUALITY"), 2, 0, repeated).contains("重复"));

        var one = new com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Result(
                com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.State.OK,
                List.of(new Classified("b01", Label.QUALITY, "质量问题", false)),
                Map.of(Label.QUALITY, 1), Map.of(), 1, 0);
        assertEquals("labels", CandidateE706Evaluation.failure("OK", Map.of("b01", "QUALITY", "b02", "LOGISTICS"), 1, 0, one));
        assertEquals("denominator", CandidateE706Evaluation.failure("OK", Map.of("b01", "QUALITY"), 2, 0, one));
    }

    @Test
    void uncertainNullEvidenceIsAcceptedAndOtherNullEvidenceFails() {
        Item blank = new Item("b01", "鞋", "质量问题", "");
        var uncertain = ExistingReturnReasonBatchService.aggregate(null,
                List.of(new Classified("b01", Label.UNCERTAIN, null, false)), 1);
        assertTrue(CandidateE706Evaluation.score(List.of(blank), "OK", Map.of("b01", "UNCERTAIN"), 1, 0, uncertain).passed());
        var missingQuote = ExistingReturnReasonBatchService.aggregate(null,
                List.of(new Classified("b01", Label.QUALITY, null, false)), 1);
        var failed = CandidateE706Evaluation.score(List.of(blank), "OK", Map.of("b01", "QUALITY"), 1, 0, missingQuote);
        assertFalse(failed.passed());
        assertEquals("evidence", failed.failedFields());
        var rejected = ExistingReturnReasonBatchService.rejected(List.of(
                new Item("b01", "鞋", "", "鞋底开胶"),
                new Item("b01", "鞋", "", "快递慢")));
        assertTrue(CandidateE706Evaluation.score(List.of(
                new Item("b01", "鞋", "", "鞋底开胶"),
                new Item("b01", "鞋", "", "快递慢")), "UNSUPPORTED", Map.of(), 2, 0, rejected).passed());
    }

    @Test
    void verdictUsesTheSharedThresholds() {
        assertEquals("NO_RELEASE", CandidateE706Evaluation.verdict(20, 20, 12, 11, true, false));
    }

    @Test
    void uncertainQuoteIsAcceptedWhenAnnotationIsNull() {
        Item item = new Item("b01", "鞋", "", "鞋底开胶");
        var quoted = ExistingReturnReasonBatchService.aggregate(null,
                List.of(new Classified("b01", Label.UNCERTAIN, "鞋底开胶", false)), 1);
        assertTrue(CandidateE706Evaluation.score(List.of(item), "OK", Map.of("b01", "UNCERTAIN"), 1, 0, quoted).passed());
    }

    @Test
    void evidenceArrayAcceptsAnyListedFragment() throws Exception {
        var cases = new ObjectMapper().readTree("""
                [{"id":"n","kind":"normal","input":{"items":[
                    {"anonymousId":"b01","category":"鞋","reason":"","description":"鞋底开胶"}]},
                  "expected":{"state":"OK","denominator":1,"failedCount":0,"items":[
                    {"anonymousId":"b01","label":"QUALITY","evidence":["鞋底开胶","开胶"],"failed":false}]}}]
                """);
        ReturnReasonBatchService phrase = input -> ExistingReturnReasonBatchService.aggregate(null,
                List.of(new Classified("b01", Label.QUALITY, "开胶", false)), 1);
        var run = CandidateE706Evaluation.compareCases(cases, phrase, phrase, new ArrayList<>(), new int[] {0}, new String[] {""});
        assertEquals(true, row(run, "n", "llm").get("passed"));
        ReturnReasonBatchService other = input -> ExistingReturnReasonBatchService.aggregate(null,
                List.of(new Classified("b01", Label.QUALITY, "鞋底", false)), 1);
        var missed = CandidateE706Evaluation.compareCases(cases, other, other, new ArrayList<>(), new int[] {0}, new String[] {""});
        assertEquals("待人工核对", row(missed, "n", "llm").get("errorCategory"));
    }

    @Test
    void bothImplementationsSeeTheSameItemsAndTimeoutContinues() throws Exception {
        List<List<Item>> seen = new ArrayList<>();
        ReturnReasonBatchService recorder = input -> {
            seen.add(input == null ? List.of() : List.copyOf(input));
            return ExistingReturnReasonBatchService.empty();
        };
        var same = new ObjectMapper().readTree("""
                [{"id":"n","kind":"normal","input":{"items":[
                    {"anonymousId":"b01","category":"鞋","reason":"质量问题","description":"示例鞋带断开"}]},
                  "expected":{"state":"NEEDS_INPUT","denominator":0,"failedCount":0,"items":[]}}]
                """);
        CandidateE706Evaluation.compareCases(same, recorder, recorder, new ArrayList<>(), new int[] {0}, new String[] {""});
        assertEquals(2, seen.size());
        assertEquals(seen.get(0), seen.get(1));
        assertEquals("b01", seen.get(0).get(0).anonymousId());
        assertEquals("示例鞋带断开", seen.get(0).get(0).description());

        String[] failure = {""};
        var existing = new ExistingReturnReasonBatchService();
        var llm = new LlmReturnReasonBatchService(existing, (system, input) -> {
            if (input.contains("超时这一条")) {
                failure[0] = "IllegalStateException: Cursor model request timed out elapsedMillis=60000";
                throw new IllegalStateException("Cursor model request timed out");
            }
            return "{\"action\":\"needs_input\"}";
        });
        var cases = new ObjectMapper().readTree("""
                [
                  {"id":"n1","kind":"normal","input":{"items":[
                    {"anonymousId":"b01","category":"鞋","reason":"","description":"鞋底开胶"}]},
                   "expected":{"state":"OK","denominator":1,"failedCount":0,"items":[
                    {"anonymousId":"b01","label":"UNCERTAIN","evidence":null,"failed":false}]}},
                  {"id":"a1","kind":"adversarial","input":{"items":[
                    {"anonymousId":"b01","category":"鞋","reason":"","description":"超时这一条"}]},
                   "expected":{"state":"OK","denominator":1,"failedCount":0,"items":[
                    {"anonymousId":"b01","label":"UNCERTAIN","evidence":null,"failed":false}]}},
                  {"id":"o1","kind":"out_of_domain","input":{"items":[
                    {"anonymousId":"b01","category":"鞋","reason":"","description":"今天天气怎么样"}]},
                   "expected":{"state":"OK","denominator":1,"failedCount":0,"items":[
                    {"anonymousId":"b01","label":"UNCERTAIN","evidence":null,"failed":false}]}},
                  {"id":"o2","kind":"ood","input":{"items":[
                    {"anonymousId":"b01","category":"鞋","reason":"质量问题","description":""}]},
                   "expected":{"state":"OK","denominator":1,"failedCount":0,"items":[
                    {"anonymousId":"b01","label":"UNCERTAIN","evidence":null,"failed":false}]}}
                ]
                """);
        CandidateE706Evaluation.ComparisonRun run = CandidateE706Evaluation.compareCases(
                cases, existing, llm, new ArrayList<>(), new int[] {0}, failure);
        assertEquals(8, run.rows().size());
        Map<String, Object> timed = row(run, "a1", "llm");
        assertEquals("UNAVAILABLE", timed.get("actualState"));
        assertEquals(false, timed.get("passed"));
        assertEquals(true, timed.get("scored"));
        assertEquals("模型不可用", timed.get("errorCategory"));
        assertEquals(false, timed.get("countedAsCorrectRejection"));
        Map<String, Object> after = row(run, "o1", "llm");
        assertEquals("OK", after.get("actualState"));
        assertEquals(true, after.get("passed"));
        assertEquals(true, row(run, "n1", "existing").get("passed"));
        assertEquals(true, row(run, "n1", "llm").get("passed"));
        assertEquals(false, row(run, "o2", "existing").get("passed"));
        assertEquals(true, row(run, "o2", "llm").get("passed"));
        assertEquals("QUALITY", labelOf(row(run, "o2", "existing")));
        assertFalse(run.safetyViolation());
    }

    @Test
    void qualityGateNeedsEighteenNormalAndEveryBoundaryCase() {
        assertTrue(CandidateE706Evaluation.meetsQualityGate(20, 20, 8, 8, 4, 4, 0));
        assertTrue(CandidateE706Evaluation.meetsQualityGate(18, 20, 8, 8, 4, 4, 0));
        assertFalse(CandidateE706Evaluation.meetsQualityGate(17, 20, 8, 8, 4, 4, 0));
        assertFalse(CandidateE706Evaluation.meetsQualityGate(20, 20, 7, 8, 4, 4, 0));
        assertFalse(CandidateE706Evaluation.meetsQualityGate(20, 20, 8, 8, 3, 4, 0));
        assertFalse(CandidateE706Evaluation.meetsQualityGate(18, 18, 8, 8, 4, 4, 0));
        assertFalse(CandidateE706Evaluation.meetsQualityGate(18, 20, 8, 8, 4, 4, 1));
        assertTrue(CandidateE706Evaluation.QUALITY_NOTE.contains("不构成放行"));
        assertTrue(EvaluationSupport.boundary("out_of_domain"));
        assertTrue(EvaluationSupport.boundary("ood"));
        assertFalse(EvaluationSupport.boundary("normal"));
    }

    @Test
    void sameCasesAreScoredSeparatelyAndOodAliasCountsAsOutOfDomain() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rows.add(scored("n" + i, "normal", "existing", i < 18));
            rows.add(scored("n" + i, "normal", "llm", i < 17));
        }
        for (int i = 0; i < 8; i++) {
            rows.add(scored("a" + i, "adversarial", "existing", true));
            rows.add(scored("a" + i, "adversarial", "llm", true));
        }
        for (int i = 0; i < 2; i++) {
            rows.add(scored("o" + i, "out_of_domain", "existing", true));
            rows.add(scored("o" + i, "out_of_domain", "llm", true));
        }
        for (int i = 2; i < 4; i++) {
            rows.add(scored("o" + i, "ood", "existing", true));
            rows.add(scored("o" + i, "ood", "llm", true));
        }
        Map<String, Object> plain = CandidateE706Evaluation.scoreboard(rows, "existing");
        Map<String, Object> llm = CandidateE706Evaluation.scoreboard(rows, "llm");
        assertEquals("18/20", plain.get("normal"));
        assertEquals("8/8", plain.get("adversarial"));
        assertEquals("4/4", plain.get("outOfDomain"));
        assertEquals("达标", plain.get("qualityGate"));
        assertEquals("17/20", llm.get("normal"));
        assertEquals("未达标", llm.get("qualityGate"));
    }

    @Test
    void offlineReplayWritesDifferenceWithoutReplacingTheFirstReport(@TempDir Path temporary) throws Exception {
        Path task = EvaluationFiles.task("E7-06");
        Path acceptance = task.resolve("acceptance.json");
        Path published = task.resolve("historical").resolve("model-eval.json");
        byte[] publishedBytes = Files.readAllBytes(published);
        assertTrue(new String(publishedBytes).contains("课程离线测试已通过"));
        Path committed = task.resolve("historical").resolve("replay.json");
        byte[] replayBytes = Files.readAllBytes(committed);
        Path report = temporary.resolve("E7-06-replay.json");
        CandidateE706Evaluation.replay(acceptance, published, report);
        assertArrayEquals(publishedBytes, Files.readAllBytes(published));
        assertArrayEquals(replayBytes, Files.readAllBytes(committed));
        var replay = new ObjectMapper().readTree(Files.readString(report));
        EvaluationFiles.assertReplayReport(replay);
        assertNotEquals("1/3", replay.path("newLlmPassed").asText());
        JsonNode n12 = EvaluationFiles.llmRow(replay, "E7-06-N12");
        assertEquals("传输未完成", n12.path("errorCategory").asText());
        assertTrue(n12.path("error").asText().contains("timed out"));
        assertFalse(n12.path("scored").asBoolean());
    }

    private static Map<String, Object> row(CandidateE706Evaluation.ComparisonRun run, String id, String implementation) {
        return run.rows().stream()
                .filter(item -> id.equals(item.get("id")) && implementation.equals(item.get("implementation")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static String labelOf(Map<String, Object> row) {
        Map<String, Object> actual = (Map<String, Object>) row.get("actual");
        List<Map<String, Object>> items = (List<Map<String, Object>>) actual.get("items");
        return String.valueOf(items.get(0).get("label"));
    }

    private static Map<String, Object> scored(String id, String kind, String implementation, boolean passed) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("kind", kind);
        row.put("implementation", implementation);
        row.put("passed", passed);
        row.put("scored", true);
        return row;
    }

    private static Map<Label, Integer> counts(Map<Label, Integer> overrides) {
        Map<Label, Integer> counts = new EnumMap<>(Label.class);
        for (Label label : Label.values()) {
            counts.put(label, 0);
        }
        counts.putAll(overrides);
        return counts;
    }

    private static Map<Label, BigDecimal> ratios(int denominator, Map<Label, Integer> overrides) {
        Map<Label, Integer> counts = counts(overrides);
        Map<Label, BigDecimal> ratios = new EnumMap<>(Label.class);
        for (Label label : Label.values()) {
            ratios.put(label, BigDecimal.valueOf(counts.get(label)).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP));
        }
        return ratios;
    }
}
