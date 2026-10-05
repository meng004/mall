package com.macro.mall.sdc.e6;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.UmsMemberReceiveAddressMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberReceiveAddress;
import com.macro.mall.model.UmsMemberReceiveAddressExample;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.UmsMemberReceiveAddressServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class SDCE603Test {
    private static final String FULLWIDTH = "\uFF10\uFF10\uFF11\uFF12\uFF13\uFF14";

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
        when(mapper.selectByExample(any())).thenReturn(List.of(owned()));
    }

    @Test
    void addKeepsNullPostCode() {
        UmsMemberReceiveAddress capturedAddress = captureInsert(postCode(null));
        assertNull(capturedAddress.getPostCode());
        assertEquals(101L, capturedAddress.getMemberId());
    }

    @Test
    void addNormalizesBlankToEmpty() {
        UmsMemberReceiveAddress capturedAddress = captureInsert(postCode("   "));
        assertEquals("", capturedAddress.getPostCode());
    }

    @Test
    void addStripsSixDigitsAndKeepsLeadingZero() {
        UmsMemberReceiveAddress capturedAddress = captureInsert(postCode(" 001234 "));
        assertEquals("001234", capturedAddress.getPostCode());
    }

    @Test
    void addStripsIdeographicSpace() {
        UmsMemberReceiveAddress capturedAddress = captureInsert(postCode("\u3000001234\u3000"));
        assertEquals("001234", capturedAddress.getPostCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567", "12345a", FULLWIDTH})
    void invalidAddDoesNotInsert(String postCode) {
        UmsMemberReceiveAddress request = postCode(postCode);
        ApiException error = assertThrows(ApiException.class, () -> service.add(request));
        assertEquals("邮政编码须为六位数字", error.getMessage());
        verify(mapper, never()).insert(any());
    }

    @Test
    void updateLeavesNullPostCodeUntouched() {
        List<UmsMemberReceiveAddress> captured = new ArrayList<>();
        when(mapper.updateByExampleSelective(any(), any())).thenAnswer(invocation -> {
            captured.add(copy(invocation.getArgument(0), invocation.getArgument(1)));
            return 1;
        });
        UmsMemberReceiveAddress request = new UmsMemberReceiveAddress();
        request.setDefaultStatus(0);
        request.setName("未传邮编");

        assertEquals(1, service.update(11L, request));
        assertNull(captured.get(0).getPostCode());
        assertEquals("未传邮编", captured.get(0).getName());
    }

    @Test
    void updateStripsPaddedPostCodeWithinMemberScope() {
        List<Write> writes = new ArrayList<>();
        when(mapper.updateByExampleSelective(any(), any())).thenAnswer(invocation -> {
            UmsMemberReceiveAddress received = invocation.getArgument(0);
            writes.add(new Write(received.getPostCode(), where(invocation.getArgument(1))));
            return 1;
        });
        UmsMemberReceiveAddress request = postCode(" 001234 ");
        request.setDefaultStatus(0);

        assertEquals(1, service.update(11L, request));
        UmsMemberReceiveAddress capturedAddress = new UmsMemberReceiveAddress();
        capturedAddress.setPostCode(writes.get(0).postCode());
        assertEquals("001234", capturedAddress.getPostCode());
        assertEquals("member_id = 101 AND id = 11", writes.get(0).where());
    }

    @Test
    void updateBlankClearsStoredPostCode() {
        List<UmsMemberReceiveAddress> captured = new ArrayList<>();
        when(mapper.updateByExampleSelective(any(), any())).thenAnswer(invocation -> {
            captured.add(copy(invocation.getArgument(0), invocation.getArgument(1)));
            return 1;
        });
        UmsMemberReceiveAddress request = postCode(" \t ");
        request.setDefaultStatus(0);

        assertEquals(1, service.update(11L, request));
        assertEquals("", captured.get(0).getPostCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567", "12345a", FULLWIDTH})
    void invalidUpdateDoesNotWrite(String postCode) {
        UmsMemberReceiveAddress request = postCode(postCode);
        request.setDefaultStatus(1);
        ApiException error = assertThrows(ApiException.class, () -> service.update(11L, request));
        assertEquals("邮政编码须为六位数字", error.getMessage());
        verify(mapper, never()).updateByExampleSelective(any(), any());
    }

    private UmsMemberReceiveAddress captureInsert(UmsMemberReceiveAddress request) {
        List<UmsMemberReceiveAddress> captured = new ArrayList<>();
        when(mapper.insert(any())).thenAnswer(invocation -> {
            captured.add(copy(invocation.getArgument(0), null));
            return 1;
        });
        assertEquals(1, service.add(request));
        return captured.get(0);
    }

    private static UmsMemberReceiveAddress postCode(String postCode) {
        UmsMemberReceiveAddress request = new UmsMemberReceiveAddress();
        request.setPostCode(postCode);
        return request;
    }

    private static UmsMemberReceiveAddress owned() {
        UmsMemberReceiveAddress address = new UmsMemberReceiveAddress();
        address.setId(11L);
        address.setMemberId(101L);
        address.setDefaultStatus(0);
        return address;
    }

    private static UmsMemberReceiveAddress copy(UmsMemberReceiveAddress received, UmsMemberReceiveAddressExample example) {
        UmsMemberReceiveAddress copy = new UmsMemberReceiveAddress();
        copy.setMemberId(received.getMemberId());
        copy.setPostCode(received.getPostCode());
        copy.setName(received.getName());
        copy.setDefaultStatus(received.getDefaultStatus());
        if (example != null) {
            copy.setDetailAddress(where(example));
        }
        return copy;
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

    private record Write(String postCode, String where) {
    }
}
