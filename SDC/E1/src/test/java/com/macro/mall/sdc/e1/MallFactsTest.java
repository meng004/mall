package com.macro.mall.sdc.e1;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MallFactsTest {
    @TempDir Path root;

    @Test void readsOnlyDeclaredInternalDependencies() throws Exception {
        Files.createDirectories(root.resolve("sample"));
        Files.writeString(root.resolve("sample/pom.xml"), """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <parent><artifactId>not-a-dependency</artifactId></parent>
              <dependencyManagement><dependencies><dependency><groupId>com.macro.mall</groupId><artifactId>managed</artifactId></dependency></dependencies></dependencyManagement>
              <dependencies>
                <dependency><groupId>com.macro.mall</groupId><artifactId>mall-common</artifactId></dependency>
                <dependency><groupId>other</groupId><artifactId>external</artifactId></dependency>
              </dependencies>
            </project>""");
        assertEquals(Set.of(new MallFacts.Edge("sample", "mall-common")), MallFacts.loadDirectEdges(root, "sample"));
    }

    @Test void rootCanBeLocatedFromModuleOrWorkspace() throws Exception {
        Path mall = Files.createDirectories(root.resolve("mall"));
        Files.writeString(mall.resolve("pom.xml"), "<project/>");
        Files.createDirectories(mall.resolve("mall-portal"));
        Path module = Files.createDirectories(mall.resolve("SDC/E1"));
        assertEquals(mall, MallFacts.findMallRoot(module));
        assertEquals(mall, MallFacts.findMallRoot(root));
        assertThrows(IllegalArgumentException.class, () -> MallFacts.findMallRoot(root.resolve("missing")));
    }

    @Test void readsUtf8AndReportsMissingAnchors() throws Exception {
        Files.writeString(root.resolve("anchor.java"), "第一行\nneedle\n");
        assertEquals("第一行\nneedle\n", MallFacts.readAnchor(root, "anchor.java"));
        assertEquals(2, MallFacts.lineOf(MallFacts.readAnchor(root, "anchor.java"), "needle"));
        assertThrows(IllegalArgumentException.class, () -> MallFacts.lineOf("text", "missing"));
    }
}
