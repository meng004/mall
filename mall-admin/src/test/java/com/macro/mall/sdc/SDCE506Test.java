package com.macro.mall.sdc;

import com.macro.mall.mapper.SmsHomeRecommendProductMapper;
import com.macro.mall.model.SmsHomeRecommendProduct;
import com.macro.mall.service.impl.SmsHomeRecommendProductServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 观察现有 create 会把重复商品逐条插入。批内去重尚未实现。
 */
class SDCE506Test {

    @Test
    void currentCreateInsertsEachInputIncludingDuplicateProduct() {
        SmsHomeRecommendProductServiceImpl service = new SmsHomeRecommendProductServiceImpl();
        SmsHomeRecommendProductMapper mapper = dependency(service, "recommendProductMapper", SmsHomeRecommendProductMapper.class);
        List<SmsHomeRecommendProduct> inserted = new ArrayList<>();
        when(mapper.insert(any())).thenAnswer(invocation -> {
            inserted.add(copyAtReception(invocation.getArgument(0)));
            return 1;
        });

        int count = service.create(List.of(item(7L, "FIRST"), item(7L, "SECOND"), item(8L, "EIGHT")));

        verify(mapper, times(3)).insert(any());
        assertEquals(List.of("FIRST", "SECOND", "EIGHT"), inserted.stream().map(SmsHomeRecommendProduct::getProductName).toList());
        assertEquals(List.of(7L, 7L, 8L), inserted.stream().map(SmsHomeRecommendProduct::getProductId).toList());
        assertTrue(inserted.stream().allMatch(row -> Integer.valueOf(1).equals(row.getRecommendStatus()) && Integer.valueOf(0).equals(row.getSort())));
        assertEquals(3, count);
    }

    @Test
    void currentCreateReturnsListSizeWhenMapperReturnsZero() {
        SmsHomeRecommendProductServiceImpl service = new SmsHomeRecommendProductServiceImpl();
        SmsHomeRecommendProductMapper mapper = dependency(service, "recommendProductMapper", SmsHomeRecommendProductMapper.class);
        when(mapper.insert(any())).thenReturn(0);

        assertEquals(2, service.create(List.of(item(7L, "FIRST"), item(8L, "EIGHT"))));
    }

    @Test
    void currentCreateOfEmptyListDoesNotInsert() {
        SmsHomeRecommendProductServiceImpl service = new SmsHomeRecommendProductServiceImpl();
        SmsHomeRecommendProductMapper mapper = dependency(service, "recommendProductMapper", SmsHomeRecommendProductMapper.class);

        assertEquals(0, service.create(List.of()));
        verify(mapper, never()).insert(any());
    }

    @Test
    void currentCreatePropagatesMapperException() {
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
