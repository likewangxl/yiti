package com.bank.branch.platform.customer.listener;

import com.bank.branch.platform.customer.event.ClaimCreatedEvent;
import com.bank.branch.platform.customer.service.TouchTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 认领成功事件监听器，负责在认领事务提交后自动创建首次触达任务。
 * <p>
 * 使用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 保证触达任务创建
 * 仅在认领事务提交成功后才执行，避免认领失败时留下孤立的触达任务记录。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimCreatedListener {

    private final TouchTaskService touchTaskService;

    /**
     * 处理认领成功事件，创建首次触达任务。
     *
     * @param event 认领成功事件，包含 custId、orgId、maintainerEmpId
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ClaimCreatedEvent event) {
        log.info("[ClaimCreatedListener.handle] claimId={}, custId={}, maintainerEmpId={}",
                event.getClaimId(), event.getCustId(), event.getMaintainerEmpId());
        try {
            touchTaskService.createFromClaim(
                    event.getCustId(), event.getOrgId(), event.getMaintainerEmpId());
            log.info("[ClaimCreatedListener.handle] touch task created for claimId={}", event.getClaimId());
        } catch (Exception e) {
            // 触达任务创建失败不应影响主流程，记录错误日志后不再向上抛出
            log.error("[ClaimCreatedListener.handle] failed to create touch task for claimId={}: {}",
                    event.getClaimId(), e.getMessage(), e);
        }
    }
}
