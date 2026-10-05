package com.macro.mall.portal.ai.returns;

import java.util.Map;
import java.util.Set;

/** 退货材料建议。只返回待确认字段，不提交申请。 */
public interface ReturnMaterialService {
    enum State { OK, NEEDS_INPUT, UNSUPPORTED, UNAVAILABLE, PARTIAL }

    Result suggest(Request input);

    record ItemContext(long orderId, long itemId, int maxQuantity, Set<Long> allowedReasonIds,
                       boolean proofRequired, Set<String> uploadedProofIds) {}

    record Request(ItemContext context, String text, Long reasonId, Integer quantity) {}

    record Result(State state, Long reasonId, Integer quantity, String description,
                  Map<String, String> evidence, Set<String> missingFields) {}
}
