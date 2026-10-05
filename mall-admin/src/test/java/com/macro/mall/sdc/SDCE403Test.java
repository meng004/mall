package com.macro.mall.sdc;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

class SDCE403Test {

    private PmsProductServiceImpl service;
    private PmsProductAttributeValueDao productAttributeValueDao;
    private PmsMemberPriceDao memberPriceDao;
    private final List<PmsProductAttributeValue> capturedValues = new ArrayList<>();

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
        doAnswer(invocation -> {
            List<PmsProductAttributeValue> incoming = invocation.getArgument(0);
            for (PmsProductAttributeValue item : incoming) {
                capturedValues.add(copyAtReceive(item));
            }
            return incoming.size();
        }).when(productAttributeValueDao).insertList(anyList());
    }

    @Test
    void emptyAttributeValuesDoNotCallDao() {
        PmsProductParam param = new PmsProductParam();
        param.setProductAttributeValueList(new ArrayList<>());

        int count = service.create(param);

        assertEquals(1, count);
        assertEquals(0, capturedValues.size());
        verify(productAttributeValueDao, never()).insertList(anyList());
        System.out.println("EMPTY daoCalls=0");
    }

    @Test
    void attributeValuesKeepWriteProjection() {
        WriteProjection beforeWriteProjection = new WriteProjection(List.of(
                "id=null,productId=7,attributeId=101,value=红色",
                "id=null,productId=7,attributeId=102,value=蓝色"));
        PmsProductParam param = new PmsProductParam();
        param.setProductAttributeValueList(List.of(
                attribute(11L, 99L, 101L, "红色"),
                attribute(12L, 98L, 102L, "蓝色")));

        int count = service.create(param);

        WriteProjection afterWriteProjection = new WriteProjection(project(capturedValues));
        assertEquals(1, count);
        assertEquals(beforeWriteProjection, afterWriteProjection);
        System.out.println("WRITE_PROJECTION " + afterWriteProjection.rows());
    }

    @Test
    void updateAttributeValuesKeepWriteProjection() {
        WriteProjection beforeWriteProjection = new WriteProjection(List.of(
                "id=null,productId=7,attributeId=201,value=大",
                "id=null,productId=7,attributeId=202,value=小"));
        PmsProductParam param = new PmsProductParam();
        param.setProductAttributeValueList(List.of(
                attribute(21L, 3L, 201L, "大"),
                attribute(22L, 4L, 202L, "小")));

        int count = service.update(7L, param);

        WriteProjection afterWriteProjection = new WriteProjection(project(capturedValues));
        assertEquals(1, count);
        assertEquals(beforeWriteProjection, afterWriteProjection);
        System.out.println("UPDATE_PROJECTION " + afterWriteProjection.rows());
    }

    @Test
    void daoFailureKeepsExternalShape() {
        IllegalStateException specified = new IllegalStateException("attribute-value-insert-failed");
        doThrow(specified).when(productAttributeValueDao).insertList(anyList());
        Throwable beforeFailure = new RuntimeException((String) null);
        PmsProductParam param = new PmsProductParam();
        param.setProductAttributeValueList(List.of(attribute(11L, 99L, 101L, "红色")));

        Throwable afterFailure = assertThrows(RuntimeException.class, () -> service.create(param));
        Throwable afterUpdateFailure = assertThrows(RuntimeException.class, () -> service.update(7L, param));

        assertEquals(beforeFailure.getClass(), afterFailure.getClass());
        assertEquals(beforeFailure.getMessage(), afterFailure.getMessage());
        assertEquals(beforeFailure.getCause(), afterFailure.getCause());
        assertEquals(beforeFailure.getClass(), afterUpdateFailure.getClass());
        assertEquals(beforeFailure.getMessage(), afterUpdateFailure.getMessage());
        assertEquals(beforeFailure.getCause(), afterUpdateFailure.getCause());
        System.out.println("FAILURE class=" + afterFailure.getClass().getName()
                + " message=" + afterFailure.getMessage()
                + " cause=" + afterFailure.getCause());
    }

    @Test
    void memberPriceRelationStaysOnOriginalPath() {
        List<PmsMemberPrice> capturedPrices = new ArrayList<>();
        doAnswer(invocation -> {
            List<PmsMemberPrice> incoming = invocation.getArgument(0);
            for (PmsMemberPrice item : incoming) {
                PmsMemberPrice copy = new PmsMemberPrice();
                copy.setId(item.getId());
                copy.setProductId(item.getProductId());
                copy.setMemberLevelId(item.getMemberLevelId());
                capturedPrices.add(copy);
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

        assertEquals(1, capturedPrices.size());
        assertNull(capturedPrices.get(0).getId());
        assertEquals(7L, capturedPrices.get(0).getProductId());
        assertEquals(8L, capturedPrices.get(0).getMemberLevelId());
        verify(productAttributeValueDao, never()).insertList(anyList());
        System.out.println("MEMBER_PRICE id=" + capturedPrices.get(0).getId()
                + " productId=" + capturedPrices.get(0).getProductId());
    }

    private static List<String> project(List<PmsProductAttributeValue> values) {
        List<String> rows = new ArrayList<>();
        for (PmsProductAttributeValue value : values) {
            rows.add("id=" + value.getId()
                    + ",productId=" + value.getProductId()
                    + ",attributeId=" + value.getProductAttributeId()
                    + ",value=" + value.getValue());
        }
        return rows;
    }

    private static PmsProductAttributeValue copyAtReceive(PmsProductAttributeValue item) {
        PmsProductAttributeValue copy = new PmsProductAttributeValue();
        copy.setId(item.getId());
        copy.setProductId(item.getProductId());
        copy.setProductAttributeId(item.getProductAttributeId());
        copy.setValue(item.getValue());
        return copy;
    }

    private static PmsProductAttributeValue attribute(Long id, Long productId, Long attributeId, String text) {
        PmsProductAttributeValue value = new PmsProductAttributeValue();
        value.setId(id);
        value.setProductId(productId);
        value.setProductAttributeId(attributeId);
        value.setValue(text);
        return value;
    }

    private static <T> T dependency(Object target, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(target, field, value);
        return value;
    }

    private record WriteProjection(List<String> rows) {
    }
}
