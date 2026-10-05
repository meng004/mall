package com.macro.mall.sdc.e2;

import com.macro.mall.mapper.SmsCouponHistoryMapper;
import com.macro.mall.mapper.SmsCouponMapper;
import com.macro.mall.model.SmsCoupon;
import com.macro.mall.model.SmsCouponHistory;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.UmsMemberCouponServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * E2-01 特征测试：领取优惠券生成的 16 位优惠码 = 时间毫秒后 8 位 + 4 位随机数字 + 会员 ID 后 4 位。
 * 只经由现有入口 {@code add(couponId)}，改前改后同一组断言。
 * 改前生成逻辑读取系统时间、每位新建一个 Random，测试只能用时间窗口和构造拦截去控制这两个隐藏输入。
 */
class SDCE201Test {
    private static final long COUPON_ID = 8L;

    @Test
    void shortMemberIdIsZeroPaddedAfterTimeAndDigits() {
        Claim claim = claim(7L);

        String code = claim.history.getCouponCode();
        assertEquals(16, code.length());
        assertTimePrefix(code, claim);
        assertEquals("1234", code.substring(8, 12));
        assertEquals("0007", code.substring(12));
        assertEquals(4, claim.randomsCreated);
        assertEquals(7L, claim.history.getMemberId());
        assertEquals(COUPON_ID, claim.history.getCouponId());
        assertEquals(1, claim.history.getGetType());
        assertEquals(0, claim.history.getUseStatus());
        assertEquals(List.of("insert", "update"), claim.writes);
    }

    @Test
    void longMemberIdKeepsItsLastFourDigits() {
        Claim claim = claim(123456L);

        String code = claim.history.getCouponCode();
        assertEquals(16, code.length());
        assertTimePrefix(code, claim);
        assertEquals("1234", code.substring(8, 12));
        assertEquals("3456", code.substring(12));
    }

    @Test
    void fourDigitMemberIdIsUsedAsIs() {
        Claim claim = claim(4321L);

        assertEquals("12344321", claim.history.getCouponCode().substring(8));
    }

    private static void assertTimePrefix(String code, Claim claim) {
        List<String> allowed = new ArrayList<>();
        for (long t = claim.before; t <= claim.after; t++) {
            String s = Long.toString(t);
            allowed.add(s.substring(s.length() - 8));
        }
        assertTrue(allowed.contains(code.substring(0, 8)), "prefix " + code.substring(0, 8) + " not in " + allowed);
    }

    private static Claim claim(long memberId) {
        Claim claim = new Claim();
        UmsMemberCouponServiceImpl service = new UmsMemberCouponServiceImpl();
        UmsMemberService memberService = dependency(service, "memberService", UmsMemberService.class);
        SmsCouponMapper couponMapper = dependency(service, "couponMapper", SmsCouponMapper.class);
        SmsCouponHistoryMapper historyMapper = dependency(service, "couponHistoryMapper", SmsCouponHistoryMapper.class);
        UmsMember member = new UmsMember();
        member.setId(memberId);
        member.setNickname("member");
        when(memberService.getCurrentMember()).thenReturn(member);
        SmsCoupon coupon = new SmsCoupon();
        coupon.setId(COUPON_ID);
        coupon.setCount(2);
        coupon.setPerLimit(1);
        coupon.setReceiveCount(4);
        coupon.setEnableTime(new Date(0));
        when(couponMapper.selectByPrimaryKey(COUPON_ID)).thenReturn(coupon);
        when(historyMapper.countByExample(any())).thenReturn(0L);
        when(historyMapper.insert(any())).thenAnswer(invocation -> {
            SmsCouponHistory history = invocation.getArgument(0);
            SmsCouponHistory copy = new SmsCouponHistory();
            copy.setCouponCode(history.getCouponCode());
            copy.setMemberId(history.getMemberId());
            copy.setCouponId(history.getCouponId());
            copy.setGetType(history.getGetType());
            copy.setUseStatus(history.getUseStatus());
            claim.history = copy;
            claim.writes.add("insert");
            return 1;
        });
        when(couponMapper.updateByPrimaryKey(any())).thenAnswer(invocation -> {
            SmsCoupon saved = invocation.getArgument(0);
            assertEquals(1, saved.getCount());
            assertEquals(5, saved.getReceiveCount());
            claim.writes.add("update");
            return 1;
        });
        // 第 n 个新建的 Random 给出数字 n，四位随机数字因此固定为 1234。
        try (MockedConstruction<Random> randoms = mockConstruction(Random.class,
                (random, context) -> when(random.nextInt(10)).thenReturn(context.getCount()))) {
            claim.before = System.currentTimeMillis();
            service.add(COUPON_ID);
            claim.after = System.currentTimeMillis();
            claim.randomsCreated = randoms.constructed().size();
        }
        return claim;
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }

    private static class Claim {
        SmsCouponHistory history;
        final List<String> writes = new ArrayList<>();
        long before;
        long after;
        int randomsCreated;
    }
}
