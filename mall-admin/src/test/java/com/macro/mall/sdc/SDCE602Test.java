package com.macro.mall.sdc;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.ResultCode;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.controller.PmsBrandController;
import com.macro.mall.mapper.PmsBrandMapper;
import com.macro.mall.model.PmsBrandExample;
import com.macro.mall.service.impl.PmsBrandServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class SDCE602Test {

    @AfterEach
    void clearPage() {
        PageHelper.clearPage();
    }

    @Test
    void factoryStatusOneIsAndedBeforePaging() {
        Harness harness = harness();
        harness.service.listBrand("华为", 1, 2, 5, 1);

        String conditions = harness.captured.conditions;
        assertTrue(conditions.contains("factory_status = 1"));
        assertTrue(conditions.contains("name like %华为%"));
        assertTrue(conditions.contains("show_status = 1"));
        assertEquals(1, harness.captured.groups);
        assertEquals("sort desc", harness.captured.orderBy);
        assertEquals(2, harness.captured.pageNum);
        assertEquals(5, harness.captured.pageSize);
    }

    @Test
    void factoryStatusZeroIsFiltered() {
        Harness harness = harness();
        harness.service.listBrand(null, null, 1, 5, 0);

        assertTrue(harness.captured.conditions.contains("factory_status = 0"));
        assertFalse(harness.captured.conditions.contains("name like"));
        assertFalse(harness.captured.conditions.contains("show_status"));
    }

    @Test
    void oldFourArgumentMethodOmitsFactoryStatus() {
        Harness harness = harness();
        harness.service.listBrand("华为", 1, 2, 5);

        assertFalse(harness.captured.conditions.contains("factory_status"));
        assertTrue(harness.captured.conditions.contains("name like %华为%"));
        assertTrue(harness.captured.conditions.contains("show_status = 1"));
        assertEquals("sort desc", harness.captured.orderBy);
        assertEquals(2, harness.captured.pageNum);
        assertEquals(5, harness.captured.pageSize);
    }

    @Test
    void nullFactoryStatusMatchesOmittedFilter() {
        Harness harness = harness();
        harness.service.listBrand("华为", 1, 2, 5, null);

        assertFalse(harness.captured.conditions.contains("factory_status"));
        assertTrue(harness.captured.conditions.contains("name like %华为%"));
        assertTrue(harness.captured.conditions.contains("show_status = 1"));
    }

    @Test
    void factoryStatusTwoIsRejectedWithoutQuery() {
        Harness harness = harness();

        ApiException error = assertThrows(ApiException.class, () -> harness.service.listBrand("华为", 1, 1, 5, 2));

        assertEquals(ResultCode.VALIDATE_FAILED, error.getErrorCode());
        verify(harness.brandMapper, never()).selectByExample(any());
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void controllerPassesFactoryStatusIntoTheQuery() {
        Harness harness = harness();
        PmsBrandController controller = new PmsBrandController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "brandService", harness.service);

        controller.getList("华为", 1, 2, 5, 0);

        assertTrue(harness.captured.conditions.contains("factory_status = 0"));
        assertTrue(harness.captured.conditions.contains("name like %华为%"));
        assertTrue(harness.captured.conditions.contains("show_status = 1"));
    }

    private static Harness harness() {
        PmsBrandServiceImpl service = new PmsBrandServiceImpl();
        PmsBrandMapper brandMapper = dependency(service, "brandMapper", PmsBrandMapper.class);
        Captured captured = new Captured();
        when(brandMapper.selectByExample(any())).thenAnswer(invocation -> {
            PmsBrandExample example = invocation.getArgument(0);
            captured.orderBy = example.getOrderByClause();
            captured.groups = example.getOredCriteria().size();
            captured.conditions = String.join("\n", conditions(example));
            captured.pageNum = PageHelper.getLocalPage().getPageNum();
            captured.pageSize = PageHelper.getLocalPage().getPageSize();
            return List.of();
        });
        return new Harness(service, brandMapper, captured);
    }

    private static List<String> conditions(PmsBrandExample example) {
        List<String> lines = new ArrayList<>();
        for (PmsBrandExample.Criteria criteria : example.getOredCriteria()) {
            for (PmsBrandExample.Criterion criterion : criteria.getAllCriteria()) {
                lines.add(criterion.isNoValue()
                        ? criterion.getCondition()
                        : criterion.getCondition() + " " + criterion.getValue());
            }
        }
        return lines;
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        org.springframework.test.util.ReflectionTestUtils.setField(service, field, value);
        return value;
    }

    private static final class Captured {
        private String conditions = "";
        private String orderBy;
        private int groups;
        private int pageNum;
        private int pageSize;
    }

    private record Harness(PmsBrandServiceImpl service, PmsBrandMapper brandMapper, Captured captured) {
    }
}
