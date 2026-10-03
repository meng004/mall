package com.macro.mall.sdc.e7;

import com.macro.mall.portal.query.*;
import com.macro.mall.portal.service.PmsPortalProductService;
import com.macro.mall.portal.service.impl.PmsPortalProductServiceImpl;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.*;
import com.github.pagehelper.PageHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductQueryTest {
    @AfterEach void cleanPage() { PageHelper.clearPage(); }
    @Test void literalNameCharactersAreEscapedInsteadOfRejectedOrExpanded() {
        record Example(String name, String modelOutput, String sqlPattern) {}
        for (var sample : List.of(
            new Example("20%", "{\"action\":\"query\",\"name\":\"20%\"}", "%20\\%%"),
            new Example("a_b", "{\"action\":\"query\",\"name\":\"a_b\"}", "%a\\_b%"),
            new Example("a\\b", "{\"action\":\"query\",\"name\":\"a\\\\b\"}", "%a\\\\b%"))) {
            var mapper = mock(PmsProductMapper.class);
            var portal = new PmsPortalProductServiceImpl();
            ReflectionTestUtils.setField(portal,"productMapper",mapper);
            when(mapper.selectByExample(any())).thenAnswer(invocation -> {
                PmsProductExample example = invocation.getArgument(0);
                assertEquals(sample.sqlPattern(), example.getOredCriteria().get(0).getAllCriteria().get(2).getValue());
                return List.of();
            });
            var existing = new ExistingProductQueryService(portal);
            var llm = new LlmProductQueryService(existing, (system,input) -> sample.modelOutput());
            for (ProductQueryService service : List.of(existing,llm)) {
                var result = assertDoesNotThrow(() -> service.query(ProductQueryRequest.text(sample.name(),1,20)));
                assertEquals(ProductQueryResult.Status.OK, result.status());
            }
            verify(mapper,times(2)).selectByExample(any());
        }
    }
    @Test void bothImplementationsUseSameStructuredQueryAndRealProducts() {
        var portal = mock(PmsPortalProductService.class);
        var criteria = new ProductQueryCriteria("手机", new BigDecimal("500"), 10);
        var product = new PmsProduct(); product.setId(1L); product.setName("手机");
        when(portal.search(criteria, 1, 20)).thenReturn(List.of(product));
        var existing = new ExistingProductQueryService(portal);
        var llm = new LlmProductQueryService(existing, (system, input) -> { throw new AssertionError("structured request must skip model"); });
        for (ProductQueryService service : List.of(existing, llm)) {
            var result = service.query(ProductQueryRequest.structured(criteria, 1, 20));
            assertEquals(ProductQueryResult.Status.OK, result.status());
            assertEquals(1L, result.products().get(0).id());
        }
        verify(portal, times(2)).search(criteria, 1, 20);
    }
    @Test void llmParsesAndConditionsWithoutGeneratingProducts() {
        var portal = mock(PmsPortalProductService.class);
        var criteria = new ProductQueryCriteria("手机", new BigDecimal("500"), 10);
        when(portal.search(criteria, 1, 20)).thenReturn(List.of());
        var service = new LlmProductQueryService(new ExistingProductQueryService(portal),
            (system, input) -> "{\"action\":\"query\",\"name\":\"手机\",\"price_lt\":500,\"stock_lt\":10}");
        var result = service.query(ProductQueryRequest.text("手机价格低于500且库存少于10", 1, 20));
        assertEquals(ProductQueryResult.Status.OK, result.status());
        assertEquals(criteria, result.criteria()); verify(portal).search(criteria, 1, 20);
    }
    @Test void rejectsInvalidModelOutputBeforeDatabase() {
        var portal = mock(PmsPortalProductService.class);
        for (String output : List.of("not json", "{}", "{\"action\":\"draft\"}", "{\"action\":\"confirm\"}", "{\"action\":\"undo\"}", "{\"action\":\"update\"}",
            "{\"action\":\"query\",\"stock_lt\":1.5}", "{\"action\":\"query\",\"price_lt\":-1}",
            "{\"action\":\"query\",\"stock_lt\":2147483648}", "{\"action\":\"query\",\"sql\":\"select *\"}",
            "{\"action\":\"query\"}",
            "{\"action\":\"query\",\"price_lt\":\"10\"}")) {
            var service = new LlmProductQueryService(new ExistingProductQueryService(portal), (s,u) -> output);
            assertEquals(ProductQueryResult.Status.UNSUPPORTED, service.query(ProductQueryRequest.text("查询",1,20)).status(), output);
        }
        verifyNoInteractions(portal);
    }
    @Test void modelFailureIsExplicitAndNeverFallsBack() {
        var portal = mock(PmsPortalProductService.class);
        var service = new LlmProductQueryService(new ExistingProductQueryService(portal), (s,u) -> {throw new IllegalStateException("secret");});
        var result = service.query(ProductQueryRequest.text("价格低于10",1,20));
        assertEquals(ProductQueryResult.Status.UNAVAILABLE, result.status());
        assertFalse(result.message().contains("secret")); verifyNoInteractions(portal);
    }
    @Test void boundsAndOrdinaryKeywordsAreValidatedWithoutLlm() {
        assertThrows(IllegalArgumentException.class, () -> new ProductQueryCriteria(null,null,null));
        assertThrows(IllegalArgumentException.class, () -> new ProductQueryCriteria(null,new BigDecimal("100000000"),null));
        assertThrows(IllegalArgumentException.class, () -> new ProductQueryCriteria(null,new BigDecimal("0.001"),null));
        assertThrows(IllegalArgumentException.class, () -> ProductQueryRequest.text("x",0,20));
        assertThrows(IllegalArgumentException.class, () -> ProductQueryRequest.text("x",1,101));
        assertThrows(IllegalArgumentException.class, () -> new ProductQueryRequest("x",new ProductQueryCriteria("x",null,null),1,20));
        assertEquals(0,new ProductQueryCriteria(null,BigDecimal.ZERO,0).stockLt());
        var portal = mock(PmsPortalProductService.class);
        var expected = new ProductQueryCriteria("价格低于100",null,null);
        when(portal.search(expected,1,20)).thenReturn(List.of());
        new ExistingProductQueryService(portal).query(ProductQueryRequest.text("价格低于100",1,20));
        verify(portal).search(expected,1,20);
    }
    @Test void originalSearchKeepsBrandCategoryAndSort() {
        var mapper = mock(PmsProductMapper.class);
        var service = new PmsPortalProductServiceImpl();
        ReflectionTestUtils.setField(service,"productMapper",mapper);
        when(mapper.selectByExample(any())).thenAnswer(invocation -> {
            PmsProductExample example = invocation.getArgument(0);
            assertEquals("price asc",example.getOrderByClause());
            assertEquals(List.of("delete_status =","publish_status =","name like","brand_id =","product_category_id ="),
                example.getOredCriteria().get(0).getAllCriteria().stream().map(PmsProductExample.Criterion::getCondition).toList());
            return List.of();
        });
        service.search("手机",1L,2L,1,20,3);
        verify(mapper).selectByExample(any());
    }
    @Test void mapperReceivesAllConditionsBeforePaginationExecutes() {
        var mapper = mock(PmsProductMapper.class);
        var service = new PmsPortalProductServiceImpl();
        ReflectionTestUtils.setField(service,"productMapper",mapper);
        when(mapper.selectByExample(any())).thenAnswer(invocation -> {
            PmsProductExample example = invocation.getArgument(0);
            var values = example.getOredCriteria().get(0).getAllCriteria();
            assertEquals(List.of("delete_status =","publish_status =","name like","price <","stock <"),
                values.stream().map(PmsProductExample.Criterion::getCondition).toList());
            assertEquals(new BigDecimal("100"), values.get(3).getValue());
            assertEquals(10, values.get(4).getValue());
            assertEquals(2, PageHelper.getLocalPage().getPageNum());
            return List.of();
        });
        service.search(new ProductQueryCriteria("手机",new BigDecimal("100"),10),2,3);
        verify(mapper).selectByExample(any());
    }
}
