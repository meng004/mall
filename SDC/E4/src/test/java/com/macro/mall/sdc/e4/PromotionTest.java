package com.macro.mall.sdc.e4;

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

class PromotionTest {
    @Test void preservesAmountsMessagesStockAndGiftPointsAcrossStrategies() {
        check(1, 1, "20.00", "单品促销");
        check(3, 3, "20.0000", "打折优惠：满3件，打8.00折");
        check(3, 1, "0", "无优惠");
        check(4, 3, "3.3000", "满减优惠：满300.00元，减10.00元");
        check(4, 1, "0", "无优惠");
        check(0, 1, "0", "无优惠");
        check(99, 1, "0", "无优惠");
    }

    private void check(int type, int quantity, String reduction, String message) {
        OmsPromotionServiceImpl service = new OmsPromotionServiceImpl();
        PortalProductDao dao = mock(PortalProductDao.class, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, "portalProductDao", dao);
        PmsSkuStock sku = new PmsSkuStock();
        sku.setId(7L);
        sku.setPrice(new BigDecimal("100.00"));
        sku.setPromotionPrice(new BigDecimal("80.00"));
        sku.setStock(10);
        sku.setLockStock(3);
        PromotionProduct product = new PromotionProduct();
        product.setId(2L);
        product.setPromotionType(type);
        product.setGiftPoint(12);
        product.setGiftGrowth(2);
        product.setSkuStockList(List.of(sku));
        PmsProductLadder ladder = new PmsProductLadder();
        ladder.setCount(3);
        ladder.setDiscount(new BigDecimal("0.80"));
        product.setProductLadderList(new ArrayList<>(List.of(ladder)));
        PmsProductFullReduction full = new PmsProductFullReduction();
        full.setFullPrice(new BigDecimal("300.00"));
        full.setReducePrice(new BigDecimal("10.00"));
        product.setProductFullReductionList(new ArrayList<>(List.of(full)));
        when(dao.getPromotionProductList(anyList())).thenReturn(List.of(product));
        OmsCartItem cart = new OmsCartItem();
        cart.setProductId(2L);
        cart.setProductSkuId(7L);
        cart.setPrice(new BigDecimal("100.00"));
        cart.setQuantity(quantity);
        List<CartPromotionItem> result = service.calcCartPromotion(List.of(cart));
        assertEquals(1, result.size());
        CartPromotionItem actual = result.get(0);
        assertEquals(0, new BigDecimal(reduction).compareTo(actual.getReduceAmount()), "type=" + type);
        assertEquals(message, actual.getPromotionMessage());
        assertEquals(7, actual.getRealStock());
        assertEquals(12, actual.getIntegration());
        assertEquals(2, actual.getGrowth());
        assertEquals(quantity, actual.getQuantity());
    }
}
