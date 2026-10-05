package com.macro.mall.sdc.e7;

import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.AttributeSpec;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.Request;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.Result;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.State;
import com.macro.mall.portal.ai.attributes.AttributeExtractionService.Value;
import com.macro.mall.portal.ai.attributes.ExistingAttributeExtractionService;
import com.macro.mall.portal.ai.attributes.LlmAttributeExtractionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SDCE705Test {
    private static final AttributeSpec COLOR = new AttributeSpec(1L, "颜色", Set.of("红", "蓝"), null);
    private static final AttributeSpec CAPACITY = new AttributeSpec(2L, "容量", Set.of("128GB", "256GB"), "GB");
    private static final List<AttributeSpec> SPECS = List.of(COLOR, CAPACITY);
    private static final String RED_128 = "红色，128G";

    @Test
    void redAnd128GUseTheGivenAliases() {
        PmsProductMapper writer = mock(PmsProductMapper.class);
        ExistingAttributeExtractionService existing = new ExistingAttributeExtractionService(writer);
        Result byRule = existing.extract(new Request(SPECS, RED_128));
        Result byModel = new LlmAttributeExtractionService(existing, (system, input) -> {
            assertEquals(RED_128, input);
            assertFalse(input.contains("128GB"));
            return """
                    {"action":"extract","values":[{"attributeId":1,"value":"红色","evidence":"红色"},{"attributeId":2,"value":"128G","evidence":"128G"}],"unknownIds":[]}""";
        }).extract(new Request(SPECS, RED_128));
        for (Result result : List.of(byRule, byModel)) {
            assertEquals(State.OK, result.state());
            assertEquals(Map.of(1L, "红", 2L, "128GB"), values(result));
            assertEquals("红色", evidence(result, 1L));
            assertEquals("128G", evidence(result, 2L));
            assertTrue(result.unknownIds().isEmpty());
            assertTrue(RED_128.contains(evidence(result, 1L)));
            assertTrue(RED_128.contains(evidence(result, 2L)));
        }
        verifyNoInteractions(writer);
    }

    @Test
    void blankTextSkipsModel() {
        PmsProductMapper writer = mock(PmsProductMapper.class);
        Result result = new LlmAttributeExtractionService(new ExistingAttributeExtractionService(writer), (system, input) -> {
            throw new AssertionError("blank text must not call the model");
        }).extract(new Request(SPECS, " "));
        assertEquals(State.NEEDS_INPUT, result.state());
        assertTrue(result.values().isEmpty());
        verifyNoInteractions(writer);
    }

    @Test
    void omittedCapacityStaysUnknown() {
        PmsProductMapper writer = mock(PmsProductMapper.class);
        Result result = new ExistingAttributeExtractionService(writer).extract(new Request(SPECS, "红色"));
        assertEquals(State.PARTIAL, result.state());
        assertEquals(Map.of(1L, "红"), values(result));
        assertEquals(Set.of(2L), result.unknownIds());
        verifyNoInteractions(writer);
    }

    @Test
    void unknownIdOrDisallowedValueIsNotSaved() {
        PmsProductMapper writer = mock(PmsProductMapper.class);
        ExistingAttributeExtractionService existing = new ExistingAttributeExtractionService(writer);
        Result unknownId = new LlmAttributeExtractionService(existing, (system, input) -> """
                {"action":"extract","values":[{"attributeId":99,"value":"红","evidence":"红色"}],"unknownIds":[]}""")
                .extract(new Request(SPECS, "红色"));
        assertEquals(State.UNSUPPORTED, unknownId.state());
        assertTrue(unknownId.values().isEmpty());
        Result disallowed = existing.extract(new Request(SPECS, "红色，512G"));
        assertEquals(State.PARTIAL, disallowed.state());
        assertEquals(Map.of(1L, "红"), values(disallowed));
        assertEquals(Set.of(2L), disallowed.unknownIds());
        assertFalse(values(disallowed).containsValue("512GB"));
        verifyNoInteractions(writer);
    }

    @Test
    void fullWidthIsFoldedAndUndefinedUnitAsks() {
        PmsProductMapper writer = mock(PmsProductMapper.class);
        ExistingAttributeExtractionService existing = new ExistingAttributeExtractionService(writer);
        Result wide = existing.extract(new Request(List.of(CAPACITY), "５１２Ｇ"));
        assertEquals(State.NEEDS_INPUT, wide.state());
        assertTrue(wide.values().isEmpty());
        assertTrue(wide.unknownIds().contains(2L));
        Result terabyte = existing.extract(new Request(SPECS, "红色，1TB"));
        assertEquals(State.NEEDS_INPUT, terabyte.state());
        assertTrue(terabyte.values().stream().noneMatch(value -> value.value().contains("TB") || value.value().endsWith("GB")));
        verifyNoInteractions(writer);
    }

    @Test
    void illegalModelOutputIsRejectedAndNotUnavailable() {
        PmsProductMapper writer = mock(PmsProductMapper.class);
        ExistingAttributeExtractionService existing = new ExistingAttributeExtractionService(writer);
        for (String output : List.of(
                "not json",
                "{\"action\":\"extract\",\"values\":[],\"unknownIds\":[]} trailing",
                "{\"action\":\"extract\",\"values\":[],\"unknownIds\":[],\"productId\":1}",
                "{\"action\":\"save\"}")) {
            Result result = new LlmAttributeExtractionService(existing, (system, input) -> output).extract(new Request(SPECS, RED_128));
            assertEquals(State.UNSUPPORTED, result.state(), output);
            assertTrue(result.values().isEmpty(), output);
        }
        verifyNoInteractions(writer);
    }

    @Test
    void modelThrowDoesNotFallBackToLiteralExtraction() {
        PmsProductMapper writer = mock(PmsProductMapper.class);
        Result result = new LlmAttributeExtractionService(new ExistingAttributeExtractionService(writer), (system, input) -> {
            throw new IllegalStateException("timeout secret-token");
        }).extract(new Request(SPECS, RED_128));
        assertEquals(State.UNAVAILABLE, result.state());
        assertTrue(result.values().isEmpty());
        verifyNoInteractions(writer);
    }

    @Test
    void evidenceMustSupportTheProposedValue() {
        AttributeSpec color = new AttributeSpec(21L, "颜色", Set.of("红", "蓝"), null);
        AttributeSpec capacity = new AttributeSpec(128L, "容量", Set.of("128GB", "256GB"), "GB");
        PmsProductMapper writer = failingWriter();
        ExistingAttributeExtractionService existing = new ExistingAttributeExtractionService(writer);
        Request blue = new Request(List.of(color), "蓝色");
        Result fabricated = new LlmAttributeExtractionService(existing, (system, input) -> """
                {"action":"extract","values":[{"attributeId":21,"value":"红","evidence":"蓝色"}],"unknownIds":[]}""")
                .extract(blue);
        assertNotEquals(State.OK, fabricated.state());
        assertTrue(fabricated.values().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> existing.check(blue, List.of(new Value(21L, "红", "蓝色")), Set.of()));

        for (String proposed : List.of("蓝", "蓝色")) {
            Result accepted = new LlmAttributeExtractionService(existing, (system, input) -> """
                    {"action":"extract","values":[{"attributeId":21,"value":"%s","evidence":"蓝色"}],"unknownIds":[]}"""
                    .formatted(proposed)).extract(blue);
            assertEquals(State.OK, accepted.state(), proposed);
            assertEquals(Map.of(21L, "蓝"), values(accepted), proposed);
            assertEquals("蓝色", evidence(accepted, 21L), proposed);
        }

        Request gigabyte = new Request(List.of(capacity), "128G");
        Result wrongCapacity = new LlmAttributeExtractionService(existing, (system, input) -> """
                {"action":"extract","values":[{"attributeId":128,"value":"256GB","evidence":"128G"}],"unknownIds":[]}""")
                .extract(gigabyte);
        assertNotEquals(State.OK, wrongCapacity.state());
        assertTrue(wrongCapacity.values().isEmpty());
        Result rightCapacity = new LlmAttributeExtractionService(existing, (system, input) -> """
                {"action":"extract","values":[{"attributeId":128,"value":"128GB","evidence":"128G"}],"unknownIds":[]}""")
                .extract(gigabyte);
        assertEquals(State.OK, rightCapacity.state());
        assertEquals(Map.of(128L, "128GB"), values(rightCapacity));
        assertEquals("128G", evidence(rightCapacity, 128L));

        Result blankEvidence = new LlmAttributeExtractionService(existing, (system, input) -> """
                {"action":"extract","values":[{"attributeId":21,"value":"蓝","evidence":""}],"unknownIds":[]}""")
                .extract(blue);
        Result duplicate = new LlmAttributeExtractionService(existing, (system, input) -> """
                {"action":"extract","values":[{"attributeId":21,"value":"蓝","evidence":"蓝色"},{"attributeId":21,"value":"红","evidence":"蓝色"}],"unknownIds":[]}""")
                .extract(blue);
        Result overlap = new LlmAttributeExtractionService(existing, (system, input) -> """
                {"action":"extract","values":[{"attributeId":21,"value":"蓝","evidence":"蓝色"}],"unknownIds":[21]}""")
                .extract(blue);
        for (Result result : List.of(blankEvidence, duplicate, overlap)) {
            assertNotEquals(State.OK, result.state());
            assertNotEquals(State.PARTIAL, result.state());
        }
        assertThrows(IllegalArgumentException.class,
                () -> existing.check(blue, List.of(new Value(21L, "蓝", "蓝色"), new Value(21L, "红", "蓝色")), Set.of()));
    }

    @Test
    void unavailableDoesNotCountAsSuccessfulRejection() {
        Result unavailable = new Result(State.UNAVAILABLE, List.of(), Set.of());
        Result rejected = new Result(State.UNSUPPORTED, List.of(), Set.of());
        assertFalse(CandidateE705Evaluation.matches("UNSUPPORTED", Map.of(), Set.of(), unavailable));
        assertTrue(CandidateE705Evaluation.matches("UNSUPPORTED", Map.of(), Set.of(), rejected));
        assertEquals("INCOMPLETE", CandidateE705Evaluation.verdict(20, 18, 12, 12, true, false));
        assertEquals("DEGRADE", CandidateE705Evaluation.verdict(20, 17, 12, 12, true, false));
        assertEquals("NO_RELEASE", CandidateE705Evaluation.verdict(20, 20, 12, 11, true, false));
        assertEquals("INCOMPLETE", CandidateE705Evaluation.verdict(19, 19, 12, 12, true, false));
    }

    private static PmsProductMapper failingWriter() {
        return (PmsProductMapper) java.lang.reflect.Proxy.newProxyInstance(PmsProductMapper.class.getClassLoader(),
                new Class<?>[] {PmsProductMapper.class}, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "writer";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    throw new AssertionError("DB called");
                });
    }

    private static Map<Long, String> values(Result result) {
        return result.values().stream().collect(Collectors.toMap(Value::attributeId, Value::value));
    }

    private static String evidence(Result result, long id) {
        return result.values().stream().filter(value -> value.attributeId() == id).findFirst().orElseThrow().evidence();
    }
}
