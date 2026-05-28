package com.bank.branch.platform.portal.listener;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.governance.service.NotificationService;
import com.bank.branch.platform.workflow.api.event.ProcessWithdrawnEvent;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import com.bank.branch.platform.workflow.service.TaskOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 审批通过 / 驳回 / 撤回 自动给申请人或下一节点审批人发通知。
 * <p>用 @TransactionalEventListener(AFTER_COMMIT) 保证事务回滚不发通知。
 * 撤回事件因 cancelProcess 内 publish 在 delete 之后已无事务一致性问题，仍用 AFTER_COMMIT 保守一致。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowApprovalNotificationListener {

    private final BizProcessMapMapper bizProcessMapMapper;
    private final UserApi userApi;
    private final NotificationService notificationService;

    @EventListener
    public void onTaskApproved(TaskOperationService.TaskApprovedEvent event) {
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(event.processInstanceId());
        if (map == null || map.getStartUser() == null) {
            log.debug("[Notification.onApproved] skip: no map / startUser pid={}", event.processInstanceId());
            return;
        }
        String approverName = safeUserName(event.empId());
        String lbl = resolveBizLabel(map.getBizType());
        notificationService.sendNotification(NotificationCmd.builder()
                .targetEmpId(map.getStartUser())
                .title(lbl + " · 审批通过")
                .content("您的【" + lbl + "】申请已通过审批（审批人：" + approverName + "）")
                .notifyType("WORKFLOW")
                .bizType(map.getBizType())
                .bizId(map.getBizId())
                .build());
    }

    @EventListener
    public void onTaskRejected(TaskOperationService.TaskRejectedEvent event) {
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(event.processInstanceId());
        if (map == null || map.getStartUser() == null) {
            log.debug("[Notification.onRejected] skip: no map / startUser pid={}", event.processInstanceId());
            return;
        }
        String approverName = safeUserName(event.empId());
        String lbl = resolveBizLabel(map.getBizType());
        notificationService.sendNotification(NotificationCmd.builder()
                .targetEmpId(map.getStartUser())
                .title(lbl + " · 审批驳回")
                .content("您的【" + lbl + "】申请被驳回（审批人：" + approverName + "）")
                .notifyType("WORKFLOW")
                .bizType(map.getBizType())
                .bizId(map.getBizId())
                .build());
    }

    @EventListener
    public void onProcessWithdrawn(ProcessWithdrawnEvent event) {
        if (event.currentAssigneeEmpId() == null) {
            log.debug("[Notification.onWithdrawn] skip: no active assignee pid={}", event.processInstanceId());
            return;
        }
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(event.processInstanceId());
        String withdrawerName = safeUserName(event.withdrawnByEmpId());
        String lbl = resolveBizLabel(map != null ? map.getBizType() : null);
        notificationService.sendNotification(NotificationCmd.builder()
                .targetEmpId(event.currentAssigneeEmpId())
                .title(lbl + " · 申请撤回")
                .content("【" + lbl + "】申请人 " + withdrawerName + " 已撤回申请")
                .notifyType("WORKFLOW")
                .bizType(map != null ? map.getBizType() : null)
                .bizId(map != null ? map.getBizId() : null)
                .build());
    }

    private String resolveBizLabel(String bizType) {
        if (bizType == null) return "流程";
        return switch (bizType) {
            case "ALLOC_ADJUST" -> "业绩调整审批";
            case "TARGET_ADJUST" -> "目标修正审批";
            case "LOAN_APPLY" -> "贷款申请审批";
            default -> bizType;
        };
    }

    private String safeUserName(String empId) {
        if (empId == null) return "未知";
        try {
            String name = userApi.getUserName(empId);
            return name != null ? name : empId;
        } catch (Exception ex) {
            log.warn("[Notification] getUserName failed empId={}, fallback to empId", empId, ex);
            return empId;
        }
    }
}
