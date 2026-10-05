package com.macro.mall.portal.ai.reasons;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ExistingReturnReasonBatchService implements ReturnReasonBatchService {
    private static final List<Map.Entry<String, Label>> REASONS = List.of(
            Map.entry("质量问题", Label.QUALITY),
            Map.entry("物流问题", Label.LOGISTICS),
            Map.entry("尺寸不符", Label.SIZE_SPEC),
            Map.entry("规格不符", Label.SIZE_SPEC),
            Map.entry("不喜欢", Label.PREFERENCE),
            Map.entry("其他", Label.OTHER));

    @Override
    public Result analyze(List<Item> input) {
        if (input == null || input.isEmpty()) {
            return empty();
        }
        if (idsIncomplete(input)) {
            return rejected(input);
        }
        List<Classified> items = new ArrayList<>();
        for (Item item : input) {
            items.add(classify(item));
        }
        return aggregate(null, items, input.size());
    }

    public Classified classify(Item item) {
        String reason = item.reason() == null ? "" : item.reason();
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, Label> entry : REASONS) {
            if (reason.contains(entry.getKey())) {
                hits.add(entry.getKey());
            }
        }
        if (hits.size() == 1 && reason.equals(hits.get(0))) {
            return new Classified(item.anonymousId(), labelOf(hits.get(0)), hits.get(0), false);
        }
        String evidence = reason.isBlank() ? null : reason;
        return new Classified(item.anonymousId(), Label.UNCERTAIN, evidence, false);
    }

    public static boolean idsIncomplete(List<Item> input) {
        Set<String> seen = new LinkedHashSet<>();
        for (Item item : input) {
            if (item == null || item.anonymousId() == null || item.anonymousId().isBlank() || !seen.add(item.anonymousId())) {
                return true;
            }
        }
        return false;
    }

    public static Result empty() {
        return new Result(State.NEEDS_INPUT, List.of(), Map.of(), Map.of(), 0, 0);
    }

    public static Result rejected(List<Item> input) {
        List<Classified> items = new ArrayList<>();
        for (Item item : input) {
            items.add(new Classified(item == null ? null : item.anonymousId(), null, null, true));
        }
        return aggregate(State.UNSUPPORTED, items, input.size());
    }

    public static Result aggregate(State forced, List<Classified> items, int denominator) {
        Map<Label, Integer> counts = new EnumMap<>(Label.class);
        for (Label label : Label.values()) {
            counts.put(label, 0);
        }
        int failed = 0;
        for (Classified item : items) {
            if (item.failed() || item.label() == null) {
                failed++;
            } else {
                counts.merge(item.label(), 1, Integer::sum);
            }
        }
        List<Label> order = new ArrayList<>(List.of(Label.values()));
        order.sort(Comparator.comparingInt((Label label) -> counts.get(label)).reversed().thenComparing(Label::name));
        Map<Label, Integer> ordered = new LinkedHashMap<>();
        Map<Label, BigDecimal> ratios = new LinkedHashMap<>();
        for (Label label : order) {
            int count = counts.get(label);
            ordered.put(label, count);
            ratios.put(label, BigDecimal.valueOf(count).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP));
        }
        State state = forced != null ? forced : (failed > 0 ? State.PARTIAL : State.OK);
        return new Result(state, List.copyOf(items), ordered, ratios, denominator, failed);
    }

    /** 证据只命中一个主因标签时返回该标签；没有规则或主因不唯一时返回 null。 */
    public static Label labelSupportedBy(String evidence) {
        Set<Label> hits = labelsIn(evidence);
        return hits.size() == 1 ? hits.iterator().next() : null;
    }

    public static boolean ambiguousLabel(String evidence) {
        return labelsIn(evidence).size() > 1;
    }

    private static Set<Label> labelsIn(String evidence) {
        Set<Label> hits = new LinkedHashSet<>();
        if (evidence == null || evidence.isBlank()) {
            return hits;
        }
        for (Map.Entry<String, Label> entry : REASONS) {
            if (evidence.contains(entry.getKey())) {
                hits.add(entry.getValue());
            }
        }
        return hits;
    }

    private static Label labelOf(String reason) {
        for (Map.Entry<String, Label> entry : REASONS) {
            if (entry.getKey().equals(reason)) {
                return entry.getValue();
            }
        }
        return Label.UNCERTAIN;
    }
}
