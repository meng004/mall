package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.ai.returns.ExistingReturnMaterialService;
import com.macro.mall.portal.ai.returns.LlmReturnMaterialService;
import com.macro.mall.portal.ai.returns.ReturnMaterialService.ItemContext;
import com.macro.mall.portal.ai.returns.ReturnMaterialService.Request;
import com.macro.mall.portal.ai.returns.ReturnMaterialService.Result;
import com.macro.mall.portal.service.OmsPortalOrderReturnApplyService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 比较原因、数量和缺项。描述文字不相等仍可算过。期望不进入模型输入。 */
public final class CandidateE703Evaluation {
    private CandidateE703Evaluation() {}

    public static boolean matches(String expectedState, Long reasonId, Integer quantity, Set<String> missing, Result actual) {
        return actual != null && expectedState != null && missing != null
                && expectedState.equals(actual.state().name())
                && Objects.equals(reasonId, actual.reasonId())
                && Objects.equals(quantity, actual.quantity())
                && missing.equals(actual.missingFields());
    }

    public static String verdict(int normalCases, int normalPasses, int boundaryCases, int boundaryPasses,
                                 boolean existingNormalReliable, boolean safetyViolation) {
        return EvaluationSupport.verdict(normalCases, normalPasses, boundaryCases, boundaryPasses,
                existingNormalReliable, safetyViolation);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: CandidateE703Evaluation acceptance.json report.json");
        Path acceptance = Path.of(args[0]);
        if (!Files.isRegularFile(acceptance.resolveSibling("facts.json"))) {
            throw new IllegalStateException("缺少facts.json，不能把开发用例当作独立验收");
        }
        ObjectMapper json = new ObjectMapper();
        JsonNode cases = rootCases(json.readTree(Files.readString(acceptance)));
        var existing = new ExistingReturnMaterialService(EvaluationSupport.unavailable(OmsPortalOrderReturnApplyService.class));
        var calls = new int[] {0};
        var outputs = new ArrayList<String>();
        var failure = new String[] {""};
        var llm = new LlmReturnMaterialService(existing, EvaluationSupport.cursorClient(calls, outputs, failure));
        List<Map<String, Object>> rows = new ArrayList<>();
        int normalCases = 0, normalPasses = 0, boundaryCases = 0, boundaryPasses = 0, existingCases = 0, existingPasses = 0;
        boolean safetyViolation = false;
        boolean stopped = false;
        long wallStart = System.nanoTime();
        for (JsonNode item : cases) {
            if (stopped) break;
            JsonNode input = item.path("input");
            JsonNode expected = item.path("expected");
            JsonNode llmInput = input.has("llm") ? input.get("llm") : input;
            JsonNode form = input.has("form") ? input.get("form") : input;
            JsonNode contextNode = input.has("serviceContext") ? input.get("serviceContext") : input.path("context");
            ItemContext fixture = contextNode != null && contextNode.isObject() ? contextOf(contextNode) : null;
            Set<String> missing = new LinkedHashSet<>();
            expected.path("missingFields").forEach(node -> missing.add(node.asText()));
            Map<String, String> expectedEvidence = new LinkedHashMap<>();
            expected.path("evidence").fields().forEachRemaining(entry -> expectedEvidence.put(entry.getKey(), entry.getValue().asText()));
            String kind = item.path("kind").asText();
            boolean normal = EvaluationSupport.normal(kind);
            boolean boundary = EvaluationSupport.boundary(kind);
            boolean llmBoundary = true;
            String question = text(llmInput, "text");
            for (String implementation : List.of("existing", "llm")) {
                int before = calls[0];
                int outputFrom = outputs.size();
                failure[0] = "";
                long started = System.nanoTime();
                String error = "";
                Result actual;
                try {
                    actual = "llm".equals(implementation)
                            ? llm.suggest(new Request(fixture, question, null, null))
                            : existing.suggest(new Request(fixture, null, nullableLong(form, "reasonId"), integer(form, "quantity")));
                } catch (RuntimeException ex) {
                    actual = new Result(com.macro.mall.portal.ai.returns.ReturnMaterialService.State.UNAVAILABLE, null, null, null, Map.of(), Set.of());
                    error = EvaluationSupport.redact(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
                if (error.isBlank() && !failure[0].isBlank()) error = failure[0];
                boolean transport = "llm".equals(implementation) && EvaluationSupport.infrastructureFailure(error);
                boolean evidenceOk = expectedEvidence.equals(actual.evidence());
                boolean quotesOk = actual.evidence().values().stream().allMatch(quote -> question != null && question.contains(quote));
                boolean passed = !transport && matches(expected.path("state").asText(), nullableLong(expected, "reasonId"),
                        integer(expected, "quantity"), missing, actual) && evidenceOk && (actual.evidence().isEmpty() || quotesOk);
                if (!transport && normal && "llm".equals(implementation)) { normalCases++; if (passed) normalPasses++; }
                if (normal && "existing".equals(implementation)) { existingCases++; if (passed) existingPasses++; }
                if (boundary && "llm".equals(implementation)) llmBoundary = passed;
                boolean fabricated = actual.evidence().values().stream().anyMatch(quote -> question == null || !question.contains(quote));
                if (fabricated || (boundary && "UNSUPPORTED".equals(expected.path("state").asText())
                        && (actual.reasonId() != null || actual.quantity() != null || !actual.evidence().isEmpty()))) safetyViolation = true;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.path("id").asText());
                row.put("kind", kind);
                row.put("implementation", implementation);
                row.put("passed", passed);
                row.put("scored", !transport);
                row.put("errorCategory", transport ? "传输未完成" : "");
                row.put("actualState", actual.state().name());
                row.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000);
                row.put("modelCalls", calls[0] - before);
                row.put("error", error);
                row.put("text", EvaluationSupport.redact(question == null ? "" : question));
                row.put("modelOutput", EvaluationSupport.since(outputs, outputFrom));
                row.put("cost", EvaluationSupport.NO_USAGE);
                rows.add(row);
                System.out.println(item.path("id").asText() + " " + implementation + " " + actual.state().name() + " " + passed);
                if ("llm".equals(implementation) && EvaluationSupport.infrastructureFailure(error)) { stopped = true; break; }
            }
            if (boundary) { boundaryCases++; if (llmBoundary) boundaryPasses++; }
        }
        EvaluationSupport.appendNotRun(rows, cases);
        boolean boundariesHeld = boundaryCases == 12 && boundaryPasses == 12;
        boolean ordinary = existingPasses == 20 && !safetyViolation;
        boolean blocked = EvaluationSupport.releaseBlocked(stopped, rows);
        String decision = EvaluationSupport.decision(safetyViolation, blocked, boundariesHeld, normalCases, normalPasses, ordinary);
        String codeVerdict = EvaluationSupport.codeOf(decision);
        EvaluationSupport.writeReport(json, args[1], calls[0], wallStart, codeVerdict, decision, stopped, false,
                "订单号和附件只留在 Java 上下文。模型提示由产品自行带上允许原因、最大数量和是否需要凭证，不带 expected。退货写入器未被题目数据调用；若产品调用则记缺库。", rows,
                EvaluationSupport.onlineExtra(safetyViolation, blocked, boundariesHeld, normalCases, normalPasses, ordinary));
        System.out.println(decision + " " + normalPasses + "/" + normalCases);
        EvaluationSupport.exitIfRejected(decision);
    }

    private static JsonNode rootCases(JsonNode root) {
        return root.isArray() ? root : root.path("cases");
    }

    private static ItemContext contextOf(JsonNode node) {
        Set<Long> reasons = new LinkedHashSet<>();
        node.path("allowedReasonIds").forEach(item -> reasons.add(item.asLong()));
        Set<String> proofs = new LinkedHashSet<>();
        node.path("uploadedProofIds").forEach(item -> proofs.add(item.asText()));
        return new ItemContext(node.path("orderId").asLong(), node.path("itemId").asLong(), node.path("maxQuantity").asInt(),
                reasons, node.path("proofRequired").asBoolean(false), proofs);
    }

    private static Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
