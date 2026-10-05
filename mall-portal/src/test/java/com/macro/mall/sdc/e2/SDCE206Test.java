package com.macro.mall.sdc.e2;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.OmsOrderItemExample;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * E2-06：订单列表与详情必须共用"当前会员、未删除"的读取范围。
 * 订单表替身里有本人订单 11、会员 202 的订单 22、本人已删除订单 33，44 不存在；
 * 按主键读取与按条件读取看到的是同一组数据。
 */
class SDCE206Test {
    private static final long VISIBLE_ID = 11L;
    private final OmsPortalOrderServiceImpl service = new OmsPortalOrderServiceImpl();

    @AfterEach
    void clearPage() {
        PageHelper.clearPage();
    }

    @Test
    void ownUndeletedDetailReturnsMatchingItems() {
        ordersScopedToMember101();
        OmsOrderItemMapper items = dependency("orderItemMapper", OmsOrderItemMapper.class);
        when(items.selectByExample(any())).thenAnswer(invocation -> {
            OmsOrderItemExample example = invocation.getArgument(0);
            assertEquals(VISIBLE_ID, orderIdAt(example));
            OmsOrderItem item = new OmsOrderItem();
            item.setId(21L);
            item.setOrderId(VISIBLE_ID);
            item.setProductName("phone");
            return List.of(item);
        });

        OmsOrderDetail detail = service.detail(VISIBLE_ID);

        assertEquals(VISIBLE_ID, detail.getId());
        assertEquals(101L, detail.getMemberId());
        assertEquals(0, detail.getDeleteStatus());
        assertEquals(1, detail.getOrderItemList().size());
        assertEquals(21L, detail.getOrderItemList().get(0).getId());
        assertEquals(VISIBLE_ID, detail.getOrderItemList().get(0).getOrderId());
        assertEquals("phone", detail.getOrderItemList().get(0).getProductName());
    }

    @Test
    void foreignDeletedAndMissingDetailsFailTheSameWay() {
        ordersScopedToMember101();
        OmsOrderItemMapper items = dependency("orderItemMapper", OmsOrderItemMapper.class);

        ApiException foreign = assertThrows(ApiException.class, () -> service.detail(22L));
        ApiException deleted = assertThrows(ApiException.class, () -> service.detail(33L));
        ApiException missing = assertThrows(ApiException.class, () -> service.detail(44L));

        assertEquals("订单不存在", foreign.getMessage());
        assertEquals(foreign.getMessage(), deleted.getMessage());
        assertEquals(foreign.getMessage(), missing.getMessage());
        verify(items, never()).selectByExample(any());
    }

    @Test
    void listAndDetailShareMemberScopeWithoutChangingPaging() {
        ordersScopedToMember101();
        OmsOrderItemMapper items = dependency("orderItemMapper", OmsOrderItemMapper.class);
        when(items.selectByExample(any())).thenAnswer(invocation -> {
            OmsOrderItem item = new OmsOrderItem();
            item.setOrderId(orderIdAt(invocation.getArgument(0)));
            item.setProductName("phone");
            return List.of(item);
        });

        CommonPage<OmsOrderDetail> page = service.list(-1, 2, 5);
        Page<Object> local = PageHelper.getLocalPage();
        OmsOrderDetail listed = page.getList().get(0);
        OmsOrderDetail detailed = service.detail(listed.getId());

        assertEquals(2, local.getPageNum());
        assertEquals(5, local.getPageSize());
        assertEquals(1, page.getPageNum());
        assertEquals(1, page.getPageSize());
        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getTotalPage());
        assertEquals(listed.getId(), detailed.getId());
        assertEquals(listed.getMemberId(), detailed.getMemberId());
        assertEquals("phone", detailed.getOrderItemList().get(0).getProductName());
        assertThrows(ApiException.class, () -> service.detail(22L));
    }

    @Test
    void listKeepsStatusFilterAndEmptyPage() {
        OmsOrderMapper orders = dependency("orderMapper", OmsOrderMapper.class);
        OmsOrderItemMapper items = dependency("orderItemMapper", OmsOrderItemMapper.class);
        currentMember(101L);
        AtomicInteger calls = new AtomicInteger();
        when(orders.selectByExample(any())).thenAnswer(invocation -> {
            Scope scope = scopeAt(invocation.getArgument(0));
            assertEquals(101L, scope.memberId());
            assertEquals(0, scope.deleteStatus());
            assertEquals("create_time desc", scope.orderBy());
            if (calls.incrementAndGet() == 1) {
                assertNull(scope.status());
            } else {
                assertEquals(1, scope.status());
            }
            return List.of();
        });

        CommonPage<OmsOrderDetail> all = service.list(-1, 1, 5);
        assertNull(all.getList());
        PageHelper.clearPage();

        CommonPage<OmsOrderDetail> waitingPay = service.list(1, 3, 4);
        assertNull(waitingPay.getList());
        assertEquals(3, PageHelper.getLocalPage().getPageNum());
        assertEquals(4, PageHelper.getLocalPage().getPageSize());
        verify(items, never()).selectByExample(any());
    }

    private OmsOrderMapper ordersScopedToMember101() {
        currentMember(101L);
        OmsOrderMapper orders = dependency("orderMapper", OmsOrderMapper.class);
        when(orders.selectByExample(any())).thenAnswer(invocation -> {
            Scope scope = scopeAt(invocation.getArgument(0));
            assertEquals(101L, scope.memberId());
            assertEquals(0, scope.deleteStatus());
            if (scope.id() != null && scope.id() != VISIBLE_ID) {
                return List.of();
            }
            if (scope.id() == null && scope.status() != null) {
                return List.of();
            }
            return List.of(order(VISIBLE_ID, 101L, 0));
        });
        when(orders.selectByPrimaryKey(any())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            if (id == VISIBLE_ID) return order(VISIBLE_ID, 101L, 0);
            if (id == 22L) return order(22L, 202L, 0);
            if (id == 33L) return order(33L, 101L, 1);
            return null;
        });
        return orders;
    }

    private static OmsOrder order(long id, long memberId, int deleteStatus) {
        OmsOrder order = new OmsOrder();
        order.setId(id);
        order.setMemberId(memberId);
        order.setDeleteStatus(deleteStatus);
        order.setStatus(1);
        return order;
    }

    private void currentMember(long id) {
        UmsMember member = new UmsMember();
        member.setId(id);
        UmsMemberService members = dependency("memberService", UmsMemberService.class);
        when(members.getCurrentMember()).thenReturn(member);
    }

    private static long orderIdAt(OmsOrderItemExample example) {
        for (OmsOrderItemExample.Criterion criterion : example.getOredCriteria().get(0).getAllCriteria()) {
            if ("order_id =".equals(criterion.getCondition())) {
                return (Long) criterion.getValue();
            }
            if ("order_id in".equals(criterion.getCondition())) {
                return (Long) ((List<?>) criterion.getValue()).get(0);
            }
        }
        throw new AssertionError("order id missing at item query");
    }

    private static Scope scopeAt(OmsOrderExample example) {
        Long id = null;
        Long memberId = null;
        Integer deleteStatus = null;
        Integer status = null;
        for (OmsOrderExample.Criterion criterion : example.getOredCriteria().get(0).getAllCriteria()) {
            switch (criterion.getCondition()) {
                case "id =" -> id = (Long) criterion.getValue();
                case "member_id =" -> memberId = (Long) criterion.getValue();
                case "delete_status =" -> deleteStatus = (Integer) criterion.getValue();
                case "status =" -> status = (Integer) criterion.getValue();
                default -> {
                }
            }
        }
        return new Scope(id, memberId, deleteStatus, status, example.getOrderByClause());
    }

    private <T> T dependency(String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }

    private record Scope(Long id, Long memberId, Integer deleteStatus, Integer status, String orderBy) {
    }
}
