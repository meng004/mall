package com.macro.mall.sdc.e1;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Read-only source observations, not execution or semantic Java analysis. */
public final class MallFacts {
    public static final String[] MODULES = {"mall-common", "mall-mbg", "mall-security", "mall-demo", "mall-admin", "mall-search", "mall-portal"};
    public record Edge(String from, String to) {}

    public static Path mallRoot() {
        return findMallRoot(Path.of(System.getProperty("mall.root", System.getProperty("user.dir"))));
    }

    public static Path findMallRoot(Path start) {
        Path absolute = start.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) throw new IllegalArgumentException("Not a directory: " + absolute);
        for (Path path = absolute; path != null; path = path.getParent()) {
            if (isMallRoot(path)) return path;
            if (isMallRoot(path.resolve("mall"))) return path.resolve("mall");
        }
        throw new IllegalArgumentException("Cannot locate mall; set -Dmall.root to the mall directory or an SDC module");
    }

    private static boolean isMallRoot(Path path) {
        return Files.isRegularFile(path.resolve("pom.xml")) && Files.isDirectory(path.resolve("mall-portal"));
    }

    public static Document readXml(Path path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try (var input = Files.newInputStream(path)) {
            return factory.newDocumentBuilder().parse(input);
        }
    }

    /** Declared project dependencies only; excludes parent, dependencyManagement and profiles. */
    public static Set<Edge> loadDirectEdges(Path root, String... modules) throws Exception {
        Set<Edge> edges = new LinkedHashSet<>();
        var xpath = XPathFactory.newInstance().newXPath();
        for (String module : modules) {
            var document = readXml(root.resolve(module).resolve("pom.xml"));
            var nodes = (NodeList) xpath.evaluate("/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency']", document, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                String group = xpath.evaluate("*[local-name()='groupId']", nodes.item(i)).trim();
                String artifact = xpath.evaluate("*[local-name()='artifactId']", nodes.item(i)).trim();
                if (group.equals("com.macro.mall") && !artifact.isEmpty()) edges.add(new Edge(module, artifact));
            }
        }
        return edges;
    }

    public static String readAnchor(Path root, String relativePath) throws IOException {
        return Files.readString(root.resolve(relativePath));
    }

    public static int lineOf(String text, String needle) {
        int index = text.indexOf(needle);
        if (index < 0) throw new IllegalArgumentException("Source anchor missing: " + needle);
        return 1 + (int) text.substring(0, index).chars().filter(c -> c == '\n').count();
    }

    // ponytail: literal section heuristic; use a Java parser if formatting-independent analysis is needed.
    public static String cancelSection(String text) {
        int start = text.indexOf("public CommonResult cancelOrder");
        if (start < 0) return "";
        int end = text.indexOf("public CommonResult", start + "public CommonResult".length());
        return text.substring(start, end < 0 ? text.length() : end);
    }

    public static void main(String[] args) throws Exception {
        Path root = mallRoot();
        System.out.println("Read-only source observations: " + root);
        loadDirectEdges(root, MODULES).forEach(System.out::println);
        String base = "mall-portal/src/main/java/com/macro/mall/portal/";
        String cancel = cancelSection(readAnchor(root, base + "controller/OmsPortalOrderController.java"));
        System.out.println("cancelOrder section schedules delay: " + cancel.contains("sendDelayMessageCancelOrder"));
        System.out.println("cancelOrder section calls cancellation directly: " + cancel.contains("portalOrderService.cancelOrder"));
        for (String[] anchor : new String[][] {{"component/CancelOrderSender.java", "QUEUE_TTL_ORDER_CANCEL"}, {"component/CancelOrderReceiver.java", "queues = \"mall.order.cancel\""}, {"component/CancelOrderReceiver.java", "portalOrderService.cancelOrder(orderId)"}}) {
            System.out.println(base + anchor[0] + ":" + lineOf(readAnchor(root, base + anchor[0]), anchor[1]) + " " + anchor[1]);
        }
    }
}
