package com.macro.mall.sdc.e6;

import com.macro.mall.model.*;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.service.impl.OmsPromotionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * E6-00 特征测试：固定 calcCartPromotion 现有行为（金额、单价、文案、库存、积分、成长值），重构前后断言不变。
 * 购物车单价取 95.00（不同于 SKU 原价），用来观察单品促销把单价改写成原价；
 * 两个 SKU 的用例覆盖逐项循环与满减按金额分摊。
 */
class SDCE600Test {
    // SKU 7：原价 100.00、促销价 80.00、库存 10 锁定 3；SKU 8：原价 200.00、促销价 150.00、库存 20 锁定 5
    private static final long SKU_A = 7L, SKU_B = 8L;

    @Test void singlePromotionReducesToPromotionPriceAndResetsPrice() {
        assertItems(calc(1, line(SKU_A, 1)), "100.00|20.00|单品促销|7");
    }

    @Test void singlePromotionHandlesEverySku() {
        assertItems(calc(1, line(SKU_A, 1), line(SKU_B, 2)),
                "100.00|20.00|单品促销|7", "200.00|50.00|单品促销|15");
    }

    @Test void ladderHitDiscountsAndKeepsCartPrice() {
        assertItems(calc(3, line(SKU_A, 3)), "95.00|20.0000|打折优惠：满3件，打8.00折|7");
    }

    @Test void ladderMissFallsBackToNoReduce() {
        assertItems(calc(3, line(SKU_A, 1)), "95.00|0|无优惠|7");
    }

    @Test void fullReductionHitSingleSku() {
        assertItems(calc(4, line(SKU_A, 3)), "95.00|3.3000|满减优惠：满300.00元，减10.00元|7");
    }

    @Test void fullReductionSplitsAcrossSkusByOriginalPrice() {
        assertItems(calc(4, line(SKU_A, 1), line(SKU_B, 1)),
                "95.00|3.3000|满减优惠：满300.00元，减10.00元|7", "95.00|6.7000|满减优惠：满300.00元，减10.00元|15");
    }

    @Test void fullReductionMissFallsBackToNoReduce() {
        assertItems(calc(4, line(SKU_A, 1)), "95.00|0|无优惠|7");
    }

    @Test void unknownTypesHaveNoReduce() {
        assertItems(calc(0, line(SKU_A, 1)), "95.00|0|无优惠|7");
        assertItems(calc(99, line(SKU_A, 1)), "95.00|0|无优惠|7");
    }

    private static OmsCartItem line(long skuId, int quantity) {
        OmsCartItem cart = new OmsCartItem();
        cart.setProductId(2L);
        cart.setProductSkuId(skuId);
        cart.setPrice(new BigDecimal("95.00"));
        cart.setQuantity(quantity);
        return cart;
    }

    private static List<CartPromotionItem> calc(int type, OmsCartItem... cart) {
        OmsPromotionServiceImpl service = new OmsPromotionServiceImpl();
        PortalProductDao dao = mock(PortalProductDao.class, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, "portalProductDao", dao);
        PromotionProduct product = new PromotionProduct();
        product.setId(2L);
        product.setPromotionType(type);
        product.setGiftPoint(12);
        product.setGiftGrowth(2);
        product.setSkuStockList(List.of(sku(SKU_A, "100.00", "80.00", 10, 3), sku(SKU_B, "200.00", "150.00", 20, 5)));
        PmsProductLadder ladder = new PmsProductLadder();
        ladder.setCount(3);
        ladder.setDiscount(new BigDecimal("0.80"));
        product.setProductLadderList(new ArrayList<>(List.of(ladder)));
        PmsProductFullReduction full = new PmsProductFullReduction();
        full.setFullPrice(new BigDecimal("300.00"));
        full.setReducePrice(new BigDecimal("10.00"));
        product.setProductFullReductionList(new ArrayList<>(List.of(full)));
        when(dao.getPromotionProductList(anyList())).thenReturn(List.of(product));
        List<CartPromotionItem> result = service.calcCartPromotion(List.of(cart));
        for (int i = 0; i < result.size(); i++) {
            assertEquals(12, result.get(i).getIntegration());
            assertEquals(2, result.get(i).getGrowth());
            assertEquals(cart[i].getQuantity(), result.get(i).getQuantity());
        }
        return result;
    }

    private static PmsSkuStock sku(long id, String price, String promotionPrice, int stock, int lockStock) {
        PmsSkuStock sku = new PmsSkuStock();
        sku.setId(id);
        sku.setPrice(new BigDecimal(price));
        sku.setPromotionPrice(new BigDecimal(promotionPrice));
        sku.setStock(stock);
        sku.setLockStock(lockStock);
        return sku;
    }

    /** 每项依次为 单价|优惠金额|文案|可用库存。 */
    private static void assertItems(List<CartPromotionItem> result, String... expected) {
        assertEquals(List.of(expected), result.stream()
                .map(r -> r.getPrice() + "|" + r.getReduceAmount() + "|" + r.getPromotionMessage() + "|" + r.getRealStock())
                .toList());
    }
}
