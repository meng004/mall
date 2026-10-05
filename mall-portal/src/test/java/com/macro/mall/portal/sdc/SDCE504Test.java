package com.macro.mall.portal.sdc;

import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.repository.MemberReadHistoryRepository;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.MemberReadHistoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SDCE504Test {
    private final MemberReadHistoryServiceImpl service = new MemberReadHistoryServiceImpl();
    private MemberReadHistoryRepository repository;
    private Long listedMemberId;
    private Pageable listedPageable;

    @BeforeEach
    void setUp() {
        repository = dependency(service, "memberReadHistoryRepository", MemberReadHistoryRepository.class);
        UmsMemberService memberService = dependency(service, "memberService", UmsMemberService.class);
        UmsMember member = new UmsMember();
        member.setId(101L);
        when(memberService.getCurrentMember()).thenReturn(member);
        when(repository.findByMemberIdOrderByCreateTimeDesc(anyLong(), any())).thenAnswer(invocation -> {
            listedMemberId = invocation.getArgument(0);
            listedPageable = invocation.getArgument(1);
            return new PageImpl<>(List.of());
        });
    }

    @Test
    void clearDeletesByCurrentMember() {
        service.clear();
        verify(repository).deleteAllByMemberId(101L);
        verify(repository, never()).deleteAll();
    }

    @Test
    void listStaysMemberScoped() {
        service.list(1, 5);
        assertEquals(101L, listedMemberId);
        assertEquals(0, listedPageable.getPageNumber());
        assertEquals(5, listedPageable.getPageSize());
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, org.mockito.Mockito.withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
