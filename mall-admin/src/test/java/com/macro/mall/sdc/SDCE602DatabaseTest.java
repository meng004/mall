package com.macro.mall.sdc;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.macro.mall.mapper.PmsBrandMapper;
import com.macro.mall.model.PmsBrand;
import com.macro.mall.model.PmsBrandExample;
import com.macro.mall.service.impl.PmsBrandServiceImpl;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 连 127.0.0.1:13306 教学库，只插入并按主键删除本次品牌行。库不可达时中止，中止不是通过。
 */
class SDCE602DatabaseTest {
    private static final String URL = "jdbc:mysql://127.0.0.1:13306/mall?useUnicode=true&characterEncoding=utf-8"
            + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=2000&socketTimeout=5000";

    @Test
    void insertedBrandsPageAfterFactoryFilterAndOldApiStaysCompatible() throws Exception {
        String[] account = account();
        Assumptions.assumeTrue(account != null, "未找到教学库账号，本项中止，不是通过");
        try (Connection probe = DriverManager.getConnection(URL, account[0], account[1])) {
            Assumptions.assumeTrue(probe.isValid(2), "教学库 127.0.0.1:13306 不可达，本项中止，不是通过");
        } catch (SQLException ex) {
            Assumptions.abort("教学库 127.0.0.1:13306 不可达，本项中止，不是通过");
        }

        PooledDataSource dataSource = new PooledDataSource("com.mysql.cj.jdbc.Driver", URL, account[0], account[1]);
        Configuration configuration = new Configuration(new Environment("sdc-review", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        PageInterceptor interceptor = new PageInterceptor();
        Properties properties = new Properties();
        properties.setProperty("helperDialect", "mysql");
        interceptor.setProperties(properties);
        configuration.addInterceptor(interceptor);
        configuration.addMapper(PmsBrandMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);

        String token = "sdc602" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        List<Long> inserted = new ArrayList<>();
        try (SqlSession session = factory.openSession(true)) {
            PmsBrandMapper mapper = session.getMapper(PmsBrandMapper.class);
            PmsBrandServiceImpl service = new PmsBrandServiceImpl();
            ReflectionTestUtils.setField(service, "brandMapper", mapper);
            PmsBrand teaching = mapper.selectByPrimaryKey(3L);
            String teachingName = teaching == null ? null : teaching.getName();
            Integer teachingFactory = teaching == null ? null : teaching.getFactoryStatus();
            long others = mapper.countByExample(others(token));

            long shownA = insert(mapper, inserted, token + "-a", 30, 1, 1);
            long shownB = insert(mapper, inserted, token + "-b", 20, 1, 1);
            long shownC = insert(mapper, inserted, token + "-c", 10, 1, 1);
            long factoryOff = insert(mapper, inserted, token + "-d", 40, 0, 1);
            insert(mapper, inserted, token + "-e", 50, 1, 0);

            Page<PmsBrand> filteredFirst = page(service.listBrand(token, 1, 1, 2, 1));
            Page<PmsBrand> filteredSecond = page(service.listBrand(token, 1, 2, 2, 1));
            assertEquals(3, filteredFirst.getTotal());
            assertEquals(3, filteredSecond.getTotal());
            assertEquals(List.of(shownA, shownB), ids(filteredFirst));
            assertEquals(List.of(shownC), ids(filteredSecond));

            Page<PmsBrand> oldFirst = page(service.listBrand(token, 1, 1, 2));
            Page<PmsBrand> oldSecond = page(service.listBrand(token, 1, 2, 2));
            assertEquals(4, oldFirst.getTotal());
            assertEquals(4, oldSecond.getTotal());
            assertEquals(List.of(factoryOff, shownA), ids(oldFirst));
            assertEquals(List.of(shownB, shownC), ids(oldSecond));
            assertEquals(others, mapper.countByExample(others(token)));
            if (teaching != null) {
                PmsBrand after = mapper.selectByPrimaryKey(3L);
                assertEquals(teachingName, after.getName());
                assertEquals(teachingFactory, after.getFactoryStatus());
            }
        } finally {
            PageHelper.clearPage();
            try (SqlSession session = factory.openSession(true)) {
                PmsBrandMapper mapper = session.getMapper(PmsBrandMapper.class);
                for (Long id : inserted) {
                    mapper.deleteByPrimaryKey(id);
                }
            } finally {
                dataSource.forceCloseAll();
            }
        }
    }

    private static long insert(PmsBrandMapper mapper, List<Long> inserted, String name, int sort,
                               int factoryStatus, int showStatus) {
        PmsBrand row = new PmsBrand();
        row.setName(name);
        row.setFirstLetter("S");
        row.setSort(sort);
        row.setFactoryStatus(factoryStatus);
        row.setShowStatus(showStatus);
        mapper.insert(row);
        inserted.add(row.getId());
        return row.getId();
    }

    private static Page<PmsBrand> page(List<PmsBrand> rows) {
        return (Page<PmsBrand>) rows;
    }

    private static List<Long> ids(List<PmsBrand> rows) {
        return rows.stream().map(PmsBrand::getId).toList();
    }

    private static PmsBrandExample others(String token) {
        PmsBrandExample example = new PmsBrandExample();
        example.createCriteria().andNameNotLike("%" + token + "%");
        return example;
    }

    private static String[] account() throws Exception {
        Path file = findConfig();
        if (file == null) {
            return null;
        }
        String username = null;
        String password = null;
        boolean inDatasource = false;
        int datasourceIndent = -1;
        for (String raw : Files.readAllLines(file)) {
            String trimmed = raw.trim();
            int indent = trimmed.isEmpty() ? raw.length() : raw.indexOf(trimmed);
            if (trimmed.equals("datasource:")) {
                inDatasource = true;
                datasourceIndent = indent;
                username = null;
                password = null;
                continue;
            }
            if (!inDatasource || trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (indent <= datasourceIndent) {
                break;
            }
            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            switch (key) {
                case "username" -> username = value;
                case "password" -> password = value;
                default -> {
                }
            }
        }
        if (username == null || password == null) {
            return null;
        }
        return new String[]{username, password};
    }

    private static Path findConfig() {
        List<Path> starts = new ArrayList<>();
        if (System.getProperty("mall.root") != null) {
            starts.add(Path.of(System.getProperty("mall.root")));
        }
        starts.add(Path.of("").toAbsolutePath());
        for (Path start : starts) {
            for (Path path = start; path != null; path = path.getParent()) {
                Path candidate = path.resolve("SDC/environment/application-sdc.yml");
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
