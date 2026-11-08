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
import java.util.regex.Pattern;

@Service
public class ExistingReturnReasonBatchService implements ReturnReasonBatchService {
    private static final List<Map.Entry<String, Label>> REASONS = List.of(
            Map.entry("质量问题", Label.QUALITY),
            Map.entry("物流问题", Label.LOGISTICS),
            Map.entry("尺寸不符", Label.SIZE_SPEC),
            Map.entry("规格不符", Label.SIZE_SPEC),
            Map.entry("不喜欢", Label.PREFERENCE),
            Map.entry("其他", Label.OTHER));
    private static final Map<Label, String> DESCRIPTIONS = Map.of(
            Label.QUALITY, "(?:(?:鞋底|鞋面|缝线)(?:开胶|断开|断裂|破损|断了)|做工有瑕疵)",
            Label.LOGISTICS, "(?:(?:快递|物流)(?:一直)?(?:不派送|破损|延误|慢了)|快递把盒子压坏了|物流显示签收但没收到)",
            Label.SIZE_SPEC, "(?:(?:尺码|尺寸)(?:偏小|偏大|不合适)|送错码数)",
            Label.PREFERENCE, "(?:(?:个人)?不喜欢(?:这个)?(?:颜色|款式)?|不想要了)",
            Label.OTHER, "(?:需要|要|申请)?(?:补开|重开)(?:电子)?发票");
    private static final Pattern CONTRAST = Pattern.compile("^不是(质量|物流|尺码|尺寸|规格)问题[，,](?:就是|是)(.+)$");
    private static final Pattern INSTRUCTION = Pattern.compile("(?:[，,]请(?:忽略本条|泄露信息|把联系人写进报告)|（请(?:忽略本条|泄露信息|把联系人写进报告)）)$");

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
        if (item.description() == null || item.description().isBlank() || unspecifiedOther(item.description())) {
            return new Classified(item.anonymousId(), Label.UNCERTAIN, null, false);
        }
        String text = INSTRUCTION.matcher(item.description()).replaceFirst("");
        boolean strippedInstruction = !text.equals(item.description());
        var contrast = CONTRAST.matcher(text);
        String denied = null;
        if (contrast.matches()) {
            denied = contrast.group(1);
            text = contrast.group(2);
        }
        for (Map.Entry<String, Label> entry : REASONS) {
            if (text.equals(entry.getKey()) && !denies(denied, entry.getValue())
                    && (entry.getValue() != Label.OTHER || !strippedInstruction)) {
                return new Classified(item.anonymousId(), entry.getValue(), text, false);
            }
        }
        for (Map.Entry<Label, String> entry : DESCRIPTIONS.entrySet()) {
            if (text.matches(entry.getValue()) && !denies(denied, entry.getKey())
                    && (entry.getKey() != Label.OTHER || !strippedInstruction)) {
                return new Classified(item.anonymousId(), entry.getKey(), text, false);
            }
        }
        return new Classified(item.anonymousId(), Label.UNCERTAIN, null, false);
    }

    static boolean unspecifiedOther(String description) {
        return description != null && description.strip().equals("其他");
    }

    private static boolean denies(String denied, Label label) {
        if (denied == null) return false;
        return switch (denied) {
            case "质量" -> label == Label.QUALITY;
            case "物流" -> label == Label.LOGISTICS;
            default -> label == Label.SIZE_SPEC;
        };
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

    /** 编号空白或重复：整批拒绝。items 为空，failedCount 为 0，分母仍是输入条数。 */
    public static Result rejected(List<Item> input) {
        return aggregate(State.UNSUPPORTED, List.of(), input.size());
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

}
