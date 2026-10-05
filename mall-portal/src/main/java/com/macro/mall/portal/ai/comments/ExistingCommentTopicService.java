package com.macro.mall.portal.ai.comments;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExistingCommentTopicService implements CommentTopicService {
    private static final Map<Topic, List<String>> PHRASES = Map.of(
            Topic.QUALITY, List.of("质量没问题", "质量差", "质量", "开胶", "做工"),
            Topic.LOGISTICS, List.of("快递太慢", "快递", "物流", "发货"),
            Topic.AFTER_SALES, List.of("售后不回复", "售后", "客服", "退货", "换货"),
            Topic.OTHER, List.of("包装", "价格", "赠品"));

    @Override
    public Result classify(String text) {
        if (text == null || text.isBlank()) {
            return new Result(State.NEEDS_INPUT, Map.of());
        }
        Map<Topic, String> found = new LinkedHashMap<>();
        for (Topic topic : Topic.values()) {
            for (String phrase : PHRASES.get(topic)) {
                if (text.contains(phrase)) {
                    found.put(topic, phrase);
                    break;
                }
            }
        }
        if (found.isEmpty()) {
            return new Result(State.NEEDS_INPUT, Map.of());
        }
        return new Result(State.OK, Map.copyOf(found));
    }

    public Result accept(String text, Map<Topic, String> evidence) {
        String body = text == null ? "" : text;
        if (evidence == null || evidence.isEmpty()) {
            return new Result(State.NEEDS_INPUT, Map.of());
        }
        Map<Topic, String> kept = new LinkedHashMap<>();
        for (var entry : evidence.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank() || !body.contains(entry.getValue())) {
                return new Result(State.UNSUPPORTED, Map.of());
            }
            kept.put(entry.getKey(), entry.getValue());
        }
        return new Result(State.OK, Map.copyOf(kept));
    }

    public static boolean supports(Topic topic, String evidence) {
        if (topic == null || evidence == null || evidence.isBlank()) {
            return false;
        }
        for (String phrase : PHRASES.getOrDefault(topic, List.of())) {
            if (evidence.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    public static boolean ruleKnown(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return false;
        }
        for (Topic topic : Topic.values()) {
            if (supports(topic, evidence)) {
                return true;
            }
        }
        return false;
    }
}
