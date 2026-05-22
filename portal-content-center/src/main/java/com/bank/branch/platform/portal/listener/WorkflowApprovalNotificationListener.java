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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskApproved(TaskOperationService.TaskApprovedEvent event) {
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(event.processInstanceId());
        if (map == null || map.getStartUser() == null) {
            log.debug("[Notification.onApproved] skip: no map / startUser pid={}", event.processInstanceId());
            return;
        }
        String approverName = safeUserName(event.empId());
        notificationService.sendNotification(NotificationCmd.builder()
                .targetEmpId(map.getStartUser())
                .title("审批通过")
                .content("您的申请已通过审批 by " + approverName)
                .notifyType("WORKFLOW")
                .bizType(map.getBizType())
                .bizId(map.getBizId())
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskRejected(TaskOperationService.TaskRejectedEvent event) {
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(event.processInstanceId());
        if (map == null || map.getStartUser() == null) {
            log.debug("[Notification.onRejected] skip: no map / startUser pid={}", event.processInstanceId());
            return;
        }
        String approverName = safeUserName(event.empId());
        notificationService.sendNotification(NotificationCmd.builder()
                .targetEmpId(map.getStartUser())
                .title("审批驳回")
                .content("您的申请被驳回 by " + approverName)
                .notifyType("WORKFLOW")
                .bizType(map.getBizType())
                .bizId(map.getBizId())
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProcessWithdrawn(ProcessWithdrawnEvent event) {
        if (event.currentAssigneeEmpId() == null) {
            // 候选组未签收：assignee 为 null，不发通知（spec §9 边界，本次不覆盖）
            log.debug("[Notification.onWithdrawn] skip: no active assignee pid={}", event.processInstanceId());
            return;
        }
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(event.processInstanceId());
        String withdrawerName = safeUserName(event.withdrawnByEmpId());
        notificationService.sendNotification(NotificationCmd.builder()
                .targetEmpId(event.currentAssigneeEmpId())
                .title("申请撤回")
                .content("申请人 " + withdrawerName + " 已撤回申请")
                .notifyType("WORKFLOW")
                .bizType(map != null ? map.getBizType() : null)
                .bizId(map != null ? map.getBizId() : null)
                .build());
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
