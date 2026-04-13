package com.bank.branch.platform.portal.listener;

import com.bank.branch.platform.portal.event.ProductResponsibleUpdatedEvent;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ProductResponsibleSyncListener 单元测试
 *
 * <p>Listener 仅负责日志记录，不再执行 addrbook 同步（已由 ProductService 内联完成）。
 * 测试验证：ADDRBOOK_SIDE 事件被跳过，PRODUCT_SIDE 事件不会调用 mapper。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductResponsibleSyncListenerTest {

    @Mock
    AddrbookEmployeeMapper addrbookMapper;

    @InjectMocks
    ProductResponsibleSyncListener listener;

    /**
     * source=ADDRBOOK_SIDE 时应跳过，mapper 不被调用
     */
    @Test
    void shouldSkipNonProductSideEvent() {
        ProductResponsibleUpdatedEvent event = new ProductResponsibleUpdatedEvent(
                "P001", "PROD_CODE",
                Collections.emptyList(),
                List.of("E001"),
                "ADDRBOOK_SIDE",
                "OP001",
                LocalDateTime.now()
        );

        listener.onProductResponsibleUpdated(event);

        verify(addrbookMapper, never()).selectByEmpId(any());
        verify(addrbookMapper, never()).updateResponsibleProductsWithOptimisticLock(
                any(), any(), any(), any());
    }

    /**
     * source=PRODUCT_SIDE 时仅记录日志，不调用 mapper 同步（已由 Service 内联完成）
     */
    @Test
    void shouldOnlyLogForProductSideEvent() {
        ProductResponsibleUpdatedEvent event = new ProductResponsibleUpdatedEvent(
                "NEW_P", "PROD_CODE",
                Collections.emptyList(),
                List.of("E001"),
                "PRODUCT_SIDE",
                "OP001",
                LocalDateTime.now()
        );

        listener.onProductResponsibleUpdated(event);

        // listener 不再做 addrbook 同步，mapper 不应被调用
        verify(addrbookMapper, never()).selectByEmpId(any());
        verify(addrbookMapper, never()).updateResponsibleProductsWithOptimisticLock(
                any(), any(), any(), any());
    }
}
