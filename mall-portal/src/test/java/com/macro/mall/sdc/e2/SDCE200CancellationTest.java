package com.macro.mall.sdc.e2;

import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderSettingMapper;
import com.macro.mall.mapper.SmsCouponHistoryMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.OmsOrderSetting;
import com.macro.mall.model.SmsCouponHistory;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.OrderCancellation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * E2-00 改后测试（HEAD_ONLY_TESTS）：直接驱动 {@link OrderCancellation}。
 * 基线上没有这个类，不能参与改前编译。
 */
class SDCE200CancellationTest {
    @Test
    void missedConditionalUpdateDoesNotCompensate() {
        OmsOrderMapper orderMapper = mock(OmsOrderMapper.class);
        OmsOrderItemMapper orderItemMapper = mock(OmsOrderItemMapper.class);
        PortalOrderDao portalOrderDao = mock(PortalOrderDao.class);
        OmsOrderSettingMapper orderSettingMapper = mock(OmsOrderSettingMapper.class);
        SmsCouponHistoryMapper couponHistoryMapper = mock(SmsCouponHistoryMapper.class);
        UmsMemberService memberService = mock(UmsMemberService.class);
        when(orderMapper.updateByExampleSelective(any(), any())).thenReturn(0);
        when(orderMapper.updateByPrimaryKeySelective(any())).thenReturn(1);
        OrderCancellation cancellation = cancellation(orderMapper, orderItemMapper, portalOrderDao,
                orderSettingMapper, couponHistoryMapper, memberService);

        assertFalse(cancellation.cancel(11L));

        ArgumentCaptor<OmsOrder> record = ArgumentCaptor.forClass(OmsOrder.class);
        ArgumentCaptor<OmsOrderExample> example = ArgumentCaptor.forClass(OmsOrderExample.class);
        verify(orderMapper).updateByExampleSelective(record.capture(), example.capture());
        assertEquals(4, record.getValue().getStatus());
        assertNull(record.getValue().getId());
        assertEquals(11L, criterion(example.getValue(), "id"));
        assertEquals(0L, criterion(example.getValue(), "status"));
        assertEquals(0L, criterion(example.getValue(), "delete_status"));
        verify(orderMapper, never()).updateByPrimaryKeySelective(any());
        verify(orderMapper, never()).selectByPrimaryKey(any());
        verifyNoInteractions(orderItemMapper, portalOrderDao, orderSettingMapper, couponHistoryMapper, memberService);
    }

    @Test
    void overdueSkipsOrderWhoseConditionalUpdateMisses() {
        OmsOrderMapper orderMapper = mock(OmsOrderMapper.class);
        OmsOrderItemMapper orderItemMapper = mock(OmsOrderItemMapper.class);
        PortalOrderDao portalOrderDao = mock(PortalOrderDao.class);
        OmsOrderSettingMapper orderSettingMapper = mock(OmsOrderSettingMapper.class);
        SmsCouponHistoryMapper couponHistoryMapper = mock(SmsCouponHistoryMapper.class);
        UmsMemberService memberService = mock(UmsMemberService.class);
        OmsOrderSetting setting = new OmsOrderSetting();
        setting.setNormalOrderOvertime(120);
        when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);
        when(portalOrderDao.getTimeOutOrders(120)).thenReturn(List.of(decoy(11L), decoy(12L)));
        when(orderMapper.updateByExampleSelective(any(), any())).thenAnswer(invocation -> {
            OmsOrderExample example = invocation.getArgument(1);
            return Long.valueOf(11L).equals(criterion(example, "id")) ? 1 : 0;
        });
        OmsOrderItem item = reloadedItem();
        when(orderMapper.selectByPrimaryKey(11L)).thenReturn(reloaded());
        when(orderItemMapper.selectByExample(any())).thenReturn(List.of(item));
        when(couponHistoryMapper.selectByExample(any())).thenReturn(List.of(usedCoupon()));
        when(memberService.getById(40L)).thenReturn(member());
        OrderCancellation cancellation = cancellation(orderMapper, orderItemMapper, portalOrderDao,
                orderSettingMapper, couponHistoryMapper, memberService);

        assertEquals(1, cancellation.cancelOverdue());

        verify(portalOrderDao).releaseSkuStockLock(List.of(item));
        verify(memberService).updateIntegration(40L, 105);
        verify(memberService, never()).getById(777L);
        verify(orderMapper, never()).selectByPrimaryKey(12L);
    }

    @Test
    void overdueConditionalUpdateIgnoresDeleteStatus() {
        OmsOrderMapper orderMapper = mock(OmsOrderMapper.class);
        PortalOrderDao portalOrderDao = mock(PortalOrderDao.class);
        OmsOrderSettingMapper orderSettingMapper = mock(OmsOrderSettingMapper.class);
        OmsOrderSetting setting = new OmsOrderSetting();
        setting.setNormalOrderOvertime(120);
        when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);
        when(portalOrderDao.getTimeOutOrders(120)).thenReturn(List.of(decoy(11L)));
        when(orderMapper.updateByExampleSelective(any(), any())).thenReturn(0);
        OrderCancellation cancellation = cancellation(orderMapper, mock(OmsOrderItemMapper.class), portalOrderDao,
                orderSettingMapper, mock(SmsCouponHistoryMapper.class), mock(UmsMemberService.class));

        assertEquals(0, cancellation.cancelOverdue());

        ArgumentCaptor<OmsOrderExample> example = ArgumentCaptor.forClass(OmsOrderExample.class);
        verify(orderMapper).updateByExampleSelective(any(), example.capture());
        assertEquals(11L, criterion(example.getValue(), "id"));
        assertEquals(0L, criterion(example.getValue(), "status"));
        assertFalse(hasCriterion(example.getValue(), "delete_status"));
    }

    @Test
    void singleAndBatchEmitSameCompensation() {
        Effect single = compensate(false);
        Effect batch = compensate(true);
        assertEquals(single, batch);
    }

    private static Effect compensate(boolean batch) {
        OmsOrderMapper orderMapper = mock(OmsOrderMapper.class);
        OmsOrderItemMapper orderItemMapper = mock(OmsOrderItemMapper.class);
        PortalOrderDao portalOrderDao = mock(PortalOrderDao.class);
        OmsOrderSettingMapper orderSettingMapper = mock(OmsOrderSettingMapper.class);
        SmsCouponHistoryMapper couponHistoryMapper = mock(SmsCouponHistoryMapper.class);
        UmsMemberService memberService = mock(UmsMemberService.class);
        when(orderMapper.updateByExampleSelective(any(), any())).thenReturn(1);
        when(orderMapper.selectByPrimaryKey(11L)).thenReturn(reloaded());
        when(orderItemMapper.selectByExample(any())).thenReturn(List.of(reloadedItem()));
        when(couponHistoryMapper.selectByExample(any())).thenReturn(List.of(usedCoupon()));
        when(memberService.getById(40L)).thenReturn(member());
        if (batch) {
            OmsOrderSetting setting = new OmsOrderSetting();
            setting.setNormalOrderOvertime(120);
            when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);
            when(portalOrderDao.getTimeOutOrders(120)).thenReturn(List.of(decoy(11L)));
        }
        OrderCancellation cancellation = cancellation(orderMapper, orderItemMapper, portalOrderDao,
                orderSettingMapper, couponHistoryMapper, memberService);
        if (batch) {
            assertEquals(1, cancellation.cancelOverdue());
        } else {
            assertEquals(true, cancellation.cancel(11L));
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OmsOrderItem>> released = ArgumentCaptor.forClass(List.class);
        verify(portalOrderDao).releaseSkuStockLock(released.capture());
        ArgumentCaptor<SmsCouponHistory> coupon = ArgumentCaptor.forClass(SmsCouponHistory.class);
        verify(couponHistoryMapper).updateByPrimaryKeySelective(coupon.capture());
        verify(memberService).updateIntegration(40L, 105);
        OmsOrderItem item = released.getValue().get(0);
        return new Effect(item.getProductSkuId(), item.getProductQuantity(), coupon.getValue().getUseStatus());
    }

    private static OrderCancellation cancellation(OmsOrderMapper orderMapper,
                                                  OmsOrderItemMapper orderItemMapper,
                                                  PortalOrderDao portalOrderDao,
                                                  OmsOrderSettingMapper orderSettingMapper,
                                                  SmsCouponHistoryMapper couponHistoryMapper,
                                                  UmsMemberService memberService) {
        return new OrderCancellation(orderMapper, orderItemMapper, portalOrderDao, orderSettingMapper,
                couponHistoryMapper, memberService);
    }

    private static OmsOrderDetail decoy(long orderId) {
        OmsOrderDetail detail = new OmsOrderDetail();
        detail.setId(orderId);
        detail.setMemberId(777L);
        detail.setCouponId(888L);
        detail.setUseIntegration(999);
        OmsOrderItem item = new OmsOrderItem();
        item.setProductSkuId(1L);
        item.setProductQuantity(9);
        detail.setOrderItemList(List.of(item));
        return detail;
    }

    private static OmsOrder reloaded() {
        OmsOrder order = new OmsOrder();
        order.setId(11L);
        order.setMemberId(40L);
        order.setCouponId(30L);
        order.setUseIntegration(5);
        order.setStatus(4);
        return order;
    }

    private static OmsOrderItem reloadedItem() {
        OmsOrderItem item = new OmsOrderItem();
        item.setOrderId(11L);
        item.setProductSkuId(70L);
        item.setProductQuantity(2);
        return item;
    }

    private static SmsCouponHistory usedCoupon() {
        SmsCouponHistory history = new SmsCouponHistory();
        history.setId(3L);
        history.setUseStatus(1);
        return history;
    }

    private static UmsMember member() {
        UmsMember member = new UmsMember();
        member.setId(40L);
        member.setIntegration(100);
        return member;
    }

    private static Long criterion(OmsOrderExample example, String column) {
        for (OmsOrderExample.Criterion criterion : example.getOredCriteria().get(0).getAllCriteria()) {
            if (criterion.getCondition().startsWith(column)) {
                return ((Number) criterion.getValue()).longValue();
            }
        }
        throw new AssertionError("条件里没有 " + column);
    }

    private static boolean hasCriterion(OmsOrderExample example, String column) {
        for (OmsOrderExample.Criterion criterion : example.getOredCriteria().get(0).getAllCriteria()) {
            if (criterion.getCondition().startsWith(column)) {
                return true;
            }
        }
        return false;
    }

    private record Effect(long skuId, int quantity, int couponUseStatus) {
    }
}
