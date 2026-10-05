package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 评测测试共用：定位 SDC/E7/evaluation/<示例> 题集目录，并检查回放报告的口径。 */
final class EvaluationFiles {
    private EvaluationFiles() {}

    static Path task(String id) {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            Path candidate = path.resolve("SDC/E7/evaluation").resolve(id);
            if (Files.isRegularFile(candidate.resolve("acceptance.json"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到 " + id + " 验收文件");
    }

    /** 回放只说明历史响应在当前比较器下的结果：不是新盲测，计数自洽，结论与代码一致。 */
    static void assertReplayReport(JsonNode replay) {
        assertTrue(replay.path("note").asText().contains("不是新盲测"));
        assertTrue(replay.path("courseOfflineTests").asText().contains("不是新盲测"));
        assertEquals("适配器未暴露", replay.path("usage").asText());
        assertFalse(replay.path("oldLlmPassed").asText().isBlank());
        assertTrue(replay.path("newLlmPassed").asText().contains("完整题量"));
        assertTrue(replay.path("newLlmPassed").asText().contains("不是新盲测"));
        assertTrue(replay.has("difference"));
        assertEquals(EvaluationSupport.codeOf(replay.path("decision").asText()), replay.path("codeVerdict").asText());
        assertTallyAddsUp(replay.path("tally"));
    }

    static void assertTallyAddsUp(JsonNode tally) {
        int leaves = tally.path("可评分通过").asInt() + tally.path("可评分失败").asInt() + tally.path("待人工").asInt()
                + tally.path("传输未完成").asInt() + tally.path("未执行").asInt() + tally.path("不可重算").asInt();
        assertEquals(tally.path("总题数").asInt(), leaves);
        assertEquals(tally.path("已执行").asInt(), tally.path("可评分通过").asInt() + tally.path("可评分失败").asInt()
                + tally.path("待人工").asInt() + tally.path("传输未完成").asInt());
    }

    static JsonNode llmRow(JsonNode replay, String id) {
        for (JsonNode row : replay.path("results")) {
            if (id.equals(row.path("id").asText()) && "llm".equals(row.path("implementation").asText())) {
                return row;
            }
        }
        throw new AssertionError(id);
    }
}
