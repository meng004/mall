package com.macro.mall.portal.ai.attributes;

import com.macro.mall.mapper.PmsProductMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExistingAttributeExtractionService implements AttributeExtractionService {
    private static final Pattern CAPACITY = Pattern.compile("([0-9０-９]+)([GgＧ])([BbＢ])?");
    private static final Pattern TERABYTE = Pattern.compile("[0-9０-９]+\\s*(?:[TtＴ][BbＢ]|TiB)");
    private static final Map<String, String> COLOR_ALIASES = Map.of("红色", "红", "蓝色", "蓝");
    private final PmsProductMapper writer;

    public ExistingAttributeExtractionService(PmsProductMapper writer) {
        if (writer == null) {
            throw new IllegalArgumentException("writer");
        }
        this.writer = writer;
    }

    @Override
    public Result extract(Request input) {
        if (writer == null) {
            throw new IllegalStateException("writer");
        }
        if (input.attributes() == null || input.attributes().isEmpty() || input.text() == null || input.text().isBlank()) {
            return new Result(State.NEEDS_INPUT, List.of(), Set.of());
        }
        if (TERABYTE.matcher(input.text()).find()) {
            return new Result(State.NEEDS_INPUT, List.of(), ids(input.attributes()));
        }
        List<Value> values = new ArrayList<>();
        Set<Long> unknown = new LinkedHashSet<>();
        for (AttributeSpec spec : input.attributes()) {
            Value value = literal(spec, input.text());
            if (value == null) {
                unknown.add(spec.id());
            } else {
                values.add(value);
            }
        }
        return finish(values, unknown);
    }

    public Result check(Request input, List<Value> proposed, Set<Long> proposedUnknown) {
        if (input.text() != null && TERABYTE.matcher(input.text()).find()) {
            return new Result(State.NEEDS_INPUT, List.of(), ids(input.attributes()));
        }
        Map<Long, AttributeSpec> specs = new LinkedHashMap<>();
        for (AttributeSpec spec : input.attributes()) {
            specs.put(spec.id(), spec);
        }
        List<Value> values = new ArrayList<>();
        Set<Long> unknown = new LinkedHashSet<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (Value value : proposed) {
            AttributeSpec spec = specs.get(value.attributeId());
            if (spec == null || value.evidence() == null || value.evidence().isBlank()
                    || input.text() == null || !input.text().contains(value.evidence())) {
                throw new IllegalArgumentException();
            }
            if (!seen.add(spec.id())) {
                throw new IllegalArgumentException();
            }
            String proposedCanonical = canonicalize(spec, value.value());
            String supported = supportedCanonical(spec, value.evidence());
            if (supported != null) {
                if (!supported.equals(proposedCanonical)) {
                    throw new IllegalArgumentException();
                }
                values.add(new Value(spec.id(), proposedCanonical, value.evidence()));
            } else if (proposedCanonical == null) {
                unknown.add(spec.id());
            } else {
                throw new IllegalArgumentException();
            }
        }
        for (Long id : proposedUnknown) {
            if (!specs.containsKey(id) || !seen.add(id)) {
                throw new IllegalArgumentException();
            }
            unknown.add(id);
        }
        for (AttributeSpec spec : input.attributes()) {
            if (!seen.contains(spec.id())) {
                unknown.add(spec.id());
            }
        }
        return finish(values, unknown);
    }

    /** 证据片段按既有别名和单位规则能支持的规范化值；没有对应规则时返回 null。 */
    public static String supportedCanonical(AttributeSpec spec, String evidence) {
        if (spec == null || evidence == null || evidence.isBlank()) {
            return null;
        }
        Value value = literal(spec, evidence);
        return value == null ? null : value.value();
    }

    private static Result finish(List<Value> values, Set<Long> unknown) {
        List<Value> ordered = values.stream().sorted(Comparator.comparingLong(Value::attributeId)).toList();
        if (ordered.isEmpty()) {
            return new Result(State.NEEDS_INPUT, List.of(), Set.copyOf(unknown));
        }
        if (!unknown.isEmpty()) {
            return new Result(State.PARTIAL, ordered, Set.copyOf(unknown));
        }
        return new Result(State.OK, ordered, Set.of());
    }

    private static Value literal(AttributeSpec spec, String text) {
        if ("GB".equals(spec.unit())) {
            Matcher matcher = CAPACITY.matcher(text);
            if (!matcher.find()) {
                return null;
            }
            String canonical = foldDigits(matcher.group(1)) + "GB";
            if (!spec.allowedValues().contains(canonical)) {
                return null;
            }
            return new Value(spec.id(), canonical, matcher.group());
        }
        for (var alias : COLOR_ALIASES.entrySet()) {
            if (spec.allowedValues().contains(alias.getValue()) && text.contains(alias.getKey())) {
                return new Value(spec.id(), alias.getValue(), alias.getKey());
            }
        }
        for (String allowed : spec.allowedValues()) {
            if (text.contains(allowed)) {
                return new Value(spec.id(), allowed, allowed);
            }
        }
        return null;
    }

    static String canonicalize(AttributeSpec spec, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if ("GB".equals(spec.unit())) {
            String folded = foldDigits(raw).replace(" ", "");
            Matcher matcher = Pattern.compile("^(\\d+)[Gg][Bb]?$").matcher(folded);
            if (!matcher.matches()) {
                return null;
            }
            String value = matcher.group(1) + "GB";
            return spec.allowedValues().contains(value) ? value : null;
        }
        if (spec.allowedValues().contains(raw)) {
            return raw;
        }
        String alias = COLOR_ALIASES.get(raw);
        if (alias != null && spec.allowedValues().contains(alias)) {
            return alias;
        }
        return null;
    }

    private static String foldDigits(String raw) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '０' && c <= '９') {
                builder.append((char) ('0' + (c - '０')));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static Set<Long> ids(List<AttributeSpec> attributes) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AttributeSpec spec : attributes) {
            ids.add(spec.id());
        }
        return ids;
    }
}
