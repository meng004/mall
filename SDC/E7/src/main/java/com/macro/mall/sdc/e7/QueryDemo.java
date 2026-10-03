package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.MallPortalApplication;
import com.macro.mall.portal.query.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** Runs the real portal service against the configured mall database. */
public final class QueryDemo {
    private QueryDemo() {}
    static ConfigurableApplicationContext openPortal() {
        var app = new SpringApplication(MallPortalApplication.class);
        // Existing security configuration requires a servlet context. Only bind loopback.
        return app.run("--server.address=127.0.0.1","--server.port=0",
            "--spring.rabbitmq.listener.simple.auto-startup=false",
            "--spring.rabbitmq.listener.direct.auto-startup=false");
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !("existing".equals(args[0]) || "llm".equals(args[0])))
            throw new IllegalArgumentException("Usage: QueryDemo existing|llm 'query text'");
        try (var context = openPortal()) {
            ProductQueryService service = "llm".equals(args[0])
                ? context.getBean(LlmProductQueryService.class) : context.getBean(ExistingProductQueryService.class);
            var result = service.query(ProductQueryRequest.text(args[1],1,20));
            System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }
}
