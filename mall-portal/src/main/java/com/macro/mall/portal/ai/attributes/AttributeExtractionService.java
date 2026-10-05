package com.macro.mall.portal.ai.attributes;

import java.util.List;
import java.util.Set;

/** 从描述中抽取白名单属性。未提到的属性保持未知，不写商品。 */
public interface AttributeExtractionService {
    enum State { OK, NEEDS_INPUT, UNSUPPORTED, UNAVAILABLE, PARTIAL }

    Result extract(Request input);

    record AttributeSpec(long id, String name, Set<String> allowedValues, String unit) {}

    record Request(List<AttributeSpec> attributes, String text) {}

    record Value(long attributeId, String value, String evidence) {}

    record Result(State state, List<Value> values, Set<Long> unknownIds) {}
}
