package com.macro.mall.sdc.e3;

import com.macro.mall.model.SmsCoupon;
import com.macro.mall.model.SmsCouponProductCategoryRelation;
import com.macro.mall.model.SmsCouponProductRelation;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.SmsCouponHistoryDetail;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.UmsMemberCouponServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** Specifications for the minimum-amount comparison. Expectations are the business rule, not the current return value. */
class SDCE301Test {
    private static final long MEMBER_ID = 101L;
    private static final Date FUTURE = new Date(4_102_444_800_000L);
    private static final Date PAST = new Date(0L);

    private UmsMemberCouponServiceImpl service;
    private SmsCouponHistoryDao historyDao;

    @BeforeEach
    void setUp() {
        service = new UmsMemberCouponServiceImpl();
        UmsMemberService memberService = dependency(service, "memberService", UmsMemberService.class);
        historyDao = dependency(service, "couponHistoryDao", SmsCouponHistoryDao.class);
        UmsMember member = new UmsMember();
        member.setId(MEMBER_ID);
        when(memberService.getCurrentMember()).thenReturn(member);
    }

    @Test
    void ninetyNineNinetyIsBelowOneHundred() {
        historyDaoReturns(coupon(1L, 0, "100", FUTURE, List.of(), List.of()));
        List<CartPromotionItem> cartAt9990 = cart("99.90", 10L, 100L);
        assertEquals(0, service.listCart(cartAt9990, 1).size());
    }

    @Test
    void universalThresholdBoundaries() {
        historyDaoReturns(coupon(1L, 0, "100", FUTURE, List.of(), List.of()));
        assertEquals(List.of(), ids(service.listCart(cart("98.90", 10L, 100L), 1)));
        assertEquals(List.of(1L), ids(service.listCart(cart("100.00", 10L, 100L), 1)));
        assertEquals(List.of(1L), ids(service.listCart(cart("100.10", 10L, 100L), 1)));
        assertEquals(List.of(1L), ids(service.listCart(cart("98.90", 10L, 100L), 0)));
        assertEquals(List.of(), ids(service.listCart(cart("100.00", 10L, 100L), 0)));
    }

    @Test
    void categoryRejectsNinetyNineNinety() {
        historyDaoReturns(coupon(2L, 1, "100", FUTURE, List.of(100L), List.of()));
        assertEquals(0, service.listCart(cart("99.90", 10L, 100L), 1).size());
    }

    @Test
    void productRejectsNinetyNineNinety() {
        historyDaoReturns(coupon(3L, 2, "100", FUTURE, List.of(), List.of(10L)));
        assertEquals(0, service.listCart(cart("99.90", 10L, 100L), 1).size());
    }

    @Test
    void unrelatedItemsCannotFillTheMinimum() {
        historyDaoReturns(
                coupon(4L, 1, "100", FUTURE, List.of(100L), List.of()),
                coupon(5L, 2, "100", FUTURE, List.of(), List.of(10L)));
        List<CartPromotionItem> cart = List.of(item("10.00", 10L, 100L), item("500.00", 20L, 200L));
        assertEquals(List.of(), ids(service.listCart(cart, 1)));
        assertEquals(List.of(4L, 5L), ids(service.listCart(cart, 0)));
    }

    @Test
    void adjacentScopeBranchesStayOnTheSameRule() {
        historyDaoReturns(coupon(11L, 2, "100", FUTURE, List.of(), List.of(10L)));
        assertEquals(List.of(11L), ids(service.listCart(cart("100.10", 10L, 100L), 1)));
        historyDaoReturns(coupon(12L, 1, "100", PAST, List.of(100L), List.of()));
        assertEquals(List.of(12L), ids(service.listCart(cart("150.00", 10L, 100L), 0)));
        historyDaoReturns(coupon(13L, 2, "100", PAST, List.of(), List.of(10L)));
        assertEquals(List.of(13L), ids(service.listCart(cart("150.00", 10L, 100L), 0)));
        historyDaoReturns(coupon(14L, 1, "0", FUTURE, List.of(), List.of()));
        assertEquals(List.of(), ids(service.listCart(cart("50.00", 10L, 100L), 1)));
        historyDaoReturns(coupon(15L, 9, "0", FUTURE, List.of(), List.of()));
        assertEquals(List.of(), ids(service.listCart(cart("100.00", 10L, 100L), 1)));
        assertEquals(List.of(), ids(service.listCart(cart("100.00", 10L, 100L), 0)));
    }

    @Test
    void expiredAndZeroMatchStayDisabledWhileAValidCouponStaysEnabled() {
        historyDaoReturns(
                coupon(6L, 0, "100", PAST, List.of(), List.of()),
                coupon(7L, 2, "0", FUTURE, List.of(), List.of(99L)),
                coupon(8L, 0, "100", FUTURE, List.of(), List.of()),
                coupon(9L, 1, "100", FUTURE, List.of(100L), List.of()));
        List<CartPromotionItem> cart = cart("100.10", 10L, 100L);
        assertEquals(List.of(8L, 9L), ids(service.listCart(cart, 1)));
        assertEquals(List.of(6L, 7L), ids(service.listCart(cart, 0)));
    }

    private void historyDaoReturns(SmsCouponHistoryDetail... details) {
        when(historyDao.getDetailList(MEMBER_ID)).thenReturn(List.of(details));
    }

    private static List<Long> ids(List<SmsCouponHistoryDetail> details) {
        return details.stream().map(SmsCouponHistoryDetail::getId).toList();
    }

    private static List<CartPromotionItem> cart(String price, long productId, long categoryId) {
        return List.of(item(price, productId, categoryId));
    }

    private static CartPromotionItem item(String price, long productId, long categoryId) {
        CartPromotionItem item = new CartPromotionItem();
        item.setPrice(new BigDecimal(price));
        item.setQuantity(1);
        item.setReduceAmount(BigDecimal.ZERO);
        item.setProductId(productId);
        item.setProductCategoryId(categoryId);
        return item;
    }

    private static SmsCouponHistoryDetail coupon(long id, int useType, String minPoint, Date endTime,
                                                  List<Long> categoryIds, List<Long> productIds) {
        SmsCoupon coupon = new SmsCoupon();
        coupon.setId(id);
        coupon.setUseType(useType);
        coupon.setMinPoint(new BigDecimal(minPoint));
        coupon.setEndTime(endTime);
        SmsCouponHistoryDetail detail = new SmsCouponHistoryDetail();
        detail.setId(id);
        detail.setCoupon(coupon);
        detail.setCategoryRelationList(categoryIds.stream().map(categoryId -> {
            SmsCouponProductCategoryRelation relation = new SmsCouponProductCategoryRelation();
            relation.setProductCategoryId(categoryId);
            return relation;
        }).toList());
        detail.setProductRelationList(productIds.stream().map(productId -> {
            SmsCouponProductRelation relation = new SmsCouponProductRelation();
            relation.setProductId(productId);
            return relation;
        }).toList());
        return detail;
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
