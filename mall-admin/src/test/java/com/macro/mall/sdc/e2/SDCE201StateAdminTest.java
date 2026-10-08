package com.macro.mall.sdc.e2;

import com.macro.mall.dao.OmsOrderDao;
import com.macro.mall.dao.OmsOrderOperateHistoryDao;
import com.macro.mall.dto.OmsOrderDeliveryParam;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderOperateHistoryMapper;
import com.macro.mall.service.impl.OmsOrderServiceImpl;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * E2-01：经后台原有入口关闭、发货，到 127.0.0.1:13306 核对状态和返回值。
 * 只插入 id ≥ 9400000 且 order_sn 以 e201- 开头的行，结束时删除并恢复自增。
 */
class SDCE201StateAdminTest {
    private static final long FLOOR = 9_400_000L;

    private static Connection monitor;
    private static SqlSession session;
    private static OmsOrderServiceImpl service;
    private static long ordersBefore;
    private static long historyBefore;

    @BeforeAll
    static void connectAndInsert() throws Exception {
        Db db = Db.openOrAbort();
        monitor = db.monitor;
        removeMarked(monitor);
        restoreAutoIncrement(monitor);
        assertRangeFree(monitor);
        ordersBefore = checksum(monitor, "oms_order", "id, ifnull(status,-1), ifnull(delete_status,-1), ifnull(delivery_sn,'')");
        historyBefore = queryLong(monitor, "select ifnull(bit_xor(crc32(concat_ws('|', id, ifnull(order_id,0), ifnull(order_status,-1), ifnull(note,'')))),0) from oms_order_operate_history where ifnull(order_id,0) < ?", FLOOR);
        session = db.session;
        Connection work = session.getConnection();
        work.setAutoCommit(false);
        insert(work, 11, 0);
        insert(work, 12, 1);
        insert(work, 13, 1);
        insert(work, 14, 0);
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
                assertEquals(ordersBefore, checksum(monitor, "oms_order", "id, ifnull(status,-1), ifnull(delete_status,-1), ifnull(delivery_sn,'')"));
                assertEquals(historyBefore, queryLong(monitor, "select ifnull(bit_xor(crc32(concat_ws('|', id, ifnull(order_id,0), ifnull(order_status,-1), ifnull(note,'')))),0) from oms_order_operate_history where ifnull(order_id,0) < ?", FLOOR));
                assertEquals(0L, queryLong(monitor, "select count(*) from oms_order where order_sn like 'e201-%'"));
                assertEquals(0L, queryLong(monitor, "select count(*) from oms_order_operate_history where note='订单关闭:e201'"));
                monitor.close();
            }
        }
    }

    @Test
    void unpaidOrderCloseCloses() throws Exception {
        int count = service.close(List.of(order(11)), "e201");
        assertEquals(1, count);
        assertEquals(4, orderStatus(11));
    }

    @Test
    void paidOrderCloseLeavesStatusAndReturnsZero() throws Exception {
        int count = service.close(List.of(order(12)), "e201");
        assertEquals(0, count);
        assertEquals(1, orderStatus(12));
    }

    @Test
    void paidOrderDeliveryShips() throws Exception {
        OmsOrderDeliveryParam param = new OmsOrderDeliveryParam();
        param.setOrderId(order(13));
        param.setDeliveryCompany("顺丰");
        param.setDeliverySn("e201-ship");
        int count = service.delivery(List.of(param));
        assertEquals(1, count);
        assertEquals(2, orderStatus(13));
        assertEquals("e201-ship", deliverySn(13));
    }

    @Test
    void unpaidOrderDeliveryDoesNotShip() throws Exception {
        OmsOrderDeliveryParam param = new OmsOrderDeliveryParam();
        param.setOrderId(order(14));
        param.setDeliveryCompany("顺丰");
        param.setDeliverySn("e201-noship");
        int count = service.delivery(List.of(param));
        assertEquals(0, count);
        assertEquals(0, orderStatus(14));
        assertNull(deliverySn(14));
    }

    private static void insert(Connection work, int n, int status) throws SQLException {
        execute(work, "insert into oms_order (id, member_id, order_sn, status, delete_status, receiver_name, receiver_phone) values (?,?,?,?,0,?,?)",
                order(n), FLOOR + n, "e201-" + order(n), status, "e201", "000");
    }

    private static OmsOrderServiceImpl wire(SqlSession sqlSession) {
        OmsOrderServiceImpl impl = new OmsOrderServiceImpl();
        ReflectionTestUtils.setField(impl, "orderMapper", sqlSession.getMapper(OmsOrderMapper.class));
        ReflectionTestUtils.setField(impl, "orderDao", sqlSession.getMapper(OmsOrderDao.class));
        ReflectionTestUtils.setField(impl, "orderOperateHistoryDao", sqlSession.getMapper(OmsOrderOperateHistoryDao.class));
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

    private static String deliverySn(int n) throws SQLException {
        flush();
        return queryString(session.getConnection(), "select delivery_sn from oms_order where id=?", order(n));
    }

    private static long order(int n) {
        return FLOOR + 200 + n;
    }

    private static void removeMarked(Connection connection) throws SQLException {
        execute(connection, "delete from oms_order_operate_history where order_id in (select id from (select id from oms_order where order_sn like 'e201-%') t)");
        execute(connection, "delete from oms_order_operate_history where note='订单关闭:e201'");
        execute(connection, "delete from oms_order_operate_history where order_id in (9400211,9400212,9400213,9400214) and note='完成发货'");
        execute(connection, "delete from oms_order where order_sn like 'e201-%'");
    }

    private static void assertRangeFree(Connection connection) throws SQLException {
        long occupied = queryLong(connection, "select count(*) from oms_order where id>=?", FLOOR);
        if (occupied != 0) {
            throw new IllegalStateException("oms_order 已有 id>=" + FLOOR + " 的行，测试中止");
        }
    }

    private static void restoreAutoIncrement(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("set session information_schema_stats_expiry=0");
        }
        for (String table : List.of("oms_order", "oms_order_operate_history")) {
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
            try (java.io.Reader reader = Files.newBufferedReader(yml)) {
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
            Configuration configuration = new Configuration(new Environment("sdc-e201-admin", new JdbcTransactionFactory(), dataSource));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(OmsOrderMapper.class);
            configuration.addMapper(OmsOrderItemMapper.class);
            configuration.addMapper(OmsOrderOperateHistoryMapper.class);
            parse(configuration, "dao/OmsOrderDao.xml");
            parse(configuration, "dao/OmsOrderOperateHistoryDao.xml");
            SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession(false);
            return new Db(monitor, session);
        }

        private static void parse(Configuration configuration, String path) throws Exception {
            try (InputStream xml = SDCE201StateAdminTest.class.getClassLoader().getResourceAsStream(path)) {
                if (xml == null) {
                    throw new IllegalStateException("classpath 上没有 " + path);
                }
                new XMLMapperBuilder(xml, configuration, path, configuration.getSqlFragments()).parse();
            }
        }
    }
}
