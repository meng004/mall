package com.macro.mall.sdc.e5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class StockImpactTest {
    @TempDir Path root;
    void write(String path, String text) throws Exception {
        Files.createDirectories(root.resolve(path).getParent());
        Files.writeString(root.resolve(path), text);
    }
    @Test void locatesMutationsAndHookCandidatesWithoutClaimingWarningsRun() throws Exception {
        write(StockImpact.ORDER, "header\nskuStock.setLockStock(1);\nprivate boolean hasStock() {}\nlockStock(cartPromotionItemList);\nportalOrderDao.updateSkuStock(items);\ngetLowStock();");
        write(StockImpact.DAO, "<mapper>\n<update id=\"updateSkuStock\">stock - #{item.productQuantity}</update>\n<update id=\"releaseSkuStockLock\">lock_stock = 0</update>\n</mapper>");
        var sites = StockImpact.impacts(root);
        assertEquals(4, sites.size());
        assertEquals(2, sites.get(0).line());
        assertEquals(2, sites.get(1).line());
        assertEquals(3, sites.get(2).line());
        assertEquals(3, sites.get(3).line());
        assertEquals(4, StockImpact.hookPoints(root).get(0).line());
        assertEquals(5, StockImpact.hookPoints(root).get(1).line());
        assertTrue(StockImpact.portalReadsLowStock(root));
    }
    @Test void scopesLowStockInspectionToNamedSqlStatementRegardlessOfOrder() throws Exception {
        write(StockImpact.DAO, "<mapper><update id=\"updateOrderStatus\">low_stock = 1</update><update id=\"updateSkuStock\">stock = stock - 1</update></mapper>");
        assertFalse(StockImpact.stockSqlSetsLowStock(root));
        write(StockImpact.DAO, "<mapper><update id=\"updateSkuStock\">low_stock = 1</update></mapper>");
        assertTrue(StockImpact.stockSqlSetsLowStock(root));
    }
    @Test void missingSqlAnchorIsReportedInsteadOfSilentlyPassing() throws Exception {
        write(StockImpact.DAO, "<mapper/>");
        assertThrows(IllegalArgumentException.class, () -> StockImpact.stockSqlSetsLowStock(root));
        write(StockImpact.ORDER, "class Order {}");
        assertFalse(StockImpact.portalReadsLowStock(root));
        assertThrows(IllegalArgumentException.class, () -> StockImpact.impacts(root));
    }
}
