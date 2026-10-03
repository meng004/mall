package com.macro.mall.sdc.e5;

import com.macro.mall.sdc.e1.MallFacts;
import javax.xml.xpath.*;
import org.w3c.dom.Node;
import java.nio.file.Path;
import java.util.List;

/** Locate mutation and hook candidates; this does not execute stock updates or warnings. */
public final class StockImpact {
    public static final String ORDER = "mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderServiceImpl.java";
    public static final String DAO = "mall-portal/src/main/resources/dao/PortalOrderDao.xml";
    public record Site(String step, String path, int line) {}

    public static List<Site> impacts(Path root) throws Exception {
        String order = MallFacts.readAnchor(root, ORDER);
        String dao = MallFacts.readAnchor(root, DAO);
        return List.of(new Site("lock", ORDER, MallFacts.lineOf(order, "skuStock.setLockStock")),
                new Site("pay", DAO, MallFacts.lineOf(dao, "stock - #{item.productQuantity}")),
                new Site("release", DAO, MallFacts.lineOf(dao, "id=\"releaseSkuStockLock\"")),
                new Site("has_stock", ORDER, MallFacts.lineOf(order, "private boolean hasStock")));
    }

    public static List<Site> hookPoints(Path root) throws Exception {
        String order = MallFacts.readAnchor(root, ORDER);
        return List.of(new Site("after_lock", ORDER, MallFacts.lineOf(order, "lockStock(cartPromotionItemList);")),
                new Site("after_pay", ORDER, MallFacts.lineOf(order, "portalOrderDao.updateSkuStock")));
    }

    public static boolean portalReadsLowStock(Path root) throws Exception {
        return MallFacts.readAnchor(root, ORDER).contains("getLowStock");
    }

    /** Reports a literal low_stock reference, not SQL assignment semantics. */
    public static boolean stockSqlSetsLowStock(Path root) throws Exception {
        var document = MallFacts.readXml(root.resolve(DAO));
        Node update = (Node) XPathFactory.newInstance().newXPath().evaluate("/mapper/update[@id='updateSkuStock']", document, XPathConstants.NODE);
        if (update == null) throw new IllegalArgumentException("SQL anchor missing: updateSkuStock");
        return update.getTextContent().contains("low_stock");
    }

    public static void main(String[] args) throws Exception {
        Path root = MallFacts.mallRoot();
        System.out.println("Source mutation/hook candidates (not runtime evidence): " + root);
        impacts(root).forEach(System.out::println);
        hookPoints(root).forEach(System.out::println);
        System.out.println("Order source contains getLowStock: " + portalReadsLowStock(root));
        System.out.println("updateSkuStock SQL references low_stock: " + stockSqlSetsLowStock(root));
    }
}
