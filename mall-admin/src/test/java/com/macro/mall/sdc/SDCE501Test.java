package com.macro.mall.sdc;

import com.macro.mall.mapper.PmsProductCategoryMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.service.impl.PmsProductCategoryServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 只核实现有 delete 按主键删除。子分类和商品引用拒绝尚未实现。
 */
class SDCE501Test {

    @Test
    void currentDeleteRemovesCategoryByPrimaryKeyOnly() {
        PmsProductCategoryServiceImpl service = new PmsProductCategoryServiceImpl();
        PmsProductCategoryMapper categoryMapper = dependency(service, "productCategoryMapper", PmsProductCategoryMapper.class);
        PmsProductMapper productMapper = dependency(service, "productMapper", PmsProductMapper.class);
        when(categoryMapper.deleteByPrimaryKey(101L)).thenReturn(1);

        assertEquals(1, service.delete(101L));

        verify(categoryMapper).deleteByPrimaryKey(101L);
        verify(categoryMapper, never()).selectByPrimaryKey(any());
        verify(categoryMapper, never()).countByExample(any());
        verify(productMapper, never()).countByExample(any());
        verify(productMapper, never()).deleteByExample(any());
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        org.springframework.test.util.ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
