package com.macro.mall.portal.ai.reasons;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ReturnReasonBatchService {
    enum State { OK, NEEDS_INPUT, UNSUPPORTED, UNAVAILABLE, PARTIAL }

    Result analyze(List<Item> input);

    enum Label { QUALITY, LOGISTICS, SIZE_SPEC, PREFERENCE, OTHER, UNCERTAIN }

    record Item(String anonymousId, String category, String reason, String description) {}

    record Classified(String anonymousId, Label label, String evidence, boolean failed) {}

    record Result(State state, List<Classified> items, Map<Label, Integer> counts,
                  Map<Label, BigDecimal> ratios, int denominator, int failedCount) {}

    static String batchTitle(int denominator) {
        return "本批" + denominator + "条";
    }
}
