package com.macro.mall.portal.query;

public record ProductQueryRequest(String text, ProductQueryCriteria criteria, int pageNum, int pageSize) {
    public ProductQueryRequest {
        if ((text == null) == (criteria == null)) throw new IllegalArgumentException("只提供文本或结构化条件之一");
        if (text != null && (text.isBlank() || text.length() > 1000)) throw new IllegalArgumentException("文本长度为1–1000");
        if (pageNum < 1 || pageNum > 100000 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页越界");
    }
    public static ProductQueryRequest text(String text, int page, int size) { return new ProductQueryRequest(text,null,page,size); }
    public static ProductQueryRequest structured(ProductQueryCriteria criteria, int page, int size) { return new ProductQueryRequest(null,criteria,page,size); }
}
