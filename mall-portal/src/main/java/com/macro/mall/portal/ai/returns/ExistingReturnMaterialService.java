package com.macro.mall.portal.ai.returns;

import com.macro.mall.portal.service.OmsPortalOrderReturnApplyService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
public class ExistingReturnMaterialService implements ReturnMaterialService {
    private final OmsPortalOrderReturnApplyService writer;

    public ExistingReturnMaterialService(OmsPortalOrderReturnApplyService writer) {
        if (writer == null) {
            throw new IllegalArgumentException("writer");
        }
        this.writer = writer;
    }

    @Override
    public Result suggest(Request input) {
        if (writer == null) {
            throw new IllegalStateException("writer");
        }
        if (input.context() == null) {
            return ask(null, null, "");
        }
        if (input.text() != null && !input.text().isBlank()) {
            return new Result(State.UNSUPPORTED, null, null, null, Map.of(), Set.of());
        }
        return accept(input.context(), "", input.reasonId(), input.quantity(), "", Map.of(), false);
    }

    public Result accept(ItemContext context, String text, Long reasonId, Integer quantity, String description,
                         Map<String, String> evidence, boolean requireEvidence) {
        if (context == null) {
            return ask(null, null, "");
        }
        String body = text == null ? "" : text;
        Long reason = reasonId;
        Integer count = quantity;
        Map<String, String> kept = new LinkedHashMap<>();
        if (requireEvidence) {
            String reasonQuote = evidence.get("reasonId");
            if (reason == null || reasonQuote == null || !body.contains(reasonQuote)) {
                reason = null;
            } else {
                kept.put("reasonId", reasonQuote);
            }
            if (count != null) {
                String quantityQuote = evidence.get("quantity");
                if (quantityQuote == null || !body.contains(quantityQuote)) {
                    count = null;
                } else {
                    kept.put("quantity", quantityQuote);
                }
            }
        }
        Set<String> missing = new LinkedHashSet<>();
        if (reason == null || context.allowedReasonIds() == null || !context.allowedReasonIds().contains(reason)) {
            reason = null;
            missing.add("reasonId");
        }
        if (count == null || count < 1 || count > context.maxQuantity()) {
            count = null;
            missing.add("quantity");
        }
        if (context.proofRequired() && (context.uploadedProofIds() == null || context.uploadedProofIds().isEmpty())) {
            missing.add("proof");
        }
        if (!missing.isEmpty()) {
            return new Result(State.NEEDS_INPUT, reason, count, description, Map.copyOf(kept), Set.copyOf(missing));
        }
        return new Result(State.OK, reason, count, description, Map.copyOf(kept), Set.of());
    }

    private static Result ask(Long reasonId, Integer quantity, String description) {
        return new Result(State.NEEDS_INPUT, reasonId, quantity, description, Map.of(), Set.of("item"));
    }
}
