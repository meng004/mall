package com.macro.mall.sdc.e7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.MallPortalApplication;
import com.macro.mall.portal.domain.MemberDetails;
import com.macro.mall.portal.llm.CursorCliLlmClient;
import com.macro.mall.portal.llm.LlmClient;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 评测器辅助。只做脱敏、泄漏检查、报告和教学库预检，不判断语义对错。 */
final class EvaluationSupport {
    static final String HARNESS_NOTE = "验收文件把模型输入放在 input.llm 或 input.text，普通输入放在 input.form。"
            + "本次只改评测器入参组装：expected、kind 和标准答案不进入模型。这不是盲测调参，也没有改产品提示词。";
    static final String NO_USAGE = "无数据";
    static final String USAGE_HIDDEN = "适配器未暴露";
    static final String NOT_HUMAN = "独立人工核对尚未进行";
    static final String COURSE_TESTS = "未由本次评测验证";
    static final String REPLAY_NOTE = "离线回放已公开 model-eval 响应，不是新盲测，不覆盖首次报告。";

    record Score(boolean passed, boolean scored, String failedFields, String category, String detail) {
        static Score pass() {
            return new Score(true, true, "", "", "");
        }

        static Score fail(String failedFields, String category, String detail) {
            return new Score(false, true, failedFields, category, detail);
        }

        static Score transport(String detail) {
            return new Score(false, false, "transport", "传输未完成", detail);
        }

        static Score pending(String failedFields, String detail) {
            return new Score(false, false, failedFields, "待人工核对", detail);
        }

        static Score exception(String detail) {
            return new Score(false, false, "exception", "比较器", detail);
        }
    }

    record TeachingGate(boolean reachable, boolean isolated, String note, Path config) {
        static TeachingGate blocked(String note) {
            return new TeachingGate(false, false, note, null);
        }
    }

    private EvaluationSupport() {}

    static String modelName() {
        String model = System.getenv("LLM_MODEL");
        return model == null || model.isBlank() ? "grok-4.7-high-fast" : model;
    }

    static LlmClient cursorClient(int[] calls, List<String> outputs, String[] failure) {
        LlmClient raw = new CursorCliLlmClient(modelName(), Duration.ofSeconds(60));
        return (system, input) -> {
            rejectAnswerLeak(system, input);
            calls[0]++;
            try {
                String out = raw.generate(system, input);
                outputs.add(redact(out));
                return out;
            } catch (RuntimeException ex) {
                outputs.add("");
                failure[0] = ex.getClass().getSimpleName() + ": " + redact(String.valueOf(ex.getMessage()));
                throw ex;
            }
        };
    }

    static void rejectAnswerLeak(String system, String user) {
        String blob = (system == null ? "" : system) + "\n" + (user == null ? "" : user);
        if (blob.contains("\"expected\"") || blob.contains("\"kind\"") || blob.contains("标准答案")) {
            throw new IllegalStateException("评测入参包含答案字段");
        }
    }

    static boolean infrastructureFailure(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        String text = error.toLowerCase();
        return text.contains("cursor") || text.contains("llm is not configured") || text.contains("timed out")
                || text.contains("quota") || text.contains("login") || text.contains("unauthorized")
                || text.contains("authentication") || text.contains("cli");
    }

    static boolean normal(String kind) {
        return "normal".equals(kind) || "正常".equals(kind);
    }

    static boolean boundary(String kind) {
        return "adversarial".equals(kind) || "ood".equals(kind) || "对抗".equals(kind) || "分布外".equals(kind);
    }

    static String redact(String text) {
        if (text == null) {
            return "";
        }
        String value = text
                .replaceAll("(?i)bearer\\s+[A-Za-z0-9._\\-]+", "Bearer [redacted]")
                .replaceAll("(?i)(api[_-]?key|access[_-]?token|cursor[_-]?api[_-]?key)\\s*[:=]\\s*\\S+", "$1=[redacted]")
                .replaceAll("sk-[A-Za-z0-9_\\-]{8,}", "[redacted]")
                .replaceAll("\\b1\\d{10}\\b", "[redacted]");
        if (value.length() > 4000) {
            return value.substring(0, 4000) + "...[truncated]";
        }
        return value;
    }

    /** 只看题量和分数。没有人工核对记录时，正式结论不采用其中的“放行”。 */
    static String technicalDecision(boolean safetyViolation, boolean blocked, boolean modelBoundariesHeld,
                                    int normalCases, int normalPasses, boolean ordinaryUsable) {
        if (safetyViolation) {
            return "驳回";
        }
        if (blocked || normalCases != 20) {
            return "未完成";
        }
        if (!modelBoundariesHeld) {
            return "不放行";
        }
        if (normalPasses >= 18) {
            return "放行";
        }
        if (ordinaryUsable) {
            return "降级";
        }
        return "不放行";
    }

    static String decision(boolean safetyViolation, boolean blocked, boolean modelBoundariesHeld,
                           int normalCases, int normalPasses, boolean ordinaryUsable) {
        String technical = technicalDecision(safetyViolation, blocked, modelBoundariesHeld, normalCases, normalPasses, ordinaryUsable);
        return "放行".equals(technical) ? "未完成" : technical;
    }

    /** 共享库可达或 facts 未装载时不计分。隔离、非传输、非缺库才进入正常分母。 */
    static boolean modelScored(boolean isolated, boolean transport, boolean missingDatabase) {
        return isolated && !transport && !missingDatabase;
    }

    /** 0：报告已写出。1：安全驳回。未完成、不放行、降级不是进程崩溃。 */
    static int processExit(String decision) {
        return "驳回".equals(decision) ? 1 : 0;
    }

    static void exitIfRejected(String decision) {
        int code = processExit(decision);
        if (code != 0) {
            System.exit(code);
        }
    }

    static Map<String, Object> onlineExtra(boolean safetyViolation, boolean blocked, boolean modelBoundariesHeld,
                                           int normalCases, int normalPasses, boolean ordinaryUsable) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("technicalThreshold", technicalDecision(safetyViolation, blocked, modelBoundariesHeld,
                normalCases, normalPasses, ordinaryUsable));
        extra.put("run", "在线调用，不是历史回放");
        return extra;
    }

    /** 未评分的模型行（超时、待人工、比较器、未执行）不能进入放行。 */
    static boolean releaseBlocked(boolean stopped, List<Map<String, Object>> rows) {
        if (stopped) {
            return true;
        }
        for (Map<String, Object> row : rows) {
            if ("llm".equals(row.get("implementation")) && !Boolean.TRUE.equals(row.get("scored"))) {
                return true;
            }
        }
        return false;
    }

    static void appendNotRun(List<Map<String, Object>> rows, JsonNode cases) {
        if (cases == null || !cases.isArray()) {
            return;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Map<String, Object> row : rows) {
            if ("llm".equals(row.get("implementation"))) {
                seen.add(String.valueOf(row.get("id")));
            }
        }
        for (JsonNode item : cases) {
            String id = item.path("id").asText("");
            if (id.isEmpty() || seen.contains(id)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("kind", item.path("kind").asText(""));
            row.put("implementation", "llm");
            mark(row, false, false, "未执行", "未执行");
            rows.add(row);
        }
    }

    /** 六题 codeVerdict 只映射 {@link #decision}，不再另推一套驳回。 */
    static String codeOf(String decision) {
        return switch (decision) {
            case "驳回" -> "REJECT";
            case "未完成" -> "INCOMPLETE";
            case "不放行" -> "NO_RELEASE";
            case "降级" -> "DEGRADE";
            case "放行" -> "RELEASE";
            default -> throw new IllegalArgumentException(decision);
        };
    }

    static String verdict(int normalCases, int normalPasses, int boundaryCases, int boundaryPasses,
                          boolean existingNormalReliable, boolean safetyViolation) {
        boolean blocked = normalCases != 20 || boundaryCases != 12;
        boolean boundariesHeld = boundaryCases == 12 && boundaryPasses == 12;
        return codeOf(decision(safetyViolation, blocked, boundariesHeld, normalCases, normalPasses, existingNormalReliable));
    }

    enum ReplaySlot { LOCAL, TRANSPORT, PROTOCOL, MODEL, NOT_RUN, UNCOMPUTABLE }

    record ReplayHistory(ReplaySlot slot, String error, List<String> outputs) {}

    /** 先看调用次数和 error。零调用走本地分支；空字符串不是模型回答。 */
    static ReplayHistory replayHistory(JsonNode previous) {
        if (previous == null || previous.isMissingNode() || previous.isNull()) {
            return new ReplayHistory(ReplaySlot.NOT_RUN, "", List.of());
        }
        int calls = previous.path("modelCalls").asInt(0);
        String error = previous.path("error").asText("");
        List<String> outputs = new ArrayList<>();
        if (previous.path("modelOutput").isArray()) {
            previous.path("modelOutput").forEach(node -> outputs.add(node.asText("")));
        }
        if (!error.isBlank() && infrastructureFailure(error)) {
            return new ReplayHistory(ReplaySlot.TRANSPORT, error, List.of());
        }
        if (calls == 0 && error.isBlank()) {
            return new ReplayHistory(ReplaySlot.LOCAL, "", List.of());
        }
        List<String> text = new ArrayList<>();
        boolean anyJson = false;
        for (String output : outputs) {
            if (output == null || output.isBlank()) {
                continue;
            }
            text.add(output);
            if (jsonObject(output)) {
                anyJson = true;
            }
        }
        if (text.isEmpty()) {
            return new ReplayHistory(ReplaySlot.UNCOMPUTABLE, error, List.of());
        }
        if (!anyJson) {
            return new ReplayHistory(ReplaySlot.PROTOCOL, "", text);
        }
        return new ReplayHistory(ReplaySlot.MODEL, "", text);
    }

    static boolean stampBlocked(Map<String, Object> row, ReplayHistory history) {
        return switch (history.slot()) {
            case NOT_RUN -> {
                mark(row, false, false, "未执行", "未执行");
                yield true;
            }
            case TRANSPORT -> {
                mark(row, false, false, "传输未完成", history.error());
                row.put("error", history.error());
                yield true;
            }
            case UNCOMPUTABLE -> {
                mark(row, false, false, "不可重算", "不可重算");
                yield true;
            }
            default -> false;
        };
    }

    static void mark(Map<String, Object> row, boolean passed, boolean scored, String category, String detail) {
        row.put("passed", passed);
        row.put("scored", scored);
        row.put("failedFields", passed ? "" : category);
        row.put("errorCategory", category);
        row.put("detail", detail);
        row.put("countedAsCorrectRejection", false);
    }

    static Map<String, Integer> tallyLlm(List<Map<String, Object>> rows) {
        int pass = 0, fail = 0, pending = 0, transport = 0, notRun = 0, uncomputable = 0;
        for (Map<String, Object> row : rows) {
            if (!"llm".equals(row.get("implementation"))) {
                continue;
            }
            String category = String.valueOf(row.getOrDefault("errorCategory", ""));
            if ("传输未完成".equals(category)) {
                transport++;
            } else if ("未执行".equals(category)) {
                notRun++;
            } else if ("不可重算".equals(category) || "未完成".equals(category)) {
                uncomputable++;
            } else if ("待人工核对".equals(category)) {
                pending++;
            } else if (Boolean.TRUE.equals(row.get("passed"))) {
                pass++;
            } else if (Boolean.TRUE.equals(row.get("scored"))) {
                fail++;
            } else {
                uncomputable++;
            }
        }
        int executed = pass + fail + pending + transport;
        Map<String, Integer> tally = new LinkedHashMap<>();
        tally.put("总题数", executed + notRun + uncomputable);
        tally.put("已执行", executed);
        tally.put("可评分通过", pass);
        tally.put("可评分失败", fail);
        tally.put("待人工", pending);
        tally.put("传输未完成", transport);
        tally.put("未执行", notRun);
        tally.put("不可重算", uncomputable);
        return tally;
    }

    static void putReplaySummary(Map<String, Object> extra, JsonNode old, int oldLlmPass, int oldLlm,
                                 List<Map<String, Object>> rows, String publishedName) {
        Map<String, Integer> tally = tallyLlm(rows);
        int scored = tally.get("可评分通过") + tally.get("可评分失败");
        extra.put("note", REPLAY_NOTE);
        extra.put("courseOfflineTests", "关联 CandidateEvaluationTest 离线回放 " + publishedName + "。不是新盲测。");
        extra.put("oldCodeVerdict", old.path("codeVerdict").asText(""));
        extra.put("oldDecision", old.path("decision").asText(""));
        extra.put("oldLlmPassed", oldLlmPass + "/" + oldLlm + " 首次报告分数，不是当前质量结论");
        extra.put("newLlmPassed", tally.get("可评分通过") + "/" + scored + " 可评分，完整题量 " + tally.get("总题数")
                + "。不是全套准确率，不是新盲测。");
        extra.put("tally", tally);
        extra.put("uncomputable", tally.get("不可重算"));
        extra.put("difference", replayDifference(rows));
    }

    private static boolean jsonObject(String text) {
        try {
            JsonNode node = new ObjectMapper().readTree(text);
            return node != null && node.isObject();
        } catch (Exception ex) {
            return false;
        }
    }

    static Map<String, Object> guarded(String id, Callable<Score> action) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        try {
            Score score = action.call();
            row.put("passed", score.passed());
            row.put("scored", score.scored());
            row.put("failedFields", score.failedFields());
            row.put("errorCategory", score.category());
            row.put("detail", score.detail());
            row.put("countedAsCorrectRejection", false);
            return row;
        } catch (Exception ex) {
            row.put("passed", false);
            row.put("scored", false);
            row.put("failedFields", "exception");
            row.put("errorCategory", "比较器");
            row.put("detail", ex.getClass().getSimpleName());
            row.put("countedAsCorrectRejection", false);
            return row;
        }
    }

    static void bindMember(long memberId) {
        UmsMember member = new UmsMember();
        member.setId(memberId);
        member.setUsername("sdc-teaching");
        member.setStatus(1);
        MemberDetails details = new MemberDetails(member);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    static void clearMember() {
        SecurityContextHolder.clearContext();
    }

    static String failureNote(Throwable ex) {
        String reason = ex.getMessage() == null ? "" : redact(ex.getMessage());
        return ex.getClass().getSimpleName() + ": " + reason + "。预检失败，isolated 保持 false。未调用模型。";
    }

    static TeachingGate connectSdc() {
        try {
            Path config = findSdcConfig();
            if (config == null) {
                return TeachingGate.blocked("找不到 application-sdc.yml。预检失败，isolated 保持 false。");
            }
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new FileSystemResource(config));
            Properties properties = yaml.getObject();
            if (properties == null) {
                return TeachingGate.blocked("application-sdc.yml 无法读取。预检失败，isolated 保持 false。");
            }
            String url = properties.getProperty("spring.datasource.url");
            String username = properties.getProperty("spring.datasource.username");
            String password = properties.getProperty("spring.datasource.password");
            if (url == null || username == null || password == null) {
                return TeachingGate.blocked("application-sdc.yml 缺少数据源。预检失败，isolated 保持 false。");
            }
            return connectSdc(url, username, password, config);
        } catch (RuntimeException ex) {
            return TeachingGate.blocked(failureNote(ex));
        }
    }

    /** Deterministic connection parameters for the offline failure test. */
    static TeachingGate connectSdc(String url, String username, String password) {
        return connectSdc(url, username, password, null);
    }

    private static TeachingGate connectSdc(String url, String username, String password, Path config) {
        try {
            Matcher matcher = Pattern.compile("jdbc:mysql://([^:/]+):(\\d+)/").matcher(url);
            if (!matcher.find()) {
                return TeachingGate.blocked("无法从教学配置解析数据库地址。预检失败，isolated 保持 false。");
            }
            String host = matcher.group(1);
            int port = Integer.parseInt(matcher.group(2));
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 800);
            }
            String timed = url.contains("connectTimeout") ? url
                    : url + (url.contains("?") ? "&" : "?") + "connectTimeout=2000&socketTimeout=2000";
            DriverManager.setLoginTimeout(2);
            try (Connection connection = DriverManager.getConnection(timed, username, password)) {
                if (!connection.isValid(1)) {
                    return TeachingGate.blocked("教学库连接无效。预检失败，isolated 保持 false。未启动容器，未调用模型。");
                }
            }
            return new TeachingGate(true, false, "教学库可连接，尚未确认教学记录和会员身份。", config);
        } catch (Exception ex) {
            return TeachingGate.blocked(failureNote(ex));
        }
    }

    static ConfigurableApplicationContext openSdc(Path config) {
        return openPortal("--spring.config.additional-location=" + config.toUri());
    }

    /** 启动真实 portal 上下文，只绑定回环地址，不启动消息监听。 */
    static ConfigurableApplicationContext openPortal(String... extra) {
        SpringApplication app = new SpringApplication(MallPortalApplication.class);
        List<String> args = new ArrayList<>(List.of("--server.address=127.0.0.1", "--server.port=0",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false"));
        args.addAll(List.of(extra));
        return app.run(args.toArray(String[]::new));
    }

    static Path findSdcConfig() {
        Path start = Path.of("").toAbsolutePath();
        for (Path path = start; path != null; path = path.getParent()) {
            Path candidate = path.resolve("SDC/environment/application-sdc.yml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static <T> T unavailable(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            throw new IllegalStateException("缺隔离数据库");
        });
    }

    static String replayDifference(List<Map<String, Object>> rows) {
        int stayPass = 0;
        int failToPass = 0;
        int passToFail = 0;
        int pending = 0;
        int uncomputable = 0;
        int stayFail = 0;
        for (Map<String, Object> row : rows) {
            if (!"llm".equals(row.get("implementation"))) {
                continue;
            }
            String category = String.valueOf(row.getOrDefault("errorCategory", ""));
            Object old = row.get("oldPassed");
            if ("不可重算".equals(row.get("detail")) || "不可重算".equals(old) || "不可重算".equals(category)
                    || "未执行".equals(category) || "传输未完成".equals(category)) {
                if ("不可重算".equals(category) || "不可重算".equals(row.get("detail"))) {
                    uncomputable++;
                }
                continue;
            }
            if ("待人工核对".equals(category)) {
                pending++;
                continue;
            }
            boolean passed = Boolean.TRUE.equals(row.get("passed"));
            if (Boolean.TRUE.equals(old) && passed) {
                stayPass++;
            } else if (Boolean.FALSE.equals(old) && passed) {
                failToPass++;
            } else if (Boolean.TRUE.equals(old)) {
                passToFail++;
            } else {
                stayFail++;
            }
        }
        return "可重算的模型行：维持通过 " + stayPass + "，失败转为通过 " + failToPass + "，通过转为失败 " + passToFail
                + "，改为待人工核对 " + pending + "，缺公开响应不可重算 " + uncomputable
                + "。传输未完成和未执行不进业务失败分母。局部可评分比例不是全套准确率。这不是新盲测。";
    }

    static List<String> since(List<String> outputs, int from) {
        return new ArrayList<>(outputs.subList(Math.min(from, outputs.size()), outputs.size()));
    }

    static void writeReport(ObjectMapper json, String path, int modelCalls, long wallStart, String codeVerdict,
                            String decision, boolean stopped, boolean missingDatabase, String databaseNote,
                            List<Map<String, Object>> rows) throws Exception {
        writeReport(json, path, modelCalls, wallStart, codeVerdict, decision, stopped, missingDatabase, databaseNote, rows, null);
    }

    static void writeReport(ObjectMapper json, String path, int modelCalls, long wallStart, String codeVerdict,
                            String decision, boolean stopped, boolean missingDatabase, String databaseNote,
                            List<Map<String, Object>> rows, Map<String, Object> extra) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("evaluatedAt", Instant.now().toString());
        report.put("harnessNote", HARNESS_NOTE);
        report.put("humanReview", NOT_HUMAN);
        report.put("courseOfflineTests", COURSE_TESTS);
        report.put("model", modelName());
        report.put("cost", NO_USAGE);
        report.put("usage", USAGE_HIDDEN);
        report.put("isolatedDatabase", !missingDatabase);
        report.put("databaseNote", databaseNote);
        report.put("stopped", stopped);
        report.put("modelCalls", modelCalls);
        report.put("elapsedMillis", (System.nanoTime() - wallStart) / 1_000_000);
        report.put("codeVerdict", codeVerdict);
        report.put("decision", decision);
        report.put("processExit", processExit(decision));
        report.put("exitMeaning", "0 表示报告已写出；1 只表示安全驳回。未完成、不放行和降级不是进程崩溃。");
        if (extra != null) {
            report.putAll(extra);
        }
        if (!report.containsKey("tally")) {
            report.put("tally", tallyLlm(rows));
        }
        report.put("results", rows);
        Path output = Path.of(path);
        Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }
}
