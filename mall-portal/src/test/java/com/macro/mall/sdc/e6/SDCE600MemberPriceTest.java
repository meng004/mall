package com.macro.mall.sdc.e6;

import com.macro.mall.mapper.PmsMemberPriceMapper;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.PmsMemberPrice;
import com.macro.mall.model.PmsMemberPriceExample;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.PromotionProduct;
import com.macro.mall.portal.service.impl.OmsPromotionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * E6-00 会员价规格测试。只替换 PortalProductDao 与两个 Mapper，不连数据库。
 * 文案固定为「会员价」。单价改写为 SKU 原价；优惠额 = SKU 原价 − 会员价，且不乘数量。
 * Mapper 字段尚不存在时跳过交互校验，红灯由优惠结果断言承担。
 */
class SDCE600MemberPriceTest {
    private static final long SKU_A = 7L;
    private static final long SKU_B = 8L;
    private static final long PRODUCT_ID = 2L;
    private static final long OTHER_PRODUCT_ID = 9L;
    private static final long MEMBER_ID = 11L;
    private static final long OTHER_MEMBER_ID = 12L;
    private static final long LEVEL_ID = 3L;
    private static final long OTHER_LEVEL_ID = 4L;

    @Test void memberPriceDiscountsSkuOriginalNotPromotionPrice() {
        Fixture fx = new Fixture();
        fx.member(MEMBER_ID, LEVEL_ID);
        fx.prices(price(PRODUCT_ID, LEVEL_ID, "70.00"));
        fx.product(PRODUCT_ID, 2);
        List<CartPromotionItem> result = fx.calc(line(PRODUCT_ID, SKU_A, MEMBER_ID, 1));
        assertItems(result, "100.00|30.00|会员价|7");
        assertGifts(result, 1);
        fx.verifyLookups(1, MEMBER_ID, PRODUCT_ID, LEVEL_ID);
    }

    @Test void twoSkusInOneGroupShareOneLookup() {
        Fixture fx = new Fixture();
        fx.member(MEMBER_ID, LEVEL_ID);
        fx.prices(price(PRODUCT_ID, LEVEL_ID, "70.00"));
        fx.product(PRODUCT_ID, 2);
        List<CartPromotionItem> result = fx.calc(
                line(PRODUCT_ID, SKU_A, MEMBER_ID, 1),
                line(PRODUCT_ID, SKU_B, MEMBER_ID, 2));
        assertItems(result, "100.00|30.00|会员价|7", "200.00|130.00|会员价|15");
        assertGifts(result, 1, 2);
        fx.verifyLookups(1, MEMBER_ID, PRODUCT_ID, LEVEL_ID);
    }

    @Test void memberPriceEqualToOriginalHasNoReduce() {
        Fixture fx = new Fixture();
        fx.member(MEMBER_ID, LEVEL_ID);
        fx.prices(price(PRODUCT_ID, LEVEL_ID, "100.00"));
        fx.product(PRODUCT_ID, 2);
        List<CartPromotionItem> result = fx.calc(line(PRODUCT_ID, SKU_A, MEMBER_ID, 1));
        assertItems(result, "95.00|0|无优惠|7");
        fx.verifyLookups(1, MEMBER_ID, PRODUCT_ID, LEVEL_ID);
    }

    @Test void memberPriceAboveOriginalHasNoReduce() {
        Fixture fx = new Fixture();
        fx.member(MEMBER_ID, LEVEL_ID);
        fx.prices(price(PRODUCT_ID, LEVEL_ID, "120.00"));
        fx.product(PRODUCT_ID, 2);
        assertItems(fx.calc(line(PRODUCT_ID, SKU_A, MEMBER_ID, 1)), "95.00|0|无优惠|7");
        fx.verifyLookups(1, MEMBER_ID, PRODUCT_ID, LEVEL_ID);
    }

    @Test void onlySkusBelowMemberPriceAreDiscounted() {
        Fixture fx = new Fixture();
        fx.member(MEMBER_ID, LEVEL_ID);
        fx.prices(price(PRODUCT_ID, LEVEL_ID, "150.00"));
        fx.product(PRODUCT_ID, 2);
        List<CartPromotionItem> result = fx.calc(
                line(PRODUCT_ID, SKU_A, MEMBER_ID, 1),
                line(PRODUCT_ID, SKU_B, MEMBER_ID, 1));
        assertItems(result, "95.00|0|无优惠|7", "200.00|50.00|会员价|15");
        fx.verifyLookups(1, MEMBER_ID, PRODUCT_ID, LEVEL_ID);
    }

    @Test void missingMemberHasNoReduceAndSkipsPriceQuery() {
        Fixture fx = new Fixture();
        fx.member(MEMBER_ID, LEVEL_ID);
        fx.product(PRODUCT_ID, 2);
        when(fx.memberMapper.selectByPrimaryKey(MEMBER_ID)).thenReturn(null);
        assertItems(fx.calc(line(PRODUCT_ID, SKU_A, MEMBER_ID, 1)), "95.00|0|无优惠|7");
        if (fx.mappersPresent) {
            verify(fx.memberMapper, times(1)).selectByPrimaryKey(MEMBER_ID);
            verify(fx.priceMapper, never()).selectByExample(any());
        }
    }

    @Test void nullMemberLevelHasNoReduceAndSkipsPriceQuery() {
        Fixture fx = new Fixture();
        fx.member(MEMBER_ID, null);
        fx.product(PRODUCT_ID, 2);
        assertItems(fx.calc(line(PRODUCT_ID, SKU_A, MEMBER_ID, 1)), "95.00|0|无优惠|7");
        if (fx.mappersPresent) {
            verify(fx.memberMapper, times(1)).selectByPrimaryKey(MEMBER_ID);
            verify(fx.priceMapper, never()).selectByExample(any());
        }
    }

    @Test void emptyPriceListHasNoReduce() {
        Fixture fx = new Fixture();
        fx.member(MEMBER_ID, LEVEL_ID);
        fx.prices();
        fx.product(PRODUCT_ID, 2);
        assertItems(fx.calc(line(PRODUCT_ID, SKU_A, MEMBER_ID, 1)), "95.00|0|无优惠|7");
        fx.verifyLookups(1, MEMBER_ID, PRODUCT_ID, LEVEL_ID);
    }

    @Test void nullMemberPriceHasNoReduce() {
        Fixture fx = new Fixture();
        fx.member(MEMBER_ID, LEVEL_ID);
        fx.prices(price(PRODUCT_ID, LEVEL_ID, null));
        fx.product(PRODUCT_ID, 2);
        assertItems(fx.calc(line(PRODUCT_ID, SKU_A, MEMBER_ID, 1)), "95.00|0|无优惠|7");
        fx.verifyLookups(1, MEMBER_ID, PRODUCT_ID, LEVEL_ID);
    }

    @Test void nullPriceListDoesNotThrow() {
        Fixture fx = new Fixture();
        fx.member(MEMBER_ID, LEVEL_ID);
        when(fx.priceMapper.selectByExample(any())).thenReturn(null);
        fx.product(PRODUCT_ID, 2);
        assertItems(fx.calc(line(PRODUCT_ID, SKU_A, MEMBER_ID, 1)), "95.00|0|无优惠|7");
        fx.verifyLookups(1, MEMBER_ID, PRODUCT_ID, LEVEL_ID);
    }

    @Test void nullMemberIdDoesNotQuery() {
        Fixture fx = new Fixture();
        fx.product(PRODUCT_ID, 2);
        assertItems(fx.calc(line(PRODUCT_ID, SKU_A, null, 1)), "95.00|0|无优惠|7");
        if (fx.mappersPresent) {
            verify(fx.memberMapper, never()).selectByPrimaryKey(nullable(Long.class));
            verify(fx.priceMapper, never()).selectByExample(any());
        }
    }

    @Test void mixedMemberIdsInOneGroupDoNotQuery() {
        Fixture fx = new Fixture();
        fx.product(PRODUCT_ID, 2);
        List<CartPromotionItem> result = fx.calc(
                line(PRODUCT_ID, SKU_A, MEMBER_ID, 1),
                line(PRODUCT_ID, SKU_B, OTHER_MEMBER_ID, 1));
        assertItems(result, "95.00|0|无优惠|7", "95.00|0|无优惠|15");
        if (fx.mappersPresent) {
            verify(fx.memberMapper, never()).selectByPrimaryKey(nullable(Long.class));
            verify(fx.priceMapper, never()).selectByExample(any());
        }
    }

    @Test void twoProductGroupsQueryOnceEach() {
        Fixture fx = new Fixture();
        fx.member(MEMBER_ID, LEVEL_ID);
        fx.member(OTHER_MEMBER_ID, OTHER_LEVEL_ID);
        fx.prices(price(PRODUCT_ID, LEVEL_ID, "70.00"), price(OTHER_PRODUCT_ID, OTHER_LEVEL_ID, "60.00"));
        fx.product(PRODUCT_ID, 2);
        fx.product(OTHER_PRODUCT_ID, 2);
        List<CartPromotionItem> result = fx.calc(
                line(OTHER_PRODUCT_ID, SKU_B, OTHER_MEMBER_ID, 1),
                line(PRODUCT_ID, SKU_A, MEMBER_ID, 1));
        assertItems(result, "100.00|30.00|会员价|7", "200.00|140.00|会员价|15");
        if (fx.mappersPresent) {
            verify(fx.memberMapper, times(1)).selectByPrimaryKey(MEMBER_ID);
            verify(fx.memberMapper, times(1)).selectByPrimaryKey(OTHER_MEMBER_ID);
            verifyNoMoreInteractions(fx.memberMapper);
            verify(fx.priceMapper, times(2)).selectByExample(any(PmsMemberPriceExample.class));
        }
    }

    @Test void otherPromotionTypesDoNotQueryMemberPrice() {
        Fixture fx = new Fixture();
        fx.product(PRODUCT_ID, 1);
        assertItems(fx.calc(line(PRODUCT_ID, SKU_A, MEMBER_ID, 1)), "100.00|20.00|单品促销|7");
        if (fx.mappersPresent) {
            verify(fx.memberMapper, never()).selectByPrimaryKey(nullable(Long.class));
            verify(fx.priceMapper, never()).selectByExample(any());
        }
    }

    @Test void perUnitReduceAmountIsWhatGenerateOrderCopies() {
        Fixture fx = new Fixture();
        fx.member(MEMBER_ID, LEVEL_ID);
        fx.prices(price(PRODUCT_ID, LEVEL_ID, "70.00"));
        fx.product(PRODUCT_ID, 2);
        List<CartPromotionItem> result = fx.calc(line(PRODUCT_ID, SKU_A, MEMBER_ID, 3));
        assertItems(result, "100.00|30.00|会员价|7");
        assertEquals(3, result.get(0).getQuantity());
        assertGifts(result, 3);
    }

    @Test void firstPriceRowIsUsedWhenSeveralMatch() {
        Fixture fx = new Fixture();
        fx.member(MEMBER_ID, LEVEL_ID);
        PmsMemberPrice first = price(PRODUCT_ID, LEVEL_ID, "70.00");
        PmsMemberPrice second = price(PRODUCT_ID, LEVEL_ID, "10.00");
        when(fx.priceMapper.selectByExample(any())).thenReturn(List.of(first, second));
        fx.product(PRODUCT_ID, 2);
        assertItems(fx.calc(line(PRODUCT_ID, SKU_A, MEMBER_ID, 1)), "100.00|30.00|会员价|7");
    }

    private static void assertItems(List<CartPromotionItem> result, String... expected) {
        assertEquals(List.of(expected), result.stream()
                .map(r -> r.getPrice() + "|" + r.getReduceAmount() + "|" + r.getPromotionMessage() + "|" + r.getRealStock())
                .toList());
    }

    private static void assertGifts(List<CartPromotionItem> result, int... quantities) {
        for (int i = 0; i < result.size(); i++) {
            assertEquals(12, result.get(i).getIntegration());
            assertEquals(2, result.get(i).getGrowth());
            assertEquals(quantities[i], result.get(i).getQuantity());
        }
    }

    private static OmsCartItem line(long productId, long skuId, Long memberId, int quantity) {
        OmsCartItem cart = new OmsCartItem();
        cart.setProductId(productId);
        cart.setProductSkuId(skuId);
        cart.setMemberId(memberId);
        cart.setPrice(new BigDecimal("95.00"));
        cart.setQuantity(quantity);
        return cart;
    }

    private static PmsMemberPrice price(long productId, long levelId, String memberPrice) {
        PmsMemberPrice row = new PmsMemberPrice();
        row.setProductId(productId);
        row.setMemberLevelId(levelId);
        if (memberPrice != null) {
            row.setMemberPrice(new BigDecimal(memberPrice));
        }
        return row;
    }

    private static final class Fixture {
        final OmsPromotionServiceImpl service = new OmsPromotionServiceImpl();
        final PortalProductDao dao = mock(PortalProductDao.class, withSettings().mockMaker("mock-maker-subclass"));
        final UmsMemberMapper memberMapper = mock(UmsMemberMapper.class, withSettings().mockMaker("mock-maker-subclass"));
        final PmsMemberPriceMapper priceMapper = mock(PmsMemberPriceMapper.class, withSettings().mockMaker("mock-maker-subclass"));
        final boolean mappersPresent;
        final List<PromotionProduct> products = new ArrayList<>();

        Fixture() {
            ReflectionTestUtils.setField(service, "portalProductDao", dao);
            mappersPresent = ReflectionUtils.findField(OmsPromotionServiceImpl.class, "umsMemberMapper") != null
                    && ReflectionUtils.findField(OmsPromotionServiceImpl.class, "pmsMemberPriceMapper") != null;
            if (mappersPresent) {
                ReflectionTestUtils.setField(service, "umsMemberMapper", memberMapper);
                ReflectionTestUtils.setField(service, "pmsMemberPriceMapper", priceMapper);
            }
        }

        void member(long memberId, Long levelId) {
            UmsMember member = new UmsMember();
            member.setId(memberId);
            member.setMemberLevelId(levelId);
            when(memberMapper.selectByPrimaryKey(memberId)).thenReturn(member);
        }

        void prices(PmsMemberPrice... rows) {
            when(priceMapper.selectByExample(any())).thenAnswer(invocation -> {
                PmsMemberPriceExample example = invocation.getArgument(0);
                Long productId = null;
                Long levelId = null;
                for (PmsMemberPriceExample.Criterion criterion : example.getOredCriteria().get(0).getCriteria()) {
                    if ("product_id =".equals(criterion.getCondition())) {
                        productId = (Long) criterion.getValue();
                    }
                    if ("member_level_id =".equals(criterion.getCondition())) {
                        levelId = (Long) criterion.getValue();
                    }
                }
                List<PmsMemberPrice> matched = new ArrayList<>();
                for (PmsMemberPrice row : rows) {
                    if (row.getProductId().equals(productId) && row.getMemberLevelId().equals(levelId)) {
                        matched.add(row);
                    }
                }
                return matched;
            });
        }

        void product(long productId, int promotionType) {
            PromotionProduct product = new PromotionProduct();
            product.setId(productId);
            product.setPromotionType(promotionType);
            product.setGiftPoint(12);
            product.setGiftGrowth(2);
            product.setSkuStockList(List.of(
                    sku(SKU_A, "100.00", "80.00", 10, 3),
                    sku(SKU_B, "200.00", "150.00", 20, 5)));
            products.add(product);
        }

        List<CartPromotionItem> calc(OmsCartItem... cart) {
            when(dao.getPromotionProductList(any())).thenReturn(List.copyOf(products));
            return service.calcCartPromotion(List.of(cart));
        }

        void verifyLookups(int times, long memberId, long productId, long levelId) {
            if (!mappersPresent) {
                return;
            }
            verify(memberMapper, times(times)).selectByPrimaryKey(eq(memberId));
            org.mockito.ArgumentCaptor<PmsMemberPriceExample> captor =
                    org.mockito.ArgumentCaptor.forClass(PmsMemberPriceExample.class);
            verify(priceMapper, times(times)).selectByExample(captor.capture());
            if (times == 1) {
                List<PmsMemberPriceExample.Criterion> criteria = captor.getValue().getOredCriteria().get(0).getCriteria();
                assertEquals("product_id =", criteria.get(0).getCondition());
                assertEquals(productId, criteria.get(0).getValue());
                assertEquals("member_level_id =", criteria.get(1).getCondition());
                assertEquals(levelId, criteria.get(1).getValue());
            }
        }
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
}
