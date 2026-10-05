package com.macro.mall.sdc.e3;

import com.macro.mall.mapper.UmsMemberReceiveAddressMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberReceiveAddress;
import com.macro.mall.model.UmsMemberReceiveAddressExample;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.UmsMemberReceiveAddressServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class SDCE307Test {
    private UmsMemberReceiveAddressServiceImpl service;
    private UmsMemberReceiveAddressMapper mapper;

    @BeforeEach
    void setUp() {
        service = new UmsMemberReceiveAddressServiceImpl();
        UmsMemberService memberService = dependency(service, "memberService", UmsMemberService.class);
        mapper = dependency(service, "addressMapper", UmsMemberReceiveAddressMapper.class);
        UmsMember current = new UmsMember();
        current.setId(101L);
        when(memberService.getCurrentMember()).thenReturn(current);
    }

    @Test
    void missingDefaultTargetDoesNotWrite() {
        when(mapper.selectByExample(any())).thenReturn(List.of());
        UmsMemberReceiveAddress request = new UmsMemberReceiveAddress();
        request.setDefaultStatus(1);

        assertEquals(0, service.update(999L, request));
        verify(mapper, never()).updateByExampleSelective(any(), any());
    }

    @Test
    void addressOwnedByAnotherMemberDoesNotWrite() {
        List<String> queries = new ArrayList<>();
        when(mapper.selectByExample(any())).thenAnswer(invocation -> {
            String where = where(invocation.getArgument(0));
            queries.add(where);
            if (where.contains("member_id = 202") && where.contains("id = 12")) {
                return List.of(owned(12L, 202L, 1));
            }
            return List.of();
        });
        UmsMemberReceiveAddress request = new UmsMemberReceiveAddress();
        request.setDefaultStatus(1);

        assertEquals(0, service.update(12L, request));
        verify(mapper, never()).updateByExampleSelective(any(), any());
        assertEquals("member_id = 101 AND id = 12", queries.get(0));
    }

    @Test
    void ownedNonDefaultBecomesDefaultAfterClear() {
        when(mapper.selectByExample(any())).thenReturn(List.of(owned(11L, 101L, 0)));
        List<Write> writes = new ArrayList<>();
        when(mapper.updateByExampleSelective(any(), any())).thenAnswer(invocation -> {
            writes.add(copy(invocation.getArgument(0), invocation.getArgument(1)));
            return writes.get(writes.size() - 1).where().contains("id = 11") ? 1 : 4;
        });
        UmsMemberReceiveAddress request = new UmsMemberReceiveAddress();
        request.setDefaultStatus(1);
        request.setName("有效地址11");

        assertEquals(1, service.update(11L, request));
        assertEquals(2, writes.size());
        assertEquals(0, writes.get(0).defaultStatus());
        assertEquals("member_id = 101 AND default_status = 1", writes.get(0).where());
        assertEquals(1, writes.get(1).defaultStatus());
        assertEquals("有效地址11", writes.get(1).name());
        assertEquals("member_id = 101 AND id = 11", writes.get(1).where());
    }

    @Test
    void alreadyDefaultAddressCanBeUpdatedAgain() {
        when(mapper.selectByExample(any())).thenReturn(List.of(owned(11L, 101L, 1)));
        List<Write> writes = new ArrayList<>();
        when(mapper.updateByExampleSelective(any(), any())).thenAnswer(invocation -> {
            writes.add(copy(invocation.getArgument(0), invocation.getArgument(1)));
            return 1;
        });
        UmsMemberReceiveAddress request = new UmsMemberReceiveAddress();
        request.setDefaultStatus(1);
        request.setName("仍是默认");

        assertEquals(1, service.update(11L, request));
        assertEquals(2, writes.size());
        assertEquals("member_id = 101 AND default_status = 1", writes.get(0).where());
        assertEquals("member_id = 101 AND id = 11", writes.get(1).where());
        assertEquals("仍是默认", writes.get(1).name());
    }

    @Test
    void plainUpdateKeepsSingleMemberScopedWrite() {
        when(mapper.selectByExample(any())).thenReturn(List.of(owned(11L, 101L, 1)));
        List<Write> writes = new ArrayList<>();
        when(mapper.updateByExampleSelective(any(), any())).thenAnswer(invocation -> {
            writes.add(copy(invocation.getArgument(0), invocation.getArgument(1)));
            return 1;
        });
        UmsMemberReceiveAddress request = new UmsMemberReceiveAddress();
        request.setDefaultStatus(0);
        request.setName("普通字段");

        assertEquals(1, service.update(11L, request));
        assertEquals(1, writes.size());
        assertEquals(0, writes.get(0).defaultStatus());
        assertEquals("普通字段", writes.get(0).name());
        assertEquals("member_id = 101 AND id = 11", writes.get(0).where());
    }

    @Test
    void nullDefaultStatusBecomesZeroWithoutClearingOthers() {
        when(mapper.selectByExample(any())).thenReturn(List.of(owned(11L, 101L, 1)));
        List<Write> writes = new ArrayList<>();
        when(mapper.updateByExampleSelective(any(), any())).thenAnswer(invocation -> {
            writes.add(copy(invocation.getArgument(0), invocation.getArgument(1)));
            return 1;
        });
        UmsMemberReceiveAddress request = new UmsMemberReceiveAddress();
        request.setName("未传默认状态");

        assertEquals(1, service.update(11L, request));
        assertEquals(1, writes.size());
        assertEquals(0, writes.get(0).defaultStatus());
        assertEquals("未传默认状态", writes.get(0).name());
        assertEquals("member_id = 101 AND id = 11", writes.get(0).where());
    }

    private static UmsMemberReceiveAddress owned(long id, long memberId, int defaultStatus) {
        UmsMemberReceiveAddress address = new UmsMemberReceiveAddress();
        address.setId(id);
        address.setMemberId(memberId);
        address.setDefaultStatus(defaultStatus);
        return address;
    }

    private static Write copy(UmsMemberReceiveAddress received, UmsMemberReceiveAddressExample example) {
        return new Write(received.getDefaultStatus(), received.getName(), where(example));
    }

    private static String where(UmsMemberReceiveAddressExample example) {
        var criteria = example.getOredCriteria().get(0).getAllCriteria();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < criteria.size(); i++) {
            if (i > 0) {
                text.append(" AND ");
            }
            var criterion = criteria.get(i);
            text.append(criterion.getCondition());
            if (!criterion.isNoValue()) {
                text.append(' ').append(criterion.getValue());
            }
        }
        return text.toString();
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }

    private record Write(Integer defaultStatus, String name, String where) {
    }
}
