package com.bank.branch.platform.customer.listener;

import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.event.LeadApprovedEvent;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.service.CustMasterAssemblerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 线索审批通过事件监听器。
 * <p>
 * 在工作流审批通过（线索状态变为 APPROVED）事务提交后触发，根据线索操作类型同步客户主档：
 * <ul>
 *   <li>CREATE：创建新客户主档</li>
 *   <li>UPDATE：更新已有客户主档</li>
 *   <li>DELETE：逻辑删除客户主档</li>
 * </ul>
 * 使用 AFTER_COMMIT 保证只在审批状态已持久化后才执行主档装配，避免读取到中间态数据。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeadApprovedListener {

    private final CustLeadMapper leadMapper;
    private final CustMasterAssemblerService assemblerService;

    /**
     * 处理线索审批通过事件。
     * <p>
     * 通过 leadId 重新读取完整线索实体（不使用事件中的冗余字段），
     * 然后委托 CustMasterAssemblerService 完成主档装配。
     * </p>
     *
     * @param event 线索审批通过事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LeadApprovedEvent event) {
        log.info("[LeadApprovedListener.handle] 接收到线索审批通过事件 leadId={}, leadOp={}, sourceCustId={}",
                event.getLeadId(), event.getLeadOp(), event.getSourceCustId());

        // 重新从数据库读取完整线索实体，确保拿到所有字段（事件中只有摘要信息）
        CustLead lead = leadMapper.selectById(event.getLeadId());
        if (lead == null) {
            log.error("[LeadApprovedListener.handle] 线索不存在，忽略事件 leadId={}", event.getLeadId());
            return;
        }

        try {
            assemblerService.assembleFromLead(lead);
        } catch (Exception e) {
            // 装配失败时记录错误日志，不重抛异常避免影响其他事件监听器
            // 实际生产环境应接入告警和补偿机制
            log.error("[LeadApprovedListener.handle] 客户主档装配失败 leadId={}, error={}",
                    event.getLeadId(), e.getMessage(), e);
        }
    }
}
