package com.macro.mall.sdc.e2;

import com.macro.mall.mapper.SmsFlashPromotionMapper;
import com.macro.mall.mapper.SmsFlashPromotionSessionMapper;
import com.macro.mall.portal.dao.HomeDao;
import com.macro.mall.portal.domain.FlashPromotionProduct;
import com.macro.mall.portal.domain.HomeFlashPromotion;
import com.macro.mall.portal.service.impl.HomeFlashPromotionSelector;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * E2-03 改后测试（HEAD_ONLY_TESTS）：用固定时刻直接驱动抽出的 {@link HomeFlashPromotionSelector}，
 * 在 127.0.0.1:13306 教学库上执行真实 SQL，只读 mall.sql 自带数据：
 * 活动 14 为 2022-11-09～2023-12-31；场次 1～7 依次为 8–10、10–12 … 20–22 点，首尾相接。
 * 改前时间来自服务内部的 new Date()，这些时刻无法在测试里指定。库不可达时中止，中止不是通过。
 */
class SDCE203SelectorDatabaseTest {
    private static final String URL = "jdbc:mysql://127.0.0.1:13306/mall?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=2000";
    private static final List<FlashPromotionProduct> PRODUCTS = List.of(new FlashPromotionProduct());
    private static PooledDataSource dataSource;
    private static SqlSession session;
    private static HomeFlashPromotionSelector selector;

    @BeforeAll
    static void connect() throws Exception {
        Path yml = Path.of("..", "SDC", "environment", "application-sdc.yml");
        Assumptions.assumeTrue(Files.isRegularFile(yml), "未找到 SDC/environment/application-sdc.yml，本项中止，不是通过");
        Map<String, Object> ds;
        try (Reader reader = Files.newBufferedReader(yml)) {
            Map<String, Map<String, Map<String, Object>>> root = new Yaml().load(reader);
            ds = root.get("spring").get("datasource");
        }
        String user = String.valueOf(ds.get("username")), password = String.valueOf(ds.get("password"));
        try (Connection probe = DriverManager.getConnection(URL, user, password)) {
            Assumptions.assumeTrue(probe.isValid(2), "教学库 127.0.0.1:13306 不可达，本项中止，不是通过");
        } catch (java.sql.SQLException ex) {
            Assumptions.abort("教学库 127.0.0.1:13306 不可达，本项中止，不是通过");
        }
        dataSource = new PooledDataSource("com.mysql.cj.jdbc.Driver", URL, user, password);
        Configuration configuration = new Configuration(new Environment("sdc-e203", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(SmsFlashPromotionMapper.class);
        configuration.addMapper(SmsFlashPromotionSessionMapper.class);
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        HomeDao homeDao = mock(HomeDao.class, withSettings().mockMaker("mock-maker-subclass"));
        when(homeDao.getFlashProductList(any(), any())).thenReturn(PRODUCTS);
        selector = new HomeFlashPromotionSelector(session.getMapper(SmsFlashPromotionMapper.class),
                session.getMapper(SmsFlashPromotionSessionMapper.class), homeDao);
    }

    @AfterAll
    static void close() {
        if (session != null) session.close();
        if (dataSource != null) dataSource.forceCloseAll();
    }

    /** 08:00:00 正好是第一场开始，闭区间命中 8–10，下一场为 10–12。 */
    @Test
    void openingMomentShowsFirstSession() {
        assertEquals("08:00-10:00 next 10:00-12:00 products", select(2023, 6, 15, 8, 0, 0, 0));
    }

    @Test
    void midSessionShowsCurrentAndNext() {
        assertEquals("10:00-12:00 next 12:00-14:00 products", select(2023, 6, 15, 11, 0, 0, 0));
    }

    /** 10:00:00 同时落在 8–10 与 10–12 两个闭区间；当前场查询没有排序，取到先返回的 8–10（现状，不在本题修改）。 */
    @Test
    void handoverMomentShowsEndingSession() {
        assertEquals("08:00-10:00 next 10:00-12:00 products", select(2023, 6, 15, 10, 0, 0, 0));
    }

    @Test
    void lastSessionHasNoNext() {
        assertEquals("20:00-22:00 next none products", select(2023, 6, 15, 21, 0, 0, 0));
    }

    @Test
    void outsideAllSessionsIsEmpty() {
        assertEquals("none", select(2023, 6, 15, 23, 0, 0, 0));
    }

    @Test
    void firstPromotionDayIsIncluded() {
        assertEquals("10:00-12:00 next 12:00-14:00 products", select(2022, 11, 9, 11, 0, 0, 0));
    }

    @Test
    void lastPromotionDayIsIncluded() {
        assertEquals("10:00-12:00 next 12:00-14:00 products", select(2023, 12, 31, 11, 0, 0, 123));
    }

    @Test
    void dayAfterPromotionIsEmpty() {
        assertEquals("none", select(2024, 1, 1, 11, 0, 0, 0));
    }

    private static String select(int y, int mo, int d, int h, int mi, int s, int ms) {
        Date now = Date.from(LocalDateTime.of(y, mo, d, h, mi, s, ms * 1_000_000).atZone(ZoneId.systemDefault()).toInstant());
        HomeFlashPromotion r = selector.select(now);
        if (r.getStartTime() == null) {
            return r.getProductList() == null ? "none" : "products-without-session";
        }
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm");
        String next = r.getNextStartTime() == null ? "none" : hm.format(r.getNextStartTime()) + "-" + hm.format(r.getNextEndTime());
        return hm.format(r.getStartTime()) + "-" + hm.format(r.getEndTime()) + " next " + next
                + (r.getProductList() == PRODUCTS ? " products" : " no-products");
    }
}
