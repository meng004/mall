package com.macro.mall.sdc.e2;

import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberExample;
import com.macro.mall.portal.service.UmsMemberCacheService;
import com.macro.mall.portal.service.impl.UmsMemberServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class SDCE207Test {
    private final UmsMemberServiceImpl service = new UmsMemberServiceImpl();

    @Test
    void cacheHitReturnsMemberWithoutMapperOrBackfill() {
        UmsMemberCacheService cache = dependency("memberCacheService", UmsMemberCacheService.class);
        UmsMemberMapper mapper = dependency("memberMapper", UmsMemberMapper.class);
        UmsMember cached = member(101L, "member-101");
        when(cache.getMember("member-101")).thenReturn(cached);

        UmsMember result = service.getByUsername("member-101");

        assertSame(cached, result);
        assertEquals(101L, result.getId());
        verify(mapper, never()).selectByExample(any());
        verify(cache, never()).setMember(any());
    }

    @Test
    void cacheMissLoadsByUsernameAndBackfillsOnce() {
        UmsMemberCacheService cache = dependency("memberCacheService", UmsMemberCacheService.class);
        UmsMemberMapper mapper = dependency("memberMapper", UmsMemberMapper.class);
        UmsMember stored = member(101L, "member-101");
        AtomicReference<String> queriedUsername = new AtomicReference<>();
        when(cache.getMember("member-101")).thenReturn(null);
        when(mapper.selectByExample(any())).thenAnswer(invocation -> {
            UmsMemberExample example = invocation.getArgument(0);
            queriedUsername.set(usernameEqualTo(example));
            return List.of(stored);
        });
        AtomicReference<Long> backfilledId = new AtomicReference<>();
        doAnswer(invocation -> {
            UmsMember member = invocation.getArgument(0);
            backfilledId.set(member.getId());
            return null;
        }).when(cache).setMember(any());

        UmsMember result = service.getByUsername("member-101");

        assertEquals(101L, result.getId());
        assertEquals("member-101", queriedUsername.get());
        assertEquals(101L, backfilledId.get());
        verify(cache).setMember(stored);
        verify(mapper).selectByExample(any());
    }

    @Test
    void missingMemberReturnsNullWithoutBackfill() {
        UmsMemberCacheService cache = dependency("memberCacheService", UmsMemberCacheService.class);
        UmsMemberMapper mapper = dependency("memberMapper", UmsMemberMapper.class);
        when(cache.getMember("nobody")).thenReturn(null);
        when(mapper.selectByExample(any())).thenReturn(List.of());

        assertNull(service.getByUsername("nobody"));
        verify(cache, never()).setMember(any());
        verify(mapper).selectByExample(any());
    }

    @Test
    void cacheReadExceptionPropagatesWithoutSilentFallback() {
        UmsMemberCacheService cache = dependency("memberCacheService", UmsMemberCacheService.class);
        UmsMemberMapper mapper = dependency("memberMapper", UmsMemberMapper.class);
        IllegalStateException failure = new IllegalStateException("cache read failed");
        when(cache.getMember("member-101")).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.getByUsername("member-101"));

        assertSame(failure, thrown);
        verifyNoInteractions(mapper);
        verify(cache, never()).setMember(any());
    }

    @Test
    void cacheBackfillExceptionPropagatesWithoutSilentFallback() {
        UmsMemberCacheService cache = dependency("memberCacheService", UmsMemberCacheService.class);
        UmsMemberMapper mapper = dependency("memberMapper", UmsMemberMapper.class);
        when(cache.getMember("member-101")).thenReturn(null);
        when(mapper.selectByExample(any())).thenReturn(List.of(member(101L, "member-101")));
        IllegalStateException failure = new IllegalStateException("cache backfill failed");
        doAnswer(invocation -> {
            throw failure;
        }).when(cache).setMember(any());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.getByUsername("member-101"));

        assertSame(failure, thrown);
    }

    private static UmsMember member(long id, String username) {
        UmsMember member = new UmsMember();
        member.setId(id);
        member.setUsername(username);
        return member;
    }

    private static String usernameEqualTo(UmsMemberExample example) {
        for (UmsMemberExample.Criterion criterion : example.getOredCriteria().get(0).getAllCriteria()) {
            if ("username =".equals(criterion.getCondition())) {
                return (String) criterion.getValue();
            }
        }
        return null;
    }

    private <T> T dependency(String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
