package com.macro.mall.sdc.e7;

import com.macro.mall.portal.query.*;
import org.junit.jupiter.api.Test;
import java.util.List;
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
}
