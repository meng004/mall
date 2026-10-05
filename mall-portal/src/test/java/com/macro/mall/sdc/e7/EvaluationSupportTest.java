package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 各 E7 示例共用的评测判定：放行阈值、未评分行、退出码与报告。不涉及具体业务语义。 */
class EvaluationSupportTest {
    @Test
    void shortRunsAndSafetyStayDistinct() {
        assertEquals("驳回", EvaluationSupport.decision(true, true, false, 0, 0, false));
        assertEquals("未完成", EvaluationSupport.decision(false, true, false, 19, 19, true));
        assertEquals("未完成", EvaluationSupport.decision(false, false, false, 19, 19, true));
        assertEquals("不放行", EvaluationSupport.decision(false, false, false, 20, 20, true));
        assertEquals("降级", EvaluationSupport.decision(false, false, true, 20, 17, true));
        assertEquals("放行", EvaluationSupport.technicalDecision(false, false, true, 20, 18, true));
        assertEquals("未完成", EvaluationSupport.decision(false, false, true, 20, 18, true));
        assertEquals("REJECT", EvaluationSupport.codeOf("驳回"));
        assertEquals("INCOMPLETE", EvaluationSupport.codeOf("未完成"));
        assertEquals("NO_RELEASE", EvaluationSupport.codeOf(EvaluationSupport.decision(false, false, false, 20, 20, true)));
        assertEquals("DEGRADE", EvaluationSupport.codeOf("降级"));
        assertEquals("REJECT", EvaluationSupport.verdict(20, 20, 12, 12, true, true));
        assertEquals("INCOMPLETE", EvaluationSupport.verdict(19, 19, 12, 12, true, false));
        assertEquals("INCOMPLETE", EvaluationSupport.verdict(20, 20, 11, 11, true, false));
        assertEquals("NO_RELEASE", EvaluationSupport.verdict(20, 20, 12, 11, true, false));
        assertEquals("DEGRADE", EvaluationSupport.verdict(20, 17, 12, 12, true, false));
    }

    @Test
    void comparatorExceptionDoesNotAbortTheReport() {
        var rows = List.of(
                EvaluationSupport.guarded("done", EvaluationSupport.Score::pass),
                EvaluationSupport.guarded("boom", () -> {
                    throw new IllegalStateException("Duplicate key 21");
                }));
        assertEquals(2, rows.size());
        assertEquals(false, rows.get(1).get("passed"));
        assertEquals(false, rows.get(1).get("scored"));
        assertEquals(false, rows.get(1).get("countedAsCorrectRejection"));
        assertEquals("比较器", rows.get(1).get("errorCategory"));
    }

    @Test
    void onlineTimeoutAndPendingStayUnscored() {
        var timeout = EvaluationSupport.Score.transport("Cursor model request timed out");
        assertFalse(timeout.scored());
        assertEquals("传输未完成", timeout.category());
        var rows = new java.util.ArrayList<java.util.Map<String, Object>>();
        var first = new java.util.LinkedHashMap<String, Object>();
        first.put("id", "N01");
        first.put("implementation", "llm");
        EvaluationSupport.mark(first, timeout.passed(), timeout.scored(), timeout.category(), timeout.detail());
        rows.add(first);
        var mapper = new ObjectMapper();
        var cases = mapper.createArrayNode();
        for (int i = 1; i <= 32; i++) cases.add(mapper.createObjectNode().put("id", String.format("N%02d", i)));
        EvaluationSupport.appendNotRun(rows, cases);
        var tally = EvaluationSupport.tallyLlm(rows);
        assertEquals(0, tally.get("可评分失败"));
        assertEquals(1, tally.get("传输未完成"));
        assertEquals(31, tally.get("未执行"));
        assertEquals(32, tally.get("总题数"));
        assertEquals("未完成", EvaluationSupport.decision(false, EvaluationSupport.releaseBlocked(true, rows), false, 0, 0, false));
        var pending = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (int i = 1; i <= 19; i++) {
            var row = new java.util.LinkedHashMap<String, Object>();
            row.put("id", "N" + i);
            row.put("implementation", "llm");
            EvaluationSupport.mark(row, true, true, "", "");
            pending.add(row);
        }
        var held = new java.util.LinkedHashMap<String, Object>();
        held.put("id", "N20");
        held.put("implementation", "llm");
        EvaluationSupport.mark(held, false, false, "待人工核对", "引文待人工");
        pending.add(held);
        for (int i = 1; i <= 12; i++) {
            var row = new java.util.LinkedHashMap<String, Object>();
            row.put("id", "B" + i);
            row.put("implementation", "llm");
            EvaluationSupport.mark(row, true, true, "", "");
            pending.add(row);
        }
        assertTrue(EvaluationSupport.releaseBlocked(false, pending));
        assertEquals("INCOMPLETE", EvaluationSupport.codeOf(
                EvaluationSupport.decision(false, true, true, 19, 19, true)));
        assertEquals("放行", EvaluationSupport.technicalDecision(false, false, true, 20, 20, true));
        assertEquals("未完成", EvaluationSupport.decision(false, false, true, 20, 20, true));
    }

    @Test
    void sharedDatabaseDoesNotEnterTheNormalDenominator(@TempDir Path temporary) throws Exception {
        assertFalse(EvaluationSupport.modelScored(false, false, false));
        assertFalse(EvaluationSupport.modelScored(false, false, true));
        assertTrue(EvaluationSupport.modelScored(true, false, false));
        assertFalse(EvaluationSupport.modelScored(true, true, false));
        var row = new java.util.LinkedHashMap<String, Object>();
        row.put("id", "N01");
        row.put("implementation", "llm");
        row.put("scored", EvaluationSupport.modelScored(false, false, false));
        row.put("passed", false);
        row.put("errorCategory", "未完成");
        var rows = new java.util.ArrayList<java.util.Map<String, Object>>();
        rows.add(row);
        int normalCases = 0;
        if (Boolean.TRUE.equals(row.get("scored"))) normalCases++;
        assertEquals(0, normalCases);
        assertEquals(0, EvaluationSupport.tallyLlm(rows).get("可评分失败"));
        String decision = EvaluationSupport.decision(false, true, false, normalCases, 0, false);
        assertEquals("未完成", decision);
        assertEquals(0, EvaluationSupport.processExit(decision));
        Path report = temporary.resolve("shared.json");
        EvaluationSupport.writeReport(new ObjectMapper(), report.toString(), 0, System.nanoTime(),
                EvaluationSupport.codeOf(decision), decision, false, true, "教学库可连接，facts 未装载", rows,
                EvaluationSupport.onlineExtra(false, true, false, normalCases, 0, false));
        JsonNode saved = new ObjectMapper().readTree(report.toFile());
        assertEquals("未完成", saved.path("decision").asText());
        assertFalse(saved.path("isolatedDatabase").asBoolean());
        assertEquals(EvaluationSupport.processExit(decision), saved.path("processExit").asInt());
    }

    @Test
    void technicalPassAndTimeoutKeepExitZero(@TempDir Path temporary) throws Exception {
        String technical = EvaluationSupport.technicalDecision(false, false, true, 20, 20, true);
        String decision = EvaluationSupport.decision(false, false, true, 20, 20, true);
        assertEquals("放行", technical);
        assertEquals("未完成", decision);
        assertEquals(0, EvaluationSupport.processExit(decision));
        assertEquals(1, EvaluationSupport.processExit("驳回"));
        Path pass = temporary.resolve("pass.json");
        var extra = EvaluationSupport.onlineExtra(false, false, true, 20, 20, true);
        EvaluationSupport.writeReport(new ObjectMapper(), pass.toString(), 32, System.nanoTime(),
                EvaluationSupport.codeOf(decision), decision, false, false, "", List.of(), extra);
        JsonNode saved = new ObjectMapper().readTree(pass.toFile());
        assertEquals("放行", saved.path("technicalThreshold").asText());
        assertEquals("未完成", saved.path("decision").asText());
        assertTrue(saved.path("isolatedDatabase").asBoolean());
        assertEquals(0, EvaluationSupport.processExit("不放行"));
        assertEquals(0, EvaluationSupport.processExit("降级"));
        assertEquals(EvaluationSupport.processExit(decision), saved.path("processExit").asInt());
        var timeout = EvaluationSupport.Score.transport("Cursor model request timed out");
        var rows = new java.util.ArrayList<java.util.Map<String, Object>>();
        var first = new java.util.LinkedHashMap<String, Object>();
        first.put("id", "N01");
        first.put("implementation", "llm");
        EvaluationSupport.mark(first, timeout.passed(), timeout.scored(), timeout.category(), timeout.detail());
        rows.add(first);
        var cases = new ObjectMapper().createArrayNode();
        for (int i = 1; i <= 32; i++) cases.add(new ObjectMapper().createObjectNode().put("id", String.format("N%02d", i)));
        EvaluationSupport.appendNotRun(rows, cases);
        assertEquals(0, EvaluationSupport.tallyLlm(rows).get("可评分失败"));
        String stopped = EvaluationSupport.decision(false, true, false, 0, 0, false);
        assertEquals("未完成", stopped);
        assertEquals(0, EvaluationSupport.processExit(stopped));
        Path timed = temporary.resolve("timeout.json");
        EvaluationSupport.writeReport(new ObjectMapper(), timed.toString(), 1, System.nanoTime(),
                EvaluationSupport.codeOf(stopped), stopped, true, false, "", rows);
        JsonNode timedReport = new ObjectMapper().readTree(timed.toFile());
        assertEquals(0, timedReport.path("processExit").asInt());
        assertEquals(EvaluationSupport.processExit(stopped), timedReport.path("processExit").asInt());
        assertEquals(1, timedReport.path("tally").path("传输未完成").asInt());
        assertEquals(31, timedReport.path("tally").path("未执行").asInt());
    }
}
