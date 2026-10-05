package com.macro.mall.sdc.e2;

import com.macro.mall.dao.PmsMemberPriceDao;
import com.macro.mall.dao.PmsProductAttributeValueDao;
import com.macro.mall.dto.PmsProductParam;
import com.macro.mall.mapper.CmsPrefrenceAreaProductRelationMapper;
import com.macro.mall.mapper.CmsSubjectProductRelationMapper;
import com.macro.mall.mapper.PmsMemberPriceMapper;
import com.macro.mall.mapper.PmsProductAttributeValueMapper;
import com.macro.mall.mapper.PmsProductFullReductionMapper;
import com.macro.mall.mapper.PmsProductLadderMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.PmsMemberPrice;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductAttributeValue;
import com.macro.mall.service.impl.PmsProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

class SDCE202Test {

    private PmsProductServiceImpl service;
    private PmsProductAttributeValueDao productAttributeValueDao;
    private PmsMemberPriceDao memberPriceDao;
    private final List<PmsProductAttributeValue> capturedValues = new ArrayList<>();
    private final List<PmsMemberPrice> capturedMemberPrices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PmsProductServiceImpl();
        PmsProductMapper productMapper = dependency(service, "productMapper", PmsProductMapper.class);
        memberPriceDao = dependency(service, "memberPriceDao", PmsMemberPriceDao.class);
        dependency(service, "memberPriceMapper", PmsMemberPriceMapper.class);
        dependency(service, "productLadderMapper", PmsProductLadderMapper.class);
        dependency(service, "productFullReductionMapper", PmsProductFullReductionMapper.class);
        dependency(service, "skuStockMapper", PmsSkuStockMapper.class);
        productAttributeValueDao = dependency(service, "productAttributeValueDao", PmsProductAttributeValueDao.class);
        dependency(service, "productAttributeValueMapper", PmsProductAttributeValueMapper.class);
        dependency(service, "subjectProductRelationMapper", CmsSubjectProductRelationMapper.class);
        dependency(service, "prefrenceAreaProductRelationMapper", CmsPrefrenceAreaProductRelationMapper.class);
        doAnswer(invocation -> {
            PmsProduct row = invocation.getArgument(0);
            row.setId(7L);
            return 1;
        }).when(productMapper).insertSelective(any(PmsProduct.class));
    }

    @Test
    void emptyAttributeValuesDoNotCallDao() {
        PmsProductParam param = new PmsProductParam();
        param.setProductAttributeValueList(new ArrayList<>());

        int count = service.create(param);

        assertEquals(1, count);
        verify(productAttributeValueDao, never()).insertList(anyList());
    }

    @Test
    void twoAttributeValuesClearIdAndAssignProductId() {
        stubAttributeCapture();
        PmsProductParam param = new PmsProductParam();
        param.setProductAttributeValueList(List.of(attribute(11L, 99L, "红色"), attribute(12L, 98L, "蓝色")));

        int count = service.create(param);

        assertEquals(1, count);
        assertEquals(2, capturedValues.size());
        assertEquals(List.of("红色", "蓝色"), capturedValues.stream().map(PmsProductAttributeValue::getValue).toList());
        assertTrue(capturedValues.stream().allMatch(v -> v.getId() == null && v.getProductId().equals(7L)));
    }

    @Test
    void updateAttributeValuesUseSameTypedContract() {
        stubAttributeCapture();
        PmsProductParam param = new PmsProductParam();
        param.setProductAttributeValueList(List.of(attribute(21L, 3L, "大"), attribute(22L, 4L, "小")));

        int count = service.update(7L, param);

        assertEquals(1, count);
        assertEquals(2, capturedValues.size());
        assertTrue(capturedValues.stream().allMatch(v -> v.getId() == null && v.getProductId().equals(7L)));
    }

    @Test
    void daoFailurePropagatesSpecifiedException() {
        IllegalStateException specified = new IllegalStateException("attribute-value-insert-failed");
        doThrow(specified).when(productAttributeValueDao).insertList(anyList());
        PmsProductParam param = new PmsProductParam();
        param.setProductAttributeValueList(List.of(attribute(11L, 99L, "红色")));

        IllegalStateException fromCreate = assertThrows(IllegalStateException.class, () -> service.create(param));
        IllegalStateException fromUpdate = assertThrows(IllegalStateException.class, () -> service.update(7L, param));

        assertSame(specified, fromCreate);
        assertSame(specified, fromUpdate);
    }

    @Test
    void memberPriceRelationStaysOnOriginalPath() {
        doAnswer(invocation -> {
            List<PmsMemberPrice> incoming = invocation.getArgument(0);
            for (PmsMemberPrice item : incoming) {
                PmsMemberPrice copy = new PmsMemberPrice();
                copy.setId(item.getId());
                copy.setProductId(item.getProductId());
                copy.setMemberLevelId(item.getMemberLevelId());
                capturedMemberPrices.add(copy);
            }
            return incoming.size();
        }).when(memberPriceDao).insertList(anyList());
        PmsMemberPrice price = new PmsMemberPrice();
        price.setId(5L);
        price.setProductId(3L);
        price.setMemberLevelId(8L);
        PmsProductParam param = new PmsProductParam();
        param.setMemberPriceList(List.of(price));
        param.setProductAttributeValueList(new ArrayList<>());

        service.create(param);

        assertEquals(1, capturedMemberPrices.size());
        assertNull(capturedMemberPrices.get(0).getId());
        assertEquals(7L, capturedMemberPrices.get(0).getProductId());
        assertEquals(8L, capturedMemberPrices.get(0).getMemberLevelId());
        verify(productAttributeValueDao, never()).insertList(anyList());
    }

    private void stubAttributeCapture() {
        doAnswer(invocation -> {
            List<PmsProductAttributeValue> incoming = invocation.getArgument(0);
            for (PmsProductAttributeValue item : incoming) {
                PmsProductAttributeValue copy = new PmsProductAttributeValue();
                copy.setId(item.getId());
                copy.setProductId(item.getProductId());
                copy.setProductAttributeId(item.getProductAttributeId());
                copy.setValue(item.getValue());
                capturedValues.add(copy);
            }
            return incoming.size();
        }).when(productAttributeValueDao).insertList(anyList());
    }

    private static PmsProductAttributeValue attribute(Long id, Long productId, String text) {
        PmsProductAttributeValue value = new PmsProductAttributeValue();
        value.setId(id);
        value.setProductId(productId);
        value.setValue(text);
        return value;
    }

    private static <T> T dependency(Object target, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(target, field, value);
        return value;
    }
}
