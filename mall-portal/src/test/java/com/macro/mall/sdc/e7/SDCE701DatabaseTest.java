package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.service.OmsPortalOrderService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
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

/** 真实教学库：E7-01 订单 facts 装入后，门户订单查询按会员、状态和创建时间左闭右开过滤，分页前完成。 */
class SDCE701DatabaseTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant LAST_WEEK_FROM = Instant.parse("2026-02-23T00:00:00+08:00");
    private static final Instant LAST_WEEK_UNTIL = Instant.parse("2026-03-02T00:00:00+08:00");
    private static final Instant TODAY_UNTIL = Instant.parse("2026-03-03T00:00:00+08:00");

    @Test
    void teachingOrderFactsMatchPortalQueries() throws Exception {
        EvaluationSupport.TeachingGate gate = EvaluationSupport.connectSdc();
        Assumptions.assumeTrue(gate.reachable(), gate.note());
        JsonNode orderFacts = JSON.readTree(Files.readString(facts("E7-01")));
        try (var context = EvaluationSupport.openSdc(gate.config())) {
            DataSource dataSource = context.getBean(DataSource.class);
            Fixture fixture = new Fixture();
            boolean committed = false;
            try {
                fixture.insert(dataSource, orderFacts);
                committed = true;
                EvaluationSupport.bindMember(orderFacts.path("memberScope").path("currentMemberId").asLong());
                OmsPortalOrderService orders = context.getBean(OmsPortalOrderService.class);
                assertOrders(orders, orderFacts, fixture);
                spoilAndReject(dataSource, orders, orderFacts, fixture);
            } finally {
                EvaluationSupport.clearMember();
                PageHelper.clearPage();
                if (committed) {
                    fixture.cleanup(dataSource);
                }
            }
        }
    }

    private static void assertOrders(OmsPortalOrderService orders, JsonNode facts, Fixture fixture) {
        long current = facts.path("memberScope").path("currentMemberId").asLong();
        long other = facts.path("otherMemberId").asLong(0);
        if (facts.path("memberScope").has("otherMemberId")) {
            other = facts.path("memberScope").path("otherMemberId").asLong();
        }
        List<OrderRow> mine = fixture.orders.stream().filter(row -> row.memberId == current && !row.deleted).toList();
        assertTrue(mine.size() >= 21, "本人未删除订单 " + mine.size());
        Set<Long> all = pageIds(orders, null, null, null, mine.size());
        assertEquals(ids(mine), all);
        assertTrue(all.contains(fixture.orderId("2026-02-23T00:00:00+08:00")));
        assertFalse(all.contains(fixture.deletedOrder()));
        assertFalse(all.contains(fixture.otherOrder()));

        Set<Long> waiting = pageIds(orders, fixture.waitingShipment, null, null,
                (int) fixture.orders.stream().filter(row -> row.memberId == current && !row.deleted
                        && fixture.waitingShipment.equals(row.status)).count());
        assertFalse(waiting.contains(fixture.unmappedOrder()));
        assertEquals(waiting.size(), (int) mine.stream().filter(row -> fixture.waitingShipment.equals(row.status)).count());

        List<OrderRow> lastWeekRows = expected(fixture, current, fixture.waitingShipment, LAST_WEEK_FROM, LAST_WEEK_UNTIL);
        Set<Long> lastWeek = pageIds(orders, fixture.waitingShipment, LAST_WEEK_FROM, LAST_WEEK_UNTIL, lastWeekRows.size());
        assertEquals(ids(lastWeekRows), lastWeek);
        assertTrue(lastWeek.contains(fixture.orderId("2026-02-23T00:00:00+08:00")));
        assertFalse(lastWeek.contains(fixture.orderId("2026-03-02T00:00:00+08:00")));
        assertFalse(lastWeek.contains(fixture.deletedOrder()));
        assertFalse(lastWeek.contains(fixture.otherOrder()));

        List<OrderRow> todayRows = expected(fixture, current, fixture.waitingShipment, LAST_WEEK_UNTIL, TODAY_UNTIL);
        Set<Long> today = pageIds(orders, fixture.waitingShipment, LAST_WEEK_UNTIL, TODAY_UNTIL, todayRows.size());
        assertEquals(ids(todayRows), today);
        assertTrue(today.contains(fixture.orderId("2026-03-02T00:00:00+08:00")));
        assertFalse(today.contains(fixture.orderId("2026-03-01T20:00:00+08:00")));

        CommonPage<OmsOrderDetail> tight = orders.list(fixture.waitingShipment,
                java.util.Date.from(LAST_WEEK_FROM), java.util.Date.from(LAST_WEEK_FROM.plusMillis(1)), 1, 5);
        assertEquals(1L, tight.getTotal());
        assertEquals(fixture.orderId("2026-02-23T00:00:00+08:00"), tight.getList().get(0).getId());

        OrderRow shipped = fixture.orders.stream().filter(row -> "T-NO-001".equals(row.code)).findFirst().orElseThrow();
        OmsOrderDetail found = find(orders, shipped.id);
        assertEquals(shipped.company, found.getDeliveryCompany());
        assertEquals(shipped.code, found.getDeliverySn());
        assertEquals(shipped.created.toEpochMilli(), found.getCreateTime().getTime());
        OrderRow unpaid = fixture.orders.stream().filter(row -> row.id == fixture.unmappedOrder()).findFirst().orElseThrow();
        assertNull(find(orders, unpaid.id).getStatus());

        EvaluationSupport.bindMember(other);
        try {
            Set<Long> theirs = pageIds(orders, null, null, null, 1);
            assertEquals(Set.of(fixture.otherOrder()), theirs);
            assertFalse(theirs.contains(mine.get(0).id));
        } finally {
            EvaluationSupport.bindMember(current);
        }
        assertTrue(CandidateE701Evaluation.teachingOrdersReady(orders, fixture.orderFacts));
    }

    private static void spoilAndReject(DataSource dataSource, OmsPortalOrderService orders, JsonNode orderFacts,
                                       Fixture fixture) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement status = conn.prepareStatement("UPDATE oms_order SET status=4 WHERE id=?")) {
            status.setLong(1, fixture.orderId("2026-02-24T10:00:00+08:00"));
            assertEquals(1, status.executeUpdate());
            assertFalse(CandidateE701Evaluation.teachingOrdersReady(orders, fixture.orderFacts));
        }
        assertFalse(orderFacts.path("orders").isEmpty());
    }

    private static Set<Long> pageIds(OmsPortalOrderService orders, Integer status, Instant from, Instant until, int expected) {
        Set<Long> seen = new LinkedHashSet<>();
        Long total = null;
        for (int pageNum = 1; pageNum <= 10; pageNum++) {
            CommonPage<OmsOrderDetail> page = orders.list(status, from == null ? null : java.util.Date.from(from),
                    until == null ? null : java.util.Date.from(until), pageNum, 5);
            if (total == null) {
                total = page.getTotal();
            } else {
                assertEquals(total, page.getTotal());
            }
            List<OmsOrderDetail> list = page.getList() == null ? List.of() : page.getList();
            assertTrue(list.size() <= 5);
            for (OmsOrderDetail row : list) {
                assertTrue(seen.add(row.getId()), "重复订单 " + row.getId());
                assertEquals(0, row.getDeleteStatus());
            }
            if (list.size() < 5) {
                break;
            }
        }
        assertEquals(expected, total);
        assertEquals(expected, seen.size());
        return seen;
    }

    private static OmsOrderDetail find(OmsPortalOrderService orders, long id) {
        for (int pageNum = 1; pageNum <= 10; pageNum++) {
            CommonPage<OmsOrderDetail> page = orders.list(null, null, null, pageNum, 5);
            if (page.getList() == null) {
                break;
            }
            for (OmsOrderDetail row : page.getList()) {
                if (row.getId() == id) {
                    return row;
                }
            }
            if (page.getList().size() < 5) {
                break;
            }
        }
        throw new AssertionError("未返回订单 " + id);
    }

    private static List<OrderRow> expected(Fixture fixture, long member, Integer status, Instant from, Instant until) {
        return fixture.orders.stream().filter(row -> row.memberId == member && !row.deleted
                && (status == null || status.equals(row.status))
                && !row.created.isBefore(from) && row.created.isBefore(until)).toList();
    }

    private static Set<Long> ids(List<OrderRow> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        rows.forEach(row -> ids.add(row.id));
        return ids;
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
        private final List<OrderRow> orders = new ArrayList<>();
        private JsonNode orderFacts;
        private Integer waitingShipment;

        private void insert(DataSource dataSource, JsonNode orderSource) throws Exception {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    insertOrders(conn, orderSource);
                    conn.commit();
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
            System.out.println("inserted " + owned);
        }

        private void insertOrders(Connection conn, JsonNode source) throws Exception {
            long current = source.path("memberScope").path("currentMemberId").asLong();
            waitingShipment = statusCode(source, "待发货");
            if (waitingShipment == null) {
                throw new IllegalStateException("facts 没有待发货状态码");
            }
            ObjectNode copy = source.deepCopy();
            var copied = copy.withArray("orders");
            int index = 0;
            for (JsonNode order : source.path("orders")) {
                long id = claim(conn, "oms_order", order.path("id").asLong());
                Integer status = statusCode(source, order.path("statusName").asText(null));
                Instant created = Instant.parse(order.path("createdAt").asText());
                boolean deleted = order.path("deleted").asBoolean();
                if (!order.has("deleted")) {
                    throw new IllegalStateException("订单缺少 deleted");
                }
                OrderRow row = new OrderRow(order.path("createdAt").asText(), id, order.path("memberId").asLong(), status,
                        created, deleted, text(order, "logisticsCompany"), text(order, "logisticsCode"));
                orders.add(row);
                insertOrder(conn, row);
                ((ObjectNode) copied.get(index)).put("id", id);
                index++;
            }
            int fillers = 21 - (int) orders.stream().filter(row -> row.memberId == current && !row.deleted).count();
            for (int i = 0; i < fillers; i++) {
                long preferred = 71011L + i;
                OrderRow row = new OrderRow(null, claim(conn, "oms_order", preferred), current, waitingShipment,
                        Instant.parse("2026-02-24T11:00:00+08:00").plusSeconds(60L * i), false, null, null);
                orders.add(row);
                insertOrder(conn, row);
            }
            orderFacts = copy;
        }

        private void insertOrder(Connection conn, OrderRow row) throws Exception {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO oms_order (id, member_id, order_sn, create_time, status, delete_status,
                    delivery_company, delivery_sn, receiver_name, receiver_phone)
                    VALUES (?,?,?,?,?,?,?,?,?,?)""")) {
                ps.setLong(1, row.id);
                ps.setLong(2, row.memberId);
                ps.setString(3, "sdc-" + row.id);
                ps.setTimestamp(4, Timestamp.from(row.created));
                if (row.status == null) {
                    ps.setNull(5, Types.INTEGER);
                } else {
                    ps.setInt(5, row.status);
                }
                ps.setInt(6, row.deleted ? 1 : 0);
                ps.setString(7, row.company);
                ps.setString(8, row.code);
                ps.setString(9, "sdc-fixture");
                ps.setString(10, "0");
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

        private long orderId(String createdAt) {
            return orders.stream().filter(row -> createdAt.equals(row.createdAt)).findFirst().orElseThrow().id;
        }

        private long deletedOrder() {
            return orders.stream().filter(row -> row.deleted).findFirst().orElseThrow().id;
        }

        private long otherOrder() {
            long current = orders.get(0).memberId;
            return orders.stream().filter(row -> row.memberId != current).findFirst().orElseThrow().id;
        }

        private long unmappedOrder() {
            return orders.stream().filter(row -> row.status == null && !row.deleted).findFirst().orElseThrow().id;
        }

    }

    private static Integer statusCode(JsonNode facts, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        var fields = facts.path("orderStatus").path("codes").fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (name.equals(entry.getValue().asText())) {
                return Integer.valueOf(entry.getKey());
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private record OrderRow(String createdAt, long id, long memberId, Integer status, Instant created, boolean deleted,
                            String company, String code) {}
}
