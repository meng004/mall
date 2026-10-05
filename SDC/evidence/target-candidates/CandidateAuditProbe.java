import com.macro.mall.common.exception.GlobalExceptionHandler;
import com.macro.mall.controller.PmsProductAttributeController;
import com.macro.mall.controller.SmsCouponController;
import com.macro.mall.dao.SmsCouponProductCategoryRelationDao;
import com.macro.mall.dao.SmsCouponProductRelationDao;
import com.macro.mall.dto.PmsProductAttributeParam;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.SmsCouponHistoryDetail;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.OmsCartItemServiceImpl;
import com.macro.mall.portal.service.impl.UmsMemberCouponServiceImpl;
import com.macro.mall.service.PmsProductAttributeService;
import com.macro.mall.service.impl.PmsProductAttributeServiceImpl;
import com.macro.mall.service.impl.SmsCouponServiceImpl;
import jakarta.validation.UnexpectedTypeException;
import jakarta.validation.Validation;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Evidence only: current source is expected to fail these six proposed specifications. */
public class CandidateAuditProbe {
    private static int specificationFailures;

    public static void main(String[] args) throws Exception {
        couponThreshold();
        cartTimestamp();
        attributeBatchDelete();
        attributeMove();
        couponScopeChange();
        attributeValidation();
        System.out.println("SUMMARY: checks=6 specificationFailures=" + specificationFailures);
        if (specificationFailures > 0) {
            throw new AssertionError("Current implementation does not satisfy "
                    + specificationFailures + " recorded specifications; see each FAIL above.");
        }
    }

    private static <T> T dependency(Object target, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(target, field, value);
        return value;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("Probe setup/control failure: " + message);
    }

    private static void result(String id, boolean conforms, String actual, String expected) {
        if (!conforms) specificationFailures++;
        System.out.println(id + (conforms ? " PASS" : " FAIL")
                + " actual={" + actual + "} expected={" + expected + "}");
    }

    private static UmsMember currentMember(Object service) {
        var member = new UmsMember();
        member.setId(1L);
        member.setNickname("synthetic-member");
        when(dependency(service, "memberService", UmsMemberService.class).getCurrentMember())
                .thenReturn(member);
        return member;
    }

    private static MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static void couponThreshold() {
        var service = new UmsMemberCouponServiceImpl();
        currentMember(service);
        var dao = dependency(service, "couponHistoryDao", SmsCouponHistoryDao.class);
        var coupon = new SmsCoupon();
        coupon.setUseType(0);
        coupon.setMinPoint(new BigDecimal("100.00"));
        coupon.setEndTime(new Date(System.currentTimeMillis() + 86400000L));
        var history = new SmsCouponHistoryDetail();
        history.setCoupon(coupon);
        when(dao.getDetailList(1L)).thenReturn(List.of(history));
        var item = new CartPromotionItem();
        item.setReduceAmount(BigDecimal.ZERO);
        item.setQuantity(1);
        item.setPrice(new BigDecimal("98.90"));
        check(service.listCart(List.of(item), 1).isEmpty(), "98.90 below threshold");
        item.setPrice(new BigDecimal("100.00"));
        check(service.listCart(List.of(item), 1).size() == 1, "100.00 at threshold");
        item.setPrice(new BigDecimal("99.90"));
        int available = service.listCart(List.of(item), 1).size();
        result("E3-01", available == 0, "99.90 available=" + available, "available=0");
    }

    private static void cartTimestamp() {
        var service = new OmsCartItemServiceImpl();
        currentMember(service);
        var mapper = dependency(service, "cartItemMapper", OmsCartItemMapper.class);
        var existing = new OmsCartItem();
        existing.setId(10L);
        existing.setProductId(100L);
        existing.setProductSkuId(200L);
        existing.setQuantity(2);
        existing.setModifyDate(new Date(1000L));
        when(mapper.selectByExample(any())).thenReturn(List.of(existing));
        AtomicReference<OmsCartItem> saved = new AtomicReference<>();
        when(mapper.updateByPrimaryKey(any())).thenAnswer(call -> {
            saved.set(call.getArgument(0));
            return 1;
        });
        var incoming = new OmsCartItem();
        incoming.setProductId(100L);
        incoming.setProductSkuId(200L);
        incoming.setQuantity(3);
        long before = System.currentTimeMillis();
        check(service.add(incoming) == 1, "cart update succeeded");
        check(saved.get().getQuantity() == 5, "cart quantities merged");
        long persistedTime = saved.get().getModifyDate().getTime();
        result("E3-02", persistedTime >= before,
                "persistedModifyTime=" + persistedTime + ",quantity=5",
                "persistedModifyTime refreshed");
    }

    private static PmsProductAttribute attribute(long id, long category, int type) {
        var value = new PmsProductAttribute();
        value.setId(id);
        value.setProductAttributeCategoryId(category);
        value.setType(type);
        return value;
    }

    private static PmsProductAttributeCategory category(long id, int attributes, int params) {
        var value = new PmsProductAttributeCategory();
        value.setId(id);
        value.setAttributeCount(attributes);
        value.setParamCount(params);
        return value;
    }

    private static void attributeBatchDelete() throws Exception {
        var service = new PmsProductAttributeServiceImpl();
        var attrs = dependency(service, "productAttributeMapper", PmsProductAttributeMapper.class);
        var categories = dependency(service, "productAttributeCategoryMapper", PmsProductAttributeCategoryMapper.class);
        var a = category(101L, 2, 0);
        var b = category(202L, 2, 0);
        when(attrs.selectByPrimaryKey(11L)).thenReturn(attribute(11L, 101L, 0));
        when(attrs.selectByPrimaryKey(22L)).thenReturn(attribute(22L, 202L, 0));
        when(attrs.deleteByExample(any())).thenReturn(2);
        when(categories.selectByPrimaryKey(101L)).thenReturn(a);
        when(categories.selectByPrimaryKey(202L)).thenReturn(b);
        var controller = new PmsProductAttributeController();
        ReflectionTestUtils.setField(controller, "productAttributeService", service);
        var response = mvc(controller).perform(post("/productAttribute/delete")
                .param("ids", "11", "22")).andReturn().getResponse();
        check(response.getStatus() == 200 && response.getContentAsString().contains("\"code\":200"),
                "mixed IDs accepted by controller");
        verify(attrs).deleteByExample(any());
        result("E3-03", a.getAttributeCount() == 1 && b.getAttributeCount() == 1,
                "category101=" + a.getAttributeCount() + ",category202=" + b.getAttributeCount(),
                "category101=1,category202=1 for accepted mixed deletion");
    }

    private static void attributeMove() throws Exception {
        var service = new PmsProductAttributeServiceImpl();
        var attrs = dependency(service, "productAttributeMapper", PmsProductAttributeMapper.class);
        var categories = dependency(service, "productAttributeCategoryMapper", PmsProductAttributeCategoryMapper.class);
        var oldCategory = category(101L, 1, 0);
        var newCategory = category(202L, 0, 0);
        var stored = attribute(11L, 101L, 0);
        when(attrs.selectByPrimaryKey(11L)).thenReturn(stored);
        when(categories.selectByPrimaryKey(101L)).thenReturn(oldCategory);
        when(categories.selectByPrimaryKey(202L)).thenReturn(newCategory);
        when(attrs.updateByPrimaryKeySelective(any())).thenAnswer(call -> {
            PmsProductAttribute update = call.getArgument(0);
            stored.setProductAttributeCategoryId(update.getProductAttributeCategoryId());
            stored.setType(update.getType());
            return 1;
        });
        var controller = new PmsProductAttributeController();
        ReflectionTestUtils.setField(controller, "productAttributeService", service);
        var response = mvc(controller).perform(post("/productAttribute/update/11")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"synthetic attribute\",\"productAttributeCategoryId\":202,\"type\":1}"))
                .andReturn().getResponse();
        check(response.getStatus() == 200 && response.getContentAsString().contains("\"code\":200"),
                "category/type change accepted by controller");
        check(stored.getProductAttributeCategoryId() == 202L && stored.getType() == 1,
                "attribute changed category and type");
        result("E3-04", oldCategory.getAttributeCount() == 0 && newCategory.getParamCount() == 1,
                "oldAttributeCount=" + oldCategory.getAttributeCount() + ",newParamCount=" + newCategory.getParamCount(),
                "oldAttributeCount=0,newParamCount=1");
    }

    private static void couponScopeChange() throws Exception {
        var service = new SmsCouponServiceImpl();
        var coupons = dependency(service, "couponMapper", SmsCouponMapper.class);
        var products = dependency(service, "productRelationMapper", SmsCouponProductRelationMapper.class);
        var categories = dependency(service, "productCategoryRelationMapper", SmsCouponProductCategoryRelationMapper.class);
        dependency(service, "productRelationDao", SmsCouponProductRelationDao.class);
        dependency(service, "productCategoryRelationDao", SmsCouponProductCategoryRelationDao.class);
        boolean[] oldProductRelationRemains = {true};
        boolean[] oldCategoryRelationRemains = {true};
        when(products.deleteByExample(any())).thenAnswer(call -> { oldProductRelationRemains[0] = false; return 1; });
        when(categories.deleteByExample(any())).thenAnswer(call -> { oldCategoryRelationRemains[0] = false; return 1; });
        when(coupons.updateByPrimaryKey(any())).thenReturn(1);
        var controller = new SmsCouponController();
        ReflectionTestUtils.setField(controller, "couponService", service);
        var response = mvc(controller).perform(post("/coupon/update/9")
                .contentType(MediaType.APPLICATION_JSON).content("{\"useType\":0}"))
                .andReturn().getResponse();
        check(response.getStatus() == 200 && response.getContentAsString().contains("\"code\":200"),
                "all-shop scope update accepted");
        result("E3-05", !oldProductRelationRemains[0] && !oldCategoryRelationRemains[0],
                "oldProductRelation=" + oldProductRelationRemains[0] + ",oldCategoryRelation=" + oldCategoryRelationRemains[0],
                "both false under the proposed relation-cleanup specification");
    }

    private static void attributeValidation() throws Exception {
        boolean wrongConstraintType = false;
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var dto = new PmsProductAttributeParam();
            dto.setName("synthetic attribute");
            dto.setProductAttributeCategoryId(101L);
            try {
                factory.getValidator().validateProperty(dto, "productAttributeCategoryId");
            } catch (UnexpectedTypeException expected) {
                wrongConstraintType = true;
            }
        }
        var controller = new PmsProductAttributeController();
        PmsProductAttributeService service = dependency(controller, "productAttributeService", PmsProductAttributeService.class);
        when(service.create(any())).thenReturn(1);
        var response = mvc(controller).perform(post("/productAttribute/create")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse();
        boolean invalidRequestReachedService = mockingDetails(service).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("create"));
        result("E3-06", !wrongConstraintType && !invalidRequestReachedService,
                "validLongThrowsUnexpectedType=" + wrongConstraintType
                        + ",emptyBodyReachedService=" + invalidRequestReachedService
                        + ",httpStatus=" + response.getStatus(),
                "valid Long is validatable; empty required fields rejected before service");
    }
}
