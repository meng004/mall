package com.macro.mall.sdc.e6;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.controller.MemberReadHistoryController;
import com.macro.mall.portal.repository.MemberReadHistoryRepository;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.MemberReadHistoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SDCE604Test {
    private final MemberReadHistoryServiceImpl service = new MemberReadHistoryServiceImpl();
    private MemberReadHistoryRepository repository;
    private MemberReadHistoryController controller;

    @BeforeEach
    void setUp() {
        repository = dependency(service, "memberReadHistoryRepository", MemberReadHistoryRepository.class);
        UmsMemberService memberService = dependency(service, "memberService", UmsMemberService.class);
        UmsMember member = new UmsMember();
        member.setId(101L);
        when(memberService.getCurrentMember()).thenReturn(member);
        controller = new MemberReadHistoryController();
        ReflectionTestUtils.setField(controller, "memberReadHistoryService", service);
        when(repository.deleteByMemberIdAndCreateTimeBefore(eq(101L), any())).thenReturn(1L);
    }

    @Test
    void clearBeforeReturnsDeletedCountForMemberCutoff() {
        Instant cutoff = OffsetDateTime.parse("2026-10-01T00:00:00Z").toInstant();
        long deletedCount = service.clearBefore(cutoff);
        assertEquals(1L, deletedCount);
        verify(repository).deleteByMemberIdAndCreateTimeBefore(101L, Date.from(cutoff));
        verify(repository, never()).deleteAllByMemberId(any());
    }

    @Test
    void sameInstantFromDifferentOffsetsUsesOneMillisecond() {
        List<Long> captured = new ArrayList<>();
        when(repository.deleteByMemberIdAndCreateTimeBefore(eq(101L), any())).thenAnswer(invocation -> {
            Date cutoff = invocation.getArgument(1);
            captured.add(cutoff.getTime());
            return 1L;
        });
        CommonResult<Long> east = controller.clearBefore("2026-10-01T00:00:00.123+08:00");
        CommonResult<Long> utc = controller.clearBefore("2026-09-30T16:00:00.123Z");
        assertEquals(1L, east.getData());
        assertEquals(1L, utc.getData());
        assertEquals(captured.get(0), captured.get(1));
        assertEquals(OffsetDateTime.parse("2026-10-01T00:00:00.123+08:00").toInstant().toEpochMilli(), captured.get(0));
    }

    @Test
    void finerThanMillisecondAndIllegalTextDoNotDelete() {
        CommonResult<Long> fraction = controller.clearBefore("2026-10-01T00:00:00.123456+08:00");
        CommonResult<Long> noOffset = controller.clearBefore("2026-10-01T00:00:00");
        assertEquals(404L, fraction.getCode());
        assertEquals(404L, noOffset.getCode());
        assertThrows(ApiException.class, () -> service.clearBefore(Instant.ofEpochSecond(1_700_000_000L, 1_000)));
        verify(repository, never()).deleteByMemberIdAndCreateTimeBefore(any(), any());
        verify(repository, never()).deleteAllByMemberId(any());
    }

    @Test
    void zeroDigitsBeyondMillisecondAreNotRejected() {
        CommonResult<Long> result = controller.clearBefore("2026-10-01T00:00:00.123000000+08:00");
        assertEquals(1L, result.getData());
        assertEquals(200L, result.getCode());
    }

    @Test
    void oldClearAndListStayMemberScoped() {
        service.clear();
        verify(repository).deleteAllByMemberId(101L);
        verify(repository, never()).deleteByMemberIdAndCreateTimeBefore(any(), any());
        when(repository.findByMemberIdOrderByCreateTimeDesc(eq(101L), any())).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(1);
            assertEquals(List.of(0, 5), List.of(pageable.getPageNumber(), pageable.getPageSize()));
            return new PageImpl<>(List.of());
        });
        service.list(1, 5);
        verify(repository).findByMemberIdOrderByCreateTimeDesc(eq(101L), any());
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, org.mockito.Mockito.withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
