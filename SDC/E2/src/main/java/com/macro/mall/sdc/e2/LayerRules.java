package com.macro.mall.sdc.e2;

import com.macro.mall.sdc.e1.MallFacts;
import java.nio.file.Path;
import java.util.*;

/** Literal source findings for architecture discussion, not a semantic layer checker. */
public final class LayerRules {
    public static final String PORTAL_CONTROLLER = "mall-portal/src/main/java/com/macro/mall/portal/controller/OmsPortalOrderController.java";
    public static final String ADMIN_CONTROLLER = "mall-admin/src/main/java/com/macro/mall/controller/UmsAdminController.java";
    public static final String ORDER_SERVICE = "mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderServiceImpl.java";
    public record Finding(String rule, String path, int line) {}

    public static List<Finding> findViolations(Path root) throws Exception {
        List<Finding> found = new ArrayList<>();
        String controller = MallFacts.readAnchor(root, PORTAL_CONTROLLER);
        String cancel = MallFacts.cancelSection(controller);
        if (cancel.contains("sendDelayMessageCancelOrder") && !cancel.contains("portalOrderService.cancelOrder")) {
            found.add(new Finding("controller_names_cancel_but_only_delays", PORTAL_CONTROLLER, MallFacts.lineOf(controller, "public CommonResult cancelOrder")));
        }
        String admin = MallFacts.readAnchor(root, ADMIN_CONTROLLER);
        for (String needle : List.of("status == -1", "status == -2", "status == -3")) {
            if (admin.contains(needle)) found.add(new Finding("controller_translates_status_code", ADMIN_CONTROLLER, MallFacts.lineOf(admin, needle)));
        }
        String order = MallFacts.readAnchor(root, ORDER_SERVICE);
        for (String needle : List.of("OmsOrderExample orderExample", "OmsOrderExample example")) {
            for (int index = order.indexOf(needle); index >= 0; index = order.indexOf(needle, index + needle.length())) {
                int line = 1 + (int) order.substring(0, index).chars().filter(c -> c == '\n').count();
                found.add(new Finding("service_uses_generated_example", ORDER_SERVICE, line));
            }
        }
        return found;
    }

    public static boolean modulesDependOnEachOther(Path root, String left, String right) throws Exception {
        var edges = MallFacts.loadDirectEdges(root, left, right);
        return edges.contains(new MallFacts.Edge(left, right)) || edges.contains(new MallFacts.Edge(right, left));
    }

    public static void main(String[] args) throws Exception {
        Path root = MallFacts.mallRoot();
        System.out.println("Literal source findings (review manually): " + root);
        findViolations(root).forEach(System.out::println);
        System.out.println("admin/portal directly depend on each other: " + modulesDependOnEachOther(root, "mall-admin", "mall-portal"));
    }
}
