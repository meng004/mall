package com.macro.mall.portal.ai.order;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.service.OmsPortalOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class ExistingOrderQuestionService implements OrderQuestionService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Map<Integer, String> STATUS_TEXT = Map.of(
            0, "待付款",
            1, "待发货",
            2, "已发货",
            3, "已完成",
            4, "已关闭",
            5, "无效订单");
    private final OmsPortalOrderService orders;

    @Autowired
    public ExistingOrderQuestionService(OmsPortalOrderService orders) {
        this.orders = orders;
    }

    public ExistingOrderQuestionService(OmsPortalOrderService orders, Clock clock, ZoneId zone) {
        this(orders);
        if (clock == null || zone == null) {
            throw new IllegalArgumentException("clock");
        }
    }

    @Override
    public Result query(Request input) {
        if (input.text() != null && !input.text().isBlank()) {
            return new Result(State.UNSUPPORTED, List.of(), 0, "普通路径只接受表单条件");
        }
        if (input.pageNum() < 1 || input.pageSize() < 1 || input.pageSize() > 100) {
            return new Result(State.NEEDS_INPUT, List.of(), 0, "页码或每页数量无效");
        }
        if (input.status() != null && (input.status() < 0 || input.status() > 5)) {
            return new Result(State.NEEDS_INPUT, List.of(), 0, "状态必须是0到5或留空");
        }
        if (input.fromInclusive() != null && input.untilExclusive() != null
                && !input.fromInclusive().isBefore(input.untilExclusive())) {
            return new Result(State.NEEDS_INPUT, List.of(), 0, "时间区间无效");
        }
        Date from = toDate(input.fromInclusive());
        Date until = toDate(input.untilExclusive());
        CommonPage<OmsOrderDetail> page = orders.list(input.status(), from, until, input.pageNum(), input.pageSize());
        if (page == null) {
            return new Result(State.UNAVAILABLE, List.of(), 0, "订单查询没有返回");
        }
        List<OmsOrderDetail> rows = page.getList() == null ? List.of() : page.getList();
        long total = page.getTotal() == null ? 0L : page.getTotal();
        List<OrderFact> facts = new ArrayList<>();
        boolean logisticsMissing = false;
        StringBuilder message = new StringBuilder();
        for (OmsOrderDetail row : rows) {
            String company = blankToNull(row.getDeliveryCompany());
            String code = blankToNull(row.getDeliverySn());
            if (company == null || code == null) {
                logisticsMissing = true;
            }
            facts.add(new OrderFact(row.getId(), row.getStatus(),
                    row.getCreateTime() == null ? null : row.getCreateTime().toInstant(), company, code));
            if (!message.isEmpty()) {
                message.append('；');
            }
            message.append("订单").append(row.getId()).append(STATUS_TEXT.getOrDefault(row.getStatus(), "未知状态"));
            if (company == null || code == null) {
                message.append("；物流未知，不能确定到货日期");
            } else {
                message.append("；物流公司").append(company).append("，单号").append(code);
            }
        }
        if (facts.isEmpty()) {
            return new Result(State.OK, facts, total, "没有符合条件的订单");
        }
        return new Result(logisticsMissing ? State.PARTIAL : State.OK, facts, total, message.toString());
    }

    private static Date toDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
