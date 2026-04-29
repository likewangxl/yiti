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
    private final TouchTaskStateMachineService stateMachine;

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
     * 由 ClaimService.reTouch 调用，对已认领客户创建一个 FOLLOW_UP（非首次）触达任务。
     * <p>
     * 与 {@link #createFromClaim(String, String, String)} 的差异：task_type=FOLLOW_UP；
     * reason 仅记录到日志（touch_task 表无 reason 列）；planFinishTime 为可选字符串，
     * 缺省/解析失败时回退到默认 7 天。
     * </p>
     *
     * @param custId         客户ID
     * @param orgId          所属机构代码
     * @param assigneeEmpId  执行人（维护人）员工工号
     * @param reason         重新触达原因（仅记日志）
     * @param planFinishTime 计划完成时间字符串（可选；格式 yyyy-MM-dd HH:mm:ss）
     * @return 创建成功的触达任务实体
     */
    @Transactional
    public TouchTask createFollowUpTask(String custId, String orgId, String assigneeEmpId,
                                         String reason, String planFinishTime) {
        log.info("[TouchTaskService.createFollowUpTask] custId={}, orgId={}, assigneeEmpId={}, reason={}, planFinishTime={}",
                custId, orgId, assigneeEmpId, reason, planFinishTime);

        LocalDateTime now = LocalDateTime.now();
        String taskNo = "TOUCH_" + System.currentTimeMillis() + "_"
                + String.format("%04d", new Random().nextInt(10000));
        String taskId = UUID.randomUUID().toString().replace("-", "");

        TouchTask entity = new TouchTask();
        entity.setId(taskId);
        entity.setTaskNo(taskNo);
        entity.setCustId(custId);
        entity.setOrgId(orgId);
        entity.setAssigneeEmpId(assigneeEmpId);
        entity.setTaskType(TouchTaskType.FOLLOW_UP.getCode());
        entity.setTaskStatus(TouchTaskStatus.PENDING.getCode());
        entity.setSlaStatus(SlaStatus.GREEN.getCode());

        LocalDateTime planTime = parsePlanFinishTimeOrDefault(planFinishTime, now);
        entity.setPlanFinishTime(planTime);
        // 预警时间 = 计划完成前 2 天（与 createFromClaim 的 5d/7d 同 2 天偏移）
        entity.setWarningTime(planTime.minusDays(2));
        entity.setBusinessKey("TOUCH:" + taskId);
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now);

        taskMapper.insert(entity);

        log.info("[TouchTaskService.createFollowUpTask] follow-up task created, taskId={}, taskNo={}", taskId, taskNo);
        return entity;
    }

    private LocalDateTime parsePlanFinishTimeOrDefault(String planFinishTime, LocalDateTime base) {
        if (planFinishTime == null || planFinishTime.isBlank()) {
            return base.plusDays(7);
        }
        try {
            return LocalDateTime.parse(planFinishTime,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("[TouchTaskService.createFollowUpTask] invalid planFinishTime={}, fallback to default 7d", planFinishTime);
            return base.plusDays(7);
        }
    }

    /**
     * 标记触达任务为已完成。
     * <p>
     * 允许起始状态：PENDING、IN_PROGRESS。
     * 状态转换由 {@link TouchTaskStateMachineService} 校验，非法转移抛 CUST-40010。
     * 完成后 successTime=now，并发布 {@link TouchCompletedEvent}。
     * </p>
     *
     * @param taskId 任务ID
     * @throws BizException CUST-40405 任务不存在
     * @throws BizException CUST-40010 非法状态转移（SUCCESS/CANCELLED 终态不允许再转移）
     */
    @Transactional
    public void markSuccess(String taskId) {
        log.info("[TouchTaskService.markSuccess] taskId={}", taskId);

        TouchTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getCode(),
                    CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getMessage());
        }

        // 通过状态机校验转移合法性：PENDING/IN_PROGRESS → SUCCESS 合法，终态不可转
        TouchTaskStatus from = TouchTaskStatus.valueOf(task.getTaskStatus());
        stateMachine.assertTransition(from, TouchTaskStatus.SUCCESS);

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

        log.info("[TouchTaskService.markSuccess] task marked success, taskId={}", taskId);
    }

    /**
     * 取消触达任务。
     * <p>
     * 允许起始状态：PENDING、IN_PROGRESS（由状态机校验）。
     * SUCCESS/CANCELLED 终态不允许取消，抛 CUST-40010。
     * 取消后状态变更为 CANCELLED，记录 cancelTime。
     * reason 仅记录日志，TouchTask 表无 cancelReason 字段。
     * </p>
     *
     * @param taskId 任务ID
     * @param reason 取消原因（仅记日志，不持久化到 touch_task 表）
     * @throws BizException CUST-40405 任务不存在
     * @throws BizException CUST-40010 非法状态转移（SUCCESS/CANCELLED 终态不允许再转移）
     */
    @Transactional
    public void cancel(String taskId, String reason) {
        log.info("[TouchTaskService.cancel] taskId={}, reason={}", taskId, reason);

        TouchTask task = getById(taskId);

        // 通过状态机校验转移合法性：PENDING/IN_PROGRESS → CANCELLED 合法，终态不可转
        TouchTaskStatus from = TouchTaskStatus.valueOf(task.getTaskStatus());
        stateMachine.assertTransition(from, TouchTaskStatus.CANCELLED);

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
     * 将待处理任务迁移到进行中状态。
     * <p>
     * 由 {@link TouchLogService} 在首次日志插入后自动触发，实现 PENDING → IN_PROGRESS 自动状态迁移。
     * 依据《功能规格》§7.3bis：首次追加触达日志时，任务状态从 PENDING 自动变为 IN_PROGRESS。
     * </p>
     *
     * @param taskId 任务ID
     * @throws BizException CUST-40405 任务不存在
     * @throws BizException CUST-40010 非法状态转移（当前状态不允许转移到 IN_PROGRESS）
     */
    @Transactional
    public void markInProgress(String taskId) {
        log.info("[TouchTaskService.markInProgress] taskId={}", taskId);

        TouchTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getCode(),
                    CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getMessage());
        }

        // 通过状态机校验转移合法性：PENDING → IN_PROGRESS 合法，其他状态均非法
        TouchTaskStatus from = TouchTaskStatus.valueOf(task.getTaskStatus());
        stateMachine.assertTransition(from, TouchTaskStatus.IN_PROGRESS);

        TouchTask updateEntity = new TouchTask();
        updateEntity.setId(taskId);
        updateEntity.setTaskStatus(TouchTaskStatus.IN_PROGRESS.getCode());
        updateEntity.setUpdatedTime(LocalDateTime.now());
        taskMapper.updateById(updateEntity);

        log.info("[TouchTaskService.markInProgress] task {} transitioned PENDING→IN_PROGRESS", taskId);
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
     * 管理后台全局分页查询触达任务（不按机构过滤，需 ADMIN 权限）。
     * <p>
     * keyword 模糊搜索 task_no，status、assigneeEmpId、orgId 精确匹配，均可为 null 表示不过滤。
     * offset = (pageNo - 1) * pageSize
     * </p>
     *
     * @param keyword       关键词（搜索 task_no），可为 null
     * @param status        任务状态过滤，可为 null
     * @param assigneeEmpId 执行人工号过滤，可为 null
     * @param orgId         机构 ID 过滤，可为 null
     * @param pageNo        页码（从 1 开始）
     * @param pageSize      每页大小
     * @return 分页的触达任务列表（跨机构全局视图）
     */
    public PageResult<TouchTask> listPageAdmin(String keyword, String status, String assigneeEmpId,
                                               String orgId, int pageNo, int pageSize) {
        log.info("[TouchTaskService.listPageAdmin] keyword={}, status={}, assigneeEmpId={}, orgId={}, pageNo={}, pageSize={}",
                keyword, status, assigneeEmpId, orgId, pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;
        List<TouchTask> records = taskMapper.selectAdminPage(keyword, status, assigneeEmpId, orgId, offset, pageSize);
        Long total = taskMapper.countAdminPage(keyword, status, assigneeEmpId, orgId);

        log.info("[TouchTaskService.listPageAdmin] total={}", total);
        return PageResult.of(pageNo, pageSize, total == null ? 0L : total, records);
    }

    /**
     * 管理后台导出全量触达任务数据。
     * <p>
     * 支持 keyword、status、orgId 过滤，最多导出 maxRows 条记录，
     * 适用于 CSV 导出场景，不分页直接返回列表。
     * </p>
     *
     * @param keyword 关键词过滤，可为 null
     * @param status  状态过滤，可为 null
     * @param orgId   机构 ID 过滤，可为 null
     * @param maxRows 最大导出行数（防止导出过多数据）
     * @return 触达任务列表
     */
    public List<TouchTask> listAllForAdminExport(String keyword, String status, String orgId, int maxRows) {
        log.info("[TouchTaskService.listAllForAdminExport] keyword={}, status={}, orgId={}, maxRows={}",
                keyword, status, orgId, maxRows);
        return taskMapper.selectAdminPage(keyword, status, null, orgId, 0, maxRows);
    }

    /**
     * 批量分配触达任务给新执行人。
     * <p>
     * 仅允许对 PENDING 或 IN_PROGRESS 状态的任务进行重分配，
     * 终态（SUCCESS/CANCELLED）任务自动跳过（不报错），
     * 不存在的任务 ID 也自动跳过。
     * 返回实际成功更新的任务数量。
     * </p>
     *
     * @param taskIds       待分配的任务 ID 列表
     * @param newAssigneeEmpId 新执行人员工工号
     * @return 实际更新的任务数量
     */
    @Transactional
    public int batchAssign(List<String> taskIds, String newAssigneeEmpId) {
        log.info("[TouchTaskService.batchAssign] taskCount={}, newAssigneeEmpId={}", taskIds.size(), newAssigneeEmpId);

        int updated = 0;
        for (String id : taskIds) {
            TouchTask task = taskMapper.selectById(id);
            if (task == null) {
                // 不存在的任务静默跳过，防止单个错误终止整批操作
                log.warn("[TouchTaskService.batchAssign] task not found, skip: {}", id);
                continue;
            }
            // 只允许对 PENDING / IN_PROGRESS 的任务重分配，终态静默跳过
            if (!"PENDING".equals(task.getTaskStatus()) && !"IN_PROGRESS".equals(task.getTaskStatus())) {
                log.debug("[TouchTaskService.batchAssign] task {} status={} is terminal, skip", id, task.getTaskStatus());
                continue;
            }
            task.setAssigneeEmpId(newAssigneeEmpId);
            taskMapper.updateById(task);
            updated++;
        }

        log.info("[TouchTaskService.batchAssign] done, updated={}", updated);
        return updated;
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
                // 仅在状态有变化时执行更新；同步维护 slaWarning 契约字段
                boolean warning = SlaStatus.YELLOW.getCode().equals(newSlaStatus)
                        || SlaStatus.RED.getCode().equals(newSlaStatus);
                TouchTask updateEntity = new TouchTask();
                updateEntity.setId(task.getId());
                updateEntity.setSlaStatus(newSlaStatus);
                updateEntity.setSlaWarning(warning);
                updateEntity.setUpdatedTime(now);
                taskMapper.updateById(updateEntity);
                updated++;
                log.debug("[TouchTaskService.refreshSla] task={} SLA {} -> {}, slaWarning={}",
                        task.getId(), task.getSlaStatus(), newSlaStatus, warning);
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
