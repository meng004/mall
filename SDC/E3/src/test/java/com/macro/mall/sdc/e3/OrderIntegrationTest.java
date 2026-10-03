package com.macro.mall.sdc.e3;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.component.CancelOrderSender;
import com.macro.mall.portal.dao.*;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.service.*;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderIntegrationTest {
    private final OmsPortalOrderServiceImpl service = new OmsPortalOrderServiceImpl();

    private <T> T dependency(String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 30})
    void pointsArePersistedSeparatelyBeforeInsert(Integer use) {
            UmsMember member = new UmsMember();
            member.setId(1L);
            member.setIntegration(100);
            when(dependency("memberService", UmsMemberService.class).getCurrentMember()).thenReturn(member);
            CartPromotionItem item = new CartPromotionItem();
            item.setId(1L);
            item.setProductSkuId(7L);
            item.setQuantity(1);
            item.setPrice(new BigDecimal("100.00"));
            item.setReduceAmount(BigDecimal.ZERO);
            item.setRealStock(10);
            item.setIntegration(12);
            item.setGrowth(2);
            when(dependency("cartItemService", OmsCartItemService.class).listPromotion(anyLong(), any())).thenReturn(List.of(item));
            when(dependency("memberReceiveAddressService", UmsMemberReceiveAddressService.class).getItem(1L)).thenReturn(new UmsMemberReceiveAddress());
            UmsIntegrationConsumeSetting setting = new UmsIntegrationConsumeSetting();
            setting.setUseUnit(10);
            setting.setMaxPercentPerOrder(100);
            when(dependency("integrationConsumeSettingMapper", UmsIntegrationConsumeSettingMapper.class).selectByPrimaryKey(1L)).thenReturn(setting);
            PmsSkuStock sku = new PmsSkuStock();
            sku.setId(7L);
            sku.setStock(10);
            sku.setLockStock(0);
            when(dependency("skuStockMapper", PmsSkuStockMapper.class).selectByPrimaryKey(7L)).thenReturn(sku);
            when(dependency("redisService", RedisService.class).incr(anyString(), eq(1L))).thenReturn(1L);
            OmsOrderSetting orderSetting = new OmsOrderSetting();
            orderSetting.setNormalOrderOvertime(30);
            OmsOrderSettingMapper settings = dependency("orderSettingMapper", OmsOrderSettingMapper.class);
            when(settings.selectByPrimaryKey(1L)).thenReturn(orderSetting);
            when(settings.selectByExample(any())).thenReturn(List.of(orderSetting));
            dependency("orderItemDao", PortalOrderItemDao.class);
            dependency("cancelOrderSender", CancelOrderSender.class);
            OmsOrderMapper orders = dependency("orderMapper", OmsOrderMapper.class);
            when(orders.insert(any())).thenAnswer(call -> {
                OmsOrder inserted = call.getArgument(0);
                assertEquals(use, inserted.getUseIntegration(), "consumed points at insert boundary");
                assertEquals(12, inserted.getIntegration(), "gift points at insert boundary");
                inserted.setId(9L);
                return 1;
            });
            OrderParam param = new OrderParam();
            param.setMemberReceiveAddressId(1L);
            param.setPayType(0);
            param.setUseIntegration(use);
            assertNotNull(service.generateOrder(param).get("order"));
            verify(orders).insert(any());
    }

    @Test void invalidOrderDoesNotReachPersistence() {
        OrderParam param = new OrderParam();
        assertThrows(ApiException.class, () -> service.generateOrder(param));
        param.setMemberReceiveAddressId(1L);
        UmsMember member = new UmsMember();
        member.setId(1L);
        when(dependency("memberService", UmsMemberService.class).getCurrentMember()).thenReturn(member);
        CartPromotionItem item = new CartPromotionItem();
        item.setQuantity(1);
        item.setPrice(new BigDecimal("100.00"));
        when(dependency("cartItemService", OmsCartItemService.class).listPromotion(anyLong(), any())).thenReturn(List.of(item));
        assertThrows(ApiException.class, () -> service.generateOrder(param));
        item.setRealStock(10);
        param.setCouponId(2L);
        when(dependency("memberCouponService", UmsMemberCouponService.class).listCart(anyList(), eq(1))).thenReturn(List.of());
        assertThrows(ApiException.class, () -> service.generateOrder(param));
    }

}
