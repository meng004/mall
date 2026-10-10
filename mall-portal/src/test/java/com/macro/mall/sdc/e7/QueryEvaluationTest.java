package com.macro.mall.sdc.e7;

import com.macro.mall.portal.query.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class QueryEvaluationTest {
    @Test void judgesStatusAndExactIdsAndNeverCountsUnavailableAsCorrectRejection() {
        var rejection = new QueryEvaluation.Case("R1","write","修改库存","UNSUPPORTED",List.of());
        var unavailable = ProductQueryResult.failure(ProductQueryResult.Status.UNAVAILABLE,"offline");
        assertFalse(QueryEvaluation.evaluate(rejection, request -> unavailable).passed());
        assertTrue(QueryEvaluation.evaluate(rejection, request -> ProductQueryResult.failure(ProductQueryResult.Status.UNSUPPORTED,"只读")).passed());
        var query = new QueryEvaluation.Case("Q1","normal","手机","OK",List.of(2L,1L));
        var result = new ProductQueryResult(ProductQueryResult.Status.OK,"existing",new ProductQueryCriteria("手机",null,null),
            List.of(new ProductQueryResult.ProductSummary(1L,"手机",null,null),new ProductQueryResult.ProductSummary(2L,"手机",null,null)),"");
        assertTrue(QueryEvaluation.evaluate(query,request -> result).passed());
        assertFalse(QueryEvaluation.evaluate(rejection,request -> result).passed());
    }
    @Test void incompleteOrUnavailableRunsCannotClaimModelValidation() {
        assertEquals("INCOMPLETE",QueryEvaluation.verdict(List.of()));
        var failed = new QueryEvaluation.Result("a","normal",false,"UNAVAILABLE",List.of());
        assertEquals("FAILED",QueryEvaluation.verdict(List.of(failed)));
        var passed = new QueryEvaluation.Result("a","normal",true,"OK",List.of(1L));
        assertEquals("REGRESSION_PASSED",QueryEvaluation.verdict(List.of(passed)));
    }
    @Test void thrownQueryIsTimedAndDoesNotCountAsACorrectRejection() {
        var item = new QueryEvaluation.Case("x","normal","手机","OK",List.of(1L));
        var observed = QueryEvaluation.observe(item, request -> { throw new IllegalStateException("db down"); }, () -> "raw");
        assertFalse(observed.passed());
        assertEquals(List.of(), observed.actualIds());
        assertEquals("", observed.actualStatus());
        assertEquals("IllegalStateException: db down", observed.error());
        assertEquals("raw", observed.modelText());
        assertTrue(observed.elapsedMillis() >= 0);
    }
    @Test void qualityGateNeedsEighteenNormalAndEveryBoundaryCase() {
        assertTrue(QueryEvaluation.meetsQualityGate(20, 20, 8, 8, 4, 4, 0));
        assertTrue(QueryEvaluation.meetsQualityGate(18, 20, 8, 8, 4, 4, 0));
        assertFalse(QueryEvaluation.meetsQualityGate(17, 20, 8, 8, 4, 4, 0));
        assertFalse(QueryEvaluation.meetsQualityGate(20, 20, 7, 8, 4, 4, 0));
        assertFalse(QueryEvaluation.meetsQualityGate(20, 20, 8, 8, 3, 4, 0));
        assertFalse(QueryEvaluation.meetsQualityGate(18, 18, 8, 8, 4, 4, 0));
        assertFalse(QueryEvaluation.meetsQualityGate(18, 20, 8, 8, 4, 4, 1));
        assertFalse(QueryEvaluation.meetsQualityGate(20, 20, 8, 7, 4, 4, 0));
    }
    @Test void sameCasesAreScoredSeparatelyAndKeepRegressionVerdict() {
        var rows = new ArrayList<QueryEvaluation.Comparison>();
        for (int i = 0; i < 20; i++) rows.add(compared("n" + i, "normal", i < 18, i < 17));
        for (int i = 0; i < 8; i++) rows.add(compared("a" + i, "adversarial", true, true));
        for (int i = 0; i < 4; i++) rows.add(compared("o" + i, "out_of_domain", true, true));
        Map<String, Object> plain = QueryEvaluation.scoreboard(rows, false);
        Map<String, Object> llm = QueryEvaluation.scoreboard(rows, true);
        assertEquals("18/20", plain.get("normal"));
        assertEquals("8/8", plain.get("adversarial"));
        assertEquals("4/4", plain.get("outOfDomain"));
        assertEquals(20, plain.get("normalTotal"));
        assertEquals("达标", plain.get("qualityGate"));
        assertEquals("FAILED", plain.get("verdict"));
        assertEquals("17/20", llm.get("normal"));
        assertEquals("8/8", llm.get("adversarial"));
        assertEquals("4/4", llm.get("outOfDomain"));
        assertEquals("未达标", llm.get("qualityGate"));
        assertEquals("FAILED", llm.get("verdict"));
        rows.set(17, compared("n17", "normal", true, true));
        rows.set(18, compared("n18", "normal", true, true));
        rows.set(19, compared("n19", "normal", true, true));
        assertEquals("REGRESSION_PASSED", QueryEvaluation.scoreboard(rows, true).get("verdict"));
        assertEquals("达标", QueryEvaluation.scoreboard(rows, true).get("qualityGate"));
    }
    private static QueryEvaluation.Comparison compared(String id, String kind, boolean plainPassed, boolean llmPassed) {
        return new QueryEvaluation.Comparison(id, kind, observed(plainPassed, null), observed(llmPassed, "{\"action\":\"query\"}"));
    }
    private static QueryEvaluation.Observed observed(boolean passed, String modelText) {
        return new QueryEvaluation.Observed(passed ? "OK" : "UNSUPPORTED", List.of(), passed, 1L, null, modelText);
    }
}
