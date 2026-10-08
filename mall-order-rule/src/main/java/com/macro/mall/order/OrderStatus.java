package com.macro.mall.order;

import java.util.List;

/**
 * 订单状态常量，以及到达目标状态时允许的出发状态。
 * 支付←{0}，发货←{1}，确认收货←{2}，后台关闭←{0}。用户取消不走这里。
 */
public final class OrderStatus {
    public static final int UNPAID = 0;
    public static final int PAID = 1;
    public static final int SHIPPED = 2;
    public static final int COMPLETE = 3;
    public static final int CLOSED = 4;

    private OrderStatus() {
    }

    public static List<Integer> from(int target) {
        return switch (target) {
            case PAID -> List.of(UNPAID);
            case SHIPPED -> List.of(PAID);
            case COMPLETE -> List.of(SHIPPED);
            case CLOSED -> List.of(UNPAID);
            default -> List.of();
        };
    }
}
