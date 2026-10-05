package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.ai.comments.CommentTopicService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** E7-04 评测器：按标签集合与原文依据计分，历史响应离线回放，不调用模型。 */
class CandidateE704EvaluationTest {
    @Test
    void supportingQuotesScoreTheSameAndContradictionsFail() {
        String comment = "质量没问题";
        var quality = new CommentTopicService.Result(CommentTopicService.State.OK, Map.of(CommentTopicService.Topic.QUALITY, "质量"));
        var longer = new CommentTopicService.Result(CommentTopicService.State.OK, Map.of(CommentTopicService.Topic.QUALITY, "质量没问题"));
        var wrongLabel = new CommentTopicService.Result(CommentTopicService.State.OK, Map.of(CommentTopicService.Topic.LOGISTICS, "质量没问题"));
        assertTrue(CandidateE704Evaluation.score(comment, "OK", Set.of(CommentTopicService.Topic.QUALITY), quality).passed());
        assertTrue(CandidateE704Evaluation.score(comment, "OK", Set.of(CommentTopicService.Topic.QUALITY), longer).passed());
        assertFalse(CandidateE704Evaluation.score(comment, "OK", Set.of(CommentTopicService.Topic.QUALITY), wrongLabel).passed());
    }

    @Test
    void publishedCounterexamplesDoNotPass() {
        var semantic = new CommentTopicService.Result(CommentTopicService.State.OK,
                Map.of(CommentTopicService.Topic.QUALITY, "客服确认缝线断开"));
        assertTrue(CandidateE704Evaluation.score("客服确认缝线断开", "OK",
                Set.of(CommentTopicService.Topic.QUALITY), semantic).passed());

        var fabricated = new CommentTopicService.Result(CommentTopicService.State.OK,
                Map.of(CommentTopicService.Topic.QUALITY, "凭空写出的句子"));
        var fake = CandidateE704Evaluation.score("客服确认缝线断开", "OK", Set.of(CommentTopicService.Topic.QUALITY), fabricated);
        assertFalse(fake.passed());
        assertTrue(fake.scored());
        assertEquals("业务", fake.category());
        var alternate = new CommentTopicService.Result(CommentTopicService.State.OK,
                Map.of(CommentTopicService.Topic.QUALITY, "客服确认缝线断开"));
        var pendingTopic = CandidateE704Evaluation.score("客服确认缝线断开", "OK", Set.of(CommentTopicService.Topic.QUALITY),
                Map.of(CommentTopicService.Topic.QUALITY, Set.of("缝线")), alternate);
        assertFalse(pendingTopic.passed());
        assertFalse(pendingTopic.scored());
        assertEquals("待人工核对", pendingTopic.category());
    }

    @Test
    void verdictUsesTheSharedThresholds() {
        assertEquals("INCOMPLETE", CandidateE704Evaluation.verdict(20, 20, 11, 11, true, false));
        assertEquals("NO_RELEASE", CandidateE704Evaluation.verdict(20, 20, 12, 11, true, false));
        assertNotEquals("REJECT", CandidateE704Evaluation.verdict(20, 20, 12, 11, true, false));
        assertEquals("DEGRADE", CandidateE704Evaluation.verdict(20, 17, 12, 12, true, false));
    }

    @Test
    void offlineReplayWritesDifferenceWithoutReplacingTheFirstReport(@TempDir Path temporary) throws Exception {
        Path task = EvaluationFiles.task("E7-04");
        Path acceptance = task.resolve("acceptance.json");
        Path published = task.resolve("historical").resolve("model-eval.json");
        byte[] publishedBytes = Files.readAllBytes(published);
        assertTrue(new String(publishedBytes).contains("课程离线测试已通过"));
        Path committed = task.resolve("historical").resolve("replay.json");
        byte[] replayBytes = Files.readAllBytes(committed);
        Path report = temporary.resolve("E7-04-replay.json");
        CandidateE704Evaluation.replay(acceptance, published, report);
        assertArrayEquals(publishedBytes, Files.readAllBytes(published));
        assertArrayEquals(replayBytes, Files.readAllBytes(committed));
        var replay = new ObjectMapper().readTree(Files.readString(report));
        EvaluationFiles.assertReplayReport(replay);
        assertNotEquals("24/29", replay.path("newLlmPassed").asText());
        JsonNode a02 = EvaluationFiles.llmRow(replay, "E7-04-A02");
        assertTrue(a02.path("passed").asBoolean());
        assertTrue(a02.path("scored").asBoolean());
        assertNotEquals("不可重算", a02.path("detail").asText());
    }

    @Test
    void replayHistorySeparatesLocalTransportAndProtocol(@TempDir Path temporary) throws Exception {
        Files.writeString(temporary.resolve("facts.json"), "{}");
        Files.writeString(temporary.resolve("acceptance.json"), """
                {"cases":[
                  {"id":"local","kind":"adversarial","input":{"text":""},"expected":{"state":"NEEDS_INPUT","evidence":{}}},
                  {"id":"transport","kind":"normal","input":{"text":"质量没问题"},"expected":{"state":"OK","evidence":{"QUALITY":"质量没问题"}}},
                  {"id":"protocol","kind":"normal","input":{"text":"质量没问题"},"expected":{"state":"OK","evidence":{"QUALITY":"质量没问题"}}},
                  {"id":"absent","kind":"normal","input":{"text":"质量没问题"},"expected":{"state":"OK","evidence":{"QUALITY":"质量没问题"}}}
                ]}
                """);
        Files.writeString(temporary.resolve("model-eval.json"), """
                {"results":[
                  {"id":"local","implementation":"llm","passed":true,"modelCalls":0,"error":"","modelOutput":[]},
                  {"id":"transport","implementation":"llm","passed":false,"modelCalls":1,"error":"IllegalStateException: Cursor model request timed out","modelOutput":[""]},
                  {"id":"protocol","implementation":"llm","passed":false,"modelCalls":1,"error":"","modelOutput":["{"]}
                ]}
                """);
        Path report = temporary.resolve("history-replay.json");
        CandidateE704Evaluation.replay(temporary.resolve("acceptance.json"), temporary.resolve("model-eval.json"), report);
        var replay = new ObjectMapper().readTree(Files.readString(report));
        JsonNode local = EvaluationFiles.llmRow(replay, "local");
        assertTrue(local.path("passed").asBoolean());
        assertTrue(local.path("scored").asBoolean());
        JsonNode transport = EvaluationFiles.llmRow(replay, "transport");
        assertEquals("传输未完成", transport.path("errorCategory").asText());
        assertTrue(transport.path("error").asText().contains("timed out"));
        assertFalse(transport.path("scored").asBoolean());
        JsonNode protocol = EvaluationFiles.llmRow(replay, "protocol");
        assertEquals("协议", protocol.path("errorCategory").asText());
        assertTrue(protocol.path("scored").asBoolean());
        assertFalse(protocol.path("passed").asBoolean());
        JsonNode absent = EvaluationFiles.llmRow(replay, "absent");
        assertEquals("未执行", absent.path("errorCategory").asText());
        assertFalse(absent.path("scored").asBoolean());
        EvaluationFiles.assertTallyAddsUp(replay.path("tally"));
    }
}
