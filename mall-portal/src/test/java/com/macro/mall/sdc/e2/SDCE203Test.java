package com.macro.mall.sdc.e2;

import com.macro.mall.mapper.CmsSubjectMapper;
import com.macro.mall.mapper.PmsProductCategoryMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.SmsFlashPromotionMapper;
import com.macro.mall.mapper.SmsFlashPromotionSessionMapper;
import com.macro.mall.mapper.SmsHomeAdvertiseMapper;
import com.macro.mall.model.CmsSubject;
import com.macro.mall.model.PmsBrand;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.SmsFlashPromotion;
import com.macro.mall.model.SmsFlashPromotionExample;
import com.macro.mall.model.SmsFlashPromotionSession;
import com.macro.mall.model.SmsFlashPromotionSessionExample;
import com.macro.mall.model.SmsHomeAdvertise;
import com.macro.mall.portal.dao.HomeDao;
import com.macro.mall.portal.domain.FlashPromotionProduct;
import com.macro.mall.portal.domain.HomeContentResult;
import com.macro.mall.portal.domain.HomeFlashPromotion;
import com.macro.mall.portal.service.impl.HomeServiceImpl;
import com.macro.mall.portal.util.DateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * E2-03 特征测试：首页六块聚合与秒杀活动／场次选择。
 * 只经由现有入口 {@code content()} 调用真实 HomeServiceImpl，替换外部 DAO；改前改后同一组断言。
 * 时间取自服务内部时钟，测试用调用前后的时间窗口核对条件值来自"现在"。
 */
class SDCE203Test {
    private static final String BEFORE_NO_PROMOTION =
            "advertise=1|brand=2|fresh=3|hot=4|subject=5|start=null|end=null|nextStart=null|nextEnd=null|products=null";
    private static final String BEFORE_NO_NEXT =
            "advertise=1|brand=2|fresh=3|hot=4|subject=5|start=7200000|end=14400000|nextStart=null|nextEnd=null|products=31";
    private static final String BEFORE_WITH_NEXT =
            "advertise=1|brand=2|fresh=3|hot=4|subject=5|start=7200000|end=14400000|nextStart=21600000|nextEnd=28800000|products=31";
    private static final String BEFORE_PROMOTION_OPERATORS = "status =\nstart_date <=\nend_date >=";
    private static final String BEFORE_CURRENT_OPERATORS = "start_time <=\nend_time >=";
    private static final String BEFORE_NEXT_OPERATORS = "start_time >";

    private HomeServiceImpl service;
    private HomeDao homeDao;
    private SmsFlashPromotionMapper flashPromotionMapper;
    private SmsFlashPromotionSessionMapper promotionSessionMapper;

    private List<SmsHomeAdvertise> ads;
    private List<PmsBrand> brands;
    private List<PmsProduct> newProducts;
    private List<PmsProduct> hotProducts;
    private List<CmsSubject> subjects;
    private SmsFlashPromotion promotion;
    private SmsFlashPromotionSession currentSession;
    private SmsFlashPromotionSession nextSession;
    private List<FlashPromotionProduct> flashProducts;

    @BeforeEach
    void setUp() {
        service = new HomeServiceImpl();
        homeDao = dependency("homeDao", HomeDao.class);
        flashPromotionMapper = dependency("flashPromotionMapper", SmsFlashPromotionMapper.class);
        promotionSessionMapper = dependency("promotionSessionMapper", SmsFlashPromotionSessionMapper.class);
        dependency("productMapper", PmsProductMapper.class);
        dependency("productCategoryMapper", PmsProductCategoryMapper.class);
        dependency("subjectMapper", CmsSubjectMapper.class);

        SmsHomeAdvertise ad = new SmsHomeAdvertise();
        ad.setId(1L);
        ad.setName("ad-1");
        ads = List.of(ad);
        PmsBrand brand = new PmsBrand();
        brand.setId(2L);
        brand.setName("brand-2");
        brands = List.of(brand);
        PmsProduct fresh = new PmsProduct();
        fresh.setId(3L);
        fresh.setName("new-3");
        newProducts = List.of(fresh);
        PmsProduct hot = new PmsProduct();
        hot.setId(4L);
        hot.setName("hot-4");
        hotProducts = List.of(hot);
        CmsSubject subject = new CmsSubject();
        subject.setId(5L);
        subject.setTitle("subject-5");
        subjects = List.of(subject);

        promotion = new SmsFlashPromotion();
        promotion.setId(11L);
        promotion.setTitle("flash-11");
        currentSession = new SmsFlashPromotionSession();
        currentSession.setId(22L);
        // 场次时刻用固定毫秒，投影字符串与运行时区无关。
        currentSession.setStartTime(new Date(7_200_000L));
        currentSession.setEndTime(new Date(14_400_000L));
        nextSession = new SmsFlashPromotionSession();
        nextSession.setId(23L);
        nextSession.setStartTime(new Date(21_600_000L));
        nextSession.setEndTime(new Date(28_800_000L));
        FlashPromotionProduct flashProduct = new FlashPromotionProduct();
        flashProduct.setId(31L);
        flashProduct.setName("flash-product-31");
        flashProduct.setFlashPromotionPrice(new BigDecimal("9.90"));
        flashProducts = List.of(flashProduct);

        when(dependencyAdvertise().selectByExample(any())).thenReturn(ads);
        when(homeDao.getRecommendBrandList(0, 6)).thenReturn(brands);
        when(homeDao.getNewProductList(0, 4)).thenReturn(newProducts);
        when(homeDao.getHotProductList(0, 4)).thenReturn(hotProducts);
        when(homeDao.getRecommendSubjectList(0, 4)).thenReturn(subjects);
    }

    private SmsHomeAdvertiseMapper advertiseMapper;

    private SmsHomeAdvertiseMapper dependencyAdvertise() {
        advertiseMapper = dependency("advertiseMapper", SmsHomeAdvertiseMapper.class);
        return advertiseMapper;
    }

    @Test
    void noPromotion_returnsOriginalEmptyFlash() {
        when(flashPromotionMapper.selectByExample(any())).thenReturn(List.of());

        HomeContentResult result = service.content();
        assertEquals(BEFORE_NO_PROMOTION, project(result));
        assertBlocks(result);
        assertNull(result.getHomeFlashPromotion().getStartTime());
        assertNull(result.getHomeFlashPromotion().getEndTime());
        assertNull(result.getHomeFlashPromotion().getNextStartTime());
        assertNull(result.getHomeFlashPromotion().getNextEndTime());
        assertNull(result.getHomeFlashPromotion().getProductList());
        verify(promotionSessionMapper, never()).selectByExample(any());
        verify(homeDao, never()).getFlashProductList(any(), any());
        System.out.println("PROJECT no-promotion " + project(result));
    }

    @Test
    void conditions_keepInclusiveOperators() {
        AtomicReference<SmsFlashPromotionExample> promotionExample = new AtomicReference<>();
        when(flashPromotionMapper.selectByExample(any())).thenAnswer(invocation -> {
            promotionExample.set(invocation.getArgument(0));
            return List.of(promotion);
        });
        AtomicReference<SmsFlashPromotionSessionExample> currentExample = new AtomicReference<>();
        AtomicReference<SmsFlashPromotionSessionExample> nextExample = new AtomicReference<>();
        when(promotionSessionMapper.selectByExample(any())).thenAnswer(invocation -> {
            SmsFlashPromotionSessionExample example = invocation.getArgument(0);
            if ("start_time asc".equals(example.getOrderByClause())) {
                nextExample.set(example);
                return List.of(nextSession);
            }
            currentExample.set(example);
            return List.of(currentSession);
        });
        when(homeDao.getFlashProductList(11L, 22L)).thenReturn(flashProducts);

        long before = System.currentTimeMillis();
        HomeContentResult result = service.content();
        long after = System.currentTimeMillis();

        assertEquals(BEFORE_PROMOTION_OPERATORS, conditions(promotionExample.get()));
        assertEquals(BEFORE_CURRENT_OPERATORS, conditions(currentExample.get()));
        assertEquals(BEFORE_NEXT_OPERATORS, conditions(nextExample.get()));
        assertEquals("start_time asc", nextExample.get().getOrderByClause());
        assertDerivedFromNow(criterionMillis(promotionExample.get(), 1), before, after, DateUtil::getDate);
        assertDerivedFromNow(criterionMillis(promotionExample.get(), 2), before, after, DateUtil::getDate);
        assertEquals(criterionMillis(promotionExample.get(), 1), criterionMillis(promotionExample.get(), 2));
        assertDerivedFromNow(criterionMillis(currentExample.get(), 0), before, after, DateUtil::getTime);
        assertEquals(criterionMillis(currentExample.get(), 0), criterionMillis(currentExample.get(), 1));
        assertEquals(BEFORE_WITH_NEXT, project(result));
        assertEquals(currentSession.getStartTime().getTime(),
                ((Date) nextExample.get().getOredCriteria().get(0).getAllCriteria().get(0).getValue()).getTime());
        assertSame(flashProducts, result.getHomeFlashPromotion().getProductList());
        System.out.println("OPERATORS promotion " + conditions(promotionExample.get()));
        System.out.println("OPERATORS current " + conditions(currentExample.get()));
        System.out.println("OPERATORS next " + conditions(nextExample.get()));
        System.out.println("PROJECT conditions " + project(result));
    }

    @Test
    void currentWithoutNext_stillLoadsProducts() {
        stubPromotionAndSessions(false);

        HomeContentResult result = service.content();

        HomeFlashPromotion flash = result.getHomeFlashPromotion();
        assertEquals(BEFORE_NO_NEXT, project(result));
        assertEquals(currentSession.getStartTime(), flash.getStartTime());
        assertEquals(currentSession.getEndTime(), flash.getEndTime());
        assertNull(flash.getNextStartTime());
        assertNull(flash.getNextEndTime());
        assertSame(flashProducts, flash.getProductList());
        verify(homeDao).getFlashProductList(eq(11L), eq(22L));
        assertBlocks(result);
        System.out.println("PROJECT current-no-next " + project(result));
    }

    @Test
    void currentWithNext_keepsAssembly() {
        stubPromotionAndSessions(true);

        HomeContentResult result = service.content();

        HomeFlashPromotion flash = result.getHomeFlashPromotion();
        assertEquals(BEFORE_WITH_NEXT, project(result));
        assertEquals(currentSession.getStartTime(), flash.getStartTime());
        assertEquals(currentSession.getEndTime(), flash.getEndTime());
        assertEquals(nextSession.getStartTime(), flash.getNextStartTime());
        assertEquals(nextSession.getEndTime(), flash.getNextEndTime());
        assertSame(flashProducts, flash.getProductList());
        verify(homeDao).getFlashProductList(eq(11L), eq(22L));
        assertBlocks(result);
        System.out.println("PROJECT current-with-next " + project(result));
    }

    private void stubPromotionAndSessions(boolean includeNext) {
        when(flashPromotionMapper.selectByExample(any())).thenReturn(List.of(promotion));
        when(promotionSessionMapper.selectByExample(any())).thenAnswer(invocation -> {
            SmsFlashPromotionSessionExample example = invocation.getArgument(0);
            if ("start_time asc".equals(example.getOrderByClause())) {
                return includeNext ? List.of(nextSession) : List.of();
            }
            return List.of(currentSession);
        });
        when(homeDao.getFlashProductList(11L, 22L)).thenReturn(flashProducts);
    }

    private void assertBlocks(HomeContentResult result) {
        assertSame(ads, result.getAdvertiseList());
        assertSame(brands, result.getBrandList());
        assertSame(newProducts, result.getNewProductList());
        assertSame(hotProducts, result.getHotProductList());
        assertSame(subjects, result.getSubjectList());
    }

    private static String project(HomeContentResult result) {
        HomeFlashPromotion flash = result.getHomeFlashPromotion();
        String products = flash.getProductList() == null ? "null" : Long.toString(flash.getProductList().get(0).getId());
        return "advertise=" + result.getAdvertiseList().get(0).getId()
                + "|brand=" + result.getBrandList().get(0).getId()
                + "|fresh=" + result.getNewProductList().get(0).getId()
                + "|hot=" + result.getHotProductList().get(0).getId()
                + "|subject=" + result.getSubjectList().get(0).getId()
                + "|start=" + ms(flash.getStartTime())
                + "|end=" + ms(flash.getEndTime())
                + "|nextStart=" + ms(flash.getNextStartTime())
                + "|nextEnd=" + ms(flash.getNextEndTime())
                + "|products=" + products;
    }

    /** 条件值必须等于调用期间某一时刻经 DateUtil 截断后的值。 */
    private static void assertDerivedFromNow(long actual, long before, long after, UnaryOperator<Date> truncate) {
        for (long t = before; t <= after; t++) {
            if (truncate.apply(new Date(t)).getTime() == actual) {
                return;
            }
        }
        fail("criterion " + actual + " is not derived from a moment in [" + before + ", " + after + "]");
    }

    private static long criterionMillis(SmsFlashPromotionExample example, int index) {
        return ((Date) example.getOredCriteria().get(0).getAllCriteria().get(index).getValue()).getTime();
    }

    private static long criterionMillis(SmsFlashPromotionSessionExample example, int index) {
        return ((Date) example.getOredCriteria().get(0).getAllCriteria().get(index).getValue()).getTime();
    }

    private static String ms(Date date) {
        return date == null ? "null" : Long.toString(date.getTime());
    }

    private static String conditions(SmsFlashPromotionExample example) {
        return example.getOredCriteria().get(0).getAllCriteria().stream()
                .map(criterion -> criterion.getCondition())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private static String conditions(SmsFlashPromotionSessionExample example) {
        return example.getOredCriteria().get(0).getAllCriteria().stream()
                .map(criterion -> criterion.getCondition())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private <T> T dependency(String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
