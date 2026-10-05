package com.macro.mall.sdc.e6;

import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.MemberReadHistory;
import com.macro.mall.portal.repository.MemberReadHistoryRepository;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.MemberReadHistoryServiceImpl;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SDCE604MongoTest {
    @Test
    void isolatedMongoDeletesOnlyEarlierRowsForTheMember() throws Exception {
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
            MemberReadHistoryRepository repository = new MongoRepositoryFactory(new MongoTemplate(client, settings.database()))
                    .getRepository(MemberReadHistoryRepository.class);
            MemberReadHistoryServiceImpl service = new MemberReadHistoryServiceImpl();
            UmsMemberService memberService = mock(UmsMemberService.class,
                    org.mockito.Mockito.withSettings().mockMaker("mock-maker-subclass"));
            UmsMember member = new UmsMember();
            member.setId(101L);
            when(memberService.getCurrentMember()).thenReturn(member);
            ReflectionTestUtils.setField(service, "memberService", memberService);
            ReflectionTestUtils.setField(service, "memberReadHistoryRepository", repository);

            Instant cutoff = Instant.parse("2026-10-01T00:00:00Z");
            String token = UUID.randomUUID().toString().replace("-", "");
            String earlyId = save(repository, inserted, "sdc-e604-early-" + token, 101L, cutoff.minusMillis(1));
            String equalTimeId = save(repository, inserted, "sdc-e604-equal-" + token, 101L, cutoff);
            String laterId = save(repository, inserted, "sdc-e604-later-" + token, 101L, cutoff.plusMillis(1));
            String otherMemberId = save(repository, inserted, "sdc-e604-other-" + token, 202L, cutoff.minusMillis(1));

            long deletedCount = service.clearBefore(cutoff);
            List<String> remainingIds = new ArrayList<>();
            for (String id : List.of(equalTimeId, laterId, otherMemberId)) {
                if (repository.findById(id).isPresent()) {
                    remainingIds.add(id);
                }
            }

            assertEquals(1L, deletedCount);
            assertTrue(repository.findById(earlyId).isEmpty());
            assertTrue(remainingIds.containsAll(List.of(equalTimeId, laterId, otherMemberId)));
            assertEquals(0L, service.clearBefore(cutoff));
        } finally {
            if (!inserted.isEmpty()) {
                MemberReadHistoryRepository repository = new MongoRepositoryFactory(new MongoTemplate(client, settings.database()))
                        .getRepository(MemberReadHistoryRepository.class);
                for (String id : inserted) {
                    repository.deleteById(id);
                }
            }
            client.close();
        }
    }

    private static String save(MemberReadHistoryRepository repository, List<String> inserted,
                               String id, long memberId, Instant createTime) {
        MemberReadHistory row = new MemberReadHistory();
        row.setId(id);
        row.setMemberId(memberId);
        row.setProductId(7L);
        row.setCreateTime(Date.from(createTime));
        repository.save(row);
        inserted.add(id);
        return id;
    }

    private record MongoSettings(String host, int port, String database, String username, String password) {
        MongoClientSettings clientSettings() {
            String userInfo = username == null ? "" : username + ":" + password + "@";
            return MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString("mongodb://" + userInfo + host + ":" + port + "/" + database))
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
