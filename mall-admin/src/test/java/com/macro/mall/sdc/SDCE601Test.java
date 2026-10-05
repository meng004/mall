package com.macro.mall.sdc;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.PmsProductCategoryMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProductCategory;
import com.macro.mall.model.PmsProductCategoryExample;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.service.impl.PmsProductCategoryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class SDCE601Test {

    @Test
    void missingCategoryReturnsZeroWithoutDelete() {
        Harness harness = harness();
        when(harness.categoryMapper.selectByPrimaryKey(101L)).thenReturn(null);
        when(harness.categoryMapper.deleteByPrimaryKey(101L)).thenReturn(1);

        assertEquals(0, harness.service.delete(101L));
        verify(harness.categoryMapper, never()).deleteByPrimaryKey(any());
    }

    @Test
    void directChildRejectsWithoutDelete() {
        Harness harness = harness();
        when(harness.categoryMapper.selectByPrimaryKey(101L)).thenReturn(category(101L));
        when(harness.categoryMapper.countByExample(any())).thenAnswer(invocation -> {
            harness.childConditions.addAll(categoryConditions(invocation.getArgument(0)));
            return 1L;
        });

        assertThrows(ApiException.class, () -> harness.service.delete(101L));
        verify(harness.categoryMapper, never()).deleteByPrimaryKey(any());
        assertEquals(List.of("parent_id = 101"), harness.childConditions);
        verify(harness.productMapper, never()).deleteByExample(any());
    }

    @Test
    void activeProductReferenceRejectsWithoutDelete() {
        rejectProductReference(harnessForProduct(1L));
    }

    @Test
    void logicallyDeletedProductReferenceRejectsWithoutDelete() {
        Harness harness = harnessForProduct(1L);
        rejectProductReference(harness);
        assertFalse(String.join("\n", harness.productConditions).contains("delete_status"));
    }

    @Test
    void emptyCategoryIsDeletedWithoutCascade() {
        Harness harness = harness();
        when(harness.categoryMapper.selectByPrimaryKey(101L)).thenReturn(category(101L));
        when(harness.categoryMapper.countByExample(any())).thenReturn(0L);
        when(harness.productMapper.countByExample(any())).thenReturn(0L);
        when(harness.categoryMapper.deleteByPrimaryKey(101L)).thenReturn(1);

        assertEquals(1, harness.service.delete(101L));
        verify(harness.categoryMapper).deleteByPrimaryKey(101L);
        verify(harness.categoryMapper, never()).deleteByExample(any());
        verify(harness.productMapper, never()).deleteByExample(any());
    }

    @Test
    void productsOfAnotherCategoryDoNotBlockDelete() {
        Harness harness = harness();
        when(harness.categoryMapper.selectByPrimaryKey(101L)).thenReturn(category(101L));
        when(harness.categoryMapper.countByExample(any())).thenReturn(0L);
        when(harness.productMapper.countByExample(any())).thenAnswer(invocation -> {
            harness.productConditions.addAll(productConditions(invocation.getArgument(0)));
            return 0L;
        });
        when(harness.categoryMapper.deleteByPrimaryKey(101L)).thenReturn(1);

        assertEquals(1, harness.service.delete(101L));
        String conditions = String.join("\n", harness.productConditions);
        assertEquals(List.of("product_category_id = 101"), harness.productConditions);
        assertFalse(conditions.contains("product_category_id = 202"));
        verify(harness.categoryMapper).deleteByPrimaryKey(101L);
        verify(harness.categoryMapper, never()).deleteByPrimaryKey(202L);
        verify(harness.productMapper, never()).deleteByExample(any());
    }

    private static void rejectProductReference(Harness harness) {
        assertThrows(ApiException.class, () -> harness.service.delete(101L));
        verify(harness.categoryMapper, never()).deleteByPrimaryKey(any());
        assertEquals(List.of("product_category_id = 101"), harness.productConditions);
        assertFalse(String.join("\n", harness.productConditions).contains("delete_status"));
    }

    private static Harness harnessForProduct(long productCount) {
        Harness harness = harness();
        when(harness.categoryMapper.selectByPrimaryKey(101L)).thenReturn(category(101L));
        when(harness.categoryMapper.countByExample(any())).thenReturn(0L);
        when(harness.productMapper.countByExample(any())).thenAnswer(invocation -> {
            harness.productConditions.addAll(productConditions(invocation.getArgument(0)));
            return productCount;
        });
        when(harness.categoryMapper.deleteByPrimaryKey(any())).thenReturn(1);
        return harness;
    }

    private static PmsProductCategory category(long id) {
        PmsProductCategory category = new PmsProductCategory();
        category.setId(id);
        return category;
    }

    private static List<String> categoryConditions(PmsProductCategoryExample example) {
        List<String> lines = new ArrayList<>();
        for (PmsProductCategoryExample.Criteria criteria : example.getOredCriteria()) {
            for (PmsProductCategoryExample.Criterion criterion : criteria.getAllCriteria()) {
                lines.add(criterion.isNoValue()
                        ? criterion.getCondition()
                        : criterion.getCondition() + " " + criterion.getValue());
            }
        }
        return lines;
    }

    private static List<String> productConditions(PmsProductExample example) {
        List<String> lines = new ArrayList<>();
        for (PmsProductExample.Criteria criteria : example.getOredCriteria()) {
            for (PmsProductExample.Criterion criterion : criteria.getAllCriteria()) {
                lines.add(criterion.isNoValue()
                        ? criterion.getCondition()
                        : criterion.getCondition() + " " + criterion.getValue());
            }
        }
        return lines;
    }

    private static Harness harness() {
        PmsProductCategoryServiceImpl service = new PmsProductCategoryServiceImpl();
        PmsProductCategoryMapper categoryMapper = dependency(service, "productCategoryMapper", PmsProductCategoryMapper.class);
        PmsProductMapper productMapper = dependency(service, "productMapper", PmsProductMapper.class);
        return new Harness(service, categoryMapper, productMapper, new ArrayList<>(), new ArrayList<>());
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        org.springframework.test.util.ReflectionTestUtils.setField(service, field, value);
        return value;
    }

    private record Harness(PmsProductCategoryServiceImpl service,
                           PmsProductCategoryMapper categoryMapper,
                           PmsProductMapper productMapper,
                           List<String> childConditions,
                           List<String> productConditions) {
    }
}
