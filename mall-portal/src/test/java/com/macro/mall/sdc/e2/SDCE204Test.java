package com.macro.mall.sdc.e2;

import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.MemberProductCollection;
import com.macro.mall.portal.domain.MemberReadHistory;
import com.macro.mall.portal.repository.MemberProductCollectionRepository;
import com.macro.mall.portal.repository.MemberReadHistoryRepository;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.MemberCollectionServiceImpl;
import com.macro.mall.portal.service.impl.MemberReadHistoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SDCE204Test {
    private final MemberCollectionServiceImpl collectionService = new MemberCollectionServiceImpl();
    private final MemberReadHistoryServiceImpl historyService = new MemberReadHistoryServiceImpl();
    private PmsProductMapper productMapper;
    private MemberProductCollectionRepository collectionRepository;
    private MemberReadHistoryRepository historyRepository;
    private MemberProductCollection collectionSnapshot;
    private MemberReadHistory historySnapshot;

    @BeforeEach
    void setUp() {
        productMapper = mock(PmsProductMapper.class, org.mockito.Mockito.withSettings().mockMaker("mock-maker-subclass"));
        collectionRepository = mock(MemberProductCollectionRepository.class,
                org.mockito.Mockito.withSettings().mockMaker("mock-maker-subclass"));
        historyRepository = mock(MemberReadHistoryRepository.class,
                org.mockito.Mockito.withSettings().mockMaker("mock-maker-subclass"));
        UmsMemberService memberService = mock(UmsMemberService.class,
                org.mockito.Mockito.withSettings().mockMaker("mock-maker-subclass"));
        when(memberService.getCurrentMember()).thenReturn(member());
        ReflectionTestUtils.setField(collectionService, "productMapper", productMapper);
        ReflectionTestUtils.setField(collectionService, "productCollectionRepository", collectionRepository);
        ReflectionTestUtils.setField(collectionService, "memberService", memberService);
        ReflectionTestUtils.setField(historyService, "productMapper", productMapper);
        ReflectionTestUtils.setField(historyService, "memberReadHistoryRepository", historyRepository);
        ReflectionTestUtils.setField(historyService, "memberService", memberService);
        when(collectionRepository.findByMemberIdAndProductId(101L, 7L)).thenReturn(null);
        when(collectionRepository.save(any())).thenAnswer(invocation -> {
            collectionSnapshot = copyCollection(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(historyRepository.save(any())).thenAnswer(invocation -> {
            historySnapshot = copyHistory(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
    }

    @Test
    void sqlEnableTrueUsesProductSnapshotAndSamePriceText() {
        sqlEnable(true);
        PmsProduct product = product(0);
        when(productMapper.selectByPrimaryKey(7L)).thenReturn(product);

        assertEquals(1, collectionService.add(collectionRequest()));
        assertEquals(1, historyService.create(historyRequest()));

        assertEquals("DB_NAME", collectionSnapshot.getProductName());
        assertEquals("DB_SUB", collectionSnapshot.getProductSubTitle());
        assertEquals("db-pic", collectionSnapshot.getProductPic());
        assertEquals("DB_NAME", historySnapshot.getProductName());
        assertEquals("DB_SUB", historySnapshot.getProductSubTitle());
        assertEquals("db-pic", historySnapshot.getProductPic());
        assertEquals(product.getPrice() + "", collectionSnapshot.getProductPrice());
        record Snapshot(String price) {}
        Snapshot collectionView = new Snapshot(collectionSnapshot.getProductPrice());
        Snapshot historyView = new Snapshot(historySnapshot.getProductPrice());
        assertEquals(collectionView.price(), historyView.price());
        verify(productMapper, times(2)).selectByPrimaryKey(7L);
    }

    @Test
    void sqlEnableFalseKeepsEachRequest() {
        sqlEnable(false);

        assertEquals(1, collectionService.add(collectionRequest()));
        assertEquals(1, historyService.create(historyRequest()));

        assertEquals("INPUT_NAME", collectionSnapshot.getProductName());
        assertEquals("INPUT_PRICE", collectionSnapshot.getProductPrice());
        assertEquals("INPUT_NAME", historySnapshot.getProductName());
        assertEquals("INPUT_PRICE", historySnapshot.getProductPrice());
        assertEquals("request-id", collectionSnapshot.getId());
        assertNull(historySnapshot.getId());
        verify(productMapper, never()).selectByPrimaryKey(any());
    }

    @Test
    void missingProductKeepsEachPreviousErrorBehavior() {
        sqlEnable(true);
        when(productMapper.selectByPrimaryKey(7L)).thenReturn(null);
        MemberProductCollection collection = collectionRequest();
        MemberReadHistory history = historyRequest();

        assertEquals(0, collectionService.add(collection));
        assertEquals(0, historyService.create(history));

        assertEquals("request-id", collection.getId());
        assertNull(history.getId());
        assertEquals("INPUT_NAME", collection.getProductName());
        assertEquals("INPUT_NAME", history.getProductName());
        verify(collectionRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void deletedProductKeepsEachPreviousErrorBehavior() {
        sqlEnable(true);
        when(productMapper.selectByPrimaryKey(7L)).thenReturn(product(1));

        assertEquals(0, collectionService.add(collectionRequest()));
        assertEquals(0, historyService.create(historyRequest()));
        verify(collectionRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void dedupeAndAppendStayIndependent() {
        sqlEnable(true);
        when(productMapper.selectByPrimaryKey(7L)).thenReturn(product(0));
        when(collectionRepository.findByMemberIdAndProductId(101L, 7L)).thenReturn(new MemberProductCollection());

        assertEquals(0, collectionService.add(collectionRequest()));
        assertEquals(1, historyService.create(historyRequest()));

        verify(collectionRepository, never()).save(any());
        verify(historyRepository).save(any());
        assertEquals("DB_NAME", historySnapshot.getProductName());
    }

    private void sqlEnable(boolean enabled) {
        ReflectionTestUtils.setField(collectionService, "sqlEnable", enabled);
        ReflectionTestUtils.setField(historyService, "sqlEnable", enabled);
    }

    private static UmsMember member() {
        UmsMember member = new UmsMember();
        member.setId(101L);
        member.setNickname("n101");
        member.setIcon("icon");
        return member;
    }

    private static PmsProduct product(int deleteStatus) {
        PmsProduct product = new PmsProduct();
        product.setName("DB_NAME");
        product.setSubTitle("DB_SUB");
        product.setPrice(new BigDecimal("19.90"));
        product.setPic("db-pic");
        product.setDeleteStatus(deleteStatus);
        return product;
    }

    private static MemberProductCollection collectionRequest() {
        MemberProductCollection request = new MemberProductCollection();
        request.setId("request-id");
        request.setProductId(7L);
        request.setProductName("INPUT_NAME");
        request.setProductSubTitle("INPUT_SUB");
        request.setProductPrice("INPUT_PRICE");
        request.setProductPic("input-pic");
        return request;
    }

    private static MemberReadHistory historyRequest() {
        MemberReadHistory request = new MemberReadHistory();
        request.setId("request-id");
        request.setProductId(7L);
        request.setProductName("INPUT_NAME");
        request.setProductSubTitle("INPUT_SUB");
        request.setProductPrice("INPUT_PRICE");
        request.setProductPic("input-pic");
        return request;
    }

    private static MemberProductCollection copyCollection(MemberProductCollection source) {
        MemberProductCollection copy = new MemberProductCollection();
        copy.setId(source.getId());
        copy.setProductName(source.getProductName());
        copy.setProductSubTitle(source.getProductSubTitle());
        copy.setProductPrice(source.getProductPrice());
        copy.setProductPic(source.getProductPic());
        return copy;
    }

    private static MemberReadHistory copyHistory(MemberReadHistory source) {
        MemberReadHistory copy = new MemberReadHistory();
        copy.setId(source.getId());
        copy.setProductName(source.getProductName());
        copy.setProductSubTitle(source.getProductSubTitle());
        copy.setProductPrice(source.getProductPrice());
        copy.setProductPic(source.getProductPic());
        return copy;
    }
}
