package com.bank.branch.platform.performance.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramNodeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 业绩分配调整审批超时提醒（定时任务业务逻辑）.
 *
 * <p>扫描 {@code PERF_ALLOC_ADJUST_APPLY} 中「审批中（IN_APPROVAL）且从申请时间起满 14 天未办结」
 * 的记录，给申请人在通知中心发一条提醒（含当前审批环节 + 已用天数），**仅提醒一次**
 * （靠 {@code overdue_notified_time} 标记列去重，发过即置时间、扫描 IS NULL 过滤）。
 *
 * <p>逐条独立、不加 @Transactional（发一条置一条标记，避免大事务；发送允许异步）；
 * 单条发送失败不置标记（次日重试）、不影响其他条。由 {@code PerfAllocOverdueNotifyQuartzJob} 定时驱动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerfAllocOverdueNotifyJob {

    /** 审批超时阈值：从申请时间起满 14 天未办结即提醒. */
    private static final int OVERDUE_DAYS = 14;

    private final PerfAllocAdjustApplyMapper applyMapper;
    private final NotifyApi notifyApi;
    private final WorkflowQueryApi workflowQueryApi;

    /** 定时入口：以当前时间运行. */
    public void run() {
        run(LocalDateTime.now());
    }

    /** 可注入运行时刻，便于测试. */
    void run(LocalDateTime now) {
        LocalDateTime threshold = now.minusDays(OVERDUE_DAYS);
        // 审批中 + 申请时间满 14 天（created_time <= now-14d）+ 未提醒过（overdue_notified_time IS NULL）
        List<PerfAllocAdjustApply> overdue = applyMapper.selectList(
                new LambdaQueryWrapper<PerfAllocAdjustApply>()
                        .eq(PerfAllocAdjustApply::getStatus, "IN_APPROVAL")
                        .le(PerfAllocAdjustApply::getCreatedTime, threshold)
                        .isNull(PerfAllocAdjustApply::getOverdueNotifiedTime));
        if (overdue == null || overdue.isEmpty()) {
            log.info("[PerfAllocOverdueNotifyJob] 无超时审批需提醒");
            return;
        }
        int sent = 0;
        for (PerfAllocAdjustApply apply : overdue) {
            try {
                String node = resolveCurrentNode(apply);
                long days = ChronoUnit.DAYS.between(apply.getCreatedTime().toLocalDate(), now.toLocalDate());
                notifyApi.sendNotification(NotificationCmd.builder()
                        .targetEmpId(apply.getCreatedBy())
                        .title("业绩分配调整审批超时提醒")
                        .content(buildContent(apply, days, node))
                        .notifyType("WORKFLOW")
                        .bizType("ALLOC_ADJUST")
                        .bizId(apply.getId())
                        .build());
                // 只提醒一次：发送成功才置提醒时间；失败不置（次日重试）。
                // 用 updateById(仅置 id+overdue_notified_time，其余 null 不更新) 而非 LambdaUpdateWrapper.set，
                // 后者会立即解析列依赖 MyBatis-Plus TableInfo，单测无上下文会抛 "lambda cache"。
                PerfAllocAdjustApply patch = new PerfAllocAdjustApply();
                patch.setId(apply.getId());
                patch.setOverdueNotifiedTime(now);
                applyMapper.updateById(patch);
                sent++;
            } catch (Exception e) {
                log.warn("[PerfAllocOverdueNotifyJob] 发送超时提醒失败 applyId={}, err={}",
                        apply.getId(), e.getMessage());
            }
        }
        log.info("[PerfAllocOverdueNotifyJob] 超时审批 {} 条，已发送提醒 {} 条", overdue.size(), sent);
    }

    /** 拼提醒正文：申请编号 + 客户 + 已用天数 + 当前审批环节. */
    private String buildContent(PerfAllocAdjustApply apply, long days, String node) {
        String cust = apply.getCustName() != null && !apply.getCustName().isBlank()
                ? apply.getCustName()
                : (apply.getCustId() != null ? apply.getCustId() : "");
        return "您提交的业绩分配调整申请【" + apply.getApplyNo() + " · 客户 " + cust + "】已提交 "
                + days + " 天仍未办结，当前审批环节：" + node + "。请关注审批进度或联系审批人跟进。";
    }

    /**
     * 取当前审批环节中文名（复用 PerfApprovalQueryFacade 同款最小逻辑，不改动 facade）：
     * 按 processInstanceId 查 Flowable 活动 userTask 节点名；无 pid / 无活动节点 / 异常 → "审批中"。
     */
    private String resolveCurrentNode(PerfAllocAdjustApply apply) {
        String pid = apply.getProcessInstanceId();
        if (pid == null || pid.isBlank()) {
            return "审批中";
        }
        try {
            ProcessDiagramDTO diagram = workflowQueryApi.getProcessNodes(pid);
            if (diagram != null && diagram.getNodes() != null) {
                for (ProcessDiagramNodeDTO n : diagram.getNodes()) {
                    if ("ACTIVE".equals(n.getStatus()) && "userTask".equals(n.getNodeType())) {
                        return n.getNodeName();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[PerfAllocOverdueNotifyJob] 查询当前审批节点失败 pid={}, err={}", pid, e.getMessage());
        }
        return "审批中";
    }
}
