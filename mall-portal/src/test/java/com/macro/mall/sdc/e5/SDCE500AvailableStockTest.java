package com.macro.mall.sdc.e5;

import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.PmsProductFullReduction;
import com.macro.mall.model.PmsProductLadder;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.PromotionProduct;
import com.macro.mall.portal.service.impl.OmsPromotionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 收口前固定“可售库存 = stock − lockStock”的现状：四种促销分支都按此式给 realStock；
 * 无优惠分支找不到 SKU 时 realStock 保持 null（其余三个分支没有这道判空，本测试不覆盖）。
 * 收口只把四处同式收成一处，本测试改前改后都应通过。
 */
class SDCE500AvailableStockTest {

    private static PmsSkuStock sku(long id, int stock, int lock) {
        PmsSkuStock s = new PmsSkuStock();
        s.setId(id);
        s.setPrice(new BigDecimal("100.00"));
        s.setPromotionPrice(new BigDecimal("90.00"));
        s.setStock(stock);
        s.setLockStock(lock);
        return s;
    }

    private static PromotionProduct product(long id, int type, PmsSkuStock sku) {
        PromotionProduct p = new PromotionProduct();
        p.setId(id);
        p.setPromotionType(type);
        p.setSkuStockList(List.of(sku));
        PmsProductLadder ladder = new PmsProductLadder();
        ladder.setCount(1);
        ladder.setDiscount(new BigDecimal("0.80"));
        p.setProductLadderList(new ArrayList<>(List.of(ladder)));
        PmsProductFullReduction reduction = new PmsProductFullReduction();
        reduction.setFullPrice(new BigDecimal("50.00"));
        reduction.setReducePrice(new BigDecimal("10.00"));
        p.setProductFullReductionList(new ArrayList<>(List.of(reduction)));
        return p;
    }

    private static OmsCartItem item(long id, long productId, long skuId) {
        OmsCartItem i = new OmsCartItem();
        i.setId(id);
        i.setProductId(productId);
        i.setProductSkuId(skuId);
        i.setQuantity(1);
        return i;
    }

    @Test
    void everyPromotionBranchReportsStockMinusLockStock() {
        OmsPromotionServiceImpl service = new OmsPromotionServiceImpl();
        PortalProductDao dao = mock(PortalProductDao.class);
        ReflectionTestUtils.setField(service, "portalProductDao", dao);
        when(dao.getPromotionProductList(anyList())).thenReturn(List.of(
                product(1, 1, sku(11, 10, 6)),
                product(3, 3, sku(31, 10, 3)),
                product(4, 4, sku(41, 7, 7)),
                product(5, 0, sku(51, 5, 1))));

        List<CartPromotionItem> result = service.calcCartPromotion(List.of(
                item(101, 1, 11), item(103, 3, 31), item(104, 4, 41), item(105, 5, 51), item(106, 5, 99)));

        Map<Long, Integer> realStock = new HashMap<>();
        result.forEach(r -> realStock.put(r.getId(), r.getRealStock()));
        assertEquals(5, result.size());
        assertEquals(4, realStock.get(101L), "单品促销");
        assertEquals(7, realStock.get(103L), "阶梯价");
        assertEquals(0, realStock.get(104L), "满减");
        assertEquals(4, realStock.get(105L), "无优惠");
        assertNull(realStock.get(106L), "无优惠且找不到 SKU");
        assertEquals("单品促销,打折优惠,满减优惠,无优惠,无优惠", result.stream()
                .map(r -> r.getPromotionMessage().split("：")[0]).collect(Collectors.joining(",")));
    }
}
