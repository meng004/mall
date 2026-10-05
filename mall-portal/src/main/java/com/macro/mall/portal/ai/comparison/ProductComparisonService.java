package com.macro.mall.portal.ai.comparison;

import java.util.List;

/** 两件当前可见商品的属性对照。模型只选择属性编号，不计算单位。 */
public interface ProductComparisonService {
    enum State { OK, NEEDS_INPUT, UNSUPPORTED, UNAVAILABLE, PARTIAL }

    Result compare(Request input);

    record Request(long leftId, long rightId, Long leftSkuId, Long rightSkuId, String text, List<Long> attributeIds) {}

    record Value(long productId, Long skuId, long attributeId, String raw, String normalized, String sourceField) {}

    record Row(long attributeId, Value left, Value right, String relation) {}

    record Result(State state, List<Row> rows, List<String> questions) {}
}
