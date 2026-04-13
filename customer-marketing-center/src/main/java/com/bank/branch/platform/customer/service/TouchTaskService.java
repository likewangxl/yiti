package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.SlaStatus;
import com.bank.branch.platform.customer.enums.TouchTaskStatus;
import com.bank.branch.platform.customer.enums.TouchTaskType;
import com.bank.branch.platform.customer.event.TouchCompletedEvent;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 触达任务业务服务。
 * <p>
 * 负责触达任务的创建（由认领事件驱动）、完成、取消、查询及 SLA 状态刷新。
 * 任务生命周期：PENDING → SUCCESS（完成） | CANCELLED（取消）。
 * SLA 状态由定时任务周期性刷新：GREEN → YELLOW（到预警时间）→ RED（超计划完成时间且未完成）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TouchTaskService {

    private final TouchTaskMapper taskMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 从认领事件创建首次触达任务。
     * <p>
     * 由 {@code ClaimCreatedListener} 在认领成功事务提交后调用。
     * 任务类型为 FIRST_TOUCH，初始状态 PENDING，SLA 状态 GREEN。
     * 计划完成时间 = now + 7 天，预警时间 = now + 5 天。
     * </p>
     *
     * @param custId         客户ID
     * @param orgId          所属机构代码
     * @param assigneeEmpId  执行人（维护人）员工工号
     * @return 创建成功的触达任务实体
     */
    @Transactional
    public TouchTask createFromClaim(String custId, String orgId, String assigneeEmpId) {
        log.info("[TouchTaskService.createFromClaim] custId={}, orgId={}, assigneeEmpId={}",
                custId, orgId, assigneeEmpId);

        LocalDateTime now = LocalDateTime.now();

        // 生成 taskNo: TOUCH_{timestamp}_{random4}
        String taskNo = "TOUCH_" + System.currentTimeMillis() + "_"
                + String.format("%04d", new Random().nextInt(10000));

        // 生成任务 ID（UUID 32 位无连字符）
        String taskId = UUID.randomUUID().toString().replace("-", "");

        TouchTask entity = new TouchTask();
        entity.setId(taskId);
        entity.setTaskNo(taskNo);
        entity.setCustId(custId);
        entity.setOrgId(orgId);
        entity.setAssigneeEmpId(assigneeEmpId);
        entity.setTaskType(TouchTaskType.FIRST_TOUCH.getCode());
        entity.setTaskStatus(TouchTaskStatus.PENDING.getCode());
        entity.setSlaStatus(SlaStatus.GREEN.getCode());
        // 计划完成时间：7 天后；预警时间：5 天后（到预警时间变黄）
        entity.setPlanFinishTime(now.plusDays(7));
        entity.setWarningTime(now.plusDays(5));
        // businessKey 格式: TOUCH:{taskId}
        entity.setBusinessKey("TOUCH:" + taskId);
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now);

        taskMapper.insert(entity);

        log.info("[TouchTaskService.createFromClaim] task created, taskId={}, taskNo={}", taskId, taskNo);
        return entity;
    }

    /**
     * 完成触达任务。
     * <p>
     * 前置条件：任务状态必须为 PENDING，否则抛出 TOUCH_TASK_NOT_PENDING。
     * 完成后状态变更为 SUCCESS，记录 successTime，并发布 {@link TouchCompletedEvent}。
     * </p>
     *
     * @param taskId 任务ID
     */
    @Transactional
    public void complete(String taskId) {
        log.info("[TouchTaskService.complete] taskId={}", taskId);

        TouchTask task = getById(taskId);

        // 非 PENDING 状态不允许完成
        if (!TouchTaskStatus.PENDING.getCode().equals(task.getTaskStatus())) {
            throw new BizException(CustomerErrorCode.TOUCH_TASK_NOT_PENDING.getCode(),
                    CustomerErrorCode.TOUCH_TASK_NOT_PENDING.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        TouchTask updateEntity = new TouchTask();
        updateEntity.setId(taskId);
        updateEntity.setTaskStatus(TouchTaskStatus.SUCCESS.getCode());
        updateEntity.setSuccessTime(now);
        updateEntity.setUpdatedTime(now);

        taskMapper.updateById(updateEntity);

        // 发布触达完成事件，下游可扩展处理逻辑
        eventPublisher.publishEvent(new TouchCompletedEvent(
                taskId, task.getTaskNo(), task.getCustId(),
                task.getAssigneeEmpId(), task.getTaskType()));

        log.info("[TouchTaskService.complete] task completed, taskId={}", taskId);
    }

    /**
     * 取消触达任务。
     * <p>
     * 前置条件：任务状态必须为 PENDING，否则抛出 TOUCH_TASK_NOT_PENDING。
     * 取消后状态变更为 CANCELLED，记录 cancelTime。
     * reason 仅记录日志，TouchTask 表无 cancelReason 字段。
     * </p>
     *
     * @param taskId 任务ID
     * @param reason 取消原因（仅记日志，不持久化到 touch_task 表）
     */
    @Transactional
    public void cancel(String taskId, String reason) {
        log.info("[TouchTaskService.cancel] taskId={}, reason={}", taskId, reason);

        TouchTask task = getById(taskId);

        // 非 PENDING 状态不允许取消
        if (!TouchTaskStatus.PENDING.getCode().equals(task.getTaskStatus())) {
            throw new BizException(CustomerErrorCode.TOUCH_TASK_NOT_PENDING.getCode(),
                    CustomerErrorCode.TOUCH_TASK_NOT_PENDING.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        TouchTask updateEntity = new TouchTask();
        updateEntity.setId(taskId);
        updateEntity.setTaskStatus(TouchTaskStatus.CANCELLED.getCode());
        updateEntity.setCancelTime(now);
        updateEntity.setUpdatedTime(now);

        taskMapper.updateById(updateEntity);

        log.info("[TouchTaskService.cancel] task cancelled, taskId={}", taskId);
    }

    /**
     * 按 ID 查询触达任务。
     *
     * @param id 任务ID
     * @return 触达任务实体
     * @throws BizException TOUCH_TASK_NOT_FOUND 当任务不存在时
     */
    public TouchTask getById(String id) {
        TouchTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getCode(),
                    CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getMessage());
        }
        return task;
    }

    /**
     * 分页查询触达任务列表。
     * <p>
     * keyword 模糊搜索 task_no，status 和 assigneeEmpId 精确匹配。
     * offset = (pageNo - 1) * pageSize
     * </p>
     *
     * @param keyword       关键词（搜索 task_no），可为 null
     * @param status        任务状态过滤，可为 null
     * @param assigneeEmpId 执行人工号过滤，可为 null
     * @param pageNo        页码（从 1 开始）
     * @param pageSize      每页大小
     * @return 分页的触达任务列表
     */
    public PageResult<TouchTask> listPage(String keyword, String status, String assigneeEmpId,
                                          int pageNo, int pageSize) {
        log.info("[TouchTaskService.listPage] keyword={}, status={}, assigneeEmpId={}, pageNo={}, pageSize={}",
                keyword, status, assigneeEmpId, pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;
        List<TouchTask> records = taskMapper.selectPage(keyword, status, assigneeEmpId, offset, pageSize);
        long total = taskMapper.countPage(keyword, status, assigneeEmpId);

        log.info("[TouchTaskService.listPage] total={}", total);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 刷新 PENDING 任务的 SLA 状态（供定时任务调用）。
     * <p>
     * 规则：
     * <ul>
     *   <li>当前时间 &ge; planFinishTime → RED（超期）</li>
     *   <li>当前时间 &ge; warningTime → YELLOW（预警）</li>
     * </ul>
     * 若 SLA 状态已经是目标值则跳过更新，减少不必要的 DB 写。
     * </p>
     */
    public void refreshSla() {
        log.info("[TouchTaskService.refreshSla] starting SLA refresh");

        List<TouchTask> pendingTasks = taskMapper.selectPendingForSlaRefresh();
        LocalDateTime now = LocalDateTime.now();
        int updated = 0;

        for (TouchTask task : pendingTasks) {
            String newSlaStatus = calcNewSlaStatus(task, now);
            if (newSlaStatus != null && !newSlaStatus.equals(task.getSlaStatus())) {
                // 仅在状态有变化时执行更新
                TouchTask updateEntity = new TouchTask();
                updateEntity.setId(task.getId());
                updateEntity.setSlaStatus(newSlaStatus);
                updateEntity.setUpdatedTime(now);
                taskMapper.updateById(updateEntity);
                updated++;
                log.debug("[TouchTaskService.refreshSla] task={} SLA {} -> {}",
                        task.getId(), task.getSlaStatus(), newSlaStatus);
            }
        }

        log.info("[TouchTaskService.refreshSla] SLA refresh done, checked={}, updated={}",
                pendingTasks.size(), updated);
    }

    /**
     * 计算任务新的 SLA 状态。
     * <p>
     * 超过计划完成时间 → RED，到达预警时间 → YELLOW，其余 → null（不需要更新）。
     * </p>
     *
     * @param task 待检查的触达任务
     * @param now  当前时间
     * @return 新的 SLA 状态字符串，若无需变更则返回 null
     */
    private String calcNewSlaStatus(TouchTask task, LocalDateTime now) {
        if (task.getPlanFinishTime() != null && !now.isBefore(task.getPlanFinishTime())) {
            // 已超过计划完成时间 → RED
            return SlaStatus.RED.getCode();
        }
        if (task.getWarningTime() != null && !now.isBefore(task.getWarningTime())) {
            // 已到达预警时间但尚未超期 → YELLOW
            return SlaStatus.YELLOW.getCode();
        }
        return null;
    }
}
