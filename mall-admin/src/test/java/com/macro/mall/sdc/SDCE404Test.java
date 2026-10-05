package com.macro.mall.sdc;

import com.macro.mall.dao.PmsSkuStockDao;
import com.macro.mall.dto.PmsProductParam;
import com.macro.mall.mapper.CmsPrefrenceAreaProductRelationMapper;
import com.macro.mall.mapper.CmsSubjectProductRelationMapper;
import com.macro.mall.mapper.PmsMemberPriceMapper;
import com.macro.mall.mapper.PmsProductAttributeValueMapper;
import com.macro.mall.mapper.PmsProductFullReductionMapper;
import com.macro.mall.mapper.PmsProductLadderMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.PmsSkuStockExample;
import com.macro.mall.service.impl.PmsProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * SKU 集合差异的特征测试。走真实 update，只替换 Mapper/DAO。期望按现有划分和写入顺序书写。
 */
class SDCE404Test {
    private static final long PRODUCT_ID = 10L;

    private final PmsProductServiceImpl service = new PmsProductServiceImpl();
    private final List<String> writes = new ArrayList<>();
    private final AtomicReference<List<PmsSkuStock>> stored = new AtomicReference<>(List.of());

    @BeforeEach
    void setUp() {
        dependency("productMapper", PmsProductMapper.class);
        dependency("memberPriceMapper", PmsMemberPriceMapper.class);
        dependency("productLadderMapper", PmsProductLadderMapper.class);
        dependency("productFullReductionMapper", PmsProductFullReductionMapper.class);
        dependency("productAttributeValueMapper", PmsProductAttributeValueMapper.class);
        dependency("subjectProductRelationMapper", CmsSubjectProductRelationMapper.class);
        dependency("prefrenceAreaProductRelationMapper", CmsPrefrenceAreaProductRelationMapper.class);
        PmsSkuStockMapper skuStockMapper = dependency("skuStockMapper", PmsSkuStockMapper.class);
        PmsSkuStockDao skuStockDao = dependency("skuStockDao", PmsSkuStockDao.class);

        when(skuStockMapper.selectByExample(any())).thenAnswer(invocation -> {
            writes.add("sku.select " + criteria(invocation.getArgument(0)));
            return stored.get();
        });
        when(skuStockMapper.deleteByExample(any())).thenAnswer(invocation -> {
            writes.add("sku.delete " + criteria(invocation.getArgument(0)));
            return 0;
        });
        when(skuStockMapper.updateByPrimaryKeySelective(any())).thenAnswer(invocation -> {
            writes.add("sku.update " + skuRow(invocation.getArgument(0)));
            return 0;
        });
        when(skuStockDao.insertList(any())).thenAnswer(invocation -> {
            List<PmsSkuStock> list = invocation.getArgument(0);
            writes.add("sku.insert " + skuRows(list));
            return 99;
        });
    }

    @Test
    void mixedInputInsertsNullIdUpdates2AndDeletes1() {
        stored.set(List.of(sku(1L, "OLD-1", 3, 9), sku(2L, "OLD-2", 4, 9)));
        PmsSkuStock kept = sku(2L, "KEEP-2", 8, 1);
        PmsSkuStock created = sku(null, null, 5, 2);
        created.setProductId(null);
        List<PmsSkuStock> incoming = new ArrayList<>(List.of(kept, created));
        String generated = skuCode(PRODUCT_ID, 1);

        int count = service.update(PRODUCT_ID, param(incoming));
        kept.setStock(999);
        created.setLowStock(999);

        assertEquals(1, count);
        assertEquals(PRODUCT_ID, created.getProductId());
        assertEquals(generated, created.getSkuCode());
        assertWrites("mixed", List.of(
                "sku.select [product_id = 10]",
                "sku.insert [null,10," + generated + ",5,2]",
                "sku.delete [id in [1]]",
                "sku.update 2,10,KEEP-2,8,1"));
    }

    @Test
    void emptyCurrentDeletesByProductIdWithoutSelect() {
        int count = service.update(PRODUCT_ID, param(new ArrayList<>()));

        assertEquals(1, count);
        assertWrites("empty", List.of("sku.delete [product_id = 10]"));
    }

    @Test
    void nullCurrentDeletesByProductIdWithoutSelect() {
        int count = service.update(PRODUCT_ID, param(null));

        assertEquals(1, count);
        assertWrites("null", List.of("sku.delete [product_id = 10]"));
    }

    @Test
    void insertOnlyKeepsExistingCodeAndNumbersBlankCodeByListIndex() {
        stored.set(List.of());
        String generated = skuCode(PRODUCT_ID, 2);

        int count = service.update(PRODUCT_ID, param(List.of(
                sku(null, "HAS", 4, 6),
                sku(null, null, 5, 7))));

        assertEquals(1, count);
        assertWrites("insert-only", List.of(
                "sku.select [product_id = 10]",
                "sku.insert [null,10,HAS,4,6, null,10," + generated + ",5,7]"));
    }

    @Test
    void updateOnlyKeepsOrderAndSkipsInsertAndDelete() {
        stored.set(List.of(sku(1L, "OLD-1", 3, 9), sku(2L, "OLD-2", 4, 9)));

        int count = service.update(PRODUCT_ID, param(List.of(
                sku(1L, "U1", 11, 1),
                sku(2L, "U2", 12, 2))));

        assertEquals(1, count);
        assertWrites("update-only", List.of(
                "sku.select [product_id = 10]",
                "sku.update 1,10,U1,11,1",
                "sku.update 2,10,U2,12,2"));
    }

    @Test
    void partialDeleteRemovesOriginalIdsMissingFromUpdateInOriginalOrder() {
        stored.set(List.of(sku(1L, "OLD-1", 3, 9), sku(2L, "OLD-2", 4, 9), sku(3L, "OLD-3", 6, 9)));

        int count = service.update(PRODUCT_ID, param(List.of(
                sku(3L, "C3", 13, 3),
                sku(1L, "C1", 11, 1))));

        assertEquals(1, count);
        assertWrites("partial-delete", List.of(
                "sku.select [product_id = 10]",
                "sku.delete [id in [2]]",
                "sku.update 3,10,C3,13,3",
                "sku.update 1,10,C1,11,1"));
    }

    private PmsProductParam param(List<PmsSkuStock> skuStockList) {
        PmsProductParam param = new PmsProductParam();
        param.setSkuStockList(skuStockList);
        return param;
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

    private static PmsSkuStock sku(Long id, String skuCode, int stock, int lowStock) {
        PmsSkuStock sku = new PmsSkuStock();
        sku.setId(id);
        sku.setProductId(PRODUCT_ID);
        sku.setSkuCode(skuCode);
        sku.setStock(stock);
        sku.setLowStock(lowStock);
        return sku;
    }

    private static String skuCode(long productId, int index) {
        return new SimpleDateFormat("yyyyMMdd").format(new Date())
                + String.format("%04d", productId)
                + String.format("%03d", index);
    }

    private static String skuRows(List<PmsSkuStock> list) {
        List<String> rows = new ArrayList<>();
        for (PmsSkuStock sku : list) {
            rows.add(skuRow(sku));
        }
        return rows.toString();
    }

    private static String skuRow(PmsSkuStock sku) {
        return sku.getId() + "," + sku.getProductId() + "," + sku.getSkuCode()
                + "," + sku.getStock() + "," + sku.getLowStock();
    }

    private static String criteria(PmsSkuStockExample example) {
        List<String> parts = new ArrayList<>();
        for (PmsSkuStockExample.Criteria criteria : example.getOredCriteria()) {
            for (PmsSkuStockExample.Criterion criterion : criteria.getAllCriteria()) {
                Object value = criterion.getValue();
                if (value instanceof List<?> values) {
                    value = List.copyOf(new ArrayList<>(values));
                }
                parts.add(criterion.getCondition() + " " + value);
            }
        }
        return parts.toString();
    }
}
