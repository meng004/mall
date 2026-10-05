package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.AttributeSpec;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.Result;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.State;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** E7-05 评测器：按属性 ID、取值与原文依据计分，历史响应离线回放，不调用模型。 */
class CandidateE705EvaluationTest {
    @Test
    void supportingQuotesScoreTheSameAndContradictionsFail() {
        AttributeSpec color = new AttributeSpec(21L, "颜色", Set.of("红", "蓝"), null);
        String text = "蓝色";
        Result blue = new Result(State.OK, List.of(new Value(21L, "蓝", "蓝")), Set.of());
        Result longBlue = new Result(State.OK, List.of(new Value(21L, "蓝", "蓝色")), Set.of());
        Result red = new Result(State.OK, List.of(new Value(21L, "红", "蓝色")), Set.of());
        assertTrue(CandidateE705Evaluation.score(text, List.of(color), "OK", Map.of(21L, "蓝"), Set.of(), blue).passed());
        assertTrue(CandidateE705Evaluation.score(text, List.of(color), "OK", Map.of(21L, "蓝"), Set.of(), longBlue).passed());
        assertFalse(CandidateE705Evaluation.score(text, List.of(color), "OK", Map.of(21L, "红"), Set.of(), red).passed());
        assertFalse(CandidateE705Evaluation.score(text, List.of(color), "OK", Map.of(21L, "蓝"), Set.of(), red).passed());

        Result unrelated = new Result(State.OK, List.of(new Value(21L, "蓝", "手机")), Set.of());
        EvaluationSupport.Score pending = CandidateE705Evaluation.score("蓝色手机", List.of(color), "OK", Map.of(21L, "蓝"), Set.of(), unrelated);
        assertFalse(pending.passed());
        assertFalse(pending.scored());
        assertEquals("待人工核对", pending.category());
    }

    @Test
    void illegalShapeFailsWithoutAbortingTheReport() {
        Result duplicate = new Result(State.OK, List.of(new Value(21L, "蓝", "蓝"), new Value(21L, "红", "蓝")), Set.of());
        assertDoesNotThrow(() -> CandidateE705Evaluation.matches("OK", Map.of(21L, "蓝"), Set.of(), duplicate));
        assertFalse(CandidateE705Evaluation.matches("OK", Map.of(21L, "蓝"), Set.of(), duplicate));
        assertTrue(CandidateE705Evaluation.failure("OK", Map.of(21L, "蓝"), Set.of(), duplicate).contains("重复"));
    }

    @Test
    void verdictUsesTheSharedThresholds() {
        assertEquals("REJECT", CandidateE705Evaluation.verdict(20, 20, 12, 12, true, true));
        assertEquals("INCOMPLETE", CandidateE705Evaluation.verdict(19, 19, 12, 12, true, false));
    }

    @Test
    void offlineReplayWritesDifferenceWithoutReplacingTheFirstReport(@TempDir Path temporary) throws Exception {
        Path task = EvaluationFiles.task("E7-05");
        Path acceptance = task.resolve("acceptance.json");
        Path published = task.resolve("historical").resolve("model-eval.json");
        byte[] publishedBytes = Files.readAllBytes(published);
        assertTrue(new String(publishedBytes).contains("课程离线测试已通过"));
        Path committed = task.resolve("historical").resolve("replay.json");
        byte[] replayBytes = Files.readAllBytes(committed);
        Path report = temporary.resolve("E7-05-replay.json");
        CandidateE705Evaluation.replay(acceptance, published, report);
        assertArrayEquals(publishedBytes, Files.readAllBytes(published));
        assertArrayEquals(replayBytes, Files.readAllBytes(committed));
        EvaluationFiles.assertReplayReport(new ObjectMapper().readTree(Files.readString(report)));
    }
}
