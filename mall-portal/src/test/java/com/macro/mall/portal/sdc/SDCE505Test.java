package com.macro.mall.portal.sdc;

import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.MemberProductCollection;
import com.macro.mall.portal.repository.MemberProductCollectionRepository;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.MemberCollectionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SDCE505Test {
    private final MemberCollectionServiceImpl service = new MemberCollectionServiceImpl();
    private Long capturedMemberId;
    private Pageable capturedPageable;
    private Page<MemberProductCollection> repositoryPage;

    @BeforeEach
    void setUp() {
        MemberProductCollectionRepository repository =
                dependency(service, "productCollectionRepository", MemberProductCollectionRepository.class);
        UmsMemberService memberService = dependency(service, "memberService", UmsMemberService.class);
        UmsMember member = new UmsMember();
        member.setId(101L);
        when(memberService.getCurrentMember()).thenReturn(member);
        MemberProductCollection row = new MemberProductCollection();
        row.setId("row-1");
        row.setMemberId(101L);
        repositoryPage = new PageImpl<>(List.of(row));
        when(repository.findByMemberId(org.mockito.ArgumentMatchers.anyLong(), any())).thenAnswer(invocation -> {
            capturedMemberId = invocation.getArgument(0);
            capturedPageable = invocation.getArgument(1);
            return repositoryPage;
        });
    }

    @Test
    void listCapturesMemberIdForPaging() {
        Page<MemberProductCollection> page = service.list(2, 5);
        assertEquals(101L, capturedMemberId);
        assertEquals(1, capturedPageable.getPageNumber());
        assertEquals(5, capturedPageable.getPageSize());
        assertSame(repositoryPage, page);
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, org.mockito.Mockito.withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
