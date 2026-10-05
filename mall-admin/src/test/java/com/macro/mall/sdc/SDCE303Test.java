package com.macro.mall.sdc;

import com.macro.mall.mapper.PmsProductAttributeCategoryMapper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.model.PmsProductAttribute;
import com.macro.mall.model.PmsProductAttributeCategory;
import com.macro.mall.model.PmsProductAttributeExample;
import com.macro.mall.service.impl.PmsProductAttributeServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class SDCE303Test {
    private PmsProductAttributeServiceImpl service;
    private PmsProductAttributeMapper attributeMapper;
    private PmsProductAttributeCategoryMapper categoryMapper;
    private final Map<Long, PmsProductAttribute> attributes = new HashMap<>();
    private final Map<Long, PmsProductAttributeCategory> categories = new HashMap<>();
    private final List<CategoryCount> categoryWrites = new ArrayList<>();
    private List<Long> removedIds = List.of();

    @BeforeEach
    void setUp() {
        service = new PmsProductAttributeServiceImpl();
        attributeMapper = dependency(service, "productAttributeMapper", PmsProductAttributeMapper.class);
        categoryMapper = dependency(service, "productAttributeCategoryMapper", PmsProductAttributeCategoryMapper.class);
        attributes.clear();
        categories.clear();
        categoryWrites.clear();
        removedIds = List.of();
        when(attributeMapper.selectByPrimaryKey(anyLong())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        when(categoryMapper.selectByPrimaryKey(anyLong())).thenAnswer(invocation -> categories.get(invocation.getArgument(0)));
        when(attributeMapper.deleteByExample(any())).thenAnswer(invocation -> {
            List<Long> requested = copyRequestedIds(invocation.getArgument(0));
            List<Long> removed = new ArrayList<>();
            for (Long id : requested) {
                if (attributes.containsKey(id) && !removed.contains(id)) {
                    attributes.remove(id);
                    removed.add(id);
                }
            }
            removedIds = List.copyOf(removed);
            return removed.size();
        });
        when(categoryMapper.updateByPrimaryKey(any())).thenAnswer(invocation -> {
            PmsProductAttributeCategory row = invocation.getArgument(0);
            categoryWrites.add(new CategoryCount(row.getId(), row.getAttributeCount(), row.getParamCount()));
            return 1;
        });
    }

    @Test
    void mixedCategoriesEachLoseOneSpecification() {
        PmsProductAttributeCategory category101 = category(101L, 2, 0);
        PmsProductAttributeCategory category202 = category(202L, 2, 0);
        attribute(11L, 101L, 0);
        attribute(22L, 202L, 0);

        int deleted = service.delete(List.of(11L, 22L));

        assertEquals(2, deleted);
        assertEquals(List.of(11L, 22L), removedIds);
        assertEquals(List.of(1, 1), List.of(category101.getAttributeCount(), category202.getAttributeCount()));
        assertEquals(1, persisted(101L).attributeCount());
        assertEquals(1, persisted(202L).attributeCount());
        assertEquals(0, persisted(101L).paramCount());
        assertEquals(0, persisted(202L).paramCount());
    }

    @Test
    void sameCategorySpecAndParamDecrementSeparateCounters() {
        PmsProductAttributeCategory category = category(101L, 2, 2);
        attribute(11L, 101L, 0);
        attribute(31L, 101L, 1);

        int deleted = service.delete(List.of(11L, 31L));

        assertEquals(2, deleted);
        assertEquals(List.of(11L, 31L), removedIds);
        assertEquals(1, category.getAttributeCount());
        assertEquals(1, category.getParamCount());
        assertEquals(1, persisted(101L).attributeCount());
        assertEquals(1, persisted(101L).paramCount());
    }

    @Test
    void duplicateIdCountedOnceAndMissingIdDoesNotDecrement() {
        PmsProductAttributeCategory category101 = category(101L, 2, 0);
        PmsProductAttributeCategory category202 = category(202L, 2, 0);
        attribute(11L, 101L, 0);
        attribute(22L, 202L, 0);

        int deleted = service.delete(List.of(11L, 11L, 999L, 22L));

        assertEquals(2, deleted);
        assertEquals(List.of(11L, 22L), removedIds);
        assertEquals(List.of(1, 1), List.of(category101.getAttributeCount(), category202.getAttributeCount()));
        assertEquals(1, persisted(101L).attributeCount());
        assertEquals(1, persisted(202L).attributeCount());
    }

    @Test
    void sameGroupDeleteReturnsActualDeletedRows() {
        PmsProductAttributeCategory category101 = category(101L, 2, 4);
        attribute(11L, 101L, 0);
        attribute(12L, 101L, 0);

        int deleted = service.delete(List.of(11L, 12L));

        assertEquals(2, deleted);
        assertEquals(List.of(11L, 12L), removedIds);
        assertEquals(0, category101.getAttributeCount());
        assertEquals(4, category101.getParamCount());
        assertEquals(0, persisted(101L).attributeCount());
        assertEquals(4, persisted(101L).paramCount());
    }

    @Test
    void sameGroupParamDeleteDecrementsOnlyParamCount() {
        PmsProductAttributeCategory category101 = category(101L, 3, 2);
        attribute(31L, 101L, 1);

        int deleted = service.delete(List.of(31L));

        assertEquals(1, deleted);
        assertEquals(List.of(31L), removedIds);
        assertEquals(3, category101.getAttributeCount());
        assertEquals(1, category101.getParamCount());
        assertEquals(3, persisted(101L).attributeCount());
        assertEquals(1, persisted(101L).paramCount());
    }

    @Test
    void attributeCountDoesNotDropBelowZero() {
        PmsProductAttributeCategory category101 = category(101L, 0, 5);
        attribute(11L, 101L, 0);

        int deleted = service.delete(List.of(11L));

        assertEquals(1, deleted);
        assertEquals(0, category101.getAttributeCount());
        assertEquals(5, category101.getParamCount());
        assertEquals(0, persisted(101L).attributeCount());
        assertEquals(5, persisted(101L).paramCount());
    }

    @Test
    void allMissingIdsReturnZeroWithoutDeleteOrCategoryUpdate() {
        assertEquals(0, service.delete(List.of(999L)));
        assertEquals(0, service.delete(List.of(999L, 999L)));
        verify(attributeMapper, never()).deleteByExample(any());
        verify(categoryMapper, never()).updateByPrimaryKey(any());
    }

    @Test
    void allMissingIdsOnMysqlLeaveCountsUnchanged() throws Exception {
        Mysql mysql = Mysql.openOrAbort();
        try {
            long categoryId = mysql.insertCategory();
            long attributeId = mysql.insertAttribute(categoryId);
            Counts beforeCounts = mysql.readCounts(categoryId);
            AttributeRow beforeAttribute = mysql.readAttribute(attributeId);
            List<Long> missing = mysql.absentIds();
            PmsProductAttributeServiceImpl real = new PmsProductAttributeServiceImpl();
            ReflectionTestUtils.setField(real, "productAttributeMapper", mysql.attributeMapper);
            ReflectionTestUtils.setField(real, "productAttributeCategoryMapper", mysql.categoryMapper);
            assertEquals(0, real.delete(missing));
            assertEquals(beforeCounts, mysql.readCounts(categoryId));
            assertEquals(beforeAttribute, mysql.readAttribute(attributeId));
        } finally {
            mysql.cleanup();
        }
    }

    @Test
    void paramCountDoesNotDropBelowZero() {
        PmsProductAttributeCategory category101 = category(101L, 4, 0);
        attribute(31L, 101L, 1);

        int deleted = service.delete(List.of(31L));

        assertEquals(1, deleted);
        assertEquals(4, category101.getAttributeCount());
        assertEquals(0, category101.getParamCount());
        assertEquals(4, persisted(101L).attributeCount());
        assertEquals(0, persisted(101L).paramCount());
    }

    private PmsProductAttributeCategory category(Long id, int attributeCount, int paramCount) {
        PmsProductAttributeCategory category = new PmsProductAttributeCategory();
        category.setId(id);
        category.setName("category-" + id);
        category.setAttributeCount(attributeCount);
        category.setParamCount(paramCount);
        categories.put(id, category);
        return category;
    }

    private void attribute(Long id, Long categoryId, int type) {
        PmsProductAttribute attribute = new PmsProductAttribute();
        attribute.setId(id);
        attribute.setProductAttributeCategoryId(categoryId);
        attribute.setType(type);
        attribute.setName("attribute-" + id);
        attributes.put(id, attribute);
    }

    private CategoryCount persisted(Long id) {
        CategoryCount found = null;
        for (CategoryCount write : categoryWrites) {
            if (id.equals(write.id())) {
                found = write;
            }
        }
        assertNotNull(found, "category " + id + " count was not persisted");
        return found;
    }

    private static List<Long> copyRequestedIds(PmsProductAttributeExample example) {
        List<Long> requested = new ArrayList<>();
        for (PmsProductAttributeExample.Criteria criteria : example.getOredCriteria()) {
            for (PmsProductAttributeExample.Criterion criterion : criteria.getAllCriteria()) {
                if (criterion.isListValue() && criterion.getValue() instanceof List<?> values) {
                    for (Object value : values) {
                        requested.add((Long) value);
                    }
                }
            }
        }
        return List.copyOf(requested);
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }

    private record CategoryCount(Long id, Integer attributeCount, Integer paramCount) {
    }

    private record Counts(int attributeCount, int paramCount) {
    }

    private record AttributeRow(long categoryId, int type, String name) {
    }

    private static final class Mysql {
        private final DataSource dataSource;
        private final PmsProductAttributeMapper attributeMapper;
        private final PmsProductAttributeCategoryMapper categoryMapper;
        private Long categoryId;
        private Long attributeId;

        private Mysql(DataSource dataSource, PmsProductAttributeMapper attributeMapper,
                      PmsProductAttributeCategoryMapper categoryMapper) {
            this.dataSource = dataSource;
            this.attributeMapper = attributeMapper;
            this.categoryMapper = categoryMapper;
        }

        private static Mysql openOrAbort() throws Exception {
            Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
            Path yml = null;
            for (int i = 0; i < 5 && dir != null; i++) {
                Path candidate = dir.resolve("SDC/environment/application-sdc.yml");
                if (Files.isRegularFile(candidate)) {
                    yml = candidate;
                    break;
                }
                dir = dir.getParent();
            }
            assumeTrue(yml != null, "阻塞: 找不到 application-sdc.yml，真实库删除未执行");
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new FileSystemResource(yml));
            Properties properties = yaml.getObject();
            String url = properties.getProperty("spring.datasource.url").replace("://localhost:", "://127.0.0.1:");
            assumeTrue(url.contains("127.0.0.1:13306"), "阻塞: 数据源不是 127.0.0.1:13306，真实库删除未执行");
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
            assumeTrue(connected, "阻塞: MySQL 127.0.0.1:13306 不可连接，全不存在 ID 的真实删除未执行"
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
            return new Mysql(dataSource, session.getMapper(PmsProductAttributeMapper.class),
                    session.getMapper(PmsProductAttributeCategoryMapper.class));
        }

        private long insertCategory() {
            PmsProductAttributeCategory category = new PmsProductAttributeCategory();
            category.setName("sdc-e303-" + UUID.randomUUID().toString().substring(0, 8));
            category.setAttributeCount(3);
            category.setParamCount(4);
            categoryMapper.insertSelective(category);
            categoryId = category.getId();
            return categoryId;
        }

        private long insertAttribute(long ownerId) {
            PmsProductAttribute attribute = new PmsProductAttribute();
            attribute.setProductAttributeCategoryId(ownerId);
            attribute.setName("sdc-e303-x-" + UUID.randomUUID().toString().substring(0, 8));
            attribute.setType(0);
            attributeMapper.insertSelective(attribute);
            attributeId = attribute.getId();
            return attributeId;
        }

        private List<Long> absentIds() throws SQLException {
            if (count(999L) == 0) {
                return List.of(999L, 999L);
            }
            long fallback = 9_000_000_000_999L;
            if (count(fallback) != 0) {
                throw new IllegalStateException("no absent attribute id");
            }
            return List.of(fallback, fallback);
        }

        private long count(long id) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "select count(*) from pms_product_attribute where id = ?")) {
                statement.setLong(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        }

        private Counts readCounts(long id) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "select attribute_count, param_count from pms_product_attribute_category where id = ?")) {
                statement.setLong(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("category " + id + " not visible");
                    }
                    return new Counts(rs.getInt(1), rs.getInt(2));
                }
            }
        }

        private AttributeRow readAttribute(long id) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "select product_attribute_category_id, type, name from pms_product_attribute where id = ?")) {
                statement.setLong(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("attribute " + id + " not visible");
                    }
                    return new AttributeRow(rs.getLong(1), rs.getInt(2), rs.getString(3));
                }
            }
        }

        private void cleanup() {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(true);
                deleteById(connection, "delete from pms_product_attribute where id = ?", attributeId);
                deleteById(connection, "delete from pms_product_attribute_category where id = ?", categoryId);
            } catch (SQLException ex) {
                throw new IllegalStateException("cleanup failed for attribute=" + attributeId + " category=" + categoryId, ex);
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
    }
}
