package com.macro.mall.portal.query;

import java.math.BigDecimal;
import java.util.List;

public record ProductQueryResult(Status status, String source, ProductQueryCriteria criteria,
                                 List<ProductSummary> products, String message) {
    public enum Status { OK, UNSUPPORTED, UNAVAILABLE }
    public record ProductSummary(Long id, String name, BigDecimal price, Integer stock) {}
    public static ProductQueryResult failure(Status status, String message) {
        return new ProductQueryResult(status, "llm", null, List.of(), message + "；可改用普通查询 /product/search");
    }
}
