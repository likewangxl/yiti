package com.bank.branch.platform.customer.listener;

import com.bank.branch.platform.customer.entity.AssetProjectUrgentApply;
import com.bank.branch.platform.customer.mapper.AssetProjectApplyMapper;
import com.bank.branch.platform.customer.mapper.AssetProjectUrgentApplyMapper;
import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/** 回写资产立项主流程和独立加急流程终态。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetProjectWorkflowListener {
    private final AssetProjectApplyMapper applyMapper;
    private final AssetProjectUrgentApplyMapper urgentMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onProcessCompleted(ProcessCompletedEvent event) {
        if (event.businessKey() == null) return;
        if (event.businessKey().startsWith("ASSET_PROJECT_URGENT:")) {
            completeUrgent(event);
        } else if (event.businessKey().startsWith("ASSET_PROJECT:")) {
            completeMain(event);
        }
    }

    private void completeMain(ProcessCompletedEvent event) {
        Long id = parseId(event.businessKey(), "ASSET_PROJECT:");
        if (id == null) return;
        String target = "REJECTED".equals(event.outcome()) ? "REJECTED" : "COMPLETED";
        int updated = applyMapper.conditionalUpdateStatus(id, "IN_APPROVAL", target, "SYSTEM", LocalDateTime.now());
        if (updated == 0) log.info("资产立项主流程已回写或状态不匹配，id={}", id);
    }

    private void completeUrgent(ProcessCompletedEvent event) {
        Long id = parseId(event.businessKey(), "ASSET_PROJECT_URGENT:");
        if (id == null) return;
        AssetProjectUrgentApply urgent = urgentMapper.selectById(id);
        if (urgent == null) return;
        boolean approved = !"REJECTED".equals(event.outcome());
        int updated = urgentMapper.complete(id, "IN_APPROVAL", approved ? "APPROVED" : "REJECTED",
                "SYSTEM", event.reason(), LocalDateTime.now());
        if (updated == 1 && approved) {
            applyMapper.markUrgentApproved(urgent.getAssetProjectApplyId(), "SYSTEM", LocalDateTime.now());
        }
    }

    private Long parseId(String businessKey, String prefix) {
        try { return Long.valueOf(businessKey.substring(prefix.length())); }
        catch (RuntimeException ex) { log.warn("忽略非法资产立项业务键: {}", businessKey); return null; }
    }
}
