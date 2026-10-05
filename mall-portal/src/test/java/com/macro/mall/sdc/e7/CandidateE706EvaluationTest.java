package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.ai.reasons.ExistingReturnReasonBatchService;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Classified;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Item;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Label;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
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
    void verdictUsesTheSharedThresholds() {
        assertEquals("NO_RELEASE", CandidateE706Evaluation.verdict(20, 20, 12, 11, true, false));
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
