package com.macro.mall.sdc.e4;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Characterization of listCart scope matching. Amounts used here stay on the same side of the
 * threshold under both integer truncation and numeric comparison. The 99.90 truncation is printed
 * for the evidence log and is not a permanent assertion.
 */
class SDCE407Test {
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
    void scopeMatchesKeepCouponIdSetAndOrder() {
        when(historyDao.getDetailList(MEMBER_ID)).thenReturn(List.of(
                detail(11L, 0, "100", FUTURE, List.of(), List.of()),
                detail(12L, 1, "100", FUTURE, List.of(100L), List.of()),
                detail(13L, 1, "50", FUTURE, List.of(100L), List.of()),
                detail(14L, 2, "100", FUTURE, List.of(), List.of(20L)),
                detail(15L, 2, "0", FUTURE, List.of(), List.of(99L)),
                detail(16L, 0, "0", PAST, List.of(), List.of()),
                detail(17L, 9, "0", FUTURE, List.of(), List.of()),
                detail(18L, 1, "400", FUTURE, List.of(200L), List.of()),
                detail(19L, 2, "100", FUTURE, List.of(), List.of(10L))));
        List<CartPromotionItem> cart = List.of(item(10L, 100L, "80.00"), item(20L, 200L, "500.00"));

        List<Long> enabled = ids(service.listCart(cart, 1));
        List<Long> disabled = ids(service.listCart(cart, 0));

        assertEquals(List.of(11L, 13L, 14L, 18L), enabled);
        assertEquals(List.of(12L, 15L, 16L, 19L), disabled);
        assertFalse(enabled.contains(17L));
        assertFalse(disabled.contains(17L));
    }

    @Test
    void expiredCouponStaysDisabledWhenAmountIsEnough() {
        when(historyDao.getDetailList(MEMBER_ID)).thenReturn(List.of(
                detail(16L, 0, "100", PAST, List.of(), List.of())));

        assertEquals(List.of(), ids(service.listCart(List.of(item(10L, 100L, "150.00")), 1)));
        assertEquals(List.of(16L), ids(service.listCart(List.of(item(10L, 100L, "150.00")), 0)));
    }

    @Test
    void emptyCartDisablesKnownScopesAndDropsUnknownScope() {
        when(historyDao.getDetailList(MEMBER_ID)).thenReturn(List.of(
                detail(21L, 0, "100", FUTURE, List.of(), List.of()),
                detail(22L, 1, "100", FUTURE, List.of(100L), List.of()),
                detail(23L, 2, "100", FUTURE, List.of(), List.of(10L)),
                detail(24L, 9, "100", FUTURE, List.of(), List.of())));

        assertEquals(List.of(), ids(service.listCart(List.of(), 1)));
        assertEquals(List.of(21L, 22L, 23L), ids(service.listCart(List.of(), 0)));
    }

    @Test
    void stableThresholdAmountsStayAvailableOrUnavailable() {
        when(historyDao.getDetailList(MEMBER_ID)).thenReturn(List.of(
                detail(31L, 0, "100", FUTURE, List.of(), List.of())));

        assertEquals(List.of(), ids(service.listCart(List.of(item(10L, 100L, "98.90")), 1)));
        assertEquals(List.of(31L), ids(service.listCart(List.of(item(10L, 100L, "100.00")), 1)));
        assertEquals(List.of(31L), ids(service.listCart(List.of(item(10L, 100L, "98.90")), 0)));
        assertEquals(List.of(), ids(service.listCart(List.of(item(10L, 100L, "100.00")), 0)));
    }

    @Test
    void truncationBoundaryIsLoggedRatherThanFrozen() {
        when(historyDao.getDetailList(MEMBER_ID)).thenReturn(List.of(
                detail(32L, 0, "100", FUTURE, List.of(), List.of())));
        int at9890 = service.listCart(List.of(item(10L, 100L, "98.90")), 1).size();
        int at9990 = service.listCart(List.of(item(10L, 100L, "99.90")), 1).size();
        int at10000 = service.listCart(List.of(item(10L, 100L, "100.00")), 1).size();
        System.out.println("THRESHOLD_BASELINE min=100.00 amount98.90=" + at9890
                + " amount99.90=" + at9990 + " amount100.00=" + at10000);
    }

    private static List<Long> ids(List<SmsCouponHistoryDetail> details) {
        return details.stream().map(SmsCouponHistoryDetail::getId).toList();
    }

    private static CartPromotionItem item(long productId, long categoryId, String price) {
        CartPromotionItem item = new CartPromotionItem();
        item.setProductId(productId);
        item.setProductCategoryId(categoryId);
        item.setPrice(new BigDecimal(price));
        item.setQuantity(1);
        item.setReduceAmount(BigDecimal.ZERO);
        return item;
    }

    private static SmsCouponHistoryDetail detail(long id, int useType, String minPoint, Date endTime,
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
