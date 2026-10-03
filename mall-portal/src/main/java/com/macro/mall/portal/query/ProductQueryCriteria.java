package com.macro.mall.portal.query;

import java.math.BigDecimal;

/** Restricted read-only AND predicate. Bounds match pms_product price/stock storage. */
public record ProductQueryCriteria(String name, BigDecimal priceLt, Integer stockLt) {
    public ProductQueryCriteria {
        if (name != null) {
            name = name.strip();
            if (name.isEmpty() || name.length() > 200)
                throw new IllegalArgumentException("名称必须为1–200字的文本");
        }
        if (priceLt != null && (priceLt.signum() < 0 || priceLt.compareTo(new BigDecimal("99999999.99")) > 0 || priceLt.stripTrailingZeros().scale() > 2))
            throw new IllegalArgumentException("价格范围为0–99999999.99，最多两位小数");
        if (stockLt != null && stockLt < 0) throw new IllegalArgumentException("库存必须是非负整数");
        if (name == null && priceLt == null && stockLt == null) throw new IllegalArgumentException("至少提供一个查询条件");
    }
}
