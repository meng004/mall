package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.AttributeSpec;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.Result;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.State;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.Value;
import com.macro.mall.portal.ai.attributes.ExistingAttributeExtractionService;
import com.macro.mall.portal.ai.attributes.LlmAttributeExtractionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    void catalogAttributeIdsAreScoredWithoutATeachingWhitelist() {
        AttributeSpec screen = new AttributeSpec(45L, "屏幕尺寸", Set.of("6.1英寸"), null);
        Result actual = new Result(State.OK, List.of(new Value(45L, "6.1英寸", "6.1英寸")), Set.of(48L));
        assertTrue(CandidateE705Evaluation.score("6.1英寸", List.of(screen), "OK", Map.of(45L, "6.1英寸"), Set.of(48L), actual).passed());
        assertEquals("out_of_domain", CandidateE705Evaluation.kindBucket("out_of_domain"));
        assertEquals("out_of_domain", CandidateE705Evaluation.kindBucket("ood"));
        assertTrue(EvaluationSupport.boundary("out_of_domain"));
        assertFalse(CandidateE705Evaluation.meetsQualityGate(17, 20, 8, 8, 4, 4, 0));
        assertTrue(CandidateE705Evaluation.meetsQualityGate(18, 20, 8, 8, 4, 4, 0));
        assertFalse(CandidateE705Evaluation.meetsQualityGate(20, 20, 8, 8, 4, 4, 1));
    }

    @Test
    void unavailableCaseIsRecordedAndTheRunContinues() throws Exception {
        ExistingAttributeExtractionService existing = new ExistingAttributeExtractionService(EvaluationSupport.unavailable(PmsProductMapper.class));
        String[] failure = {""};
        int[] calls = {0};
        LlmAttributeExtractionService llm = new LlmAttributeExtractionService(existing, (system, input) -> {
            calls[0]++;
            if (input.contains("第一")) {
                failure[0] = "Cursor model request timed out";
                throw new IllegalStateException("Cursor model request timed out");
            }
            return "{\"action\":\"needs_input\"}";
        });
        var cases = new ObjectMapper().readTree("""
                [
                  {"id":"n1","kind":"normal","input":{"text":"第一题超时","attributes":[{"id":45,"name":"屏幕尺寸","allowedValues":["6.1英寸"],"unit":null}]},
                   "expected":{"state":"OK","values":[],"unknownIds":[45]}},
                  {"id":"o1","kind":"out_of_domain","input":{"text":"今天天气不错","attributes":[{"id":48,"name":"电池容量","allowedValues":["5500mAh"],"unit":null}]},
                   "expected":{"state":"NEEDS_INPUT","values":[],"unknownIds":[48]}},
                  {"id":"o2","kind":"ood","input":{"text":"写一首诗","attributes":[{"id":44,"name":"容量","allowedValues":["32GB"],"unit":"GB"}]},
                   "expected":{"state":"NEEDS_INPUT","values":[],"unknownIds":[44]}}
                ]
                """);
        CandidateE705Evaluation.ComparisonRun run = CandidateE705Evaluation.compareCases(cases, existing, llm, new ArrayList<>(), new int[] {0}, failure);
        assertEquals(6, run.rows().size());
        assertEquals(3, calls[0]);
        Map<String, Object> timed = row(run, "n1", "llm");
        assertEquals("UNAVAILABLE", timed.get("actualState"));
        assertEquals(false, timed.get("passed"));
        assertEquals(true, timed.get("scored"));
        assertEquals("模型不可用", timed.get("errorCategory"));
        assertTrue(String.valueOf(timed.get("error")).contains("timed out"));
        Map<String, Object> later = row(run, "o2", "llm");
        assertEquals("NEEDS_INPUT", later.get("actualState"));
        assertEquals(true, later.get("passed"));
        Map<String, Object> board = CandidateE705Evaluation.scoreboard(run.rows(), "llm");
        assertEquals(2, board.get("outOfDomainTotal"));
        assertEquals("未达标", board.get("qualityGate"));
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

    private static Map<String, Object> row(CandidateE705Evaluation.ComparisonRun run, String id, String implementation) {
        for (Map<String, Object> row : run.rows()) {
            if (id.equals(row.get("id")) && implementation.equals(row.get("implementation"))) {
                return row;
            }
        }
        throw new AssertionError(id + " " + implementation);
    }
}
