package com.bank.branch.platform.portal.listener;

import com.bank.branch.platform.portal.api.event.ProductResponsibleUpdatedEvent;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.ArrayList;
import java.util.List;

/**
 * 产品负责人双向同步监听器
 * <p>在主事务提交后，独立事务内更新 addrbook_employee 的 responsible_product_ids</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductResponsibleSyncListener {

    private final AddrbookEmployeeMapper addrbookMapper;

    /**
     * 在主事务提交后，独立事务内更新 addrbook_employee 的 responsible_product_ids
     * 失败重试 3 次（指数退避 100/500/2000ms），仍失败记 ERROR 日志
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProductResponsibleUpdated(ProductResponsibleUpdatedEvent event) {
        if (!"PRODUCT_SIDE".equals(event.getSource())) {
            log.debug("Skip event from source={}", event.getSource());
            return;
        }
        for (String empId : event.getRemoved()) {
            updateWithRetry(empId, event.getProductId(), event.getOperatorEmpId(), false);
        }
        for (String empId : event.getAdded()) {
            updateWithRetry(empId, event.getProductId(), event.getOperatorEmpId(), true);
        }
    }

    private void updateWithRetry(String empId, String productId, String operator, boolean isAdd) {
        long[] backoffMs = {100, 500, 2000};
        for (int attempt = 0; attempt < 3; attempt++) {
            AddrbookEmployee emp = addrbookMapper.selectByEmpId(empId);
            if (emp == null) {
                log.warn("addrbook employee {} not found, skip sync", empId);
                return;
            }
            List<String> current = emp.getResponsibleProductIds() != null
                ? new ArrayList<>(emp.getResponsibleProductIds())
                : new ArrayList<>();
            if (isAdd) {
                if (!current.contains(productId)) current.add(productId);
            } else {
                current.remove(productId);
            }
            int rows = addrbookMapper.updateResponsibleProductsWithOptimisticLock(
                empId, current, emp.getUpdatedTime(), operator);
            if (rows > 0) return;
            try { Thread.sleep(backoffMs[attempt]); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            log.debug("Optimistic lock retry {} for empId={}", attempt + 1, empId);
        }
        log.error("addrbook sync failed after 3 retries: empId={} productId={} isAdd={}", empId, productId, isAdd);
    }
}
