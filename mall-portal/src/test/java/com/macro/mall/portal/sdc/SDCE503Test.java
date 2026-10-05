package com.macro.mall.portal.sdc;

import com.macro.mall.mapper.UmsMemberReceiveAddressMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberReceiveAddress;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.UmsMemberReceiveAddressServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Observes today's pass-through of a six-digit post code. It does not lock in
 * acceptance of invalid post codes, and it does not implement the new rule.
 */
class SDCE503Test {
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
        UmsMemberReceiveAddress owned = new UmsMemberReceiveAddress();
        owned.setId(11L);
        owned.setMemberId(101L);
        when(mapper.selectByExample(any())).thenReturn(List.of(owned));
    }

    @Test
    void addPassesOriginalPostCodeToMapper() {
        List<UmsMemberReceiveAddress> captured = new ArrayList<>();
        when(mapper.insert(any())).thenAnswer(invocation -> {
            UmsMemberReceiveAddress received = invocation.getArgument(0);
            captured.add(copy(received));
            return 1;
        });
        UmsMemberReceiveAddress request = new UmsMemberReceiveAddress();
        request.setPostCode("001234");

        assertEquals(1, service.add(request));
        UmsMemberReceiveAddress capturedAddress = captured.get(0);
        assertEquals("001234", capturedAddress.getPostCode());
        assertEquals(101L, capturedAddress.getMemberId());
    }

    @Test
    void updatePassesOriginalPostCodeToMapper() {
        List<UmsMemberReceiveAddress> captured = new ArrayList<>();
        when(mapper.updateByExampleSelective(any(), any())).thenAnswer(invocation -> {
            captured.add(copy(invocation.getArgument(0)));
            return 1;
        });
        UmsMemberReceiveAddress request = new UmsMemberReceiveAddress();
        request.setPostCode("001234");
        request.setDefaultStatus(0);

        assertEquals(1, service.update(11L, request));
        UmsMemberReceiveAddress capturedAddress = captured.get(0);
        assertEquals("001234", capturedAddress.getPostCode());
    }

    @Test
    void postCodeIsAlreadyAStringInModelAndTable() throws Exception {
        assertEquals(String.class, UmsMemberReceiveAddress.class.getDeclaredField("postCode").getType());
        String sql = Files.readString(source("document/sql/mall.sql"));
        assertTrue(sql.contains("`post_code` varchar(100)"));
        String xml = Files.readString(source("mall-mbg/src/main/resources/com/macro/mall/mapper/UmsMemberReceiveAddressMapper.xml"));
        assertTrue(xml.contains("jdbcType=\"VARCHAR\" property=\"postCode\""));
    }

    private static UmsMemberReceiveAddress copy(UmsMemberReceiveAddress received) {
        UmsMemberReceiveAddress copy = new UmsMemberReceiveAddress();
        copy.setMemberId(received.getMemberId());
        copy.setPostCode(received.getPostCode());
        copy.setDefaultStatus(received.getDefaultStatus());
        return copy;
    }

    private static Path source(String relative) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Cannot find " + relative);
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
