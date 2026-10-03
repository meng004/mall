package com.macro.mall.sdc.e6;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.component.CancelOrderSender;
import com.macro.mall.portal.dao.*;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.service.*;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockWarningTest {
    private final OmsPortalOrderServiceImpl service = new OmsPortalOrderServiceImpl();

    private <T> T dependency(String field, Class<T> type) {
        T value = mock(type, withSettings().mockMaker("mock-maker-subclass"));
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }

    @Test void paymentWarnsOnlyBelowThresholdAndKeepsReturnValue() {
        dependency("orderMapper", OmsOrderMapper.class);
        PortalOrderDao dao = dependency("portalOrderDao", PortalOrderDao.class);
        OmsOrderItem item = new OmsOrderItem();
        item.setProductSkuId(7L);
        OmsOrderDetail detail = new OmsOrderDetail();
        detail.setOrderItemList(List.of(item));
        when(dao.getDetail(9L)).thenReturn(detail);
        when(dao.updateSkuStock(detail.getOrderItemList())).thenReturn(3);
        PmsSkuStock sku = new PmsSkuStock();
        sku.setStock(10);
        sku.setLockStock(6);
        PmsSkuStockMapper mapper = dependency("skuStockMapper", PmsSkuStockMapper.class);
        when(mapper.selectByPrimaryKey(7L)).thenReturn(sku);
        Logger logger = (Logger) LoggerFactory.getLogger(OmsPortalOrderServiceImpl.class);
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        logger.addAppender(log);
        try {
            for (Integer low : new Integer[]{5, 4, null}) {
                log.list.clear();
                sku.setLowStock(low);
                assertEquals(3, service.paySuccess(9L, 1));
                assertEquals(low != null && low == 5 ? 1 : 0, log.list.size());
                if (!log.list.isEmpty()) assertEquals("sku 7 available 4 is below 5", log.list.get(0).getFormattedMessage());
                assertEquals(10, sku.getStock());
                assertEquals(6, sku.getLockStock());
            }
            log.list.clear();
            when(mapper.selectByPrimaryKey(7L)).thenReturn(null);
            assertEquals(3, service.paySuccess(9L, 1));
            when(mapper.selectByPrimaryKey(7L)).thenReturn(sku);
            sku.setLowStock(5);
            sku.setStock(null);
            assertEquals(3, service.paySuccess(9L, 1));
            sku.setStock(10);
            sku.setLockStock(null);
            assertEquals(3, service.paySuccess(9L, 1));
            assertTrue(log.list.isEmpty());
        } finally {
            logger.detachAppender(log);
            log.stop();
        }
    }

    @Test void warningReadFailureDoesNotChangeSuccessfulPaymentResult() {
        dependency("orderMapper", OmsOrderMapper.class);
        PortalOrderDao dao = dependency("portalOrderDao", PortalOrderDao.class);
        OmsOrderItem item = new OmsOrderItem();
        item.setProductSkuId(7L);
        OmsOrderDetail detail = new OmsOrderDetail();
        detail.setOrderItemList(List.of(item));
        when(dao.getDetail(9L)).thenReturn(detail);
        when(dao.updateSkuStock(detail.getOrderItemList())).thenReturn(3);
        when(dependency("skuStockMapper", PmsSkuStockMapper.class).selectByPrimaryKey(7L))
                .thenThrow(new DataAccessResourceFailureException("warning read unavailable"));
        assertEquals(3, service.paySuccess(9L, 1));
    }
}
