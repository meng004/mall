package com.macro.mall.sdc.e6;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.controller.MemberProductCollectionController;
import com.macro.mall.portal.domain.MemberProductCollection;
import com.macro.mall.portal.repository.MemberProductCollectionRepository;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.MemberCollectionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SDCE605Test {
    private final MemberCollectionServiceImpl service = new MemberCollectionServiceImpl();
    private MemberProductCollectionRepository repository;

    @BeforeEach
    void setUp() {
        repository = dependency(service, "productCollectionRepository", MemberProductCollectionRepository.class);
        UmsMemberService memberService = dependency(service, "memberService", UmsMemberService.class);
        UmsMember member = new UmsMember();
        member.setId(101L);
        when(memberService.getCurrentMember()).thenReturn(member);
        when(repository.findByMemberId(eq(101L), any())).thenReturn(page("unfiltered", 9, 0, 5));
    }

    @Test
    void emptyKeywordKeepsMemberPaging() {
        MemberProductCollection row = item("old-id");
        Page<MemberProductCollection> expected = new PageImpl<>(List.of(row), PageRequest.of(1, 5), 20);
        when(repository.findByMemberId(eq(101L), any())).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(1);
            assertEquals(1, pageable.getPageNumber());
            assertEquals(5, pageable.getPageSize());
            return expected;
        });

        Page<MemberProductCollection> page = service.list(2, 5, "");
        Page<MemberProductCollection> oldCall = service.list(2, 5);

        assertEquals(20, page.getTotalElements());
        assertEquals("old-id", page.getContent().get(0).getId());
        assertEquals(20, oldCall.getTotalElements());
        verify(repository, never()).findByMemberIdAndProductNameRegex(any(), any(), any());
    }

    @Test
    void literalDotIsQuotedAndNotAnyCharacter() {
        when(repository.findByMemberId(eq(101L), any())).thenReturn(page("unfiltered", 9, 0, 5));
        when(repository.findByMemberIdAndProductNameRegex(eq(101L), eq(Pattern.quote(".")), any()))
                .thenReturn(page("dot-id", 1, 0, 5));

        Page<MemberProductCollection> page = service.list(1, 5, ".");

        assertEquals(1, page.getTotalElements());
        assertEquals("dot-id", page.getContent().get(0).getId());
        verify(repository).findByMemberIdAndProductNameRegex(101L, Pattern.quote("."), PageRequest.of(0, 5));
    }

    @Test
    void filteredFirstPageReturnsMatchThatUnfilteredPageWouldMiss() {
        when(repository.findByMemberId(eq(101L), any())).thenReturn(page("nomatch", 4, 0, 1));
        when(repository.findByMemberIdAndProductNameRegex(eq(101L), eq(Pattern.quote("a.b")), any()))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(2);
                    assertEquals(0, pageable.getPageNumber());
                    assertEquals(1, pageable.getPageSize());
                    assertFalse(invocation.getArgument(1).toString().contains("(?i)"));
                    return page("match-id", 2, 0, 1);
                });

        Page<MemberProductCollection> page = service.list(1, 1, "a.b");

        assertEquals(2, page.getTotalElements());
        assertEquals("match-id", page.getContent().get(0).getId());
    }

    @Test
    void otherMemberAndStarStayOutOfTheQuery() {
        when(repository.findByMemberIdAndProductNameRegex(eq(101L), eq(Pattern.quote("*")), any()))
                .thenReturn(page("star-id", 1, 0, 5));

        Page<MemberProductCollection> page = service.list(1, 5, "*");

        assertEquals(1, page.getTotalElements());
        assertEquals("star-id", page.getContent().get(0).getId());
        verify(repository, never()).findByMemberIdAndProductNameRegex(eq(202L), any(), any());
    }

    @Test
    void controllerPassesKeywordIntoTheSameList() {
        when(repository.findByMemberIdAndProductNameRegex(eq(101L), eq(Pattern.quote("plain")), any()))
                .thenReturn(page("plain-id", 1, 0, 5));
        MemberProductCollectionController controller = new MemberProductCollectionController();
        ReflectionTestUtils.setField(controller, "memberCollectionService", service);

        CommonResult<CommonPage<MemberProductCollection>> result = controller.list(1, 5, "plain");

        assertEquals(200L, result.getCode());
        assertEquals("plain-id", result.getData().getList().get(0).getId());
    }

    @Test
    void blankKeywordIsMatchedLiterally() {
        when(repository.findByMemberIdAndProductNameRegex(eq(101L), eq(Pattern.quote(" ")), any()))
                .thenReturn(page("space-id", 1, 0, 5));

        Page<MemberProductCollection> page = service.list(1, 5, " ");

        assertEquals("space-id", page.getContent().get(0).getId());
        verify(repository, never()).findByMemberId(any(), any());
    }

    private static Page<MemberProductCollection> page(String id, long total, int page, int size) {
        return new PageImpl<>(List.of(item(id)), PageRequest.of(page, size), total);
    }

    private static MemberProductCollection item(String id) {
        MemberProductCollection row = new MemberProductCollection();
        row.setId(id);
        row.setMemberId(101L);
        row.setProductName(id);
        return row;
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, org.mockito.Mockito.withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
