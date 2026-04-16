package com.bank.branch.platform.customer.listener;

import com.bank.branch.platform.customer.event.LeadDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 线索删除事件监听器。
 * <p>
 * 在线索被强制删除（非审批流删除，直接物理删除草稿线索）事务提交后触发，
 * 负责清理与该线索关联的数据（如标签关联关系等）。
 * <p>
 * 注意：通过审批流删除客户主档的场景由 LeadApprovedListener + CustMasterAssemblerService 处理，
 * 不经过此监听器。此监听器仅处理线索本身被删除时的旁路数据清理。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeadDeletedListener {

    /**
     * 处理线索删除事件。
     * <p>
     * 当前版本仅记录审计日志。后续如有线索关联的标签关系、附件等数据需要清理，
     * 在此处扩展清理逻辑，无需修改发布方代码（开闭原则）。
     * </p>
     *
     * @param event 线索删除事件，包含 leadId、leadNo、sourceCustId、operatorEmpId
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LeadDeletedEvent event) {
        log.info("[LeadDeletedListener.handle] 线索删除事件处理 leadId={}, leadNo={}, sourceCustId={}, operator={}",
                event.getLeadId(), event.getLeadNo(), event.getSourceCustId(), event.getOperatorEmpId());

        // 当前版本：仅记录日志，线索关联数据随线索级联删除（DDL ON DELETE CASCADE）
        // TODO: 若后续线索有单独的标签关联表（非 cust_tag_rel），在此处清理
    }
}
