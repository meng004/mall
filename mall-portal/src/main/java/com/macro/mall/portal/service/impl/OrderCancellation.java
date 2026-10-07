package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderSettingMapper;
import com.macro.mall.mapper.SmsCouponHistoryMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.OmsOrderItemExample;
import com.macro.mall.model.OmsOrderSetting;
import com.macro.mall.model.SmsCouponHistory;
import com.macro.mall.model.SmsCouponHistoryExample;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;

/**
 * 取消订单的唯一 module：条件更新命中一行才做同一份补偿。
 * 须经 OmsPortalOrderService 调用，事务在接口上。
 */
@Component
public class OrderCancellation {
    private final OmsOrderMapper orderMapper;
    private final OmsOrderItemMapper orderItemMapper;
    private final PortalOrderDao portalOrderDao;
    private final OmsOrderSettingMapper orderSettingMapper;
    private final SmsCouponHistoryMapper couponHistoryMapper;
    private final UmsMemberService memberService;

    public OrderCancellation(OmsOrderMapper orderMapper,
                             OmsOrderItemMapper orderItemMapper,
                             PortalOrderDao portalOrderDao,
                             OmsOrderSettingMapper orderSettingMapper,
                             SmsCouponHistoryMapper couponHistoryMapper,
                             UmsMemberService memberService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.portalOrderDao = portalOrderDao;
        this.orderSettingMapper = orderSettingMapper;
        this.couponHistoryMapper = couponHistoryMapper;
        this.memberService = memberService;
    }

    /**
     * 仅当订单仍是未删除的待付款时取消。返回是否本次取消成功。
     */
    public boolean cancel(Long orderId) {
        return cancelIf(orderId, true);
    }

    /**
     * 按订单设置的超时分钟数查出超时订单，逐笔取消。
     * 与单笔不同，不要求 delete_status=0，以便后台删除过的待付款单仍能释放库存、券和积分。
     */
    public int cancelOverdue() {
        OmsOrderSetting orderSetting = orderSettingMapper.selectByPrimaryKey(1L);
        List<OmsOrderDetail> timeOutOrders = portalOrderDao.getTimeOutOrders(orderSetting.getNormalOrderOvertime());
        if (CollectionUtils.isEmpty(timeOutOrders)) {
            return 0;
        }
        int cancelled = 0;
        for (OmsOrderDetail timeOutOrder : timeOutOrders) {
            if (cancelIf(timeOutOrder.getId(), false)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    private boolean cancelIf(Long orderId, boolean requireNotDeleted) {
        OmsOrder record = new OmsOrder();
        record.setStatus(4);
        OmsOrderExample example = new OmsOrderExample();
        OmsOrderExample.Criteria criteria = example.createCriteria().andIdEqualTo(orderId).andStatusEqualTo(0);
        if (requireNotDeleted) {
            criteria.andDeleteStatusEqualTo(0);
        }
        int updated = orderMapper.updateByExampleSelective(record, example);
        if (updated != 1) {
            return false;
        }
        OmsOrder cancelOrder = orderMapper.selectByPrimaryKey(orderId);
        OmsOrderItemExample orderItemExample = new OmsOrderItemExample();
        orderItemExample.createCriteria().andOrderIdEqualTo(orderId);
        List<OmsOrderItem> orderItemList = orderItemMapper.selectByExample(orderItemExample);
        if (!CollectionUtils.isEmpty(orderItemList)) {
            portalOrderDao.releaseSkuStockLock(orderItemList);
        }
        updateCouponStatus(cancelOrder.getCouponId(), cancelOrder.getMemberId(), 0);
        if (cancelOrder.getUseIntegration() != null) {
            UmsMember member = memberService.getById(cancelOrder.getMemberId());
            memberService.updateIntegration(cancelOrder.getMemberId(), member.getIntegration() + cancelOrder.getUseIntegration());
        }
        return true;
    }

    /**
     * 将优惠券信息更改为指定状态。从基线原样搬入：按会员和券取第一条相反使用状态的记录。
     *
     * @param couponId  优惠券id
     * @param memberId  会员id
     * @param useStatus 0->未使用；1->已使用
     */
    private void updateCouponStatus(Long couponId, Long memberId, Integer useStatus) {
        if (couponId == null) {
            return;
        }
        SmsCouponHistoryExample example = new SmsCouponHistoryExample();
        example.createCriteria().andMemberIdEqualTo(memberId)
                .andCouponIdEqualTo(couponId).andUseStatusEqualTo(useStatus == 0 ? 1 : 0);
        List<SmsCouponHistory> couponHistoryList = couponHistoryMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(couponHistoryList)) {
            SmsCouponHistory couponHistory = couponHistoryList.get(0);
            couponHistory.setUseTime(new Date());
            couponHistory.setUseStatus(useStatus);
            couponHistoryMapper.updateByPrimaryKeySelective(couponHistory);
        }
    }
}
