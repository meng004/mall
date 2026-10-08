package com.macro.mall.sdc.e2;

import com.macro.mall.order.OrderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 规则单测：只在改后编译。支付、发货、确认收货、后台关闭各只有一个出发状态。 */
class SDCE201StateRuleTest {
    @Test
    void eachTargetAllowsOnlyItsSourceStatus() {
        assertEquals(0, OrderStatus.UNPAID);
        assertEquals(1, OrderStatus.PAID);
        assertEquals(2, OrderStatus.SHIPPED);
        assertEquals(3, OrderStatus.COMPLETE);
        assertEquals(4, OrderStatus.CLOSED);
        assertEquals(List.of(OrderStatus.UNPAID), OrderStatus.from(OrderStatus.PAID));
        assertEquals(List.of(OrderStatus.PAID), OrderStatus.from(OrderStatus.SHIPPED));
        assertEquals(List.of(OrderStatus.SHIPPED), OrderStatus.from(OrderStatus.COMPLETE));
        assertEquals(List.of(OrderStatus.UNPAID), OrderStatus.from(OrderStatus.CLOSED));
    }
}
