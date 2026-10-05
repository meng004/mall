package com.macro.mall.sdc.e2;

import com.macro.mall.mapper.SmsCouponHistoryMapper;
import com.macro.mall.mapper.SmsCouponMapper;
import com.macro.mall.model.SmsCoupon;
import com.macro.mall.model.SmsCouponHistory;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.CouponCodeGenerator;
import com.macro.mall.portal.service.impl.UmsMemberCouponServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * E2-01 改后测试（HEAD_ONLY_TESTS）：用到新接缝 {@link CouponCodeGenerator}，基线上无法编译。
 * 与 {@link SDCE201Test} 对照：改前要用时间窗口和拦截 Random 构造才能控制隐藏输入；
 * 改后时间和数字源由调用方给定，优惠码可以整串断言。
 */
class SDCE201GeneratorTest {
    private static final long NOW = 1759651234567L;

    @Test
    void fixedTimeAndDigitsGiveExactCode() {
        PrimitiveIterator.OfInt digits = IntStream.of(9, 8, 7, 6).iterator();
        CouponCodeGenerator generator = new CouponCodeGenerator(() -> NOW, digits::nextInt);

        assertEquals("5123456798760007", generator.generate(7L));
    }

    @Test
    void longMemberIdKeepsLastFourDigits() {
        assertEquals("5123456700003456", new CouponCodeGenerator(() -> NOW, () -> 0).generate(123456L));
    }

    @Test
    void claimUsesInjectedGeneratorWithoutTimeWindowOrConstructionInterception() {
        UmsMemberCouponServiceImpl service = new UmsMemberCouponServiceImpl();
        UmsMemberService memberService = dependency(service, "memberService", UmsMemberService.class);
        SmsCouponMapper couponMapper = dependency(service, "couponMapper", SmsCouponMapper.class);
        SmsCouponHistoryMapper historyMapper = dependency(service, "couponHistoryMapper", SmsCouponHistoryMapper.class);
        UmsMember member = new UmsMember();
        member.setId(7L);
        when(memberService.getCurrentMember()).thenReturn(member);
        SmsCoupon coupon = new SmsCoupon();
        coupon.setId(8L);
        coupon.setCount(2);
        coupon.setPerLimit(1);
        coupon.setReceiveCount(4);
        coupon.setEnableTime(new Date(0));
        when(couponMapper.selectByPrimaryKey(8L)).thenReturn(coupon);
        when(historyMapper.countByExample(any())).thenReturn(0L);
        service.setCouponCodeGenerator(new CouponCodeGenerator(() -> NOW, () -> 1));

        service.add(8L);

        ArgumentCaptor<SmsCouponHistory> saved = ArgumentCaptor.forClass(SmsCouponHistory.class);
        verify(historyMapper).insert(saved.capture());
        assertEquals("5123456711110007", saved.getValue().getCouponCode());
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
