package com.macro.mall.sdc.e7;

import com.macro.mall.portal.ai.returns.ExistingReturnMaterialService;
import com.macro.mall.portal.ai.returns.LlmReturnMaterialService;
import com.macro.mall.portal.ai.returns.ReturnMaterialService.ItemContext;
import com.macro.mall.portal.ai.returns.ReturnMaterialService.Request;
import com.macro.mall.portal.ai.returns.ReturnMaterialService.Result;
import com.macro.mall.portal.ai.returns.ReturnMaterialService.State;
import com.macro.mall.portal.service.OmsPortalOrderReturnApplyService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SDCE703Test {
    private static final String WORN = "买了两双，一双鞋底开胶，只退这一双";
    private static final String SUGGEST = """
            {"action":"suggest","reasonId":7,"quantity":1,"description":"一双鞋底开胶","evidence":{"reasonId":"鞋底开胶","quantity":"只退这一双"},"missingFields":[]}""";

    @Test
    void oneDefectivePairKeepsReasonQuantityAndQuote() {
        OmsPortalOrderReturnApplyService writer = mock(OmsPortalOrderReturnApplyService.class);
        ItemContext context = new ItemContext(50L, 80L, 2, Set.of(7L), false, Set.of());
        ExistingReturnMaterialService existing = new ExistingReturnMaterialService(writer);
        LlmReturnMaterialService llm = new LlmReturnMaterialService(existing, (system, input) -> {
            assertEquals(WORN, input);
            assertFalse(input.contains("\"reasonId\":7"));
            return SUGGEST;
        });

        Result byText = llm.suggest(new Request(context, WORN, null, null));
        Result byForm = existing.suggest(new Request(context, null, 7L, 1));

        assertEquals(State.OK, byText.state());
        assertEquals(7L, byText.reasonId());
        assertEquals(1, byText.quantity());
        assertEquals("鞋底开胶", byText.evidence().get("reasonId"));
        assertEquals("只退这一双", byText.evidence().get("quantity"));
        assertTrue(WORN.contains(byText.evidence().get("reasonId")));
        assertTrue(WORN.contains(byText.evidence().get("quantity")));
        assertTrue(byText.missingFields().isEmpty());
        assertEquals(7L, byForm.reasonId());
        assertEquals(1, byForm.quantity());
        assertTrue(byForm.missingFields().isEmpty());
        verifyNoInteractions(writer);
    }

    @Test
    void blankTextSkipsModel() {
        OmsPortalOrderReturnApplyService writer = mock(OmsPortalOrderReturnApplyService.class);
        ItemContext context = new ItemContext(50L, 80L, 2, Set.of(7L), false, Set.of());
        LlmReturnMaterialService llm = new LlmReturnMaterialService(new ExistingReturnMaterialService(writer), (system, input) -> {
            throw new AssertionError("form path must not call the model");
        });
        Result result = llm.suggest(new Request(context, " ", 7L, 1));
        assertEquals(State.OK, result.state());
        assertEquals(1, result.quantity());
        verifyNoInteractions(writer);
    }

    @Test
    void unknownItemAndContradictoryQuantityAskInsteadOfGuessing() {
        OmsPortalOrderReturnApplyService writer = mock(OmsPortalOrderReturnApplyService.class);
        LlmReturnMaterialService llm = new LlmReturnMaterialService(new ExistingReturnMaterialService(writer), (system, input) -> {
            throw new AssertionError("missing item must not be guessed by the model");
        });
        Result unknown = llm.suggest(new Request(null, "退掉那个开胶的", null, null));
        assertEquals(State.NEEDS_INPUT, unknown.state());
        assertNull(unknown.reasonId());
        assertNull(unknown.quantity());

        ItemContext context = new ItemContext(50L, 80L, 2, Set.of(7L), false, Set.of());
        Result tooMany = new LlmReturnMaterialService(new ExistingReturnMaterialService(writer), (system, input) -> """
                {"action":"suggest","reasonId":7,"quantity":5,"description":"五双都开胶","evidence":{"reasonId":"开胶","quantity":"五双"},"missingFields":[]}""")
                .suggest(new Request(context, "五双都开胶", null, null));
        assertEquals(State.NEEDS_INPUT, tooMany.state());
        assertNull(tooMany.quantity());
        verifyNoInteractions(writer);
    }

    @Test
    void claimedPhotoDoesNotReplaceEmptyUpload() {
        OmsPortalOrderReturnApplyService writer = mock(OmsPortalOrderReturnApplyService.class);
        ItemContext context = new ItemContext(50L, 80L, 2, Set.of(7L), true, Set.of());
        String text = WORN + "，照片已经传了";
        Result result = new LlmReturnMaterialService(new ExistingReturnMaterialService(writer), (system, input) -> SUGGEST)
                .suggest(new Request(context, text, null, null));
        assertEquals(State.NEEDS_INPUT, result.state());
        assertEquals(1, result.quantity());
        assertEquals(7L, result.reasonId());
        assertTrue(result.missingFields().contains("proof"));
        verifyNoInteractions(writer);
    }

    @Test
    void illegalModelOutputIsRejectedAndNotUnavailable() {
        OmsPortalOrderReturnApplyService writer = mock(OmsPortalOrderReturnApplyService.class);
        ItemContext context = new ItemContext(50L, 80L, 2, Set.of(7L), false, Set.of());
        for (String output : List.of(
                "not json",
                SUGGEST + " trailing",
                "{\"action\":\"suggest\",\"reasonId\":7,\"quantity\":1,\"description\":\"退\",\"evidence\":{},\"missingFields\":[],\"memberId\":3}",
                "{\"action\":\"suggest\",\"reasonId\":7,\"quantity\":1,\"description\":\"退\",\"evidence\":{},\"missingFields\":[],\"refundAmount\":10,\"create\":true}",
                "{\"action\":\"create\"}")) {
            Result result = new LlmReturnMaterialService(new ExistingReturnMaterialService(writer), (system, input) -> output)
                    .suggest(new Request(context, WORN, null, null));
            assertEquals(State.UNSUPPORTED, result.state(), output);
            assertNull(result.reasonId(), output);
            assertNull(result.quantity(), output);
        }
        verifyNoInteractions(writer);
    }

    @Test
    void modelThrowIsUnavailableRatherThanRejection() {
        OmsPortalOrderReturnApplyService writer = mock(OmsPortalOrderReturnApplyService.class);
        ItemContext context = new ItemContext(50L, 80L, 2, Set.of(7L), false, Set.of());
        Result result = new LlmReturnMaterialService(new ExistingReturnMaterialService(writer), (system, input) -> {
            throw new IllegalStateException("timeout secret-token");
        }).suggest(new Request(context, WORN, null, null));
        assertEquals(State.UNAVAILABLE, result.state());
        assertNull(result.reasonId());
        assertTrue(result.description() == null || !result.description().contains("secret-token"));
        verifyNoInteractions(writer);
    }

    @Test
    void unavailableDoesNotCountAsSuccessfulRejection() {
        Result unavailable = new Result(State.UNAVAILABLE, null, null, null, java.util.Map.of(), Set.of());
        Result rejected = new Result(State.UNSUPPORTED, null, null, null, java.util.Map.of(), Set.of());
        assertFalse(CandidateE703Evaluation.matches("UNSUPPORTED", null, null, Set.of(), unavailable));
        assertTrue(CandidateE703Evaluation.matches("UNSUPPORTED", null, null, Set.of(), rejected));
        assertEquals("INCOMPLETE", CandidateE703Evaluation.verdict(20, 18, 12, 12, true, false));
        assertEquals("DEGRADE", CandidateE703Evaluation.verdict(20, 17, 12, 12, true, false));
        assertEquals("REJECT", CandidateE703Evaluation.verdict(20, 20, 12, 12, true, true));
        assertEquals("INCOMPLETE", CandidateE703Evaluation.verdict(19, 19, 12, 12, true, false));
    }
}
