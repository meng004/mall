package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.query.*;

/** Runs the real portal service against the configured mall database. */
public final class QueryDemo {
    private QueryDemo() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !("existing".equals(args[0]) || "llm".equals(args[0])))
            throw new IllegalArgumentException("Usage: QueryDemo existing|llm 'query text'");
        try (var context = EvaluationSupport.openPortal()) {
            ProductQueryService service = "llm".equals(args[0])
                ? context.getBean(LlmProductQueryService.class) : context.getBean(ExistingProductQueryService.class);
            var result = service.query(ProductQueryRequest.text(args[1],1,20));
            System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }
}
