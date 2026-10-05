package com.macro.mall.sdc;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.dto.PmsProductAttributeParam;
import com.macro.mall.mapper.PmsProductAttributeCategoryMapper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.model.PmsProductAttribute;
import com.macro.mall.model.PmsProductAttributeCategory;
import com.macro.mall.service.impl.PmsProductAttributeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class SDCE304Test {
    private PmsProductAttributeServiceImpl service;
    private PmsProductAttributeMapper attributeMapper;
    private PmsProductAttributeCategoryMapper categoryMapper;
    private final Map<Long, PmsProductAttribute> attributes = new HashMap<>();
    private final Map<Long, PmsProductAttributeCategory> categories = new HashMap<>();
    private final List<CategoryCount> categoryWrites = new ArrayList<>();
    private PmsProductAttribute savedAttribute;
    private int updateRows = 1;

    @BeforeEach
    void setUp() {
        service = new PmsProductAttributeServiceImpl();
        attributeMapper = dependency(service, "productAttributeMapper", PmsProductAttributeMapper.class);
        categoryMapper = dependency(service, "productAttributeCategoryMapper", PmsProductAttributeCategoryMapper.class);
        attributes.clear();
        categories.clear();
        categoryWrites.clear();
        savedAttribute = null;
        updateRows = 1;
        when(attributeMapper.selectByPrimaryKey(anyLong())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        when(categoryMapper.selectByPrimaryKey(anyLong())).thenAnswer(invocation -> categories.get(invocation.getArgument(0)));
        when(attributeMapper.updateByPrimaryKeySelective(any())).thenAnswer(invocation -> {
            PmsProductAttribute row = invocation.getArgument(0);
            PmsProductAttribute copy = new PmsProductAttribute();
            copy.setId(row.getId());
            copy.setName(row.getName());
            copy.setProductAttributeCategoryId(row.getProductAttributeCategoryId());
            copy.setType(row.getType());
            savedAttribute = copy;
            return updateRows;
        });
        when(categoryMapper.updateByPrimaryKey(any())).thenAnswer(invocation -> {
            PmsProductAttributeCategory row = invocation.getArgument(0);
            categoryWrites.add(new CategoryCount(row.getId(), row.getAttributeCount(), row.getParamCount()));
            return 1;
        });
    }

    @Test
    void specificationMovesToAnotherCategoryParameter() {
        PmsProductAttributeCategory oldCategory = category(101L, 1, 4);
        PmsProductAttributeCategory newCategory = category(202L, 3, 0);
        attribute(11L, 101L, 0, "颜色");

        int updated = service.update(11L, param(202L, 1, "材质"));

        assertEquals(0, oldCategory.getAttributeCount());
        assertEquals(1, newCategory.getParamCount());
        assertEquals(4, oldCategory.getParamCount());
        assertEquals(3, newCategory.getAttributeCount());
        assertEquals(1, updated);
        assertEquals(202L, savedAttribute.getProductAttributeCategoryId());
        assertEquals(1, savedAttribute.getType());
        assertEquals("材质", savedAttribute.getName());
        assertEquals(0, persisted(101L).attributeCount());
        assertEquals(4, persisted(101L).paramCount());
        assertEquals(3, persisted(202L).attributeCount());
        assertEquals(1, persisted(202L).paramCount());
    }

    @Test
    void sameCategoryAndTypeDoesNotChangeCounts() {
        PmsProductAttributeCategory category = category(101L, 1, 2);
        attribute(11L, 101L, 0, "颜色");

        int updated = service.update(11L, param(101L, 0, "色号"));

        assertEquals(1, updated);
        assertEquals(1, category.getAttributeCount());
        assertEquals(2, category.getParamCount());
        assertEquals("色号", savedAttribute.getName());
        assertEquals(101L, savedAttribute.getProductAttributeCategoryId());
        assertEquals(0, savedAttribute.getType());
        verify(categoryMapper, never()).updateByPrimaryKey(any());
    }

    @Test
    void onlyTypeChangeMaintainsBothCounters() {
        PmsProductAttributeCategory category = category(101L, 1, 0);
        attribute(11L, 101L, 0, "颜色");

        service.update(11L, param(101L, 1, "颜色"));

        assertEquals(0, category.getAttributeCount());
        assertEquals(1, category.getParamCount());
        assertEquals(0, persisted(101L).attributeCount());
        assertEquals(1, persisted(101L).paramCount());
        assertEquals(1, categoryWrites.size());
    }

    @Test
    void onlyTypeChangeFromParameterMaintainsBothCounters() {
        PmsProductAttributeCategory category = category(101L, 0, 1);
        attribute(11L, 101L, 1, "材质");

        service.update(11L, param(101L, 0, "材质"));

        assertEquals(1, category.getAttributeCount());
        assertEquals(0, category.getParamCount());
        assertEquals(1, persisted(101L).attributeCount());
        assertEquals(0, persisted(101L).paramCount());
    }

    @Test
    void onlyCategoryChangeKeepsTypeCounter() {
        PmsProductAttributeCategory oldCategory = category(101L, 1, 5);
        PmsProductAttributeCategory newCategory = category(202L, 0, 6);
        attribute(11L, 101L, 0, "颜色");

        service.update(11L, param(202L, 0, "颜色"));

        assertEquals(0, oldCategory.getAttributeCount());
        assertEquals(1, newCategory.getAttributeCount());
        assertEquals(5, oldCategory.getParamCount());
        assertEquals(6, newCategory.getParamCount());
        assertEquals(0, persisted(101L).attributeCount());
        assertEquals(1, persisted(202L).attributeCount());
    }

    @Test
    void onlyCategoryChangeMovesParameterCount() {
        PmsProductAttributeCategory oldCategory = category(101L, 7, 1);
        PmsProductAttributeCategory newCategory = category(202L, 8, 0);
        attribute(11L, 101L, 1, "材质");

        service.update(11L, param(202L, 1, "材质"));

        assertEquals(0, oldCategory.getParamCount());
        assertEquals(1, newCategory.getParamCount());
        assertEquals(7, oldCategory.getAttributeCount());
        assertEquals(8, newCategory.getAttributeCount());
    }

    @Test
    void missingTargetCategoryRejectsBeforeWrite() {
        PmsProductAttributeCategory oldCategory = category(101L, 1, 0);
        attribute(11L, 101L, 0, "颜色");

        ApiException exception = assertThrows(ApiException.class,
                () -> service.update(11L, param(303L, 1, "材质")));

        assertEquals("商品属性分类不存在", exception.getMessage());
        assertEquals(1, oldCategory.getAttributeCount());
        assertEquals(0, oldCategory.getParamCount());
        verify(attributeMapper, never()).updateByPrimaryKeySelective(any());
        verify(categoryMapper, never()).updateByPrimaryKey(any());
    }

    @Test
    void zeroRowAttributeUpdateDoesNotChangeCounts() {
        PmsProductAttributeCategory oldCategory = category(101L, 1, 0);
        PmsProductAttributeCategory newCategory = category(202L, 0, 0);
        attribute(11L, 101L, 0, "颜色");
        updateRows = 0;

        int updated = service.update(11L, param(202L, 1, "材质"));

        assertEquals(0, updated);
        assertEquals(1, oldCategory.getAttributeCount());
        assertEquals(0, newCategory.getParamCount());
        verify(categoryMapper, never()).updateByPrimaryKey(any());
    }

    @Test
    void missingAttributeDoesNotAdjustCounts() {
        category(101L, 1, 0);
        updateRows = 0;

        int updated = service.update(11L, param(101L, 0, "颜色"));

        assertEquals(0, updated);
        verify(categoryMapper, never()).updateByPrimaryKey(any());
    }

    @Test
    void nameChangeWithoutTypeOrCategoryKeepsCounts() {
        PmsProductAttributeCategory category = category(101L, 1, 2);
        attribute(11L, 101L, 0, "颜色");

        PmsProductAttributeParam nameOnly = param(101L, 0, "色号");
        nameOnly.setType(null);
        nameOnly.setProductAttributeCategoryId(null);
        assertEquals(1, service.update(11L, nameOnly));
        assertEquals(1, category.getAttributeCount());
        assertEquals(2, category.getParamCount());

        PmsProductAttributeParam withoutType = param(101L, 0, "色号");
        withoutType.setType(null);
        assertEquals(1, service.update(11L, withoutType));
        assertEquals(1, category.getAttributeCount());
        assertEquals(2, category.getParamCount());

        PmsProductAttributeParam withoutCategory = param(101L, 0, "色号");
        withoutCategory.setProductAttributeCategoryId(null);
        assertEquals(1, service.update(11L, withoutCategory));
        assertEquals(1, category.getAttributeCount());
        assertEquals(2, category.getParamCount());
        verify(categoryMapper, never()).updateByPrimaryKey(any());
    }

    @Test
    void movingSpecificationFromZeroAttributeCountStaysZero() {
        category(101L, 0, 4);
        category(202L, 3, 0);
        attribute(11L, 101L, 0, "颜色");

        service.update(11L, param(202L, 1, "材质"));

        assertEquals(0, persisted(101L).attributeCount());
    }

    @Test
    void movingParameterFromZeroParamCountStaysZero() {
        category(101L, 4, 0);
        category(202L, 0, 3);
        attribute(11L, 101L, 1, "材质");

        service.update(11L, param(202L, 1, "材质"));

        assertEquals(0, persisted(101L).paramCount());
    }

    private PmsProductAttributeCategory category(Long id, int attributeCount, int paramCount) {
        PmsProductAttributeCategory category = new PmsProductAttributeCategory();
        category.setId(id);
        category.setName("category-" + id);
        category.setAttributeCount(attributeCount);
        category.setParamCount(paramCount);
        categories.put(id, category);
        return category;
    }

    private void attribute(Long id, Long categoryId, int type, String name) {
        PmsProductAttribute attribute = new PmsProductAttribute();
        attribute.setId(id);
        attribute.setProductAttributeCategoryId(categoryId);
        attribute.setType(type);
        attribute.setName(name);
        attributes.put(id, attribute);
    }

    private static PmsProductAttributeParam param(Long categoryId, int type, String name) {
        PmsProductAttributeParam param = new PmsProductAttributeParam();
        param.setProductAttributeCategoryId(categoryId);
        param.setType(type);
        param.setName(name);
        return param;
    }

    private CategoryCount persisted(Long id) {
        CategoryCount found = null;
        for (CategoryCount write : categoryWrites) {
            if (id.equals(write.id())) {
                found = write;
            }
        }
        assertNotNull(found, "category " + id + " count was not persisted");
        return found;
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }

    private record CategoryCount(Long id, Integer attributeCount, Integer paramCount) {
    }
}
