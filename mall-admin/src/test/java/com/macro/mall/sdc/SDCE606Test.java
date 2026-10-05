package com.macro.mall.sdc;

import com.macro.mall.mapper.SmsHomeRecommendProductMapper;
import com.macro.mall.model.SmsHomeRecommendProduct;
import com.macro.mall.service.impl.SmsHomeRecommendProductServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class SDCE606Test {

    @Test
    void emptyListReturnsZeroWithoutInsert() {
        SmsHomeRecommendProductServiceImpl service = new SmsHomeRecommendProductServiceImpl();
        SmsHomeRecommendProductMapper mapper = dependency(service, "recommendProductMapper", SmsHomeRecommendProductMapper.class);

        assertEquals(0, service.create(List.of()));
        verify(mapper, never()).insert(any());
    }

    @Test
    void duplicateProductIdsKeepTheFirstRecord() {
        SmsHomeRecommendProductServiceImpl service = new SmsHomeRecommendProductServiceImpl();
        SmsHomeRecommendProductMapper mapper = dependency(service, "recommendProductMapper", SmsHomeRecommendProductMapper.class);
        List<String> insertedNames = new ArrayList<>();
        List<SmsHomeRecommendProduct> inserted = new ArrayList<>();
        when(mapper.insert(any())).thenAnswer(invocation -> {
            SmsHomeRecommendProduct copy = copyAtReception(invocation.getArgument(0));
            inserted.add(copy);
            insertedNames.add(copy.getProductName());
            return 1;
        });

        int insertedCount = service.create(List.of(item(7L, "FIRST"), item(7L, "SECOND"), item(8L, "EIGHT")));

        assertEquals(2, insertedCount);
        assertEquals(List.of("FIRST", "EIGHT"), insertedNames);
        assertEquals(List.of(7L, 8L), inserted.stream().map(SmsHomeRecommendProduct::getProductId).toList());
        assertTrue(inserted.stream().allMatch(row -> Integer.valueOf(1).equals(row.getRecommendStatus())
                && Integer.valueOf(0).equals(row.getSort())));
    }

    @Test
    void distinctProductsAreAllInsertedInInputOrder() {
        SmsHomeRecommendProductServiceImpl service = new SmsHomeRecommendProductServiceImpl();
        SmsHomeRecommendProductMapper mapper = dependency(service, "recommendProductMapper", SmsHomeRecommendProductMapper.class);
        List<String> insertedNames = new ArrayList<>();
        List<SmsHomeRecommendProduct> inserted = new ArrayList<>();
        when(mapper.insert(any())).thenAnswer(invocation -> {
            SmsHomeRecommendProduct copy = copyAtReception(invocation.getArgument(0));
            inserted.add(copy);
            insertedNames.add(copy.getProductName());
            return 1;
        });
        SmsHomeRecommendProduct nine = item(9L, "NINE");
        nine.setRecommendStatus(0);
        nine.setSort(5);

        int insertedCount = service.create(List.of(nine, item(7L, "SEVEN"), item(8L, "EIGHT")));

        assertEquals(3, insertedCount);
        assertEquals(List.of("NINE", "SEVEN", "EIGHT"), insertedNames);
        assertTrue(inserted.stream().allMatch(row -> Integer.valueOf(1).equals(row.getRecommendStatus())
                && Integer.valueOf(0).equals(row.getSort())));
    }

    @Test
    void nonAdjacentDuplicateKeepsTheFirstRecord() {
        SmsHomeRecommendProductServiceImpl service = new SmsHomeRecommendProductServiceImpl();
        SmsHomeRecommendProductMapper mapper = dependency(service, "recommendProductMapper", SmsHomeRecommendProductMapper.class);
        List<String> insertedNames = new ArrayList<>();
        when(mapper.insert(any())).thenAnswer(invocation -> {
            insertedNames.add(copyAtReception(invocation.getArgument(0)).getProductName());
            return 1;
        });

        int insertedCount = service.create(List.of(item(8L, "FIRST"), item(7L, "SEVEN"), item(8L, "SECOND")));

        assertEquals(2, insertedCount);
        assertEquals(List.of("FIRST", "SEVEN"), insertedNames);
    }

    @Test
    void returnedCountSumsMapperResults() {
        SmsHomeRecommendProductServiceImpl service = new SmsHomeRecommendProductServiceImpl();
        SmsHomeRecommendProductMapper mapper = dependency(service, "recommendProductMapper", SmsHomeRecommendProductMapper.class);
        AtomicInteger calls = new AtomicInteger();
        when(mapper.insert(any())).thenAnswer(invocation -> calls.getAndIncrement() == 0 ? 1 : 0);

        int actualInsertedCount = service.create(List.of(item(7L, "FIRST"), item(8L, "EIGHT")));

        assertEquals(1, actualInsertedCount);
    }

    @Test
    void mapperExceptionPropagates() {
        SmsHomeRecommendProductServiceImpl service = new SmsHomeRecommendProductServiceImpl();
        SmsHomeRecommendProductMapper mapper = dependency(service, "recommendProductMapper", SmsHomeRecommendProductMapper.class);
        when(mapper.insert(any())).thenThrow(new IllegalStateException("db down"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.create(List.of(item(7L, "FIRST"))));
        assertEquals("db down", error.getMessage());
    }

    private static SmsHomeRecommendProduct item(Long productId, String name) {
        SmsHomeRecommendProduct product = new SmsHomeRecommendProduct();
        product.setProductId(productId);
        product.setProductName(name);
        return product;
    }

    private static SmsHomeRecommendProduct copyAtReception(SmsHomeRecommendProduct source) {
        SmsHomeRecommendProduct copy = new SmsHomeRecommendProduct();
        copy.setId(source.getId());
        copy.setProductId(source.getProductId());
        copy.setProductName(source.getProductName());
        copy.setRecommendStatus(source.getRecommendStatus());
        copy.setSort(source.getSort());
        return copy;
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        org.springframework.test.util.ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
