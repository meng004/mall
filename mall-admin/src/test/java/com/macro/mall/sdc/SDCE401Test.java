package com.macro.mall.sdc;

import com.macro.mall.dao.SmsCouponProductCategoryRelationDao;
import com.macro.mall.dao.SmsCouponProductRelationDao;
import com.macro.mall.dto.SmsCouponParam;
import com.macro.mall.mapper.SmsCouponMapper;
import com.macro.mall.mapper.SmsCouponProductCategoryRelationMapper;
import com.macro.mall.mapper.SmsCouponProductRelationMapper;
import com.macro.mall.model.SmsCoupon;
import com.macro.mall.model.SmsCouponProductCategoryRelation;
import com.macro.mall.model.SmsCouponProductCategoryRelationExample;
import com.macro.mall.model.SmsCouponProductRelation;
import com.macro.mall.model.SmsCouponProductRelationExample;
import com.macro.mall.service.impl.SmsCouponServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 优惠券 create/update 关联写入的特征测试。期望来自当前服务源码，不随重构改写。
 */
class SDCE401Test {
    private static final long GENERATED_COUPON_ID = 8802L;
    private static final long UPDATE_COUPON_ID = 77L;

    private final SmsCouponServiceImpl service = new SmsCouponServiceImpl();
    private final List<String> writes = new ArrayList<>();
    private final AtomicInteger insertRows = new AtomicInteger(1);
    private final AtomicInteger updateRows = new AtomicInteger(1);
    private final AtomicReference<List<SmsCouponProductRelation>> productRelations = new AtomicReference<>();
    private final AtomicReference<List<SmsCouponProductCategoryRelation>> categoryRelations = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        SmsCouponMapper couponMapper = dependency("couponMapper", SmsCouponMapper.class);
        SmsCouponProductRelationMapper productRelationMapper =
                dependency("productRelationMapper", SmsCouponProductRelationMapper.class);
        SmsCouponProductCategoryRelationMapper categoryRelationMapper =
                dependency("productCategoryRelationMapper", SmsCouponProductCategoryRelationMapper.class);
        SmsCouponProductRelationDao productRelationDao =
                dependency("productRelationDao", SmsCouponProductRelationDao.class);
        SmsCouponProductCategoryRelationDao categoryRelationDao =
                dependency("productCategoryRelationDao", SmsCouponProductCategoryRelationDao.class);

        when(couponMapper.insert(any())).thenAnswer(invocation -> {
            SmsCoupon coupon = invocation.getArgument(0);
            writes.add("coupon.insert " + couponSnapshot(coupon));
            coupon.setId(GENERATED_COUPON_ID);
            return insertRows.get();
        });
        when(couponMapper.updateByPrimaryKey(any())).thenAnswer(invocation -> {
            SmsCoupon coupon = invocation.getArgument(0);
            writes.add("coupon.update " + couponSnapshot(coupon));
            return updateRows.get();
        });
        when(productRelationMapper.deleteByExample(any())).thenAnswer(invocation -> {
            SmsCouponProductRelationExample example = invocation.getArgument(0);
            writes.add("product.delete " + criteria(example.getOredCriteria())
                    + " relations=" + productRows(productRelations.get()));
            return 1;
        });
        when(categoryRelationMapper.deleteByExample(any())).thenAnswer(invocation -> {
            SmsCouponProductCategoryRelationExample example = invocation.getArgument(0);
            writes.add("category.delete " + criteria(example.getOredCriteria())
                    + " relations=" + categoryRows(categoryRelations.get()));
            return 1;
        });
        when(productRelationDao.insertList(any())).thenAnswer(invocation -> {
            List<SmsCouponProductRelation> list = invocation.getArgument(0);
            writes.add("product.insert " + productRows(list));
            return 4;
        });
        when(categoryRelationDao.insertList(any())).thenAnswer(invocation -> {
            List<SmsCouponProductCategoryRelation> list = invocation.getArgument(0);
            writes.add("category.insert " + categoryRows(list));
            return 5;
        });
    }

    @Test
    void createUseType0InitializesCountsAndSkipsRelations() {
        insertRows.set(4);
        SmsCouponParam param = coupon(0, "全场券", 10);
        param.setCount(99);
        param.setUseCount(5);
        param.setReceiveCount(6);
        param.setProductRelationList(List.of(product(11L, 1L, 1001L, "手机", "SN1")));
        param.setProductCategoryRelationList(List.of(category(21L, 1L, 301L, "手机", "数码")));
        remember(param);

        int count = service.create(param);

        assertEquals(4, count);
        assertWrites("create-useType-0", List.of(
                "coupon.insert id=null,count=10,useCount=0,receiveCount=0,publishCount=10,useType=0,name=全场券"));
    }

    @Test
    void createUseType1FillsCategoryCouponIdWithoutDelete() {
        SmsCouponParam param = coupon(1, "分类券", 10);
        param.setProductCategoryRelationList(List.of(
                category(21L, 1L, 301L, "手机", "数码"),
                category(22L, 1L, 302L, "配件", "数码")));
        param.setProductRelationList(List.of(product(11L, 1L, 1001L, "手机", "SN1")));
        remember(param);

        int count = service.create(param);

        assertEquals(1, count);
        assertWrites("create-useType-1", List.of(
                "coupon.insert id=null,count=10,useCount=0,receiveCount=0,publishCount=10,useType=1,name=分类券",
                "category.insert [21,8802,301,手机,数码, 22,8802,302,配件,数码]"));
    }

    @Test
    void createUseType2FillsProductCouponIdAndSnapshotSurvivesLaterMutation() {
        SmsCouponParam param = coupon(2, "商品券", 10);
        param.setCount(99);
        param.setUseCount(5);
        param.setReceiveCount(6);
        param.setProductRelationList(new ArrayList<>(List.of(
                product(11L, 1L, 1001L, "手机", "SN1"),
                product(12L, 1L, 1002L, "耳机", "SN2"))));
        param.setProductCategoryRelationList(new ArrayList<>(List.of(
                category(21L, 1L, 301L, "手机", "数码"))));
        remember(param);

        int count = service.create(param);

        assertEquals(1, count);
        assertEquals(8802L, param.getProductRelationList().get(0).getCouponId());
        assertEquals(11L, param.getProductRelationList().get(0).getId());
        param.setCount(123);
        param.getProductRelationList().get(0).setCouponId(999L);
        param.getProductRelationList().add(product(13L, 999L, 1003L, "后加", "SN3"));
        assertWrites("create-useType-2", List.of(
                "coupon.insert id=null,count=10,useCount=0,receiveCount=0,publishCount=10,useType=2,name=商品券",
                "product.insert [11,8802,1001,手机,SN1, 12,8802,1002,耳机,SN2]"));
    }

    @Test
    void createUseType2EmptyRelationStillInsertsOnce() {
        SmsCouponParam param = coupon(2, "商品券", 10);
        param.setProductRelationList(new ArrayList<>());
        remember(param);

        int count = service.create(param);

        assertEquals(1, count);
        assertWrites("create-useType-2-empty", List.of(
                "coupon.insert id=null,count=10,useCount=0,receiveCount=0,publishCount=10,useType=2,name=商品券",
                "product.insert []"));
    }

    @Test
    void createUseType2ReturnsMapperZeroAndStillInsertsRelations() {
        insertRows.set(0);
        SmsCouponParam param = coupon(2, "商品券", 10);
        param.setProductRelationList(List.of(product(11L, 1L, 1001L, "手机", "SN1")));
        remember(param);

        int count = service.create(param);

        assertEquals(0, count);
        assertWrites("create-useType-2-zero", List.of(
                "coupon.insert id=null,count=10,useCount=0,receiveCount=0,publishCount=10,useType=2,name=商品券",
                "product.insert [11,8802,1001,手机,SN1]"));
    }

    @Test
    void updateUseType0DoesNotCleanOrInsertRelations() {
        updateRows.set(0);
        SmsCouponParam param = coupon(0, "全场券", 10);
        param.setId(999L);
        param.setCount(9);
        param.setUseCount(3);
        param.setReceiveCount(4);
        param.setProductRelationList(List.of(product(11L, 1L, 1001L, "手机", "SN1")));
        param.setProductCategoryRelationList(List.of(category(21L, 1L, 301L, "手机", "数码")));
        remember(param);

        int count = service.update(UPDATE_COUPON_ID, param);

        assertEquals(0, count);
        assertEquals(UPDATE_COUPON_ID, param.getId());
        assertWrites("update-useType-0", List.of(
                "coupon.update id=77,count=9,useCount=3,receiveCount=4,publishCount=10,useType=0,name=全场券"));
    }

    @Test
    void updateUseType1DeletesOnlyCategoryThenInserts() {
        updateRows.set(2);
        SmsCouponParam param = coupon(1, "分类券", 10);
        param.setId(999L);
        param.setCount(9);
        param.setUseCount(3);
        param.setReceiveCount(4);
        param.setProductCategoryRelationList(new ArrayList<>(List.of(
                category(21L, 1L, 301L, "手机", "数码"),
                category(22L, 1L, 302L, "配件", "数码"))));
        param.setProductRelationList(new ArrayList<>(List.of(
                product(11L, 1L, 1001L, "手机", "SN1"))));
        remember(param);

        int count = service.update(UPDATE_COUPON_ID, param);

        assertEquals(2, count);
        assertWrites("update-useType-1", List.of(
                "coupon.update id=77,count=9,useCount=3,receiveCount=4,publishCount=10,useType=1,name=分类券",
                "category.delete [coupon_id = 77] relations=[21,77,301,手机,数码, 22,77,302,配件,数码]",
                "category.insert [21,77,301,手机,数码, 22,77,302,配件,数码]"));
    }

    @Test
    void updateUseType2DeletesOnlyProductThenInserts() {
        SmsCouponParam param = coupon(2, "商品券", 10);
        param.setId(999L);
        param.setCount(9);
        param.setUseCount(3);
        param.setReceiveCount(4);
        param.setProductRelationList(new ArrayList<>(List.of(
                product(11L, 1L, 1001L, "手机", "SN1"),
                product(12L, 1L, 1002L, "耳机", "SN2"))));
        param.setProductCategoryRelationList(new ArrayList<>(List.of(
                category(21L, 1L, 301L, "手机", "数码"))));
        remember(param);

        int count = service.update(UPDATE_COUPON_ID, param);

        assertEquals(1, count);
        param.getProductRelationList().get(0).setProductName("改后");
        assertWrites("update-useType-2", List.of(
                "coupon.update id=77,count=9,useCount=3,receiveCount=4,publishCount=10,useType=2,name=商品券",
                "product.delete [coupon_id = 77] relations=[11,77,1001,手机,SN1, 12,77,1002,耳机,SN2]",
                "product.insert [11,77,1001,手机,SN1, 12,77,1002,耳机,SN2]"));
    }

    @Test
    void updateUseType1EmptyRelationDeletesThenInsertsEmpty() {
        SmsCouponParam param = coupon(1, "分类券", 10);
        param.setCount(9);
        param.setUseCount(3);
        param.setReceiveCount(4);
        param.setProductCategoryRelationList(new ArrayList<>());
        param.setProductRelationList(List.of(product(11L, 1L, 1001L, "手机", "SN1")));
        remember(param);

        int count = service.update(UPDATE_COUPON_ID, param);

        assertEquals(1, count);
        assertWrites("update-useType-1-empty", List.of(
                "coupon.update id=77,count=9,useCount=3,receiveCount=4,publishCount=10,useType=1,name=分类券",
                "category.delete [coupon_id = 77] relations=[]",
                "category.insert []"));
    }

    @Test
    void updateUseType2NullRelationFailsBeforeDelete() {
        SmsCouponParam param = coupon(2, "商品券", 10);
        param.setCount(9);
        param.setUseCount(3);
        param.setReceiveCount(4);
        param.setProductRelationList(null);
        remember(param);

        assertThrows(NullPointerException.class, () -> service.update(UPDATE_COUPON_ID, param));
        assertWrites("update-useType-2-null", List.of(
                "coupon.update id=77,count=9,useCount=3,receiveCount=4,publishCount=10,useType=2,name=商品券"));
    }

    private void remember(SmsCouponParam param) {
        productRelations.set(param.getProductRelationList());
        categoryRelations.set(param.getProductCategoryRelationList());
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

    private static SmsCouponParam coupon(int useType, String name, int publishCount) {
        SmsCouponParam param = new SmsCouponParam();
        param.setUseType(useType);
        param.setName(name);
        param.setPublishCount(publishCount);
        return param;
    }

    private static SmsCouponProductRelation product(Long id, Long couponId, Long productId, String name, String sn) {
        SmsCouponProductRelation relation = new SmsCouponProductRelation();
        relation.setId(id);
        relation.setCouponId(couponId);
        relation.setProductId(productId);
        relation.setProductName(name);
        relation.setProductSn(sn);
        return relation;
    }

    private static SmsCouponProductCategoryRelation category(Long id, Long couponId, Long categoryId,
                                                             String name, String parentName) {
        SmsCouponProductCategoryRelation relation = new SmsCouponProductCategoryRelation();
        relation.setId(id);
        relation.setCouponId(couponId);
        relation.setProductCategoryId(categoryId);
        relation.setProductCategoryName(name);
        relation.setParentCategoryName(parentName);
        return relation;
    }

    private static String couponSnapshot(SmsCoupon coupon) {
        return "id=" + coupon.getId()
                + ",count=" + coupon.getCount()
                + ",useCount=" + coupon.getUseCount()
                + ",receiveCount=" + coupon.getReceiveCount()
                + ",publishCount=" + coupon.getPublishCount()
                + ",useType=" + coupon.getUseType()
                + ",name=" + coupon.getName();
    }

    private static String productRows(List<SmsCouponProductRelation> list) {
        if (list == null) {
            return "null";
        }
        List<String> rows = new ArrayList<>();
        for (SmsCouponProductRelation relation : list) {
            rows.add(relation.getId() + "," + relation.getCouponId() + "," + relation.getProductId()
                    + "," + relation.getProductName() + "," + relation.getProductSn());
        }
        return rows.toString();
    }

    private static String categoryRows(List<SmsCouponProductCategoryRelation> list) {
        if (list == null) {
            return "null";
        }
        List<String> rows = new ArrayList<>();
        for (SmsCouponProductCategoryRelation relation : list) {
            rows.add(relation.getId() + "," + relation.getCouponId() + "," + relation.getProductCategoryId()
                    + "," + relation.getProductCategoryName() + "," + relation.getParentCategoryName());
        }
        return rows.toString();
    }

    private static String criteria(List<?> oredCriteria) {
        List<String> parts = new ArrayList<>();
        for (Object criteria : oredCriteria) {
            List<?> items = criteria instanceof SmsCouponProductRelationExample.Criteria productCriteria
                    ? productCriteria.getAllCriteria()
                    : ((SmsCouponProductCategoryRelationExample.Criteria) criteria).getAllCriteria();
            for (Object item : items) {
                String condition;
                Object value;
                if (item instanceof SmsCouponProductRelationExample.Criterion productCriterion) {
                    condition = productCriterion.getCondition();
                    value = productCriterion.getValue();
                } else {
                    SmsCouponProductCategoryRelationExample.Criterion categoryCriterion =
                            (SmsCouponProductCategoryRelationExample.Criterion) item;
                    condition = categoryCriterion.getCondition();
                    value = categoryCriterion.getValue();
                }
                if (value instanceof List<?> values) {
                    value = List.copyOf(values);
                }
                parts.add(condition + " " + value);
            }
        }
        return parts.toString();
    }
}
