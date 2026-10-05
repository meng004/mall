package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.pagehelper.PageHelper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.mapper.PmsProductAttributeValueMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.portal.ai.comparison.ExistingProductComparisonService;
import com.macro.mall.portal.ai.comparison.ProductComparisonService.Request;
import com.macro.mall.portal.ai.comparison.ProductComparisonService.Result;
import com.macro.mall.portal.ai.comparison.ProductComparisonService.State;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实教学库：E7-02 商品 facts 装入后，规格对照只读可见商品，按字段和单位规则比较。 */
class SDCE702DatabaseTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void teachingProductFactsMatchPortalQueries() throws Exception {
        EvaluationSupport.TeachingGate gate = EvaluationSupport.connectSdc();
        Assumptions.assumeTrue(gate.reachable(), gate.note());
        JsonNode productFacts = JSON.readTree(Files.readString(facts("E7-02")));
        try (var context = EvaluationSupport.openSdc(gate.config())) {
            DataSource dataSource = context.getBean(DataSource.class);
            Fixture fixture = new Fixture();
            boolean committed = false;
            try {
                fixture.insert(dataSource, productFacts);
                committed = true;
                ExistingProductComparisonService products = new ExistingProductComparisonService(
                        context.getBean(PmsProductMapper.class), context.getBean(PmsProductAttributeMapper.class),
                        context.getBean(PmsProductAttributeValueMapper.class), context.getBean(PmsSkuStockMapper.class));
                assertProducts(products, fixture);
                spoilAndReject(dataSource, products, fixture);
            } finally {
                PageHelper.clearPage();
                if (committed) {
                    fixture.cleanup(dataSource);
                }
            }
        }
    }

    private static void assertProducts(ExistingProductComparisonService service, Fixture fixture) {
        PageHelper.clearPage();
        long left = fixture.product("801");
        long right = fixture.product("802");
        long multi = fixture.product("803");
        long hidden = fixture.product("804");
        long renamed = fixture.product("805");
        long capacity = fixture.attribute(7);
        long memory = fixture.attribute(8);
        long color = fixture.attribute(9);
        long screen = fixture.attribute(10);
        long weight = fixture.attribute(11);
        assertTrue(service.visible(left));
        assertTrue(service.visible(right));
        assertTrue(service.visible(multi));
        assertTrue(service.visible(renamed));
        assertFalse(service.visible(hidden));
        assertFalse(service.visible(fixture.deletedProduct));
        assertEquals(Set.of(capacity, memory, color, screen, weight), service.candidateIds(left, right));
        if (capacity != 7L) {
            assertFalse(service.candidateIds(left, left).contains(7L));
        }
        Result outside = service.compare(new Request(left, right, null, null, null, List.of(7L)));
        if (capacity != 7L) {
            assertEquals(State.NEEDS_INPUT, outside.state());
            assertTrue(outside.rows().isEmpty());
        }

        Result stored = service.compare(new Request(left, right, null, null, null, List.of(capacity)));
        assertEquals("1024MB", stored.rows().get(0).left().raw());
        assertEquals("1GB", stored.rows().get(0).right().raw());
        assertEquals("1024MB", stored.rows().get(0).left().normalized());
        assertEquals("1024MB", stored.rows().get(0).right().normalized());
        assertEquals("equivalent", stored.rows().get(0).relation());

        Result ram = service.compare(new Request(left, right, null, null, null, List.of(memory)));
        assertEquals("8GB", ram.rows().get(0).left().raw());
        assertEquals("6GB", ram.rows().get(0).right().raw());
        assertEquals("8192MB", ram.rows().get(0).left().normalized());
        assertEquals("6144MB", ram.rows().get(0).right().normalized());
        assertEquals("different", ram.rows().get(0).relation());

        Result paint = service.compare(new Request(left, right, null, null, null, List.of(color)));
        assertEquals("红", paint.rows().get(0).left().raw());
        assertEquals("蓝", paint.rows().get(0).right().raw());
        assertEquals("uncompared", paint.rows().get(0).relation());

        Result mass = service.compare(new Request(left, right, null, null, null, List.of(weight)));
        assertEquals("500g", mass.rows().get(0).left().raw());
        assertEquals("0.5kg", mass.rows().get(0).right().raw());
        assertEquals("uncompared", mass.rows().get(0).relation());

        Result absent = service.compare(new Request(left, right, null, null, null, List.of(screen)));
        assertNull(absent.rows().get(0).left().raw());
        assertEquals("unknown", absent.rows().get(0).relation());

        Result needSku = service.compare(new Request(multi, right, null, null, null, List.of(capacity)));
        assertEquals(State.NEEDS_INPUT, needSku.state());
        Result sku = service.compare(new Request(multi, multi, fixture.sku("80301"), fixture.sku("80302"), null, List.of(capacity)));
        assertEquals("256GB", sku.rows().get(0).left().raw());
        assertEquals("128GB", sku.rows().get(0).right().raw());
        assertEquals("262144MB", sku.rows().get(0).left().normalized());
        assertEquals("131072MB", sku.rows().get(0).right().normalized());
        assertEquals("different", sku.rows().get(0).relation());

        Result buried = service.compare(new Request(hidden, left, null, null, null, List.of(capacity)));
        assertEquals(State.UNSUPPORTED, buried.state());
        assertTrue(buried.rows().isEmpty());
        Result removed = service.compare(new Request(fixture.deletedProduct, left, null, null, null, List.of(capacity)));
        assertEquals(State.UNSUPPORTED, removed.state());
        assertTrue(removed.rows().isEmpty());
        assertTrue(CandidateE702Evaluation.teachingProductsReady(service, fixture.productFacts));
    }

    private static void spoilAndReject(DataSource dataSource, ExistingProductComparisonService products,
                                       Fixture fixture) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement spec = conn.prepareStatement("UPDATE pms_sku_stock SET sp_data=? WHERE id=?")) {
            spec.setString(1, "[{\"key\":\"容量\",\"value\":\"1MB\"}]");
            spec.setLong(2, fixture.sku("80101"));
            assertEquals(1, spec.executeUpdate());
            assertTrue(products.visible(fixture.product("801")));
            assertFalse(CandidateE702Evaluation.teachingProductsReady(products, fixture.productFacts));
        }
    }

    private static Path facts(String task) {
        Path start = Path.of("").toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            for (String relative : List.of("SDC/E7/evaluation/" + task + "/facts.json", "evaluation/" + task + "/facts.json")) {
                Path candidate = path.resolve(relative);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("找不到 " + task + " facts.json");
    }

    private static final class Fixture {
        private final Map<String, List<Long>> owned = new LinkedHashMap<>();
        private final Set<String> claimed = new LinkedHashSet<>();
        private final Map<Long, Long> attributes = new LinkedHashMap<>();
        private final Map<String, Long> products = new LinkedHashMap<>();
        private final Map<String, Long> skus = new LinkedHashMap<>();
        private JsonNode productFacts;
        private long deletedProduct;

        private void insert(DataSource dataSource, JsonNode productSource) throws Exception {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    insertProducts(conn, productSource);
                    conn.commit();
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
            System.out.println("inserted " + owned);
            System.out.println("attribute-map " + attributes);
        }

        private void insertProducts(Connection conn, JsonNode source) throws Exception {
            Set<Long> skuAttributes = new LinkedHashSet<>();
            source.path("snapshots").fields().forEachRemaining(entry -> entry.getValue().path("skuValues")
                    .fieldNames().forEachRemaining(sku -> entry.getValue().path("skuValues").path(sku)
                            .fieldNames().forEachRemaining(id -> skuAttributes.add(Long.parseLong(id)))));
            long category = claim(conn, "pms_product_attribute_category", 901);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO pms_product_attribute_category (id, name) VALUES (?,?)")) {
                ps.setLong(1, category);
                ps.setString(2, "sdc-e702");
                ps.executeUpdate();
            }
            for (JsonNode item : source.path("catalog")) {
                long factsId = item.path("id").asLong();
                long preferred = factsId == 7 && exists(conn, "pms_product_attribute", 7) ? 9007 : factsId;
                long id = claim(conn, "pms_product_attribute", preferred);
                boolean spec = "MB_GB".equals(item.path("comparable").asText()) || skuAttributes.contains(factsId);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO pms_product_attribute (id, product_attribute_category_id, name, type) VALUES (?,?,?,?)")) {
                    ps.setLong(1, id);
                    ps.setLong(2, category);
                    ps.setString(3, item.path("name").asText());
                    ps.setInt(4, spec ? 0 : 1);
                    ps.executeUpdate();
                }
                attributes.put(factsId, id);
            }
            ObjectNode copy = source.deepCopy();
            for (JsonNode item : copy.withArray("catalog")) {
                ((ObjectNode) item).put("id", attributes.get(item.path("id").asLong()));
            }
            var snapshots = copy.withObject("snapshots");
            source.path("snapshots").fields().forEachRemaining(entry -> {
                try {
                    insertSnapshot(conn, category, entry.getKey(), entry.getValue(), (ObjectNode) snapshots.get(entry.getKey()), skuAttributes);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            deletedProduct = claim(conn, "pms_product", 806);
            insertProduct(conn, deletedProduct, category, "sdc-deleted", "sdc-deleted-" + deletedProduct, 1, 1);
            long deletedSku = claim(conn, "pms_sku_stock", 80601);
            insertSku(conn, deletedSku, deletedProduct, "[{\"key\":\"容量\",\"value\":\"fixture-deleted\"}]");
            productFacts = copy;
        }

        private void insertSnapshot(Connection conn, long category, String factsId, JsonNode snap, ObjectNode copy,
                                    Set<Long> skuAttributes) throws Exception {
            long id = claim(conn, "pms_product", Long.parseLong(factsId));
            products.put(factsId, id);
            if (!snap.has("visible")) {
                throw new IllegalStateException("商品缺少 visible");
            }
            boolean visible = snap.path("visible").asBoolean();
            insertProduct(conn, id, category, snap.path("name").asText(), "sdc-" + id, visible ? 1 : 0, 0);
            List<String> skuIds = new ArrayList<>();
            snap.path("skuIds").forEach(node -> skuIds.add(node.asText()));
            snap.path("skuValues").fieldNames().forEachRemaining(skuIds::add);
            Set<String> uniqueSkus = new LinkedHashSet<>(skuIds);
            if (uniqueSkus.isEmpty()) {
                throw new IllegalStateException("商品缺少 sku");
            }
            for (String skuFacts : uniqueSkus) {
                long skuId = claim(conn, "pms_sku_stock", Long.parseLong(skuFacts));
                skus.put(skuFacts, skuId);
                insertSku(conn, skuId, id, spData(snap, skuFacts, skuAttributes));
            }
            var values = snap.path("values").fields();
            while (values.hasNext()) {
                var value = values.next();
                long factsAttr = Long.parseLong(value.getKey());
                if (skuAttributes.contains(factsAttr) || factsAttr == 7 || factsAttr == 8) {
                    continue;
                }
                long valueId = claim(conn, "pms_product_attribute_value", 970000 + id + factsAttr);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO pms_product_attribute_value (id, product_id, product_attribute_id, value) VALUES (?,?,?,?)")) {
                    ps.setLong(1, valueId);
                    ps.setLong(2, id);
                    ps.setLong(3, attributes.get(factsAttr));
                    ps.setString(4, value.getValue().asText());
                    ps.executeUpdate();
                }
            }
            remap(copy, "values");
            if (copy.has("skuValues")) {
                ((ObjectNode) copy.get("skuValues")).fields().forEachRemaining(sku -> remap((ObjectNode) sku.getValue(), null));
            }
        }

        private String spData(JsonNode snap, String skuFacts, Set<Long> skuAttributes) throws Exception {
            var array = JSON.createArrayNode();
            JsonNode source = snap.path("skuValues").path(skuFacts);
            if (source.isMissingNode() || source.isNull() || !source.isObject()) {
                source = snap.path("values");
            }
            var fields = source.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                long factsAttr = Long.parseLong(field.getKey());
                if (!skuAttributes.contains(factsAttr) && !isStorage(factsAttr)) {
                    continue;
                }
                var item = array.addObject();
                item.put("key", attributeName(factsAttr));
                item.put("value", field.getValue().asText());
            }
            return JSON.writeValueAsString(array);
        }

        private boolean isStorage(long factsAttr) {
            return factsAttr == 7 || factsAttr == 8;
        }

        private String attributeName(long factsAttr) {
            return switch ((int) factsAttr) {
                case 7 -> "容量";
                case 8 -> "运行内存";
                case 9 -> "颜色";
                case 10 -> "屏幕尺寸";
                case 11 -> "重量";
                default -> throw new IllegalStateException("未知属性 " + factsAttr);
            };
        }

        private void remap(ObjectNode node, String field) {
            if (node == null || field != null && !node.has(field)) {
                return;
            }
            ObjectNode values = field == null ? node : (ObjectNode) node.get(field);
            List<String> names = new ArrayList<>();
            values.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                long mapped = attributes.get(Long.parseLong(name));
                if (mapped != Long.parseLong(name)) {
                    values.set(Long.toString(mapped), values.remove(name));
                }
            }
        }

        private void insertProduct(Connection conn, long id, long category, String name, String sn, int publish, int deleted) throws Exception {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO pms_product (id, product_attribute_category_id, name, product_sn, publish_status, delete_status)
                    VALUES (?,?,?,?,?,?)""")) {
                ps.setLong(1, id);
                ps.setLong(2, category);
                ps.setString(3, name);
                ps.setString(4, sn);
                ps.setInt(5, publish);
                ps.setInt(6, deleted);
                ps.executeUpdate();
            }
        }

        private void insertSku(Connection conn, long id, long productId, String spData) throws Exception {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO pms_sku_stock (id, product_id, sku_code, sp_data) VALUES (?,?,?,?)")) {
                ps.setLong(1, id);
                ps.setLong(2, productId);
                ps.setString(3, "sdc-" + id);
                ps.setString(4, spData);
                ps.executeUpdate();
            }
        }

        private long claim(Connection conn, String table, long preferred) throws Exception {
            if (free(conn, table, preferred)) {
                return preferred;
            }
            long id = 970000;
            while (!free(conn, table, id)) {
                id++;
            }
            return id;
        }

        private boolean free(Connection conn, String table, long id) throws Exception {
            String key = table + "#" + id;
            if (claimed.contains(key) || exists(conn, table, id)) {
                return false;
            }
            claimed.add(key);
            owned.computeIfAbsent(table, name -> new ArrayList<>()).add(id);
            return true;
        }

        private boolean exists(Connection conn, String table, long id) throws Exception {
            if (!Set.of("oms_order", "pms_product", "pms_product_attribute", "pms_product_attribute_category",
                    "pms_product_attribute_value", "pms_sku_stock").contains(table)) {
                throw new IllegalArgumentException(table);
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM " + table + " WHERE id=?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }

        private void cleanup(DataSource dataSource) throws Exception {
            List<String> tables = List.of("pms_product_attribute_value", "pms_sku_stock", "pms_product",
                    "pms_product_attribute", "pms_product_attribute_category", "oms_order");
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    for (String table : tables) {
                        for (long id : owned.getOrDefault(table, List.of())) {
                            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE id=?")) {
                                ps.setLong(1, id);
                                int removed = ps.executeUpdate();
                                if (removed != 1) {
                                    throw new IllegalStateException("cleanup " + table + " " + id + " deleted " + removed);
                                }
                            }
                        }
                    }
                    conn.commit();
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                }
            }
            System.out.println("cleanup " + owned);
        }

        private long product(String factsId) {
            return products.get(factsId);
        }

        private long sku(String factsId) {
            return skus.get(factsId);
        }

        private long attribute(long factsId) {
            return attributes.get(factsId);
        }
    }
}
