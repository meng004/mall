package com.macro.mall.sdc.e2;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * E2-01：经 portal 原有入口支付、确认收货，到 127.0.0.1:13306 核对状态和库存。
 * 只插入 id ≥ 9400000 且 order_sn/sku_code 以 e201- 开头的行，结束时删除并恢复自增。
 */
class SDCE201StatePortalTest {
    private static final long FLOOR = 9_400_000L;
    private static final int QTY = 3;
    private static final long ME = member(1);

    private static Connection monitor;
    private static SqlSession session;
    private static OmsPortalOrderServiceImpl service;
    private static long ordersBefore;
    private static long itemsBefore;
    private static long stockBefore;

    @BeforeAll
    static void connectAndInsert() throws Exception {
        Db db = Db.openOrAbort();
        monitor = db.monitor;
        removeMarked(monitor);
        restoreAutoIncrement(monitor);
        assertRangeFree(monitor);
        ordersBefore = checksum(monitor, "oms_order", "id, ifnull(status,-1), ifnull(pay_type,-1), ifnull(confirm_status,-1), ifnull(delete_status,-1)");
        itemsBefore = checksum(monitor, "oms_order_item", "id, ifnull(order_id,0), ifnull(product_sku_id,0), ifnull(product_quantity,-1)");
        stockBefore = checksum(monitor, "pms_sku_stock", "id, ifnull(stock,-1), ifnull(lock_stock,-1)");
        session = db.session;
        Connection work = session.getConnection();
        work.setAutoCommit(false);
        insert(work, 1, 0, null, null);
        insert(work, 2, 1, 1, Timestamp.valueOf("2020-01-01 00:00:00"));
        insert(work, 3, 2, null, null);
        insert(work, 4, 0, null, null);
        insert(work, 5, 2, null, null);
        service = wire(session);
    }

    @AfterAll
    static void rollBackAndCheck() throws Exception {
        try {
            if (session != null) {
                session.rollback();
                session.close();
                session = null;
            }
        } finally {
            if (monitor != null) {
                removeMarked(monitor);
                restoreAutoIncrement(monitor);
                assertEquals(ordersBefore, checksum(monitor, "oms_order", "id, ifnull(status,-1), ifnull(pay_type,-1), ifnull(confirm_status,-1), ifnull(delete_status,-1)"));
                assertEquals(itemsBefore, checksum(monitor, "oms_order_item", "id, ifnull(order_id,0), ifnull(product_sku_id,0), ifnull(product_quantity,-1)"));
                assertEquals(stockBefore, checksum(monitor, "pms_sku_stock", "id, ifnull(stock,-1), ifnull(lock_stock,-1)"));
                assertEquals(0L, queryLong(monitor, "select count(*) from oms_order where order_sn like 'e201-%'"));
                assertEquals(0L, queryLong(monitor, "select count(*) from pms_sku_stock where sku_code like 'e201-%'"));
                monitor.close();
            }
        }
    }

    @Test
    void unpaidOrderPaySuccessDeductsOnce() throws Exception {
        assertStock(1, 20, 10);
        assertEquals(0, orderStatus(1));
        Integer count = service.paySuccess(order(1), 2);
        assertEquals(1, count);
        assertEquals(1, orderStatus(1));
        assertEquals(2, payType(1));
        assertStock(1, 17, 7);
    }

    @Test
    void paidOrderPaySuccessDoesNotDeductOrRewrite() throws Exception {
        assertStock(2, 20, 10);
        String paidAt = paymentTime(2);
        Integer count = service.paySuccess(order(2), 2);
        assertEquals(0, count);
        assertEquals(1, orderStatus(2));
        assertEquals(1, payType(2));
        assertEquals(paidAt, paymentTime(2));
        assertStock(2, 20, 10);
    }

    @Test
    void shippedOrderConfirmReceiveCompletes() throws Exception {
        asMember(ME);
        service.confirmReceiveOrder(order(3));
        assertEquals(3, orderStatus(3));
        assertEquals(1, confirmStatus(3));
        assertNotNull(receiveTime(3));
    }

    @Test
    void unshippedConfirmReceiveKeepsFailure() throws Exception {
        asMember(ME);
        ApiException ex = assertThrows(ApiException.class, () -> service.confirmReceiveOrder(order(4)));
        assertEquals("该订单还未发货！", ex.getMessage());
        assertEquals(0, orderStatus(4));
        assertEquals(0, confirmStatus(4));
    }

    @Test
    void otherMembersOrderConfirmReceiveKeepsFailure() throws Exception {
        asMember(ME);
        ApiException ex = assertThrows(ApiException.class, () -> service.confirmReceiveOrder(order(5)));
        assertEquals("不能确认他人订单！", ex.getMessage());
        assertEquals(2, orderStatus(5));
        assertEquals(0, confirmStatus(5));
    }

    private static void asMember(long memberId) {
        UmsMember member = new UmsMember();
        member.setId(memberId);
        when(serviceMember().getCurrentMember()).thenReturn(member);
    }

    private static UmsMemberService serviceMember() {
        return (UmsMemberService) ReflectionTestUtils.getField(service, "memberService");
    }

    private static void insert(Connection work, int n, int status, Integer payType, Timestamp paidAt) throws SQLException {
        execute(work, "insert into pms_sku_stock (id, sku_code, stock, lock_stock) values (?,?,20,10)",
                sku(n), "e201-" + sku(n));
        execute(work, "insert into oms_order (id, member_id, order_sn, status, delete_status, pay_type, payment_time, confirm_status, receiver_name, receiver_phone) values (?,?,?,?,0,?,?,0,?,?)",
                order(n), n == 5 ? member(5) : ME, "e201-" + order(n), status, payType, paidAt, "e201", "000");
        execute(work, "insert into oms_order_item (id, order_id, product_sku_id, product_quantity) values (?,?,?,?)",
                item(n), order(n), sku(n), QTY);
    }

    private static OmsPortalOrderServiceImpl wire(SqlSession sqlSession) throws Exception {
        OmsPortalOrderServiceImpl impl = new OmsPortalOrderServiceImpl();
        UmsMemberService members = mock(UmsMemberService.class);
        UmsMember me = new UmsMember();
        me.setId(ME);
        when(members.getCurrentMember()).thenReturn(me);
        ReflectionTestUtils.setField(impl, "orderMapper", sqlSession.getMapper(OmsOrderMapper.class));
        ReflectionTestUtils.setField(impl, "portalOrderDao", sqlSession.getMapper(PortalOrderDao.class));
        ReflectionTestUtils.setField(impl, "memberService", members);
        return impl;
    }

    private static void flush() {
        session.flushStatements();
        session.clearCache();
    }

    private static int orderStatus(int n) throws SQLException {
        flush();
        return (int) queryLong(session.getConnection(), "select status from oms_order where id=?", order(n));
    }

    private static int payType(int n) throws SQLException {
        flush();
        return (int) queryLong(session.getConnection(), "select pay_type from oms_order where id=?", order(n));
    }

    private static int confirmStatus(int n) throws SQLException {
        flush();
        return (int) queryLong(session.getConnection(), "select confirm_status from oms_order where id=?", order(n));
    }

    private static String paymentTime(int n) throws SQLException {
        flush();
        return queryString(session.getConnection(), "select date_format(payment_time, '%Y-%m-%d %H:%i:%s') from oms_order where id=?", order(n));
    }

    private static String receiveTime(int n) throws SQLException {
        flush();
        return queryString(session.getConnection(), "select date_format(receive_time, '%Y-%m-%d %H:%i:%s') from oms_order where id=?", order(n));
    }

    private static void assertStock(int n, int stock, int lock) throws SQLException {
        flush();
        try (PreparedStatement statement = session.getConnection().prepareStatement(
                "select stock, lock_stock from pms_sku_stock where id=?")) {
            statement.setLong(1, sku(n));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("缺少测试 sku " + sku(n));
                }
                assertEquals(stock, rs.getInt(1));
                assertEquals(lock, rs.getInt(2));
            }
        }
    }

    private static long member(int n) {
        return FLOOR + n;
    }

    private static long sku(int n) {
        return FLOOR + 100 + n;
    }

    private static long order(int n) {
        return FLOOR + 200 + n;
    }

    private static long item(int n) {
        return FLOOR + 300 + n;
    }

    private static void removeMarked(Connection connection) throws SQLException {
        execute(connection, "delete from oms_order_item where order_id in (select id from (select id from oms_order where order_sn like 'e201-%') t)");
        execute(connection, "delete from oms_order where order_sn like 'e201-%'");
        execute(connection, "delete from pms_sku_stock where sku_code like 'e201-%'");
    }

    private static void assertRangeFree(Connection connection) throws SQLException {
        for (String table : List.of("oms_order", "oms_order_item", "pms_sku_stock")) {
            long occupied = queryLong(connection, "select count(*) from " + table + " where id>=?", FLOOR);
            if (occupied != 0) {
                throw new IllegalStateException(table + " 已有 id>=" + FLOOR + " 的行，测试中止");
            }
        }
    }

    private static void restoreAutoIncrement(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("set session information_schema_stats_expiry=0");
        }
        for (String table : List.of("oms_order", "oms_order_item", "pms_sku_stock")) {
            long maxId = queryLong(connection, "select ifnull(max(id),0) from " + table);
            try (Statement statement = connection.createStatement()) {
                statement.execute("alter table " + table + " auto_increment = " + (maxId + 1));
            }
            long next = queryLong(connection, "select auto_increment from information_schema.tables where table_schema=database() and table_name=?", table);
            assertEquals(maxId + 1, next, table);
        }
    }

    private static long checksum(Connection connection, String table, String expr) throws SQLException {
        return queryLong(connection, "select ifnull(bit_xor(crc32(concat_ws('|', " + expr + "))),0) from " + table + " where id < " + FLOOR);
    }

    private static void execute(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            statement.executeUpdate();
        }
    }

    private static long queryLong(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("查询无结果: " + sql);
                }
                return rs.getLong(1);
            }
        }
    }

    private static String queryString(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("查询无结果: " + sql);
                }
                return rs.getString(1);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, args[i]);
        }
    }

    private static final class Db {
        final Connection monitor;
        final SqlSession session;

        Db(Connection monitor, SqlSession session) {
            this.monitor = monitor;
            this.session = session;
        }

        static Db openOrAbort() throws Exception {
            Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
            Path yml = null;
            for (int i = 0; i < 6 && dir != null; i++) {
                Path candidate = dir.resolve("SDC/environment/application-sdc.yml");
                if (Files.isRegularFile(candidate)) {
                    yml = candidate;
                    break;
                }
                dir = dir.getParent();
            }
            Assumptions.assumeTrue(yml != null, "未找到 SDC/environment/application-sdc.yml，本项中止，不是通过");
            Map<String, Object> datasource;
            try (Reader reader = Files.newBufferedReader(yml)) {
                Map<String, Map<String, Map<String, Object>>> root = new Yaml().load(reader);
                datasource = root.get("spring").get("datasource");
            }
            String url = String.valueOf(datasource.get("url")).replace("://localhost:", "://127.0.0.1:");
            Assumptions.assumeTrue(url.contains("127.0.0.1:13306"), "数据源不是 127.0.0.1:13306，本项中止，不是通过");
            if (!url.contains("connectTimeout=")) {
                url = url + (url.contains("?") ? "&" : "?") + "connectTimeout=3000&socketTimeout=15000";
            }
            String user = String.valueOf(datasource.get("username"));
            String password = String.valueOf(datasource.get("password"));
            try (Connection probe = DriverManager.getConnection(url, user, password)) {
                Assumptions.assumeTrue(probe.isValid(2), "教学库 127.0.0.1:13306 不可达，本项中止，不是通过");
            } catch (SQLException ex) {
                Assumptions.abort("教学库 127.0.0.1:13306 不可达，本项中止，不是通过");
            }
            Connection monitor = DriverManager.getConnection(url, user, password);
            monitor.setAutoCommit(true);
            UnpooledDataSource dataSource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, user, password);
            Configuration configuration = new Configuration(new Environment("sdc-e201-portal", new JdbcTransactionFactory(), dataSource));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(OmsOrderMapper.class);
            configuration.addMapper(OmsOrderItemMapper.class);
            try (InputStream xml = SDCE201StatePortalTest.class.getClassLoader().getResourceAsStream("dao/PortalOrderDao.xml")) {
                if (xml == null) {
                    throw new IllegalStateException("classpath 上没有 dao/PortalOrderDao.xml");
                }
                new XMLMapperBuilder(xml, configuration, "dao/PortalOrderDao.xml", configuration.getSqlFragments()).parse();
            }
            SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession(false);
            return new Db(monitor, session);
        }
    }
}
