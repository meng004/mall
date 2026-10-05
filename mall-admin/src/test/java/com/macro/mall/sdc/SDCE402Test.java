package com.macro.mall.sdc;

import com.macro.mall.dao.PmsProductVertifyRecordDao;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.model.PmsProductVertifyRecord;
import com.macro.mall.service.impl.PmsProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 商品四种状态批量更新的特征测试。期望来自当前四个公开方法，审核路径只作相邻回归。
 */
class SDCE402Test {
    private final PmsProductServiceImpl service = new PmsProductServiceImpl();
    private final List<String> writes = new ArrayList<>();
    private final AtomicInteger mapperRows = new AtomicInteger(6);

    @BeforeEach
    void setUp() {
        PmsProductMapper productMapper = dependency("productMapper", PmsProductMapper.class);
        PmsProductVertifyRecordDao vertifyDao = dependency("productVertifyRecordDao", PmsProductVertifyRecordDao.class);
        when(productMapper.updateByExampleSelective(any(), any())).thenAnswer(invocation -> {
            PmsProduct record = invocation.getArgument(0);
            PmsProductExample example = invocation.getArgument(1);
            writes.add("product.update " + nonNullFields(record) + " criteria=" + criteria(example));
            return mapperRows.get();
        });
        when(vertifyDao.insertList(any())).thenAnswer(invocation -> {
            List<PmsProductVertifyRecord> records = invocation.getArgument(0);
            writes.add("verify.insert " + verifyRows(records));
            return records.size();
        });
    }

    @Test
    void publishStatusUpdatesOnlyThatFieldForIds7And8() {
        List<Long> ids = new ArrayList<>(List.of(7L, 8L));

        int count = service.updatePublishStatus(ids, 1);
        ids.add(9L);

        assertEquals(6, count);
        assertWrites("publish", List.of(
                "product.update {publishStatus=1} criteria=[id in [7, 8]]"));
    }

    @Test
    void recommendStatusUpdatesOnlyThatFieldForIds7And8() {
        List<Long> ids = new ArrayList<>(List.of(7L, 8L));

        int count = service.updateRecommendStatus(ids, 1);
        ids.clear();

        assertEquals(6, count);
        assertWrites("recommend", List.of(
                "product.update {recommandStatus=1} criteria=[id in [7, 8]]"));
    }

    @Test
    void newStatusUpdatesOnlyThatFieldForIds7And8() {
        int count = service.updateNewStatus(new ArrayList<>(List.of(7L, 8L)), 1);

        assertEquals(6, count);
        assertWrites("new", List.of(
                "product.update {newStatus=1} criteria=[id in [7, 8]]"));
    }

    @Test
    void deleteStatusUpdatesOnlyThatFieldForIds7And8() {
        int count = service.updateDeleteStatus(new ArrayList<>(List.of(7L, 8L)), 1);

        assertEquals(6, count);
        assertWrites("delete", List.of(
                "product.update {deleteStatus=1} criteria=[id in [7, 8]]"));
    }

    @Test
    void mapperZeroIsReturnedForEachStatus() {
        mapperRows.set(0);
        List<Long> ids = List.of(7L, 8L);

        assertEquals(0, service.updatePublishStatus(ids, 1));
        assertEquals(0, service.updateRecommendStatus(ids, 1));
        assertEquals(0, service.updateNewStatus(ids, 1));
        assertEquals(0, service.updateDeleteStatus(ids, 1));
        assertWrites("mapper-zero", List.of(
                "product.update {publishStatus=1} criteria=[id in [7, 8]]",
                "product.update {recommandStatus=1} criteria=[id in [7, 8]]",
                "product.update {newStatus=1} criteria=[id in [7, 8]]",
                "product.update {deleteStatus=1} criteria=[id in [7, 8]]"));
    }

    @Test
    void verifyStatusKeepsAuditInsertAndDoesNotSetOtherFields() {
        int count = service.updateVerifyStatus(new ArrayList<>(List.of(7L, 8L)), 1, "驳回原因");

        assertEquals(6, count);
        assertWrites("verify", List.of(
                "product.update {verifyStatus=1} criteria=[id in [7, 8]]",
                "verify.insert [productId=7,status=1,detail=驳回原因,vertifyMan=test,createTime=present, productId=8,status=1,detail=驳回原因,vertifyMan=test,createTime=present]"));
    }

    private void assertWrites(String scenario, List<String> expected) {
        System.out.println("SNAPSHOT " + scenario);
        for (String write : writes) {
            System.out.println("SNAPSHOT " + scenario + " | " + write);
        }
        assertEquals(expected, List.copyOf(writes));
    }

    private <T> T dependency(String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }

    private static String nonNullFields(PmsProduct product) throws ReflectiveOperationException {
        TreeMap<String, Object> fields = new TreeMap<>();
        for (Method method : PmsProduct.class.getMethods()) {
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class
                    || !method.getName().startsWith("get")) {
                continue;
            }
            Object value = method.invoke(product);
            if (value != null) {
                String name = method.getName().substring(3);
                fields.put(Character.toLowerCase(name.charAt(0)) + name.substring(1), value);
            }
        }
        return fields.toString();
    }

    private static String criteria(PmsProductExample example) {
        List<String> parts = new ArrayList<>();
        for (PmsProductExample.Criteria criteria : example.getOredCriteria()) {
            for (PmsProductExample.Criterion criterion : criteria.getAllCriteria()) {
                Object value = criterion.getValue();
                if (value instanceof List<?> values) {
                    value = List.copyOf(new ArrayList<>(values));
                }
                parts.add(criterion.getCondition() + " " + value);
            }
        }
        return parts.toString();
    }

    private static String verifyRows(List<PmsProductVertifyRecord> records) {
        List<String> rows = new ArrayList<>();
        for (PmsProductVertifyRecord record : records) {
            rows.add("productId=" + record.getProductId()
                    + ",status=" + record.getStatus()
                    + ",detail=" + record.getDetail()
                    + ",vertifyMan=" + record.getVertifyMan()
                    + ",createTime=" + (record.getCreateTime() == null ? "null" : "present"));
        }
        return rows.toString();
    }
}
