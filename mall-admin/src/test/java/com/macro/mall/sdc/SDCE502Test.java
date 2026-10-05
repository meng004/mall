package com.macro.mall.sdc;

import com.github.pagehelper.PageHelper;
import com.macro.mall.mapper.PmsBrandMapper;
import com.macro.mall.model.PmsBrandExample;
import com.macro.mall.service.impl.PmsBrandServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 观察现有四参数 listBrand。厂家筛选只写在说明里，这里不实现。
 */
class SDCE502Test {

    @AfterEach
    void clearPage() {
        PageHelper.clearPage();
    }

    @Test
    void currentListBrandDoesNotFilterFactoryStatus() {
        PmsBrandServiceImpl service = new PmsBrandServiceImpl();
        PmsBrandMapper brandMapper = dependency(service, "brandMapper", PmsBrandMapper.class);
        String[] orderBy = new String[1];
        int[] pageAtSelect = new int[2];
        int[] criteriaGroups = new int[1];
        List<String> captured = new ArrayList<>();
        when(brandMapper.selectByExample(any())).thenAnswer(invocation -> {
            PmsBrandExample example = invocation.getArgument(0);
            orderBy[0] = example.getOrderByClause();
            criteriaGroups[0] = example.getOredCriteria().size();
            captured.addAll(conditions(example));
            pageAtSelect[0] = PageHelper.getLocalPage().getPageNum();
            pageAtSelect[1] = PageHelper.getLocalPage().getPageSize();
            return List.of();
        });

        service.listBrand("华为", 1, 2, 5);

        String existingConditions = String.join("\n", captured);
        assertFalse(existingConditions.contains("factory_status"));
        assertTrue(existingConditions.contains("name like %华为%"));
        assertTrue(existingConditions.contains("show_status = 1"));
        assertEquals(2, captured.size());
        assertEquals(1, criteriaGroups[0]);
        assertEquals("sort desc", orderBy[0]);
        assertEquals(2, pageAtSelect[0]);
        assertEquals(5, pageAtSelect[1]);
        verify(brandMapper).selectByExample(any());
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
}
