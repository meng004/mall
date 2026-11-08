package com.macro.mall.portal.ai.reasons;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Classified;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Item;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Label;
import com.macro.mall.portal.ai.reasons.ReturnReasonBatchService.Result;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** 教师显式提交人工结果；不调用模型或写入业务数据。 */
public final class ManualReasonReview {
    private ManualReasonReview() {}

    public static Result review(List<Item> input, List<Classified> decisions) {
        if (input == null || input.isEmpty() || ExistingReturnReasonBatchService.idsIncomplete(input)
                || decisions == null || decisions.size() != input.size()) throw new IllegalArgumentException("批次或人工结果不完整");
        Map<String, Item> items = new HashMap<>();
        for (Item item : input) items.put(item.anonymousId(), item);
        var seen = new HashSet<String>();
        for (Classified row : decisions) {
            if (row == null || row.anonymousId() == null || !seen.add(row.anonymousId()) || !items.containsKey(row.anonymousId()))
                throw new IllegalArgumentException("人工结果编号不匹配");
            String text = items.get(row.anonymousId()).description();
            if (row.failed()) {
                if (row.label() != null || row.evidence() != null) throw new IllegalArgumentException("未完成项不得填写标签或依据");
            } else if (row.label() == null || (ExistingReturnReasonBatchService.unspecifiedOther(text) && row.label() != Label.UNCERTAIN)
                    || (row.label() != Label.UNCERTAIN && (row.evidence() == null || row.evidence().isBlank()))
                    || (row.evidence() != null && (row.evidence().isBlank() || text == null || !text.contains(row.evidence())))) {
                throw new IllegalArgumentException("人工标签或描述依据无效");
            }
        }
        return ExistingReturnReasonBatchService.aggregate(null, decisions, input.size());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("需要输入批次和人工结果两个 JSON 文件路径");
        var json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        List<Item> input = json.readValue(Path.of(args[0]).toFile(), new TypeReference<List<Item>>() {});
        List<Classified> decisions = json.readValue(Path.of(args[1]).toFile(), new TypeReference<List<Classified>>() {});
        System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(review(input, decisions)));
    }
}
