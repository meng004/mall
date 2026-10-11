package com.macro.mall.portal.ai.order;

import java.time.Instant;
import java.util.List;

/** 当前会员的订单状态问答。请求不含会员编号，会员来自既有登录上下文。 */
public interface OrderQuestionService {
    enum State {
        OK, NEEDS_INPUT, UNSUPPORTED, UNAVAILABLE,
        /** 查询不再返回。物流公司或单号为空时结果仍是 OK，空着的字段保持为空。 */
        PARTIAL
    }

    Result query(Request input);

    record Request(String text, Integer status, Instant fromInclusive, Instant untilExclusive,
                   int pageNum, int pageSize) {}

    record OrderFact(long id, int status, Instant createdAt, String logisticsCompany, String logisticsCode) {}

    record Result(State state, List<OrderFact> orders, long total, String message) {}
}
