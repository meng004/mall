package com.macro.mall.portal.ai.comparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.mapper.PmsProductAttributeValueMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductAttribute;
import com.macro.mall.model.PmsProductAttributeExample;
import com.macro.mall.model.PmsProductAttributeValue;
import com.macro.mall.model.PmsProductAttributeValueExample;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.PmsSkuStockExample;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExistingProductComparisonService implements ProductComparisonService {
    public static final String UNIT_RULE = "本题存储容量仅按1GB=1024MB折算，不是所有领域的单位约定";
    private static final Pattern UNIT = Pattern.compile("^(\\d+(?:\\.\\d+)?)(MB|GB)$", Pattern.CASE_INSENSITIVE);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final PmsProductMapper products;
    private final PmsProductAttributeMapper attributes;
    private final PmsProductAttributeValueMapper values;
    private final PmsSkuStockMapper skus;

    public ExistingProductComparisonService(PmsProductMapper products, PmsProductAttributeMapper attributes,
                                           PmsProductAttributeValueMapper values, PmsSkuStockMapper skus) {
        this.products = products;
        this.attributes = attributes;
        this.values = values;
        this.skus = skus;
    }

    @Override
    public Result compare(Request input) {
        if (input.text() != null && !input.text().isBlank()) {
            return new Result(State.UNSUPPORTED, List.of(), List.of());
        }
        if (input.attributeIds() == null || input.attributeIds().isEmpty()) {
            return new Result(State.NEEDS_INPUT, List.of(), List.of("请选择最多三个属性"));
        }
        if (input.attributeIds().size() > 3) {
            return new Result(State.NEEDS_INPUT, List.of(), List.of("最多比较三个属性"));
        }
        Side left = load(input.leftId());
        Side right = load(input.rightId());
        if (left == null || right == null) {
            return new Result(State.UNSUPPORTED, List.of(), List.of());
        }
        List<Row> rows = new ArrayList<>();
        List<String> questions = new ArrayList<>();
        boolean unknown = false;
        for (Long attributeId : input.attributeIds()) {
            PmsProductAttribute attribute = left.attributes.getOrDefault(attributeId, right.attributes.get(attributeId));
            if (attribute == null) {
                return new Result(State.NEEDS_INPUT, List.of(), List.of("属性不在当前可见商品上"));
            }
            if (needsSku(left, attribute, input.leftSkuId()) || needsSku(right, attribute, input.rightSkuId())) {
                return new Result(State.NEEDS_INPUT, List.of(), List.of("请先选择规格"));
            }
            Value leftValue = read(left, attribute, input.leftSkuId());
            Value rightValue = read(right, attribute, input.rightSkuId());
            String relation = relation(leftValue.raw(), rightValue.raw());
            if ("unknown".equals(relation)) {
                unknown = true;
                questions.add("属性未记录");
            }
            rows.add(new Row(attributeId, leftValue, rightValue, relation));
        }
        return new Result(unknown ? State.PARTIAL : State.OK, rows, questions);
    }

    public boolean visible(long id) {
        return findVisible(id) != null;
    }

    public String catalogText(long leftId, long rightId) {
        StringBuilder builder = new StringBuilder();
        for (Side side : List.of(loadAttributes(leftId), loadAttributes(rightId))) {
            if (side == null) {
                continue;
            }
            for (PmsProductAttribute attribute : side.attributes.values()) {
                builder.append(attribute.getId()).append('=').append(attribute.getName()).append(';');
            }
        }
        return builder.toString();
    }

    public Set<Long> candidateIds(long leftId, long rightId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Side side : List.of(loadAttributes(leftId), loadAttributes(rightId))) {
            if (side != null) {
                ids.addAll(side.attributes.keySet());
            }
        }
        return ids;
    }

    private Side load(long id) {
        Side side = loadAttributes(id);
        if (side == null) {
            return null;
        }
        PmsProductAttributeValueExample valueExample = new PmsProductAttributeValueExample();
        valueExample.createCriteria().andProductIdEqualTo(id);
        for (PmsProductAttributeValue value : values.selectByExample(valueExample)) {
            side.values.put(value.getProductAttributeId(), value.getValue());
        }
        PmsSkuStockExample skuExample = new PmsSkuStockExample();
        skuExample.createCriteria().andProductIdEqualTo(id);
        side.skus.addAll(skus.selectByExample(skuExample));
        return side;
    }

    private Side loadAttributes(long id) {
        PmsProduct product = findVisible(id);
        if (product == null) {
            return null;
        }
        Side side = new Side(product);
        PmsProductAttributeExample example = new PmsProductAttributeExample();
        example.createCriteria().andProductAttributeCategoryIdEqualTo(product.getProductAttributeCategoryId());
        for (PmsProductAttribute attribute : attributes.selectByExample(example)) {
            side.attributes.put(attribute.getId(), attribute);
        }
        return side;
    }

    private PmsProduct findVisible(long id) {
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andIdEqualTo(id).andDeleteStatusEqualTo(0).andPublishStatusEqualTo(1);
        List<PmsProduct> found = products.selectByExample(example);
        return found == null || found.isEmpty() ? null : found.get(0);
    }

    private static boolean needsSku(Side side, PmsProductAttribute attribute, Long skuId) {
        if (attribute.getType() == null || attribute.getType() != 0 || !side.attributes.containsKey(attribute.getId())) {
            return false;
        }
        return side.skus.size() > 1 && skuId == null;
    }

    private Value read(Side side, PmsProductAttribute attribute, Long skuId) {
        if (attribute.getType() != null && attribute.getType() == 0 && !side.skus.isEmpty()) {
            PmsSkuStock sku = side.skus.size() == 1 ? side.skus.get(0) : side.skus.stream()
                    .filter(item -> item.getId().equals(skuId)).findFirst().orElse(null);
            String raw = sku == null ? null : skuValue(sku.getSpData(), attribute.getName());
            return new Value(side.product.getId(), sku == null ? null : sku.getId(), attribute.getId(), raw, normalize(raw),
                    raw == null ? null : "skuSpData");
        }
        String raw = blank(side.values.get(attribute.getId()));
        return new Value(side.product.getId(), null, attribute.getId(), raw, normalize(raw),
                raw == null ? null : "productAttributeValue");
    }

    private static String skuValue(String spData, String name) {
        if (spData == null || spData.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(spData);
            if (!node.isArray()) {
                return null;
            }
            for (JsonNode item : node) {
                if (name.equals(item.path("key").asText())) {
                    return blank(item.path("value").asText(null));
                }
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }

    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = UNIT.matcher(raw.replace(" ", ""));
        if (!matcher.matches()) {
            return raw.trim();
        }
        BigDecimal number = new BigDecimal(matcher.group(1));
        BigDecimal megabytes = matcher.group(2).equalsIgnoreCase("GB") ? number.multiply(new BigDecimal("1024")) : number;
        return megabytes.stripTrailingZeros().toPlainString() + "MB";
    }

    private static String relation(String leftRaw, String rightRaw) {
        if (leftRaw == null || rightRaw == null) {
            return "unknown";
        }
        boolean leftUnit = UNIT.matcher(leftRaw.replace(" ", "")).matches();
        boolean rightUnit = UNIT.matcher(rightRaw.replace(" ", "")).matches();
        if (leftUnit && rightUnit) {
            return normalize(leftRaw).equals(normalize(rightRaw)) ? "equivalent" : "different";
        }
        return "uncompared";
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static final class Side {
        private final PmsProduct product;
        private final Map<Long, PmsProductAttribute> attributes = new LinkedHashMap<>();
        private final Map<Long, String> values = new LinkedHashMap<>();
        private final List<PmsSkuStock> skus = new ArrayList<>();

        private Side(PmsProduct product) {
            this.product = product;
        }
    }
}
