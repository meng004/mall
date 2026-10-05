package com.macro.mall.sdc.e7;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.ai.order.ExistingOrderQuestionService;
import com.macro.mall.portal.ai.order.LlmOrderQuestionService;
import com.macro.mall.portal.ai.order.OrderQuestionService;
import com.macro.mall.portal.ai.order.OrderQuestionService.OrderFact;
import com.macro.mall.portal.ai.order.OrderQuestionService.Request;
import com.macro.mall.portal.ai.order.OrderQuestionService.Result;
import com.macro.mall.portal.ai.order.OrderQuestionService.State;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.llm.LlmClient;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class SDCE701Test {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    /** 2026-03-02 是周一；10:00 在 Asia/Shanghai。上周为 2 月 23 日 0 点至 3 月 2 日 0 点，左闭右开。 */
    private static final Clock MONDAY = Clock.fixed(Instant.parse("2026-03-02T02:00:00Z"), SHANGHAI);
    private static final Instant LAST_WEEK_FROM = LocalDate.of(2026, 2, 23).atStartOfDay(SHANGHAI).toInstant();
    private static final Instant LAST_WEEK_UNTIL = LocalDate.of(2026, 3, 2).atStartOfDay(SHANGHAI).toInstant();
    private static final List<Long> PAGE_IDS = List.of(11L, 12L);
    private static final long FILTERED_TOTAL = 5L;
    private static final String LAST_WEEK_JSON = """
            {"action":"query","status":1,"period":"LAST_WEEK","fromDate":null,"throughDate":null}""";

    @AfterEach
    void clearPage() {
        PageHelper.clearPage();
    }

    @Test
    void paidUnshippedLastWeekMatchesStructuredForm() {
        assertEquals(DayOfWeek.MONDAY, LocalDate.now(MONDAY).getDayOfWeek());
        assertEquals(Instant.parse("2026-02-22T16:00:00Z"), LAST_WEEK_FROM);
        assertEquals(Instant.parse("2026-03-01T16:00:00Z"), LAST_WEEK_UNTIL);
        OmsPortalOrderService orders = waitingShipmentPage();
        ExistingOrderQuestionService existing = new ExistingOrderQuestionService(orders, MONDAY, SHANGHAI);
        AtomicReference<String> sent = new AtomicReference<>();
        LlmClient client = (system, input) -> {
            sent.set(input);
            return LAST_WEEK_JSON;
        };
        LlmOrderQuestionService llm = new LlmOrderQuestionService(existing, client, MONDAY, SHANGHAI);
        Request form = new Request(null, 1, LAST_WEEK_FROM, LAST_WEEK_UNTIL, 1, 2);
        Request text = new Request("上周付过钱还没寄的订单", null, null, null, 1, 2);

        Result byForm = existing.query(form);
        Result byText = llm.query(text);

        assertEquals(PAGE_IDS, ids(byForm));
        assertEquals(FILTERED_TOTAL, byForm.total());
        assertEquals(PAGE_IDS, ids(byText));
        assertEquals(FILTERED_TOTAL, byText.total());
        assertEquals(List.of(1, 1), byText.orders().stream().map(OrderFact::status).toList());
        assertEquals("上周付过钱还没寄的订单", sent.get());
        assertFalse(sent.get().contains("11"));
        verify(orders, never()).deleteOrder(any());
        verify(orders, never()).cancelOrder(any());
    }

    @Test
    void blankTextSelectsOrdinaryPathWithoutCallingModel() {
        OmsPortalOrderService orders = waitingShipmentPage();
        ExistingOrderQuestionService existing = new ExistingOrderQuestionService(orders, MONDAY, SHANGHAI);
        LlmOrderQuestionService llm = new LlmOrderQuestionService(existing, (system, input) -> {
            throw new AssertionError("explicit form path must not call the model");
        }, MONDAY, SHANGHAI);

        Result result = llm.query(new Request("  ", 1, LAST_WEEK_FROM, LAST_WEEK_UNTIL, 1, 2));

        assertEquals(State.OK, result.state());
        assertEquals(PAGE_IDS, ids(result));
        assertEquals(FILTERED_TOTAL, result.total());
    }

    @Test
    void vagueNotShippedAsksInsteadOfIncludingUnpaid() {
        OmsPortalOrderService orders = mock(OmsPortalOrderService.class);
        LlmOrderQuestionService llm = llm(orders, (system, input) -> "{\"action\":\"needs_input\"}");

        Result result = llm.query(new Request("没发货", null, null, null, 1, 10));

        assertEquals(State.NEEDS_INPUT, result.state());
        assertTrue(result.orders().isEmpty());
        assertEquals(0L, result.total());
        verify(orders, never()).list(any(), any(), any(), any(), any());
        verify(orders, never()).deleteOrder(any());
    }

    @Test
    void missingLogisticsStaysUnknown() {
        OmsOrderDetail order = order(11L, 1, LAST_WEEK_FROM);
        order.setDeliveryCompany(null);
        order.setDeliverySn("");
        order.setDeliveryTime(Date.from(Instant.parse("2026-03-09T00:00:00Z")));
        order.setReceiverPhone("13812345678");
        order.setReceiverDetailAddress("秘密地址");
        OmsPortalOrderService orders = mock(OmsPortalOrderService.class);
        when(orders.list(eq(1), eq(Date.from(LAST_WEEK_FROM)), eq(Date.from(LAST_WEEK_UNTIL)), eq(1), eq(10)))
                .thenReturn(page(List.of(order), 1));
        ExistingOrderQuestionService existing = new ExistingOrderQuestionService(orders, MONDAY, SHANGHAI);

        Result result = existing.query(new Request(null, 1, LAST_WEEK_FROM, LAST_WEEK_UNTIL, 1, 10));

        assertEquals(State.PARTIAL, result.state());
        assertNull(result.orders().get(0).logisticsCompany());
        assertNull(result.orders().get(0).logisticsCode());
        assertTrue(result.message().contains("未知"));
        assertFalse(result.message().contains("预计"));
        assertFalse(result.message().contains("2026-03-09"));
        assertFalse(result.message().contains("13812345678"));
        assertFalse(result.message().contains("秘密地址"));
    }

    @Test
    void illegalModelOutputIsRejectedAndNotUnavailable() {
        for (String output : List.of(
                "not json",
                "{\"action\":\"query\"} trailing",
                "{\"action\":\"query\",\"status\":1,\"period\":\"ANY\",\"fromDate\":null,\"throughDate\":null,\"memberId\":202}",
                "{\"action\":\"query\",\"status\":1,\"period\":\"ANY\",\"fromDate\":null,\"throughDate\":null,\"orderId\":9}",
                "{\"action\":\"query\",\"status\":1,\"period\":\"ANY\",\"fromDate\":null,\"throughDate\":null,\"sql\":\"delete from oms_order\"}",
                "{\"action\":\"cancel\"}",
                "{\"action\":\"query\",\"status\":6,\"period\":\"ANY\",\"fromDate\":null,\"throughDate\":null}",
                "{\"action\":\"query\",\"status\":1,\"period\":\"LAST_MONTH\",\"fromDate\":null,\"throughDate\":null}",
                "{\"action\":\"query\",\"status\":1,\"period\":\"ANY\",\"fromDate\":\"2026-02-01\",\"throughDate\":null}",
                "{\"action\":\"query\",\"status\":\"1\",\"period\":\"ANY\",\"fromDate\":null,\"throughDate\":null}")) {
            OmsPortalOrderService orders = mock(OmsPortalOrderService.class);
            Result result = llm(orders, (system, input) -> output)
                    .query(new Request("查订单", null, null, null, 1, 10));
            assertEquals(State.UNSUPPORTED, result.state(), output);
            assertTrue(result.orders().isEmpty(), output);
            verify(orders, never()).list(any(), any(), any(), any(), any());
            verify(orders, never()).deleteOrder(any());
            verify(orders, never()).cancelOrder(any());
        }
    }

    @Test
    void modelThrowOrTimeoutIsUnavailableRatherThanRejection() {
        OmsPortalOrderService orders = mock(OmsPortalOrderService.class);
        Result result = llm(orders, (system, input) -> {
            throw new IllegalStateException("timeout secret-token");
        }).query(new Request("上周的订单", null, null, null, 1, 10));

        assertEquals(State.UNAVAILABLE, result.state());
        assertFalse(result.message().contains("secret-token"));
        assertTrue(result.orders().isEmpty());
        verify(orders, never()).list(any(), any(), any(), any(), any());
    }

    @Test
    void rangeUsesInclusiveThroughDateWithoutTrustingModelOffsets() {
        Instant from = LocalDate.of(2026, 2, 1).atStartOfDay(SHANGHAI).toInstant();
        Instant until = LocalDate.of(2026, 2, 4).atStartOfDay(SHANGHAI).toInstant();
        OmsPortalOrderService orders = mock(OmsPortalOrderService.class);
        when(orders.list(eq(null), eq(Date.from(from)), eq(Date.from(until)), eq(1), eq(10)))
                .thenReturn(page(List.of(), 0));
        String json = """
                {"action":"query","status":null,"period":"RANGE","fromDate":"2026-02-01","throughDate":"2026-02-03"}""";

        Result result = llm(orders, (system, input) -> json)
                .query(new Request("2月1日到2月3日的订单", null, null, null, 1, 10));

        assertEquals(State.OK, result.state());
        assertEquals(0L, result.total());
        verify(orders).list(null, Date.from(from), Date.from(until), 1, 10);
    }

    @Test
    void ordinaryTextIsNotParsedAsLanguage() {
        OmsPortalOrderService orders = mock(OmsPortalOrderService.class);
        ExistingOrderQuestionService existing = new ExistingOrderQuestionService(orders, MONDAY, SHANGHAI);

        Result result = existing.query(new Request("上周付过钱还没寄的订单", 1, LAST_WEEK_FROM, LAST_WEEK_UNTIL, 1, 2));

        assertEquals(State.UNSUPPORTED, result.state());
        verify(orders, never()).list(any(), any(), any(), any(), any());
    }

    @Test
    void listKeepsMemberAndDeletedFiltersWhileApplyingHalfOpenRange() {
        OmsPortalOrderServiceImpl service = new OmsPortalOrderServiceImpl();
        currentMember(service, 101L);
        OmsOrderMapper mapper = dependency(service, "orderMapper", OmsOrderMapper.class);
        OmsOrderItemMapper items = dependency(service, "orderItemMapper", OmsOrderItemMapper.class);
        Date from = Date.from(LAST_WEEK_FROM);
        Date until = Date.from(LAST_WEEK_UNTIL);
        when(mapper.selectByExample(any())).thenAnswer(invocation -> {
            assertNotNull(PageHelper.getLocalPage());
            OmsOrderExample example = invocation.getArgument(0);
            assertEquals("create_time desc", example.getOrderByClause());
            Long memberId = null;
            Integer deleteStatus = null;
            Integer status = null;
            Date lower = null;
            Date upper = null;
            for (OmsOrderExample.Criterion criterion : example.getOredCriteria().get(0).getAllCriteria()) {
                switch (criterion.getCondition()) {
                    case "member_id =" -> memberId = (Long) criterion.getValue();
                    case "delete_status =" -> deleteStatus = (Integer) criterion.getValue();
                    case "status =" -> status = (Integer) criterion.getValue();
                    case "create_time >=" -> lower = (Date) criterion.getValue();
                    case "create_time <" -> upper = (Date) criterion.getValue();
                    default -> {
                    }
                }
            }
            assertEquals(101L, memberId);
            assertEquals(0, deleteStatus);
            assertEquals(1, status);
            assertEquals(from, lower);
            assertEquals(until, upper);
            return List.of();
        });

        CommonPage<?> page = service.list(1, from, until, 1, 2);

        assertEquals(0L, page.getTotal());
        assertEquals(1, PageHelper.getLocalPage().getPageNum());
        assertEquals(2, PageHelper.getLocalPage().getPageSize());
        verify(items, never()).selectByExample(any());
    }

    @Test
    void oldListEntryStillOmitsCreateTimeAndKeepsStatusSentinel() {
        OmsPortalOrderServiceImpl service = new OmsPortalOrderServiceImpl();
        currentMember(service, 101L);
        OmsOrderMapper mapper = dependency(service, "orderMapper", OmsOrderMapper.class);
        when(mapper.selectByExample(any())).thenAnswer(invocation -> {
            OmsOrderExample example = invocation.getArgument(0);
            boolean sawStatus = false;
            for (OmsOrderExample.Criterion criterion : example.getOredCriteria().get(0).getAllCriteria()) {
                assertFalse(criterion.getCondition().startsWith("create_time"));
                if ("status =".equals(criterion.getCondition())) {
                    sawStatus = true;
                }
                if ("member_id =".equals(criterion.getCondition())) {
                    assertEquals(101L, criterion.getValue());
                }
                if ("delete_status =".equals(criterion.getCondition())) {
                    assertEquals(0, criterion.getValue());
                }
            }
            assertFalse(sawStatus);
            return List.of();
        });

        assertNull(service.list(-1, 2, 5).getList());
    }

    @Test
    void unavailableDoesNotCountAsSuccessfulRejection() {
        Result unavailable = new Result(State.UNAVAILABLE, List.of(), 0, "模型不可用");
        Result rejected = new Result(State.UNSUPPORTED, List.of(), 0, "不支持");
        assertFalse(CandidateE701Evaluation.matches("UNSUPPORTED", List.of(), 0, unavailable));
        assertTrue(CandidateE701Evaluation.matches("UNSUPPORTED", List.of(), 0, rejected));
        assertEquals("INCOMPLETE", CandidateE701Evaluation.verdict(20, 18, 12, 12, true, false));
        assertEquals("DEGRADE", CandidateE701Evaluation.verdict(20, 17, 12, 12, true, false));
        assertEquals("NO_RELEASE", CandidateE701Evaluation.verdict(20, 20, 12, 11, true, false));
        assertEquals("REJECT", CandidateE701Evaluation.verdict(20, 20, 12, 12, true, true));
        assertEquals("INCOMPLETE", CandidateE701Evaluation.verdict(19, 19, 12, 12, true, false));
        assertEquals("NO_RELEASE", CandidateE701Evaluation.verdict(20, 10, 12, 12, false, false));
        assertEquals("驳回", EvaluationSupport.decision(true, false, true, 20, 20, true));
        assertEquals("未完成", EvaluationSupport.decision(false, true, true, 20, 20, true));
        assertEquals("未完成", EvaluationSupport.decision(false, false, false, 19, 19, true));
        assertEquals("降级", EvaluationSupport.decision(false, false, true, 20, 17, true));
    }

    @Test
    void incompleteWrongStatusOrdersAreNotReady() throws Exception {
        var facts = new ObjectMapper().readTree("""
                {"memberScope":{"currentMemberId":101},"orders":[
                  {"id":1,"memberId":101,"statusName":"待发货","deleted":false},
                  {"id":2,"memberId":101,"statusName":"待发货","deleted":false}]}
                """);
        OmsOrderDetail wrong = new OmsOrderDetail();
        wrong.setId(1L);
        wrong.setMemberId(101L);
        wrong.setStatus(4);
        CommonPage<OmsOrderDetail> incomplete = new CommonPage<>();
        incomplete.setList(List.of(wrong));
        incomplete.setTotal(1L);
        OmsPortalOrderService orders = (OmsPortalOrderService) Proxy.newProxyInstance(
                OmsPortalOrderService.class.getClassLoader(), new Class<?>[] {OmsPortalOrderService.class},
                (proxy, method, values) -> method.getName().equals("list") ? incomplete : null);
        assertFalse(CandidateE701Evaluation.teachingOrdersReady(orders, facts));
        assertFalse(EvaluationSupport.modelScored(false, false, false));
        var readyFacts = new ObjectMapper().readTree("""
                {"memberScope":{"currentMemberId":101},"orderStatus":{"codes":{"1":"待发货"}},
                 "orders":[{"id":1,"memberId":101,"statusName":"待发货","deleted":false}]}
                """);
        OmsOrderDetail matched = new OmsOrderDetail();
        matched.setId(1L);
        matched.setMemberId(101L);
        matched.setStatus(1);
        matched.setDeleteStatus(0);
        CommonPage<OmsOrderDetail> complete = new CommonPage<>();
        complete.setList(List.of(matched));
        complete.setTotal(1L);
        OmsPortalOrderService readyOrders = (OmsPortalOrderService) Proxy.newProxyInstance(
                OmsPortalOrderService.class.getClassLoader(), new Class<?>[] {OmsPortalOrderService.class},
                (proxy, method, values) -> method.getName().equals("list") ? complete : null);
        assertTrue(CandidateE701Evaluation.teachingOrdersReady(readyOrders, readyFacts));
        assertTrue(EvaluationSupport.modelScored(true, false, false));
    }

    @Test
    void deletedOrderPastFirstPageIsNotReady() throws Exception {
        StringBuilder body = new StringBuilder("{\"memberScope\":{\"currentMemberId\":101},\"orderStatus\":{\"codes\":{\"1\":\"待发货\"}},\"orders\":[");
        for (int i = 1; i <= 100; i++) {
            if (i > 1) {
                body.append(',');
            }
            body.append("{\"id\":").append(i).append(",\"memberId\":101,\"statusName\":\"待发货\",\"deleted\":false}");
        }
        body.append(",{\"id\":101,\"memberId\":101,\"statusName\":\"待发货\",\"deleted\":true}]}");
        var facts = new ObjectMapper().readTree(body.toString());
        AtomicInteger maxPage = new AtomicInteger();
        OmsPortalOrderService orders = (OmsPortalOrderService) Proxy.newProxyInstance(
                OmsPortalOrderService.class.getClassLoader(), new Class<?>[] {OmsPortalOrderService.class},
                (proxy, method, values) -> {
                    if (!method.getName().equals("list")) {
                        return null;
                    }
                    int pageNum = (Integer) values[3];
                    maxPage.set(Math.max(maxPage.get(), pageNum));
                    CommonPage<OmsOrderDetail> page = new CommonPage<>();
                    page.setTotal(101L);
                    List<OmsOrderDetail> rows = new ArrayList<>();
                    if (pageNum == 1) {
                        for (int i = 1; i <= 100; i++) {
                            rows.add(order(i, 1, LAST_WEEK_FROM));
                        }
                    } else if (pageNum == 2) {
                        OmsOrderDetail leaked = order(101, 1, LAST_WEEK_FROM);
                        leaked.setDeleteStatus(1);
                        rows.add(leaked);
                    }
                    page.setList(rows);
                    return page;
                });
        assertFalse(CandidateE701Evaluation.teachingOrdersReady(orders, facts));
        assertTrue(maxPage.get() >= 2);
    }

    @Test
    void unreachableTeachingDatabaseStaysBlocked() {
        EvaluationSupport.TeachingGate gate = EvaluationSupport.connectSdc(
                "jdbc:mysql://127.0.0.1:1/mall?connectTimeout=1000&socketTimeout=1000", "mall", "sdc-local-app");
        assertFalse(gate.reachable());
        assertFalse(gate.isolated());
        assertTrue(gate.note().startsWith("ConnectException") || gate.note().startsWith("SQLException")
                || gate.note().contains("CommunicationsException"));
        assertFalse(gate.note().equals("教学库不可达。预检失败，isolated 保持 false，不改为 true。未启动容器，未调用模型。"));
        assertEquals("未完成", EvaluationSupport.decision(false, true, false, 0, 0, false));
        Request request = CandidateE701Evaluation.modelRequest("上周付过钱还没寄的订单", 1, 10);
        assertNull(request.status());
        assertNull(request.fromInclusive());
    }

    private static LlmOrderQuestionService llm(OmsPortalOrderService orders, LlmClient client) {
        return new LlmOrderQuestionService(new ExistingOrderQuestionService(orders, MONDAY, SHANGHAI), client, MONDAY, SHANGHAI);
    }

    private static OmsPortalOrderService waitingShipmentPage() {
        OmsOrderDetail first = order(11L, 1, LAST_WEEK_FROM.plusSeconds(3600));
        first.setDeliveryCompany("顺丰");
        first.setDeliverySn("SF1");
        OmsOrderDetail second = order(12L, 1, LAST_WEEK_FROM.plusSeconds(7200));
        second.setDeliveryCompany("圆通");
        second.setDeliverySn("YT2");
        OmsPortalOrderService orders = mock(OmsPortalOrderService.class);
        when(orders.list(eq(1), eq(Date.from(LAST_WEEK_FROM)), eq(Date.from(LAST_WEEK_UNTIL)), eq(1), eq(2)))
                .thenReturn(page(List.of(first, second), FILTERED_TOTAL));
        return orders;
    }

    private static OmsOrderDetail order(long id, int status, Instant createdAt) {
        OmsOrderDetail order = new OmsOrderDetail();
        order.setId(id);
        order.setStatus(status);
        order.setCreateTime(Date.from(createdAt));
        order.setMemberId(101L);
        order.setDeleteStatus(0);
        return order;
    }

    private static CommonPage<OmsOrderDetail> page(List<OmsOrderDetail> rows, long total) {
        CommonPage<OmsOrderDetail> page = new CommonPage<>();
        page.setList(rows);
        page.setTotal(total);
        page.setPageNum(1);
        page.setPageSize(rows.isEmpty() ? 10 : rows.size());
        return page;
    }

    private static List<Long> ids(Result result) {
        return result.orders().stream().map(OrderFact::id).toList();
    }

    private static void currentMember(OmsPortalOrderServiceImpl service, long id) {
        UmsMember member = new UmsMember();
        member.setId(id);
        UmsMemberService members = dependency(service, "memberService", UmsMemberService.class);
        when(members.getCurrentMember()).thenReturn(member);
    }

    private static <T> T dependency(OmsPortalOrderServiceImpl service, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
