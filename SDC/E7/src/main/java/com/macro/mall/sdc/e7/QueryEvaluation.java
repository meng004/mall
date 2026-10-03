package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.query.*;
import com.macro.mall.portal.llm.LlmClient;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Compares expected results; independence is established by the case-set provenance, not this runner. */
public final class QueryEvaluation {
    public record Case(String id, String kind, String text, String expected_status, List<Long> expected_ids) {}
    public record Result(String id, String kind, boolean passed, String actualStatus, List<Long> actualIds) {}
    private QueryEvaluation() {}
    public static Result evaluate(Case item, ProductQueryService service) {
        var answer = service.query(ProductQueryRequest.text(item.text(),1,100));
        var ids = answer.products().stream().map(ProductQueryResult.ProductSummary::id).sorted().toList();
        var expected = item.expected_ids().stream().sorted().toList();
        boolean passed = answer.status().name().equals(item.expected_status()) && ids.equals(expected);
        return new Result(item.id(),item.kind(),passed,answer.status().name(),ids);
    }
    public static String verdict(List<Result> results) {
        if (results.isEmpty()) return "INCOMPLETE";
        return results.stream().allMatch(Result::passed) ? "REGRESSION_PASSED" : "FAILED";
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: QueryEvaluation cases.json report.json");
        var json = new ObjectMapper();
        List<Case> cases = json.readValue(Files.readString(Path.of(args[0])),new TypeReference<>() {});
        var results = new ArrayList<Result>();
        var modelResponses = new LinkedHashMap<String,List<String>>();
        try (var context = QueryDemo.openPortal()) {
            var existing = context.getBean(ExistingProductQueryService.class);
            var client = context.getBean(LlmClient.class);
            for (var item : cases) {
                var responses = new ArrayList<String>();
                var service = new LlmProductQueryService(existing, (system, input) -> {
                    String response = client.generate(system, input);
                    responses.add(response);
                    return response;
                });
                var result = evaluate(item,service);
                results.add(result);
                modelResponses.put(item.id(), responses);
                System.out.printf("%s %s %s%n", item.id(), result.actualStatus(), result.passed() ? "PASS" : "FAIL");
            }
        }
        var report = new LinkedHashMap<String,Object>();
        report.put("evaluatedAt", Instant.now().toString());
        report.put("evidenceType", "case-set comparison; independence and model accuracy require separate provenance");
        report.put("cases",args[0]); report.put("pageSize",100);
        report.put("verdict",verdict(results)); report.put("results",results);
        report.put("modelResponses",modelResponses);
        Path output = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);
        System.out.println(verdict(results));
        if (!"REGRESSION_PASSED".equals(verdict(results))) System.exit(1);
    }
}
