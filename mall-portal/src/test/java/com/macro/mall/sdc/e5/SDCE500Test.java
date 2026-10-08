package com.macro.mall.sdc.e5;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 锁定基线支付成功路径的现状，不实现库存预警。
 * 调用前把 sku 7 打成 stock=10、lockStock=6、lowStock=5（可售 4，已低于阈值）。
 * 实现预警后会改变：不调用 selectByPrimaryKey、本类日志为空（这两条在 assertAll 中）。
 * 实现后仍应保持：返回值是 updateSkuStock 的行数；本路径不经 skuStockMapper 写库存。
 */
class SDCE500Test {

    @Test
    void paymentSuccessDoesNotReadLowStockAndReturnsDeductedRows() {
        OmsPortalOrderServiceImpl service = new OmsPortalOrderServiceImpl();
        dependency(service, "orderMapper", OmsOrderMapper.class);
        PortalOrderDao dao = dependency(service, "portalOrderDao", PortalOrderDao.class);
        PmsSkuStockMapper skuStockMapper = dependency(service, "skuStockMapper", PmsSkuStockMapper.class);
        OmsOrderItem item = new OmsOrderItem();
        item.setProductSkuId(7L);
        OmsOrderDetail detail = new OmsOrderDetail();
        detail.setOrderItemList(List.of(item));
        when(dao.getDetail(9L)).thenReturn(detail);
        when(dao.updateSkuStock(detail.getOrderItemList())).thenReturn(3);
        PmsSkuStock sku = new PmsSkuStock();
        sku.setStock(10);
        sku.setLockStock(6);
        sku.setLowStock(5);
        when(skuStockMapper.selectByPrimaryKey(7L)).thenReturn(sku);

        Logger logger = (Logger) LoggerFactory.getLogger(OmsPortalOrderServiceImpl.class);
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        logger.addAppender(log);
        try {
            assertEquals(3, service.paySuccess(9L, 1));
            verify(dao).updateSkuStock(detail.getOrderItemList());
            verify(skuStockMapper, never()).updateByPrimaryKeySelective(any());
            assertAll(
                    () -> verify(skuStockMapper, never()).selectByPrimaryKey(any()),
                    () -> assertTrue(log.list.isEmpty()));
        } finally {
            logger.detachAppender(log);
            log.stop();
        }
    }

    private static <T> T dependency(Object service, String field, Class<T> type) {
        T value = mock(type);
        ReflectionTestUtils.setField(service, field, value);
        return value;
    }
}
