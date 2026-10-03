package com.macro.mall.sdc.e2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class LayerRulesTest {
    @TempDir Path root;
    void write(String path, String text) throws Exception {
        Files.createDirectories(root.resolve(path).getParent());
        Files.writeString(root.resolve(path), text);
    }

    @Test void reportsAllThreeRuleKindsAndActualFixtureLines() throws Exception {
        write(LayerRules.PORTAL_CONTROLLER, "header\npublic CommonResult cancelOrder() {\n sendDelayMessageCancelOrder();\n}\npublic CommonResult next() {}\n");
        write(LayerRules.ADMIN_CONTROLLER, "status == -1\nstatus == -2\nstatus == -3\n");
        write(LayerRules.ORDER_SERVICE, "OmsOrderExample orderExample;\nOmsOrderExample example;\nOmsOrderExample example;\n");
        var found = LayerRules.findViolations(root);
        assertEquals(7, found.size());
        assertEquals(new LayerRules.Finding("controller_names_cancel_but_only_delays", LayerRules.PORTAL_CONTROLLER, 2), found.get(0));
        assertEquals(3, found.stream().filter(f -> f.rule().equals("controller_translates_status_code")).count());
        assertEquals(3, found.get(6).line());
    }

    @Test void doesNotReportFixedOrAbsentCancelMethod() throws Exception {
        write(LayerRules.ADMIN_CONTROLLER, "");
        write(LayerRules.ORDER_SERVICE, "");
        write(LayerRules.PORTAL_CONTROLLER, "public CommonResult cancelOrder() { portalOrderService.cancelOrder(); }\n");
        assertTrue(LayerRules.findViolations(root).isEmpty());
        write(LayerRules.PORTAL_CONTROLLER, "public CommonResult other() { sendDelayMessageCancelOrder(); }");
        assertTrue(LayerRules.findViolations(root).isEmpty());
    }

    @Test void recognizesOnlyActualModuleDependenciesInEitherDirection() throws Exception {
        write("left/pom.xml", "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><artifactId>right</artifactId></project>");
        write("right/pom.xml", "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"/>");
        assertFalse(LayerRules.modulesDependOnEachOther(root, "left", "right"));
        write("right/pom.xml", "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><dependencies><dependency><groupId>com.macro.mall</groupId><artifactId>left</artifactId></dependency></dependencies></project>");
        assertTrue(LayerRules.modulesDependOnEachOther(root, "left", "right"));
    }
}
