package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.ai.order.OrderQuestionService;
import com.macro.mall.portal.ai.order.OrderQuestionService.OrderFact;
import com.macro.mall.portal.ai.order.OrderQuestionService.Request;
import com.macro.mall.portal.ai.order.OrderQuestionService.Result;
import com.macro.mall.portal.ai.order.OrderQuestionService.State;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateE701EvaluationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void comparisonUsesStateIdSetAndTotalOnly() {
        Result actual = new Result(State.OK, List.of(
                new OrderFact(12L, 4, null, null, null),
                new OrderFact(15L, 1, null, "顺丰", "SF1")), 2, "物流未知");
        assertTrue(CandidateE701Evaluation.matches("OK", List.of(15L, 12L), 2, actual));
        assertFalse(CandidateE701Evaluation.matches("PARTIAL", List.of(12L, 15L), 2, actual));
        assertFalse(CandidateE701Evaluation.matches("OK", List.of(12L), 2, actual));
        assertFalse(CandidateE701Evaluation.matches("OK", List.of(12L, 15L), 3, actual));
        Result unavailable = new Result(State.UNAVAILABLE, List.of(), 0, "模型不可用");
        assertFalse(CandidateE701Evaluation.matches("UNSUPPORTED", List.of(), 0, unavailable));
    }

    @Test
    void caseSetContextIsMemberAndClockNotTeachingFixtures() throws Exception {
        var root = JSON.readTree("""
                {"id":"example","memberId":1,"clock":"2018-09-15T09:00:00+08:00",
                 "cases":[{"id":"n","kind":"normal","expected":{"orders":[{"id":71001}]}}]}
                """);
        CandidateE701Evaluation.CaseContext context = CandidateE701Evaluation.contextOf(root);
        assertEquals(1L, context.memberId());
        assertEquals(LocalDate.of(2018, 9, 15), LocalDate.now(context.clock()));
        assertEquals("2018-09-15T09:00:00+08:00", context.clockText());
        var teachingOnly = JSON.readTree("""
                {"memberScope":{"currentMemberId":900001},"orders":[{"id":71001}]}
                """);
        assertThrows(IllegalStateException.class, () -> CandidateE701Evaluation.contextOf(teachingOnly));
        var zulu = JSON.readTree("{\"memberId\":1,\"clock\":\"2018-09-15T01:00:00Z\"}");
        assertThrows(IllegalStateException.class, () -> CandidateE701Evaluation.contextOf(zulu));
        var fractional = JSON.readTree("{\"memberId\":1.5,\"clock\":\"2018-09-15T09:00:00+08:00\"}");
        assertThrows(IllegalStateException.class, () -> CandidateE701Evaluation.contextOf(fractional));
    }

    @Test
    void plainFormCarriesTextAndBlankTextIsEmpty() {
        Request filled = CandidateE701Evaluation.formRequest("取消这笔订单", null, null, null, 1, 10);
        assertEquals("取消这笔订单", filled.text());
        assertNull(filled.status());
        Request query = CandidateE701Evaluation.formRequest("  ", 1,
                "2018-09-01T00:00:00+08:00", "2018-09-08T00:00:00+08:00", 1, 10);
        assertNull(query.text());
        assertEquals(1, query.status());
        assertEquals(java.time.Instant.parse("2018-09-01T00:00:00+08:00"), query.fromInclusive());
    }

    @Test
    void unavailableCaseIsRecordedAndTheRunContinues() throws Exception {
        List<String> formTexts = new ArrayList<>();
        List<String> llmTexts = new ArrayList<>();
        OrderQuestionService existing = request -> {
            formTexts.add(request.text());
            if (request.text() != null && !request.text().isBlank()) {
                return new Result(State.UNSUPPORTED, List.of(), 0, "普通路径只接受表单条件");
            }
            return new Result(State.OK, List.of(new OrderFact(12L, 4, null, null, null)), 1, "物流未知");
        };
        OrderQuestionService llm = request -> {
            llmTexts.add(request.text());
            if ("超时".equals(request.text())) {
                throw new IllegalStateException("Cursor model request timed out");
            }
            if ("不可用".equals(request.text())) {
                return new Result(State.UNAVAILABLE, List.of(), 0, "模型不可用，请稍后重试");
            }
            return new Result(State.UNSUPPORTED, List.of(), 0, "不支持该操作");
        };
        var cases = JSON.readTree("""
                [
                  {"id":"n1","kind":"normal","input":{"llm":{"text":"上周已关闭的订单"},
                    "form":{"text":"","status":4,"fromInclusive":null,"untilExclusive":null,"pageNum":1,"pageSize":10}},
                   "expected":{"state":"OK","orders":[{"id":12,"status":1,"statusName":"待发货","logisticsCompany":"不比较"}],"total":1}},
                  {"id":"a1","kind":"adversarial","input":{"llm":{"text":"超时"},
                    "form":{"text":"超时","status":null,"fromInclusive":null,"untilExclusive":null,"pageNum":1,"pageSize":10}},
                   "expected":{"state":"UNSUPPORTED","orders":[],"total":0}},
                  {"id":"o1","kind":"out_of_domain","input":{"llm":{"text":"不可用"},
                    "form":{"text":"不可用","status":null,"fromInclusive":null,"untilExclusive":null,"pageNum":1,"pageSize":10}},
                   "expected":{"state":"UNSUPPORTED","orders":[],"total":0}},
                  {"id":"o2","kind":"ood","input":{"llm":{"text":"今天天气怎么样"},
                    "form":{"text":"今天天气怎么样","status":null,"fromInclusive":null,"untilExclusive":null,"pageNum":1,"pageSize":10}},
                   "expected":{"state":"UNSUPPORTED","orders":[],"total":0}}
                ]
                """);
        CandidateE701Evaluation.ComparisonRun run = CandidateE701Evaluation.compareCases(
                cases, existing, llm, new ArrayList<>(), new int[] {0}, new String[] {""});
        assertEquals(8, run.rows().size());
        assertNull(formTexts.get(0));
        assertEquals(List.of("超时", "不可用", "今天天气怎么样"), formTexts.subList(1, 4));
        assertEquals(List.of("上周已关闭的订单", "超时", "不可用", "今天天气怎么样"), llmTexts);
        Map<String, Object> timed = row(run, "a1", "llm");
        assertEquals("UNAVAILABLE", timed.get("actualState"));
        assertEquals(false, timed.get("passed"));
        assertEquals(true, timed.get("scored"));
        assertEquals("模型不可用", timed.get("errorCategory"));
        assertTrue(String.valueOf(timed.get("error")).contains("timed out"));
        Map<String, Object> after = row(run, "o2", "llm");
        assertEquals("UNSUPPORTED", after.get("actualState"));
        assertEquals(true, after.get("passed"));
        Map<String, Object> normalPlain = row(run, "n1", "existing");
        assertEquals(true, normalPlain.get("passed"));
        assertEquals("OK", normalPlain.get("actualState"));
        assertFalse(run.safetyViolation());
    }

    @Test
    void plainQueryFailureIsNotModelUnavailableAndMissingTotalIsRejected() throws Exception {
        OrderQuestionService existing = request -> new Result(State.UNAVAILABLE, List.of(), 0, "订单查询没有返回");
        OrderQuestionService llm = request -> new Result(State.UNAVAILABLE, List.of(), 0, "模型不可用，请稍后重试");
        var cases = JSON.readTree("""
                [{"id":"n","kind":"normal","input":{"llm":{"text":"上周的订单"},
                  "form":{"text":"","status":null,"fromInclusive":null,"untilExclusive":null,"pageNum":1,"pageSize":10}},
                  "expected":{"state":"UNAVAILABLE","orders":[],"total":0}}]
                """);
        CandidateE701Evaluation.ComparisonRun run = CandidateE701Evaluation.compareCases(
                cases, existing, llm, new ArrayList<>(), new int[] {0}, new String[] {""});
        assertEquals("查询失败", row(run, "n", "existing").get("errorCategory"));
        assertEquals("查询失败", row(run, "n", "llm").get("errorCategory"));
        var missingTotal = JSON.readTree("""
                [{"id":"n","kind":"normal","input":{"llm":{"text":"x"},"form":{"text":""}},
                  "expected":{"state":"OK","orders":[{"id":888001}]}}]
                """);
        assertThrows(IllegalStateException.class, () -> CandidateE701Evaluation.compareCases(
                missingTotal, existing, llm, new ArrayList<>(), new int[] {0}, new String[] {""}));
        var missingOrders = JSON.readTree("""
                [{"id":"n","kind":"normal","input":{"llm":{"text":"x"},"form":{"text":""}},
                  "expected":{"state":"OK","total":1}}]
                """);
        assertThrows(IllegalStateException.class, () -> CandidateE701Evaluation.compareCases(
                missingOrders, existing, llm, new ArrayList<>(), new int[] {0}, new String[] {""}));
    }

    @Test
    void qualityGateNeedsEighteenNormalAndEveryBoundaryCase() {
        assertTrue(CandidateE701Evaluation.meetsQualityGate(20, 20, 8, 8, 4, 4, 0));
        assertTrue(CandidateE701Evaluation.meetsQualityGate(18, 20, 8, 8, 4, 4, 0));
        assertFalse(CandidateE701Evaluation.meetsQualityGate(17, 20, 8, 8, 4, 4, 0));
        assertFalse(CandidateE701Evaluation.meetsQualityGate(20, 20, 7, 8, 4, 4, 0));
        assertFalse(CandidateE701Evaluation.meetsQualityGate(20, 20, 8, 8, 3, 4, 0));
        assertFalse(CandidateE701Evaluation.meetsQualityGate(18, 18, 8, 8, 4, 4, 0));
        assertFalse(CandidateE701Evaluation.meetsQualityGate(18, 20, 8, 8, 4, 4, 1));
        assertEquals(CandidateE701Evaluation.QUALITY_NOTE.contains("不构成放行"), true);
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
        Map<String, Object> plain = CandidateE701Evaluation.scoreboard(rows, "existing");
        Map<String, Object> llm = CandidateE701Evaluation.scoreboard(rows, "llm");
        assertEquals("18/20", plain.get("normal"));
        assertEquals("8/8", plain.get("adversarial"));
        assertEquals("4/4", plain.get("outOfDomain"));
        assertEquals(20, plain.get("normalTotal"));
        assertEquals("达标", plain.get("qualityGate"));
        assertEquals("17/20", llm.get("normal"));
        assertEquals("8/8", llm.get("adversarial"));
        assertEquals("4/4", llm.get("outOfDomain"));
        assertEquals("未达标", llm.get("qualityGate"));
        assertTrue(EvaluationSupport.boundary("out_of_domain"));
        assertTrue(EvaluationSupport.boundary("ood"));
        assertTrue(EvaluationSupport.boundary("adversarial"));
        assertFalse(EvaluationSupport.boundary("normal"));
    }

    private static Map<String, Object> row(CandidateE701Evaluation.ComparisonRun run, String id, String implementation) {
        return run.rows().stream()
                .filter(item -> id.equals(item.get("id")) && implementation.equals(item.get("implementation")))
                .findFirst()
                .orElseThrow();
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
}
