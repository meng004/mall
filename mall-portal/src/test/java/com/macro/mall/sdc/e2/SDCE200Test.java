package com.macro.mall.sdc.e2;

import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderSettingMapper;
import com.macro.mall.mapper.SmsCouponHistoryMapper;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.service.UmsMemberCacheService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import com.macro.mall.portal.service.impl.UmsMemberServiceImpl;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * E2-00 特征测试：经 {@link com.macro.mall.portal.service.OmsPortalOrderService} 的既有入口取消订单，
 * 到 127.0.0.1:13306 教学库核对订单状态、锁定库存、优惠券使用记录和会员积分。
 * 只插入 id ≥ 9200000 且带 e200- 标记的行；全部写在一条未提交事务里，结束时回滚。
 * 库里已有一笔 2023-05-11 的待付款订单，默认超时 120 分钟会把它算进批量取消。
 * 因此事务内把超时分钟改成 5000000（约 9.5 年，截止约 2017 年），只让 2010 年的测试单超时。
 */
class SDCE200Test {
    private static final int ISOLATED_OVERTIME_MINUTES = 5_000_000;
    private static final long ID_FLOOR = 9_200_000L;

    private static final Fixture PENDING = new Fixture(1, 0, 0, "2026-10-01 00:00:00");
    private static final Fixture PAID = new Fixture(2, 1, 0, "2026-10-01 00:00:00");
    private static final Fixture DELETED = new Fixture(3, 0, 1, "2026-10-01 00:00:00");
    private static final Fixture OVERDUE = new Fixture(4, 0, 0, "2010-01-01 00:00:00");
    private static final Fixture FRESH = new Fixture(5, 0, 0, "2026-10-01 00:00:00");
    private static final Fixture OLD_PAID = new Fixture(6, 1, 0, "2010-01-01 00:00:00");
    /** 后台已删除、但仍待付款且已超时。批量取消必须把它关掉并补偿，单笔 cancelOrder 则不动。 */
    private static final Fixture DELETED_OVERDUE = new Fixture(7, 0, 1, "2010-01-01 00:00:00");
    private static final Fixture[] FIXTURES = {PENDING, PAID, DELETED, OVERDUE, FRESH, OLD_PAID, DELETED_OVERDUE};

    private static Connection monitor;
    private static SqlSession session;
    private static OmsPortalOrderServiceImpl service;
    private static long ordersBefore;
    private static long membersBefore;
    private static long stockBefore;
    private static long couponsBefore;
    private static int overtimeBefore;

    @BeforeAll
    static void connectAndInsert() throws Exception {
        Db db = Db.openOrAbort();
        monitor = db.monitor;
        removeMarkedRows(monitor);
        restoreInflatedAutoIncrement(monitor);
        ordersBefore = checksum(monitor, "oms_order", "id, status, delete_status, IFNULL(coupon_id,0), IFNULL(use_integration,-1), IFNULL(member_id,0)");
        membersBefore = checksum(monitor, "ums_member", "id, IFNULL(integration,-1), IFNULL(growth,-1)");
        stockBefore = checksum(monitor, "pms_sku_stock", "id, IFNULL(lock_stock,-1), IFNULL(stock,-1)");
        couponsBefore = checksum(monitor, "sms_coupon_history", "id, IFNULL(use_status,-1), IFNULL(member_id,0), IFNULL(coupon_id,0)");
        overtimeBefore = (int) queryLong(monitor, "select normal_order_overtime from oms_order_setting where id=1");

        session = db.session;
        Connection work = session.getConnection();
        work.setAutoCommit(false);
        for (Fixture fixture : FIXTURES) {
            insertFixture(work, fixture);
        }
        execute(work, "update oms_order_setting set normal_order_overtime=? where id=1", ISOLATED_OVERTIME_MINUTES);
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
                removeMarkedRows(monitor);
                restoreInflatedAutoIncrement(monitor);
                int overtime = (int) queryLong(monitor, "select normal_order_overtime from oms_order_setting where id=1");
                if (overtime != overtimeBefore) {
                    execute(monitor, "update oms_order_setting set normal_order_overtime=? where id=1", overtimeBefore);
                }
                assertEquals(ordersBefore, checksum(monitor, "oms_order", "id, status, delete_status, IFNULL(coupon_id,0), IFNULL(use_integration,-1), IFNULL(member_id,0)"));
                assertEquals(membersBefore, checksum(monitor, "ums_member", "id, IFNULL(integration,-1), IFNULL(growth,-1)"));
                assertEquals(stockBefore, checksum(monitor, "pms_sku_stock", "id, IFNULL(lock_stock,-1), IFNULL(stock,-1)"));
                assertEquals(couponsBefore, checksum(monitor, "sms_coupon_history", "id, IFNULL(use_status,-1), IFNULL(member_id,0), IFNULL(coupon_id,0)"));
                assertEquals(overtimeBefore, (int) queryLong(monitor, "select normal_order_overtime from oms_order_setting where id=1"));
                assertEquals(0, queryLong(monitor, "select count(*) from oms_order where id>=?", ID_FLOOR));
                monitor.close();
            }
        }
    }

    @Test
    void pendingOrderCancelClosesAndCompensates() throws Exception {
        assertUntouched(PENDING);
        service.cancelOrder(PENDING.orderId);
        assertCompensated(PENDING);
    }

    @Test
    void paidOrderStaysUntouched() throws Exception {
        assertUntouched(PAID);
        service.cancelOrder(PAID.orderId);
        assertUntouched(PAID);
    }

    @Test
    void deletedOrderStaysUntouched() throws Exception {
        assertUntouched(DELETED);
        service.cancelOrder(DELETED.orderId);
        assertUntouched(DELETED);
    }

    @Test
    void cancelTimeOutOrderCancelsOnlyOverduePending() throws Exception {
        Connection work = session.getConnection();
        assertEquals(ISOLATED_OVERTIME_MINUTES, (int) queryLong(work, "select normal_order_overtime from oms_order_setting where id=1"));
        List<Long> candidates = queryIds(work,
                "select id from oms_order where status=0 and create_time < date_add(NOW(), interval -" + ISOLATED_OVERTIME_MINUTES + " minute) order by id");
        for (Long id : candidates) {
            if (id < ID_FLOOR) {
                throw new IllegalStateException("批量取消会碰到既有订单 " + id + "，测试中止且不会调用取消");
            }
        }
        assertEquals(List.of(OVERDUE.orderId, DELETED_OVERDUE.orderId), candidates);
        assertUntouched(OVERDUE);
        assertUntouched(DELETED_OVERDUE);
        assertUntouched(FRESH);
        assertUntouched(OLD_PAID);

        Integer cancelled = service.cancelTimeOutOrder();

        assertEquals(candidates.size(), cancelled);
        assertCompensated(OVERDUE);
        assertCompensated(DELETED_OVERDUE);
        assertUntouched(FRESH);
        assertUntouched(OLD_PAID);
    }

    private static void assertUntouched(Fixture fixture) throws Exception {
        Observed observed = read(fixture);
        assertEquals(fixture.status, observed.status);
        assertEquals(fixture.deleteStatus, observed.deleteStatus);
        assertEquals(10, observed.lockStock);
        assertEquals(20, observed.stock);
        assertEquals(1, observed.useStatus);
        assertEquals(100, observed.integration);
    }

    private static void assertCompensated(Fixture fixture) throws Exception {
        Observed observed = read(fixture);
        assertEquals(4, observed.status);
        assertEquals(fixture.deleteStatus, observed.deleteStatus);
        assertEquals(7, observed.lockStock);
        assertEquals(20, observed.stock);
        assertEquals(0, observed.useStatus);
        assertEquals(140, observed.integration);
    }

    private static Observed read(Fixture fixture) throws Exception {
        Connection work = session.getConnection();
        session.flushStatements();
        Observed observed = new Observed();
        try (PreparedStatement statement = work.prepareStatement(
                "select status, delete_status from oms_order where id=?")) {
            statement.setLong(1, fixture.orderId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                observed.status = rs.getInt(1);
                observed.deleteStatus = rs.getInt(2);
            }
        }
        try (PreparedStatement statement = work.prepareStatement(
                "select lock_stock, stock from pms_sku_stock where id=?")) {
            statement.setLong(1, fixture.skuId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                observed.lockStock = rs.getInt(1);
                observed.stock = rs.getInt(2);
            }
        }
        try (PreparedStatement statement = work.prepareStatement(
                "select use_status from sms_coupon_history where id=?")) {
            statement.setLong(1, fixture.historyId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                observed.useStatus = rs.getInt(1);
            }
        }
        try (PreparedStatement statement = work.prepareStatement(
                "select integration from ums_member where id=?")) {
            statement.setLong(1, fixture.memberId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                observed.integration = rs.getInt(1);
            }
        }
        return observed;
    }

    private static OmsPortalOrderServiceImpl wire(SqlSession sqlSession) throws Exception {
        OmsPortalOrderServiceImpl impl = new OmsPortalOrderServiceImpl();
        OmsOrderMapper orderMapper = sqlSession.getMapper(OmsOrderMapper.class);
        OmsOrderItemMapper orderItemMapper = sqlSession.getMapper(OmsOrderItemMapper.class);
        PortalOrderDao portalOrderDao = sqlSession.getMapper(PortalOrderDao.class);
        OmsOrderSettingMapper orderSettingMapper = sqlSession.getMapper(OmsOrderSettingMapper.class);
        SmsCouponHistoryMapper couponHistoryMapper = sqlSession.getMapper(SmsCouponHistoryMapper.class);
        UmsMemberMapper memberMapper = sqlSession.getMapper(UmsMemberMapper.class);
        UmsMemberServiceImpl members = new UmsMemberServiceImpl();
        ReflectionTestUtils.setField(members, "memberMapper", memberMapper);
        ReflectionTestUtils.setField(members, "memberCacheService", mock(UmsMemberCacheService.class));
        ReflectionTestUtils.setField(impl, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(impl, "orderItemMapper", orderItemMapper);
        ReflectionTestUtils.setField(impl, "portalOrderDao", portalOrderDao);
        ReflectionTestUtils.setField(impl, "orderSettingMapper", orderSettingMapper);
        ReflectionTestUtils.setField(impl, "couponHistoryMapper", couponHistoryMapper);
        ReflectionTestUtils.setField(impl, "memberService", members);
        Map<Class<?>, Object> deps = new HashMap<>();
        deps.put(OmsOrderMapper.class, orderMapper);
        deps.put(OmsOrderItemMapper.class, orderItemMapper);
        deps.put(PortalOrderDao.class, portalOrderDao);
        deps.put(OmsOrderSettingMapper.class, orderSettingMapper);
        deps.put(SmsCouponHistoryMapper.class, couponHistoryMapper);
        deps.put(UmsMemberService.class, members);
        for (Field field : OmsPortalOrderServiceImpl.class.getDeclaredFields()) {
            if (!"com.macro.mall.portal.service.impl.OrderCancellation".equals(field.getType().getName())) {
                continue;
            }
            Constructor<?> ctor = null;
            for (Constructor<?> candidate : field.getType().getDeclaredConstructors()) {
                if (ctor == null || candidate.getParameterCount() > ctor.getParameterCount()) {
                    ctor = candidate;
                }
            }
            if (ctor == null) {
                throw new IllegalStateException("OrderCancellation 没有构造函数");
            }
            Object[] args = new Object[ctor.getParameterCount()];
            for (int i = 0; i < args.length; i++) {
                args[i] = deps.get(ctor.getParameterTypes()[i]);
                if (args[i] == null) {
                    throw new IllegalStateException("无法为 OrderCancellation 提供 " + ctor.getParameterTypes()[i].getName());
                }
            }
            ctor.setAccessible(true);
            ReflectionTestUtils.setField(impl, field.getName(), ctor.newInstance(args));
        }
        return impl;
    }

    private static void insertFixture(Connection work, Fixture fixture) throws SQLException {
        execute(work, "insert into ums_member (id, username, integration, growth, status) values (?,?,?,?,1)",
                fixture.memberId, "sdc-e200-" + fixture.memberId, 100, 8);
        execute(work, "insert into pms_sku_stock (id, sku_code, stock, lock_stock) values (?,?,20,10)",
                fixture.skuId, "e200-" + fixture.skuId);
        execute(work, "insert into sms_coupon_history (id, coupon_id, member_id, use_status, coupon_code) values (?,?,?,1,?)",
                fixture.historyId, fixture.couponId, fixture.memberId, "e200-" + fixture.historyId);
        execute(work, "insert into oms_order (id, member_id, coupon_id, order_sn, create_time, status, delete_status, use_integration, growth, receiver_name, receiver_phone) values (?,?,?,?,?,?,?,?,?,?,?)",
                fixture.orderId, fixture.memberId, fixture.couponId, "e200-" + fixture.orderId,
                Timestamp.valueOf(fixture.createTime), fixture.status, fixture.deleteStatus, 40, 15, "e200", "000");
        execute(work, "insert into oms_order_item (id, order_id, product_sku_id, product_quantity) values (?,?,?,3)",
                fixture.itemId, fixture.orderId, fixture.skuId);
    }

    private static void removeMarkedRows(Connection connection) throws SQLException {
        execute(connection, "delete from oms_order_item where order_id in (select id from (select id from oms_order where order_sn like 'e200-%') t)");
        execute(connection, "delete from oms_order where order_sn like 'e200-%'");
        execute(connection, "delete from sms_coupon_history where coupon_code like 'e200-%'");
        execute(connection, "delete from pms_sku_stock where sku_code like 'e200-%'");
        execute(connection, "delete from ums_member where username like 'sdc-e200-%'");
    }

    /** 清完测试行后把插过数据的表的自增设为 max(id)+1。先关掉 information_schema 统计缓存，不记原值。 */
    private static void restoreInflatedAutoIncrement(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("set session information_schema_stats_expiry=0");
        }
        for (String table : List.of("ums_member", "pms_sku_stock", "sms_coupon_history", "oms_order", "oms_order_item")) {
            long maxId = queryLong(connection, "select ifnull(max(id),0) from " + table);
            try (Statement statement = connection.createStatement()) {
                statement.execute("alter table " + table + " auto_increment = " + (maxId + 1));
            }
            long next = queryLong(connection, "select auto_increment from information_schema.tables where table_schema=database() and table_name=?", table);
            assertEquals(maxId + 1, next, table);
        }
    }

    private static long checksum(Connection connection, String table, String expr) throws SQLException {
        return queryLong(connection, "select ifnull(bit_xor(crc32(concat_ws('|', " + expr + "))),0) from " + table + " where id < " + ID_FLOOR);
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

    private static List<Long> queryIds(Connection connection, String sql) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private static void bind(PreparedStatement statement, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, args[i]);
        }
    }

    private static final class Fixture {
        final long memberId;
        final long skuId;
        final long couponId;
        final long historyId;
        final long orderId;
        final long itemId;
        final int status;
        final int deleteStatus;
        final String createTime;

        Fixture(int n, int status, int deleteStatus, String createTime) {
            this.memberId = 9_200_000L + n;
            this.skuId = 9_200_100L + n;
            this.couponId = 9_200_200L + n;
            this.historyId = 9_200_300L + n;
            this.orderId = 9_200_400L + n;
            this.itemId = 9_200_500L + n;
            this.status = status;
            this.deleteStatus = deleteStatus;
            this.createTime = createTime;
        }
    }

    private static final class Observed {
        int status;
        int deleteStatus;
        int lockStock;
        int stock;
        int useStatus;
        int integration;
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
            Configuration configuration = new Configuration(new Environment("sdc-e200", new JdbcTransactionFactory(), dataSource));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(OmsOrderMapper.class);
            configuration.addMapper(OmsOrderItemMapper.class);
            configuration.addMapper(OmsOrderSettingMapper.class);
            configuration.addMapper(SmsCouponHistoryMapper.class);
            configuration.addMapper(UmsMemberMapper.class);
            try (InputStream xml = SDCE200Test.class.getClassLoader().getResourceAsStream("dao/PortalOrderDao.xml")) {
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
