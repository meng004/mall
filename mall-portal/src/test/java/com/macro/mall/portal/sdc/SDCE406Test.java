package com.macro.mall.portal.sdc;

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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Characterization of the original flash selection. Expectations stay fixed
 * across the later extraction.
 */
class SDCE406Test {
    private static final String PROMOTION_OPERATORS = "status =\nstart_date <=\nend_date >=";
    private static final String CURRENT_OPERATORS = "start_time <=\nend_time >=";
    private static final String NEXT_OPERATORS = "start_time >";

    private HomeServiceImpl service;
    private SmsHomeAdvertiseMapper advertiseMapper;
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
        advertiseMapper = dependency("advertiseMapper", SmsHomeAdvertiseMapper.class);
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
        currentSession.setStartTime(at(1970, Calendar.JANUARY, 1, 10, 0, 0));
        currentSession.setEndTime(at(1970, Calendar.JANUARY, 1, 12, 0, 0));
        nextSession = new SmsFlashPromotionSession();
        nextSession.setId(23L);
        nextSession.setStartTime(at(1970, Calendar.JANUARY, 1, 14, 0, 0));
        nextSession.setEndTime(at(1970, Calendar.JANUARY, 1, 16, 0, 0));
        FlashPromotionProduct flashProduct = new FlashPromotionProduct();
        flashProduct.setId(31L);
        flashProduct.setName("flash-product-31");
        flashProduct.setFlashPromotionPrice(new BigDecimal("9.90"));
        flashProducts = List.of(flashProduct);

        when(advertiseMapper.selectByExample(any())).thenReturn(ads);
        when(homeDao.getRecommendBrandList(0, 6)).thenReturn(brands);
        when(homeDao.getNewProductList(0, 4)).thenReturn(newProducts);
        when(homeDao.getHotProductList(0, 4)).thenReturn(hotProducts);
        when(homeDao.getRecommendSubjectList(0, 4)).thenReturn(subjects);
    }

    @Test
    void noPromotion_keepsEmptyFlashAndOtherBlocks() {
        when(flashPromotionMapper.selectByExample(any())).thenReturn(List.of());

        HomeContentResult result = service.content();

        assertBlocks(result);
        assertEmptyFlash(result.getHomeFlashPromotion());
        verify(promotionSessionMapper, never()).selectByExample(any());
        verify(homeDao, never()).getFlashProductList(any(), any());
        System.out.println("PROJECT no-promotion " + project(result));
    }

    @Test
    void noCurrentSession_skipsNextAndProducts() {
        when(flashPromotionMapper.selectByExample(any())).thenReturn(List.of(promotion));
        AtomicInteger nextQueries = new AtomicInteger();
        when(promotionSessionMapper.selectByExample(any())).thenAnswer(invocation -> {
            SmsFlashPromotionSessionExample example = invocation.getArgument(0);
            if ("start_time asc".equals(example.getOrderByClause())) {
                nextQueries.incrementAndGet();
            }
            return List.of();
        });

        HomeContentResult result = service.content();

        assertBlocks(result);
        assertEmptyFlash(result.getHomeFlashPromotion());
        assertEquals(0, nextQueries.get());
        verify(homeDao, never()).getFlashProductList(any(), any());
        System.out.println("PROJECT no-current-session " + project(result));
    }

    @Test
    void noNextSession_stillLoadsProducts() {
        stubSessions(false);
        HomeContentResult result = service.content();
        HomeFlashPromotion flash = result.getHomeFlashPromotion();
        assertBlocks(result);
        assertEquals(currentSession.getStartTime(), flash.getStartTime());
        assertEquals(currentSession.getEndTime(), flash.getEndTime());
        assertNull(flash.getNextStartTime());
        assertNull(flash.getNextEndTime());
        assertSame(flashProducts, flash.getProductList());
        verify(homeDao).getFlashProductList(eq(11L), eq(22L));
        System.out.println("PROJECT no-next " + project(result));
    }

    @Test
    void fullSession_keepsAssembly() {
        stubSessions(true);
        HomeContentResult result = service.content();
        HomeFlashPromotion flash = result.getHomeFlashPromotion();
        assertBlocks(result);
        assertEquals(currentSession.getStartTime(), flash.getStartTime());
        assertEquals(currentSession.getEndTime(), flash.getEndTime());
        assertEquals(nextSession.getStartTime(), flash.getNextStartTime());
        assertEquals(nextSession.getEndTime(), flash.getNextEndTime());
        assertSame(flashProducts, flash.getProductList());
        verify(homeDao).getFlashProductList(eq(11L), eq(22L));
        System.out.println("PROJECT full " + project(result));
    }

    @Test
    void boundaryOperators_stayInclusiveOnBothEnds() {
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

        Date before = new Date();
        HomeContentResult result = service.content();
        Date after = new Date();

        assertEquals(PROMOTION_OPERATORS, conditions(promotionExample.get()));
        assertEquals(CURRENT_OPERATORS, conditions(currentExample.get()));
        assertEquals(NEXT_OPERATORS, conditions(nextExample.get()));
        assertEquals("start_time asc", nextExample.get().getOrderByClause());
        assertEquals(criterionMillis(promotionExample.get(), 1), criterionMillis(promotionExample.get(), 2));
        assertEquals(criterionMillis(currentExample.get(), 0), criterionMillis(currentExample.get(), 1));
        assertBetween(criterionMillis(promotionExample.get(), 1), DateUtil.getDate(before).getTime(), DateUtil.getDate(after).getTime());
        assertBetween(criterionMillis(currentExample.get(), 0), DateUtil.getTime(before).getTime(), DateUtil.getTime(after).getTime());
        assertEquals(currentSession.getStartTime().getTime(), criterionMillis(nextExample.get(), 0));
        assertEquals(currentSession.getStartTime(), result.getHomeFlashPromotion().getStartTime());
        assertEquals(nextSession.getEndTime(), result.getHomeFlashPromotion().getNextEndTime());
        assertSame(flashProducts, result.getHomeFlashPromotion().getProductList());
        System.out.println("OPERATORS promotion " + PROMOTION_OPERATORS.replace('\n', '|'));
        System.out.println("OPERATORS current " + CURRENT_OPERATORS.replace('\n', '|'));
        System.out.println("OPERATORS next " + NEXT_OPERATORS);
        System.out.println("PROJECT boundary " + project(result));
    }

    private void stubSessions(boolean includeNext) {
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

    private void assertEmptyFlash(HomeFlashPromotion flash) {
        assertEquals(true, flash != null);
        assertNull(flash.getStartTime());
        assertNull(flash.getEndTime());
        assertNull(flash.getNextStartTime());
        assertNull(flash.getNextEndTime());
        assertNull(flash.getProductList());
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

    private static String ms(Date date) {
        return date == null ? "null" : Long.toString(date.getTime());
    }

    private static void assertBetween(long value, long left, long right) {
        long low = Math.min(left, right);
        long high = Math.max(left, right);
        assertTrue(value >= low && value <= high);
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

    private static long criterionMillis(SmsFlashPromotionExample example, int index) {
        return ((Date) example.getOredCriteria().get(0).getAllCriteria().get(index).getValue()).getTime();
    }

    private static long criterionMillis(SmsFlashPromotionSessionExample example, int index) {
        return ((Date) example.getOredCriteria().get(0).getAllCriteria().get(index).getValue()).getTime();
    }

    private static Date at(int year, int month, int day, int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, hour, minute, second);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private <T> T dependency(String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
