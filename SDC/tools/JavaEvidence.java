import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;

/** Java 17 source-file launcher: java SDC/tools/JavaEvidence.java [--original]. */
class JavaEvidence {
    private static final String REVISION = "dcaa93b3150352e5044708d7211b5bed0af4509f";
    private static final String JACOCO = "org.jacoco:jacoco-maven-plugin:0.8.13:";

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("mall-portal"))) root = root.getParent();
        if (root == null) throw new IllegalArgumentException("Run from mall or a directory inside mall");
        if (args.length > 1 || args.length == 1 && !args[0].equals("--original")) {
            throw new IllegalArgumentException("Usage: java SDC/tools/JavaEvidence.java [--original]");
        }
        Path evidence = root.resolve("SDC/evidence/java-migration");
        Files.createDirectories(evidence);
        if (args.length == 0) {
            Path exec = root.resolve("SDC/target/jacoco.exec");
            Files.deleteIfExists(exec);
            int result = run(root, evidence.resolve("current.log"), "SDC/E3,SDC/E4,SDC/E6",
                    "OrderIntegrationTest,PromotionTest,StockWarningTest", exec);
            if (result != 0) throw new IllegalStateException("Current Java tests failed; see current.log");
            List<String> command = new ArrayList<>(List.of("rtk", "proxy", "mvn", "-B", "-o", "-pl", "mall-portal",
                    "-Djacoco.dataFile=" + exec, JACOCO + "report"));
            int report = execute(root, command, evidence.resolve("coverage.log"));
            if (report != 0) throw new IllegalStateException("Coverage report failed");
            Files.copy(root.resolve("mall-portal/target/site/jacoco/jacoco.xml"), evidence.resolve("jacoco.xml"),
                    StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Current tests passed; coverage and logs: " + evidence);
            return;
        }
        Path work = Files.createTempDirectory("sdc-original-");
        try {
            Files.copy(root.resolve("pom.xml"), work.resolve("pom.xml"));
            try (var modules = Files.list(root)) {
                for (Path module : modules.filter(p -> p.getFileName().toString().startsWith("mall-"))
                        .filter(p -> Files.exists(p.resolve("pom.xml"))).toList()) {
                    Path destination = work.resolve(module.getFileName());
                    Files.createDirectories(destination);
                    Files.copy(module.resolve("pom.xml"), destination.resolve("pom.xml"));
                    copyTree(module.resolve("src"), destination.resolve("src"));
                }
            }
            Files.createDirectories(work.resolve("SDC"));
            Files.copy(root.resolve("SDC/pom.xml"), work.resolve("SDC/pom.xml"));
            for (int i = 1; i <= 7; i++) {
                Path target = work.resolve("SDC/E" + i);
                Files.createDirectories(target);
                Files.copy(root.resolve("SDC/E" + i + "/pom.xml"), target.resolve("pom.xml"));
                if (i == 3 || i == 4 || i == 6) copyTree(root.resolve("SDC/E" + i + "/src"), target.resolve("src"));
            }
            for (String name : List.of("OmsPortalOrderServiceImpl", "OmsPromotionServiceImpl")) {
                String path = "mall-portal/src/main/java/com/macro/mall/portal/service/impl/" + name + ".java";
                Process git = new ProcessBuilder("rtk", "proxy", "git", "show", REVISION + ":" + path)
                        .directory(root.toFile()).redirectOutput(work.resolve(path).toFile())
                        .redirectError(ProcessBuilder.Redirect.INHERIT).start();
                if (git.waitFor() != 0) throw new IOException("Cannot reconstruct " + name);
            }
            int promotion = run(work, evidence.resolve("original-promotion.log"), "SDC/E4", "PromotionTest", null);
            int order = run(work, evidence.resolve("original-order.log"), "SDC/E3,SDC/E6",
                    "OrderIntegrationTest,StockWarningTest", null);
            if (promotion != 0 || order == 0
                    || !expectedFailures(work.resolve("SDC/E3/target/surefire-reports"), 2)
                    || !expectedFailures(work.resolve("SDC/E6/target/surefire-reports"), 1)) {
                throw new IllegalStateException("Original comparison did not show expected assertion failures; inspect logs");
            }
            System.out.println("Original promotion passed; original E3/E6 failed the expected assertions. Logs: " + evidence);
        } finally {
            try (var paths = Files.walk(work)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static int run(Path root, Path log, String modules, String tests, Path exec) throws Exception {
        List<String> command = new ArrayList<>(List.of("rtk", "proxy", "mvn", "-B", "-o", "-fae",
                "-pl", modules, "-am", "-DskipTests=false", "-Dtest=" + tests,
                "-Dsurefire.failIfNoSpecifiedTests=false"));
        if (exec != null) command.addAll(List.of("-Djacoco.destFile=" + exec, JACOCO + "prepare-agent"));
        command.add("test");
        return execute(root, command, log);
    }

    private static int execute(Path root, List<String> command, Path log) throws Exception {
        Files.writeString(log, "Working directory: " + root + "\nReference revision: " + REVISION
                + "\nCommand: " + String.join(" ", command) + "\n");
        int code = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start().waitFor();
        System.out.println(log.getFileName() + " exit=" + code);
        return code;
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) return;
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination);
            }
        }
    }

    private static boolean expectedFailures(Path directory, int expected) throws Exception {
        int failures = 0;
        int errors = 0;
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith("TEST-")
                    && p.toString().endsWith(".xml")).toList()) {
                var suite = factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
                failures += Integer.parseInt(suite.getAttribute("failures"));
                errors += Integer.parseInt(suite.getAttribute("errors"));
            }
        }
        return failures == expected && errors == 0;
    }
}
