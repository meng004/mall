package com.macro.mall.sdc;

import com.macro.mall.dto.PmsProductAttributeParam;
import com.macro.mall.mapper.PmsProductAttributeCategoryMapper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.model.PmsProductAttribute;
import com.macro.mall.model.PmsProductAttributeCategory;
import com.macro.mall.service.PmsProductAttributeService;
import com.macro.mall.service.impl.PmsProductAttributeServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SDCE304TransactionTest {

    @Test
    void updateTransactionAttributeIsPresent() throws Exception {
        Method method = PmsProductAttributeService.class.getMethod(
                "update", Long.class, PmsProductAttributeParam.class);
        TransactionAttribute attribute = new AnnotationTransactionAttributeSource()
                .getTransactionAttribute(method, PmsProductAttributeServiceImpl.class);
        assertNotNull(attribute);
    }

    @Test
    void crossCategoryMovePersistsAttributeAndCounts() throws Exception {
        run(Fail.NONE, Move.CROSS, (before, after, fixture) -> {
            assertEquals(new AttributeState(fixture.targetId(), 1, fixture.movedName()), after.attribute());
            assertEquals(new CategoryCounts(before.source().attributeCount() - 1, before.source().paramCount()), after.source());
            assertEquals(new CategoryCounts(before.target().attributeCount(), before.target().paramCount() + 1), after.target());
        });
    }

    @Test
    void sameCategoryTypeChangePersistsBothCounters() throws Exception {
        run(Fail.NONE, Move.SAME_TYPE, (before, after, fixture) -> {
            assertEquals(new AttributeState(fixture.sourceId(), 1, fixture.typedName()), after.attribute());
            assertEquals(new CategoryCounts(before.source().attributeCount() - 1, before.source().paramCount() + 1), after.source());
            assertEquals(before.target(), after.target());
        });
    }

    @Test
    void oldCategoryWriteFailureRestoresPreviousState() throws Exception {
        run(Fail.OLD, Move.CROSS, (before, after, fixture) -> assertEquals(before, after));
    }

    @Test
    void newCategoryWriteFailureRestoresPreviousState() throws Exception {
        run(Fail.NEW, Move.CROSS, (before, after, fixture) -> assertEquals(before, after));
    }

    private void run(Fail fail, Move move, Check check) throws Exception {
        Db db = Db.openOrAbort();
        try {
            Fixture fixture = db.insertFixture();
            Snapshot before = db.read(fixture);
            assertEquals(new AttributeState(fixture.sourceId(), 0, fixture.originalName()), before.attribute());
            assertEquals(new CategoryCounts(4, 7), before.source());
            assertEquals(new CategoryCounts(2, 5), before.target());
            PmsProductAttributeService service = db.proxied(fail, fixture);
            PmsProductAttributeParam param = move.param(fixture);
            if (fail == Fail.NONE) {
                assertEquals(1, service.update(fixture.attributeId(), param));
            } else {
                assertThrows(RuntimeException.class, () -> service.update(fixture.attributeId(), param));
            }
            check.accept(before, db.read(fixture), fixture);
        } finally {
            db.cleanup();
        }
    }

    private enum Fail {
        NONE, OLD, NEW
    }

    private enum Move {
        CROSS, SAME_TYPE;

        private PmsProductAttributeParam param(Fixture fixture) {
            PmsProductAttributeParam param = new PmsProductAttributeParam();
            if (this == CROSS) {
                param.setProductAttributeCategoryId(fixture.targetId());
                param.setType(1);
                param.setName(fixture.movedName());
            } else {
                param.setProductAttributeCategoryId(fixture.sourceId());
                param.setType(1);
                param.setName(fixture.typedName());
            }
            return param;
        }
    }

    @FunctionalInterface
    private interface Check {
        void accept(Snapshot before, Snapshot after, Fixture fixture);
    }

    private record AttributeState(long categoryId, int type, String name) {
    }

    private record CategoryCounts(int attributeCount, int paramCount) {
    }

    private record Snapshot(AttributeState attribute, CategoryCounts source, CategoryCounts target) {
    }

    private record Fixture(long sourceId, long targetId, long attributeId, String originalName, String movedName, String typedName) {
    }

    private static final class Db {
        private final DataSource dataSource;
        private final PmsProductAttributeMapper attributeMapper;
        private final PmsProductAttributeCategoryMapper categoryMapper;
        private Long sourceId;
        private Long targetId;
        private Long attributeId;

        private Db(DataSource dataSource, PmsProductAttributeMapper attributeMapper,
                   PmsProductAttributeCategoryMapper categoryMapper) {
            this.dataSource = dataSource;
            this.attributeMapper = attributeMapper;
            this.categoryMapper = categoryMapper;
        }

        private static Db openOrAbort() throws Exception {
            Path yml = applicationYml();
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new FileSystemResource(yml));
            Properties properties = yaml.getObject();
            String url = properties.getProperty("spring.datasource.url").replace("://localhost:", "://127.0.0.1:");
            assumeTrue(url.contains("127.0.0.1:13306"), "阻塞: 数据源不是 127.0.0.1:13306，事务回滚未执行");
            if (!url.contains("connectTimeout=")) {
                url = url + "&connectTimeout=3000&socketTimeout=15000";
            }
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    url, properties.getProperty("spring.datasource.username"), properties.getProperty("spring.datasource.password"));
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            SQLException failure = null;
            boolean connected = false;
            try (Connection connection = dataSource.getConnection()) {
                connected = connection.isValid(2);
            } catch (SQLException ex) {
                failure = ex;
            }
            assumeTrue(connected, "阻塞: MySQL 127.0.0.1:13306 不可连接，事务回滚未执行"
                    + (failure == null ? "" : ": " + failure.getMessage()));
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] attributeXml = resolver.getResources("classpath*:com/macro/mall/mapper/PmsProductAttributeMapper.xml");
            Resource[] categoryXml = resolver.getResources("classpath*:com/macro/mall/mapper/PmsProductAttributeCategoryMapper.xml");
            factoryBean.setMapperLocations(attributeXml[0], categoryXml[0]);
            factoryBean.afterPropertiesSet();
            SqlSessionFactory factory = factoryBean.getObject();
            SqlSessionTemplate session = new SqlSessionTemplate(factory);
            return new Db(dataSource, session.getMapper(PmsProductAttributeMapper.class),
                    session.getMapper(PmsProductAttributeCategoryMapper.class));
        }

        private Fixture insertFixture() {
            String token = UUID.randomUUID().toString().substring(0, 8);
            sourceId = insertCategory("sdc-e304-a-" + token, 4, 7);
            targetId = insertCategory("sdc-e304-b-" + token, 2, 5);
            String originalName = "sdc-e304-x-" + token;
            PmsProductAttribute attribute = new PmsProductAttribute();
            attribute.setProductAttributeCategoryId(sourceId);
            attribute.setName(originalName);
            attribute.setType(0);
            attributeMapper.insertSelective(attribute);
            attributeId = attribute.getId();
            return new Fixture(sourceId, targetId, attributeId, originalName, originalName + "-moved", originalName + "-type");
        }

        private long insertCategory(String name, int attributeCount, int paramCount) {
            PmsProductAttributeCategory category = new PmsProductAttributeCategory();
            category.setName(name);
            category.setAttributeCount(attributeCount);
            category.setParamCount(paramCount);
            categoryMapper.insertSelective(category);
            return category.getId();
        }

        private PmsProductAttributeService proxied(Fail fail, Fixture fixture) {
            Long failId = switch (fail) {
                case OLD -> fixture.sourceId();
                case NEW -> fixture.targetId();
                case NONE -> null;
            };
            PmsProductAttributeCategoryMapper failingCategoryMapper = (PmsProductAttributeCategoryMapper) Proxy.newProxyInstance(
                    PmsProductAttributeCategoryMapper.class.getClassLoader(),
                    new Class<?>[]{PmsProductAttributeCategoryMapper.class},
                    (proxy, method, args) -> {
                        if (failId != null
                                && "updateByPrimaryKey".equals(method.getName())
                                && args != null
                                && args.length == 1
                                && args[0] instanceof PmsProductAttributeCategory row
                                && failId.equals(row.getId())) {
                            throw new RuntimeException("category write failed: " + failId);
                        }
                        try {
                            return method.invoke(categoryMapper, args);
                        } catch (InvocationTargetException ex) {
                            Throwable cause = ex.getCause();
                            if (cause instanceof RuntimeException runtime) {
                                throw runtime;
                            }
                            if (cause instanceof Error error) {
                                throw error;
                            }
                            throw new IllegalStateException(cause);
                        }
                    });
            PmsProductAttributeServiceImpl target = new PmsProductAttributeServiceImpl();
            ReflectionTestUtils.setField(target, "productAttributeMapper", attributeMapper);
            ReflectionTestUtils.setField(target, "productAttributeCategoryMapper", failingCategoryMapper);
            ProxyFactory factory = new ProxyFactory(target);
            factory.setProxyTargetClass(false);
            factory.setInterfaces(PmsProductAttributeService.class);
            factory.addAdvice(new TransactionInterceptor(
                    new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
            return (PmsProductAttributeService) factory.getProxy();
        }

        private Snapshot read(Fixture fixture) throws SQLException {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(true);
                return new Snapshot(
                        readAttribute(connection, fixture.attributeId()),
                        readCounts(connection, fixture.sourceId()),
                        readCounts(connection, fixture.targetId()));
            }
        }

        private AttributeState readAttribute(Connection connection, long id) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select product_attribute_category_id, type, name from pms_product_attribute where id = ?")) {
                statement.setLong(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("attribute " + id + " not visible");
                    }
                    return new AttributeState(rs.getLong(1), rs.getInt(2), rs.getString(3));
                }
            }
        }

        private CategoryCounts readCounts(Connection connection, long id) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "select attribute_count, param_count from pms_product_attribute_category where id = ?")) {
                statement.setLong(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("category " + id + " not visible");
                    }
                    return new CategoryCounts(rs.getInt(1), rs.getInt(2));
                }
            }
        }

        private void cleanup() {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(true);
                deleteById(connection, "delete from pms_product_attribute where id = ?", attributeId);
                deleteById(connection, "delete from pms_product_attribute_category where id = ?", sourceId);
                deleteById(connection, "delete from pms_product_attribute_category where id = ?", targetId);
            } catch (SQLException ex) {
                throw new IllegalStateException("cleanup failed for attribute=" + attributeId
                        + " source=" + sourceId + " target=" + targetId, ex);
            }
        }

        private static void deleteById(Connection connection, String sql, Long id) throws SQLException {
            if (id == null) {
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
        }

        private static Path applicationYml() {
            Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
            for (int i = 0; i < 5 && dir != null; i++) {
                Path candidate = dir.resolve("SDC/environment/application-sdc.yml");
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
                dir = dir.getParent();
            }
            throw new IllegalStateException("application-sdc.yml not found from " + System.getProperty("user.dir"));
        }
    }
}
