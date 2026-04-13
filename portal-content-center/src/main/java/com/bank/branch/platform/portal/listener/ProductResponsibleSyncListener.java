package com.bank.branch.platform.portal.listener;

import com.bank.branch.platform.portal.event.ProductResponsibleUpdatedEvent;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 产品负责人变更事件监听器
 *
 * <p>仅负责事务提交后的缓存清理和日志记录。
 * 实际的 addrbook_employee 双向同步由 ProductService.syncResponsibleToAddrbook() 在事务内完成，
 * 保证数据一致性。本监听器不再重复同步，避免双写问题。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductResponsibleSyncListener {

    private final AddrbookEmployeeMapper addrbookMapper;

    /**
     * 在主事务提交后记录日志，不再执行 addrbook 同步（已由 Service 内联完成）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductResponsibleUpdated(ProductResponsibleUpdatedEvent event) {
        if (!"PRODUCT_SIDE".equals(event.getSource())) {
            log.debug("Skip event from source={}", event.getSource());
            return;
        }
        log.info("[ProductResponsibleSyncListener] 产品负责人变更已提交, productId={}, before={}, after={}, operator={}",
                event.getProductId(), event.getBeforeEmpIds(), event.getAfterEmpIds(), event.getOperatorEmpId());
    }
}
