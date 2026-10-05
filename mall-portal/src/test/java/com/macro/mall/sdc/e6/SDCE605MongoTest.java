package com.macro.mall.sdc.e6;

import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.MemberProductCollection;
import com.macro.mall.portal.repository.MemberProductCollectionRepository;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.MemberCollectionServiceImpl;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SDCE605MongoTest {
    @Test
    void isolatedMongoFiltersLiteralNameBeforePaging() throws Exception {
        MongoSettings settings = MongoSettings.read();
        Assumptions.assumeTrue(settings != null, "未找到教师隔离 Mongo 配置，本项中止，不是通过");
        MongoClient client = MongoClients.create(settings.clientSettings());
        List<String> inserted = new ArrayList<>();
        try {
            try {
                client.getDatabase(settings.database()).runCommand(new Document("ping", 1));
            } catch (RuntimeException ex) {
                Assumptions.abort("隔离 Mongo 未连接，本项中止，不是通过");
            }
            MemberProductCollectionRepository repository = new MongoRepositoryFactory(
                    new MongoTemplate(client, settings.database()))
                    .getRepository(MemberProductCollectionRepository.class);
            MemberCollectionServiceImpl service = new MemberCollectionServiceImpl();
            UmsMemberService memberService = mock(UmsMemberService.class,
                    org.mockito.Mockito.withSettings().mockMaker("mock-maker-subclass"));
            UmsMember member = new UmsMember();
            member.setId(101L);
            when(memberService.getCurrentMember()).thenReturn(member);
            ReflectionTestUtils.setField(service, "memberService", memberService);
            ReflectionTestUtils.setField(service, "productCollectionRepository", repository);

            String token = "e605" + UUID.randomUUID().toString().replace("-", "");
            String keyword = token + ".";
            String matchA = save(repository, inserted, "sdc-e605-a-" + token, 101L, "a" + keyword);
            String matchB = save(repository, inserted, "sdc-e605-b-" + token, 101L, "b" + keyword);
            String matchC = save(repository, inserted, "sdc-e605-c-" + token, 101L, "c" + keyword);
            String sibling = save(repository, inserted, "sdc-e605-sibling-" + token, 101L, "pre" + token + "Xend");
            String star = save(repository, inserted, "sdc-e605-star-" + token, 101L, "star*" + token);
            String plain = save(repository, inserted, "sdc-e605-plain-" + token, 101L, "plain" + token);
            String other = save(repository, inserted, "sdc-e605-other-" + token, 202L, "a" + keyword);
            String upper = save(repository, inserted, "sdc-e605-upper-" + token, 101L, "A" + keyword.toUpperCase());
            String quoteEnd = save(repository, inserted, "sdc-e605-qe-" + token, 101L, "x\\E." + token);
            save(repository, inserted, "sdc-e605-qeany-" + token, 101L, "x\\Ea" + token);
            String noName = save(repository, inserted, "sdc-e605-noname-" + token, 101L, null);

            assertEquals(10, service.list(1, 1, "").getTotalElements());
            assertEquals(10, service.list(1, 1, null).getTotalElements());

            Page<MemberProductCollection> page1 = service.list(1, 2, keyword);
            Page<MemberProductCollection> page2 = service.list(2, 2, keyword);
            assertEquals(3, page1.getTotalElements());
            assertEquals(3, page2.getTotalElements());
            assertEquals(2, page1.getContent().size());
            assertEquals(1, page2.getContent().size());
            List<String> paged = new ArrayList<>();
            page1.getContent().forEach(row -> paged.add(row.getId()));
            page2.getContent().forEach(row -> paged.add(row.getId()));
            assertEquals(List.of(matchA, matchB, matchC).stream().sorted().toList(), paged.stream().sorted().toList());
            assertFalse(paged.contains(other));
            assertFalse(paged.contains(sibling));
            assertFalse(paged.contains(star));
            assertFalse(paged.contains(plain));
            assertFalse(paged.contains(upper));
            assertFalse(paged.contains(noName));

            Page<MemberProductCollection> dots = service.list(1, 5, ".");
            assertEquals(5, dots.getTotalElements());
            assertFalse(dots.getContent().stream().anyMatch(row -> sibling.equals(row.getId()) || other.equals(row.getId())));
            Page<MemberProductCollection> stars = service.list(1, 5, "*");
            assertEquals(1, stars.getTotalElements());
            assertEquals(star, stars.getContent().get(0).getId());
            Page<MemberProductCollection> quoteEnds = service.list(1, 5, "\\E.");
            assertEquals(1, quoteEnds.getTotalElements());
            assertEquals(quoteEnd, quoteEnds.getContent().get(0).getId());
        } finally {
            if (!inserted.isEmpty()) {
                MemberProductCollectionRepository repository = new MongoRepositoryFactory(
                        new MongoTemplate(client, settings.database()))
                        .getRepository(MemberProductCollectionRepository.class);
                for (String id : inserted) {
                    repository.deleteById(id);
                }
                client.getDatabase(settings.database()).drop();
            }
            client.close();
        }
    }

    private static String save(MemberProductCollectionRepository repository, List<String> inserted,
                               String id, long memberId, String name) {
        MemberProductCollection row = new MemberProductCollection();
        row.setId(id);
        row.setMemberId(memberId);
        row.setProductId(7L);
        row.setProductName(name);
        repository.save(row);
        inserted.add(id);
        return id;
    }

    private record MongoSettings(String host, int port, String database, String username, String password) {
        MongoClientSettings clientSettings() {
            String userInfo = username == null ? "" : username + ":" + password + "@";
            ConnectionString connection = new ConnectionString(
                    "mongodb://" + userInfo + host + ":" + port + "/" + database);
            return MongoClientSettings.builder()
                    .applyConnectionString(connection)
                    .applyToClusterSettings(builder -> builder.serverSelectionTimeout(1500, TimeUnit.MILLISECONDS))
                    .build();
        }

        static MongoSettings read() throws Exception {
            Path file = findConfig();
            if (file == null) {
                return null;
            }
            String host = null;
            Integer port = null;
            String database = null;
            String username = null;
            String password = null;
            boolean inMongo = false;
            int mongoIndent = -1;
            for (String raw : Files.readAllLines(file)) {
                String trimmed = raw.trim();
                int indent = trimmed.isEmpty() ? raw.length() : raw.indexOf(trimmed);
                if (trimmed.equals("mongodb:")) {
                    inMongo = true;
                    mongoIndent = indent;
                    host = null;
                    port = null;
                    database = null;
                    username = null;
                    password = null;
                    continue;
                }
                if (!inMongo || trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (indent <= mongoIndent) {
                    break;
                }
                int colon = trimmed.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = trimmed.substring(0, colon).trim();
                String value = trimmed.substring(colon + 1).trim();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                switch (key) {
                    case "host" -> host = value;
                    case "port" -> port = Integer.valueOf(value);
                    case "database" -> database = value;
                    case "username" -> username = value;
                    case "password" -> password = value;
                    default -> {
                    }
                }
            }
            if (host == null || port == null || database == null) {
                return null;
            }
            return new MongoSettings(host, port, "sdc_review_" + UUID.randomUUID().toString().replace("-", ""), username, password);
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
}
