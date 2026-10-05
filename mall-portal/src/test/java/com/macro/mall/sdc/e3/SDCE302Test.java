package com.macro.mall.sdc.e3;

import com.macro.mall.mapper.OmsCartItemMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.OmsCartItemServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class SDCE302Test {
    private final OmsCartItemServiceImpl service = new OmsCartItemServiceImpl();

    @Test
    void mergeUpdatesSavedQuantityAndModifyDate() {
        currentMember(101L, "m101");
        OmsCartItemMapper mapper = dependency("cartItemMapper", OmsCartItemMapper.class);
        OmsCartItem existing = new OmsCartItem();
        existing.setId(7L);
        existing.setQuantity(2);
        existing.setModifyDate(new Date(1000L));
        when(mapper.selectByExample(any())).thenReturn(List.of(existing));
        AtomicReference<OmsCartItem> saved = new AtomicReference<>();
        when(mapper.updateByPrimaryKey(any())).thenAnswer(invocation -> {
            OmsCartItem argument = invocation.getArgument(0);
            assertSame(existing, argument);
            OmsCartItem copy = new OmsCartItem();
            copy.setQuantity(argument.getQuantity());
            Date modified = argument.getModifyDate();
            copy.setModifyDate(modified == null ? null : new Date(modified.getTime()));
            saved.set(copy);
            return 1;
        });
        OmsCartItem incoming = new OmsCartItem();
        incoming.setProductId(8L);
        incoming.setQuantity(3);

        Date start = new Date();
        service.add(incoming);
        Date end = new Date();

        OmsCartItem persisted = saved.get();
        assertNotNull(persisted);
        assertEquals(5, persisted.getQuantity());
        assertNotNull(persisted.getModifyDate());
        assertTrue(!persisted.getModifyDate().before(start) && !persisted.getModifyDate().after(end),
                "saved modifyDate=" + persisted.getModifyDate().getTime());
        verify(mapper, never()).insert(any());
    }

    @Test
    void insertKeepsInitialTimeAndMemberSource() {
        currentMember(101L, "m101");
        OmsCartItemMapper mapper = dependency("cartItemMapper", OmsCartItemMapper.class);
        when(mapper.selectByExample(any())).thenReturn(List.of());
        AtomicReference<OmsCartItem> inserted = new AtomicReference<>();
        when(mapper.insert(any())).thenAnswer(invocation -> {
            OmsCartItem argument = invocation.getArgument(0);
            OmsCartItem copy = new OmsCartItem();
            copy.setQuantity(argument.getQuantity());
            copy.setMemberId(argument.getMemberId());
            copy.setMemberNickname(argument.getMemberNickname());
            copy.setDeleteStatus(argument.getDeleteStatus());
            Date created = argument.getCreateDate();
            copy.setCreateDate(created == null ? null : new Date(created.getTime()));
            inserted.set(copy);
            return 1;
        });
        OmsCartItem incoming = new OmsCartItem();
        incoming.setProductId(8L);
        incoming.setQuantity(3);
        incoming.setMemberId(999L);
        incoming.setMemberNickname("other");
        incoming.setDeleteStatus(1);

        Date start = new Date();
        service.add(incoming);
        Date end = new Date();

        OmsCartItem persisted = inserted.get();
        assertNotNull(persisted);
        assertEquals(3, persisted.getQuantity());
        assertEquals(101L, persisted.getMemberId());
        assertEquals("m101", persisted.getMemberNickname());
        assertEquals(0, persisted.getDeleteStatus());
        assertNotNull(persisted.getCreateDate());
        assertTrue(!persisted.getCreateDate().before(start) && !persisted.getCreateDate().after(end));
        verify(mapper, never()).updateByPrimaryKey(any());
    }

    private void currentMember(long id, String nickname) {
        UmsMember member = new UmsMember();
        member.setId(id);
        member.setNickname(nickname);
        UmsMemberService members = dependency("memberService", UmsMemberService.class);
        when(members.getCurrentMember()).thenReturn(member);
    }

    private <T> T dependency(String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }

}
