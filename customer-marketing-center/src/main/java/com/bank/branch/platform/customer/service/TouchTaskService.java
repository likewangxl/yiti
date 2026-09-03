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
import com.bank.branch.platform.customer.mapper.TouchWorklogMapper;
import com.bank.branch.platform.governance.api.ConfigApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 触达任务业务服务。
 * <p>
 * 负责触达任务的创建（由认领事件驱动）、完成、取消、查询及 SLA 状态刷新。
 * 任务生命周期：PENDING → IN_PROGRESS → SUCCESS（完成），
 * PENDING / IN_PROGRESS → CANCELLED（取消）。
 * SLA 状态由治理中心调度刷新：BLUE → YELLOW（到预警时间）→ RED（超计划完成时间且未完成）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TouchTaskService {

    /** 系统治理中心中的触达 SLA 配置键；缺少配置时按默认值处理。 */
    static final String SLA_DAYS_CONFIG_KEY = "CUSTOMER_TOUCH_TASK_SLA_DAYS";
    static final int DEFAULT_SLA_DAYS = 7;
    private static final int MAX_SLA_DAYS = 365;

    private final TouchTaskMapper taskMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TouchTaskStateMachineService stateMachine;
    private final TouchEligibilityService touchEligibilityService;
    private final TouchWorklogMapper worklogMapper;
    /** 正式 MARKETING_* 客户链路的资格校验，旧服务仅保留兼容旧 ID 的入口。 */
    private final MarketingTouchEligibilityService marketingTouchEligibilityService;

    /** 系统配置读取由治理中心提供；缺少键或异常值时由服务层回退默认值。 */
    private final ConfigApi configApi;

    /**
     * 从认领事件创建首次触达任务。
     * <p>
     * 由 {@code ClaimCreatedListener} 在认领成功事务提交后调用。
     * 任务类型为 FIRST_TOUCH，初始状态 PENDING，SLA 状态 BLUE。
     * 计划完成时间 = 发起时间 + {@value #SLA_DAYS_CONFIG_KEY} 配置天数（默认 7 天），
     * 预警时间为计划完成前 2 天。
     * </p>
     *
     * @param custId         客户ID
     * @param orgId          所属机构代码
     * @param assigneeEmpId  执行人（维护人）员工工号
     * @return 创建成功的触达任务实体
     */
    @Transactional
    public TouchTask createFromClaim(String custId, String orgId, String assigneeEmpId) {
        return createFirstTouchTask(custId, orgId, assigneeEmpId, null);
    }

    /**
     * 手动发起首次触达任务。认领本身不再自动建任务。
     *
     * @param planFinishTime 兼容旧客户端的字段；服务端忽略，统一按后台 SLA 配置计算
     */
    @Transactional
    public TouchTask createFirstTouchTask(String custId, String orgId, String assigneeEmpId,
                                           String planFinishTime) {
        return createFirstTouchTask(custId, orgId, assigneeEmpId, planFinishTime, null);
    }

    /**
     * 从正式认领关系创建首次触达任务，并保存认领关系来源。
     *
     * <p>旧调用方没有认领关系 ID 时继续使用四参数重载；正式认领入口传入
     * {@code sourceBizId} 后，已认领池可以优先按认领关系精确回查任务。</p>
     *
     * @param planFinishTime 兼容旧客户端的字段；服务端忽略，统一按后台 SLA 配置计算
     * @param sourceBizId    正式认领关系 ID，可为空以兼容历史调用方
     */
    @Transactional
    public TouchTask createFirstTouchTask(String custId, String orgId, String assigneeEmpId,
                                           String planFinishTime, Long sourceBizId) {
        log.info("[TouchTaskService.createFromClaim] custId={}, orgId={}, assigneeEmpId={}",
                custId, orgId, assigneeEmpId);

        assertTouchEligible(custId);
        LocalDateTime now = LocalDateTime.now();

        // 生成 taskNo: TOUCH_{timestamp}_{random4}
        String taskNo = "TOUCH_" + System.currentTimeMillis() + "_"
                + String.format("%04d", new Random().nextInt(10000));

        TouchTask entity = new TouchTask();
        entity.setTaskNo(taskNo);
        entity.setCustId(parseId(custId));
        entity.setSourceType("CLAIM");
        entity.setSourceBizId(sourceBizId);
        entity.setOrgId(orgId);
        entity.setAssigneeEmpId(assigneeEmpId);
        entity.setTaskType(TouchTaskType.FIRST_TOUCH.getCode());
        entity.setTaskStatus(TouchTaskStatus.PENDING.getCode());
        entity.setSlaStatus(SlaStatus.BLUE.getCode());
        LocalDateTime planTime = calculatePlanFinishTime(now);
        entity.setPlanFinishTime(planTime);
        entity.setWarningTime(planTime.minusDays(2));
        entity.setCreatedTime(now);
        entity.setCreatedBy(assigneeEmpId);
        entity.setUpdatedTime(now);
        entity.setUpdatedBy(assigneeEmpId);
        entity.setLockVersion(0);

        taskMapper.insert(entity);

        log.info("[TouchTaskService.createFromClaim] task created, taskId={}, taskNo={}", entity.getId(), taskNo);
        return entity;
    }

    /**
     * 由 ClaimService.reTouch 调用，对已认领客户创建一个 FOLLOW_UP（非首次）触达任务。
     * <p>
     * 与 {@link #createFromClaim(String, String, String)} 的差异：task_type=FOLLOW_UP；
     * reason 仅记录到日志（touch_task 表无 reason 列）；planFinishTime 仅为旧客户端兼容字段，
     * 服务端忽略该值并按后台 SLA 配置计算。
     * </p>
     *
     * @param custId         客户ID
     * @param orgId          所属机构代码
     * @param assigneeEmpId  执行人（维护人）员工工号
     * @param reason         重新触达原因（仅记日志）
     * @param planFinishTime 兼容旧客户端的字段；服务端忽略，统一按后台 SLA 配置计算
     * @return 创建成功的触达任务实体
     */
    @Transactional
    public TouchTask createFollowUpTask(String custId, String orgId, String assigneeEmpId,
                                         String reason, String planFinishTime) {
        return createFollowUpTask(custId, orgId, assigneeEmpId, reason, planFinishTime, null);
    }

    /**
     * 从正式认领关系创建后续触达任务，并保存认领关系来源。
     *
     * <p>无来源 ID 的旧调用仍保留 {@code MANUAL} 来源；正式认领入口传入来源 ID
     * 后统一使用 {@code CLAIM}，确保首次和再次触达在已认领池使用同一关联口径。</p>
     *
     * @param reason         重新触达原因（仅记日志）
     * @param planFinishTime 兼容旧客户端的字段；服务端忽略，统一按后台 SLA 配置计算
     * @param sourceBizId    正式认领关系 ID，可为空以兼容历史调用方
     */
    @Transactional
    public TouchTask createFollowUpTask(String custId, String orgId, String assigneeEmpId,
                                         String reason, String planFinishTime, Long sourceBizId) {
        log.info("[TouchTaskService.createFollowUpTask] custId={}, orgId={}, assigneeEmpId={}, reason={}",
                custId, orgId, assigneeEmpId, reason);

        assertTouchEligible(custId);
        LocalDateTime now = LocalDateTime.now();
        String taskNo = "TOUCH_" + System.currentTimeMillis() + "_"
                + String.format("%04d", new Random().nextInt(10000));
        TouchTask entity = new TouchTask();
        entity.setTaskNo(taskNo);
        entity.setCustId(parseId(custId));
        entity.setSourceType(sourceBizId == null ? "MANUAL" : "CLAIM");
        entity.setSourceBizId(sourceBizId);
        entity.setOrgId(orgId);
        entity.setAssigneeEmpId(assigneeEmpId);
        entity.setTaskType(TouchTaskType.FOLLOW_UP.getCode());
        entity.setTaskStatus(TouchTaskStatus.PENDING.getCode());
        entity.setSlaStatus(SlaStatus.BLUE.getCode());

        LocalDateTime planTime = calculatePlanFinishTime(now);
        entity.setPlanFinishTime(planTime);
        // 预警时间 = 计划完成前 2 天（与 createFromClaim 的 5d/7d 同 2 天偏移）
        entity.setWarningTime(planTime.minusDays(2));
        entity.setCreatedTime(now);
        entity.setCreatedBy(assigneeEmpId);
        entity.setUpdatedTime(now);
        entity.setUpdatedBy(assigneeEmpId);
        entity.setLockVersion(0);

        taskMapper.insert(entity);

        log.info("[TouchTaskService.createFollowUpTask] follow-up task created, taskId={}, taskNo={}", entity.getId(), taskNo);
        return entity;
    }

    /**
     * 计算服务端计划完成时间。客户端字段不参与计算，配置异常或暂时不可用时安全回退七天。
     */
    private LocalDateTime calculatePlanFinishTime(LocalDateTime base) {
        int slaDays = DEFAULT_SLA_DAYS;
        if (configApi != null) {
            try {
                String configured = configApi.getConfigValue(SLA_DAYS_CONFIG_KEY,
                        String.valueOf(DEFAULT_SLA_DAYS));
                if (configured != null && !configured.isBlank()) {
                    int parsed = Integer.parseInt(configured.trim());
                    if (parsed >= 1 && parsed <= MAX_SLA_DAYS) {
                        slaDays = parsed;
                    } else {
                        log.warn("[TouchTaskService] invalid {}={}, fallback to {} days",
                                SLA_DAYS_CONFIG_KEY, configured, DEFAULT_SLA_DAYS);
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("[TouchTaskService] failed to read {}, fallback to {} days",
                        SLA_DAYS_CONFIG_KEY, DEFAULT_SLA_DAYS, ex);
            }
        }
        return base.plusDays(slaDays);
    }

    /** 正式数字客户 ID 走 MARKETING_* 表资格校验，兼容旧 UUID 客户仍走旧入口。 */
    private void assertTouchEligible(String custId) {
        if (isNumericCustomerId(custId)) {
            marketingTouchEligibilityService.assertEligible(custId);
            return;
        }
        touchEligibilityService.assertEligible(custId);
    }

    private boolean isNumericCustomerId(String custId) {
        if (custId == null || custId.isBlank()) {
            return false;
        }
        try {
            return Long.parseLong(custId) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * 标记触达任务为已完成。
     * <p>
     * 仅允许至少保存一条有效工作日志的 IN_PROGRESS 任务完成。
     * 状态转换由 {@link TouchTaskStateMachineService} 校验，非法转移抛 CUST-40010。
     * 完成后 successTime=now，并发布 {@link TouchCompletedEvent}。
     * </p>
     *
     * @param taskId 任务ID
     * @throws BizException CUST-40405 任务不存在
     * @throws BizException CUST-40409 任务未关联工作日志
     * @throws BizException CUST-40010 非法状态转移（SUCCESS/CANCELLED 终态不允许再转移）
     */
    @Transactional
    public void markSuccess(String taskId, String operatorEmpId, boolean operatorIsAdmin) {
        log.info("[TouchTaskService.markSuccess] taskId={}, operatorEmpId={}, isAdmin={}",
                taskId, operatorEmpId, operatorIsAdmin);

        // 完成与取消必须在同一行锁上串行化，避免两个并发请求都通过终态校验并重复发布事件。
        TouchTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BizException(CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getCode(),
                    CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getMessage());
        }

        // 操作人校验（CUST-40302）：仅任务执行人本人或系统管理员可标成功
        assertAssigneeOrAdmin(task, operatorEmpId, operatorIsAdmin);

        // 通过状态机校验转移合法性：必须先由工作日志驱动到 IN_PROGRESS。
        TouchTaskStatus from = TouchTaskStatus.valueOf(task.getTaskStatus());
        stateMachine.assertTransition(from, TouchTaskStatus.SUCCESS);
        if (worklogMapper.countValidByTaskId(task.getId()) == 0) {
            throw new BizException(CustomerErrorCode.TOUCH_WORKLOG_NOT_FOUND.getCode(),
                    CustomerErrorCode.TOUCH_WORKLOG_NOT_FOUND.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        TouchTask updateEntity = new TouchTask();
        updateEntity.setId(task.getId());
        updateEntity.setTaskStatus(TouchTaskStatus.SUCCESS.getCode());
        updateEntity.setSuccessTime(now);
        updateEntity.setUpdatedTime(now);
        updateEntity.setUpdatedBy(operatorEmpId);

        taskMapper.updateById(updateEntity);

        // 发布触达完成事件，下游可扩展处理逻辑
        eventPublisher.publishEvent(new TouchCompletedEvent(
                taskId, task.getTaskNo(), String.valueOf(task.getCustId()),
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
     * P1C 2026-04-29：加 operator 校验，仅任务执行人本人或系统管理员可取消（CUST-40302）。
     * </p>
     *
     * @param taskId           任务ID
     * @param reason           取消原因（仅记日志，不持久化到 touch_task 表）
     * @param operatorEmpId    操作人员工工号（用于 assignee 校验）
     * @param operatorIsAdmin  操作人是否为系统管理员（true 则跳过 assignee 校验）
     * @throws BizException CUST-40405 任务不存在
     * @throws BizException CUST-40302 操作人非任务执行人且非管理员
     * @throws BizException CUST-40010 非法状态转移（SUCCESS/CANCELLED 终态不允许再转移）
     */
    @Transactional
    public void cancel(String taskId, String reason, String operatorEmpId, boolean operatorIsAdmin) {
        log.info("[TouchTaskService.cancel] taskId={}, reason={}, operatorEmpId={}, isAdmin={}",
                taskId, reason, operatorEmpId, operatorIsAdmin);

        // 与完成操作共用锁定读，保证 SUCCESS/CANCELLED 终态竞争时只有一个请求可以提交。
        TouchTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BizException(CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getCode(),
                    CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getMessage());
        }

        // 操作人校验（CUST-40302）：仅任务执行人本人或系统管理员可取消
        assertAssigneeOrAdmin(task, operatorEmpId, operatorIsAdmin);

        // 通过状态机校验转移合法性：PENDING/IN_PROGRESS → CANCELLED 合法，终态不可转
        TouchTaskStatus from = TouchTaskStatus.valueOf(task.getTaskStatus());
        stateMachine.assertTransition(from, TouchTaskStatus.CANCELLED);

        LocalDateTime now = LocalDateTime.now();
        TouchTask updateEntity = new TouchTask();
        updateEntity.setId(task.getId());
        updateEntity.setTaskStatus(TouchTaskStatus.CANCELLED.getCode());
        updateEntity.setCancelTime(now);
        updateEntity.setCancelReason(reason);
        updateEntity.setUpdatedTime(now);
        updateEntity.setUpdatedBy(operatorEmpId);

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
        updateEntity.setId(task.getId());
        updateEntity.setTaskStatus(TouchTaskStatus.IN_PROGRESS.getCode());
        updateEntity.setUpdatedTime(LocalDateTime.now());
        updateEntity.setUpdatedBy(task.getAssigneeEmpId());
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

    /** 同机构可查看，跨机构不可查看；系统管理员可跨机构查看。 */
    public TouchTask getVisibleById(String id, String operatorEmpId, String operatorOrgCode,
                                    boolean operatorIsAdmin) {
        TouchTask task = getById(id);
        if (!operatorIsAdmin && (operatorOrgCode == null || !operatorOrgCode.equals(task.getOrgId()))) {
            log.warn("[TouchTaskService.getVisibleById] cross-org denied, taskId={}, taskOrg={}, operatorOrg={}",
                    id, task.getOrgId(), operatorOrgCode);
            throw new BizException(CustomerErrorCode.HISTORY_ACCESS_FORBIDDEN.getCode(),
                    CustomerErrorCode.HISTORY_ACCESS_FORBIDDEN.getMessage());
        }
        // 读取展示字段使用一次聚合查询；先用原任务做机构校验，避免跨机构响应任何详情数据。
        TouchTask view = taskMapper.selectViewById(id);
        TouchTask result = view == null ? task : view;
        normalizeViewFields(result);
        applyViewerCapabilities(result, operatorEmpId, operatorOrgCode, operatorIsAdmin);
        return result;
    }

    /**
     * 分页查询触达任务列表。
     * <p>
     * keyword 模糊搜索任务编号或客户名称，status 和 assigneeEmpId 精确匹配。
     * offset = (pageNo - 1) * pageSize
     * </p>
     *
     * @param keyword       关键词（搜索任务编号或客户名称），可为 null
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

        normalizeViewFields(records);
        log.info("[TouchTaskService.listPage] total={}", total);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 按当前用户可见范围分页查询触达任务。
     * <p>
     * 服务端把机构边界和协同参与关系下推到同一条 SQL：仅返回同机构的任务，且当前员工
     * 必须是主执行人，或已由主执行人在有效日志中登记为 COLLABORATOR。列表展示字段由
     * SQL 聚合一次返回，不按任务逐条查询日志，避免 N+1。
     * </p>
     */
    public PageResult<TouchTask> listPage(String keyword, String status, String viewerEmpId,
                                          String viewerOrgId, int pageNo, int pageSize) {
        log.info("[TouchTaskService.listPage] keyword={}, status={}, viewerEmpId={}, viewerOrgId={}, pageNo={}, pageSize={}",
                keyword, status, viewerEmpId, viewerOrgId, pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;
        List<TouchTask> records = taskMapper.selectPageForViewer(
                keyword, status, viewerEmpId, viewerOrgId, offset, pageSize);
        long total = taskMapper.countPageForViewer(keyword, status, viewerEmpId, viewerOrgId);

        normalizeViewFields(records);
        if (records != null) {
            records.forEach(task -> applyViewerCapabilities(task, viewerEmpId, viewerOrgId, false));
        }
        log.info("[TouchTaskService.listPage] total={}", total);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 管理后台全局分页查询触达任务（不按机构过滤，需 ADMIN 权限）。
     * <p>
     * keyword 模糊搜索任务编号或客户名称，status、assigneeEmpId、orgId 精确匹配，均可为 null 表示不过滤。
     * offset = (pageNo - 1) * pageSize
     * </p>
     *
     * @param keyword       关键词（搜索任务编号或客户名称），可为 null
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

        normalizeViewFields(records);
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
        List<TouchTask> records = taskMapper.selectAdminPage(keyword, status, null, orgId, 0, maxRows);
        normalizeViewFields(records);
        return records;
    }

    /** 将 SQL 聚合的页面字段转换成前端稳定的 JSON 类型。 */
    private void normalizeViewFields(List<TouchTask> records) {
        if (records == null) {
            return;
        }
        records.forEach(this::normalizeViewFields);
    }

    private void normalizeViewFields(TouchTask task) {
        if (task == null) {
            return;
        }
        if (task.getCustomerName() == null) {
            task.setCustomerName(task.getCustName());
        }
        if (task.getCustName() == null) {
            task.setCustName(task.getCustomerName());
        }
        String participants = task.getParticipantEmpIdsText();
        if (participants != null && !participants.isBlank()) {
            task.setParticipantEmpIds(java.util.Arrays.stream(participants.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .distinct()
                    .toList());
        } else if (task.getParticipantEmpIds() == null) {
            task.setParticipantEmpIds(List.of());
        }
        if (task.getLogCount() == null) {
            task.setLogCount(0L);
        }
    }

    /** 页面能力与服务端写权限保持一致，避免协同人员看到完成/取消入口。 */
    private void applyViewerCapabilities(TouchTask task, String viewerEmpId, String viewerOrgId,
                                         boolean viewerIsAdmin) {
        boolean assignee = Objects.equals(viewerEmpId, task.getAssigneeEmpId());
        boolean sameOrg = Objects.equals(viewerOrgId, task.getOrgId());
        boolean collaborator = sameOrg && csvContains(task.getEligibleCollaboratorEmpIdsText(), viewerEmpId);
        task.setCanWriteLog(assignee || collaborator);
        task.setCanOperateTask(viewerIsAdmin || assignee);
    }

    private boolean csvContains(String csv, String value) {
        if (csv == null || csv.isBlank() || value == null || value.isBlank()) {
            return false;
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .anyMatch(value::equals);
    }

    /**
     * 批量分配触达任务给新执行人。
     * <p>
     * 仅允许对 PENDING 或 IN_PROGRESS 状态的任务进行重分配，
     * 终态（SUCCESS/CANCELLED）任务自动跳过（不报错），
     * 不存在的任务 ID 也自动跳过。
     * 输入中的重复 ID 只处理一次，并按稳定顺序获取行锁，避免并发批次反序锁定。
     * 返回实际成功更新的任务数量。
     * </p>
     *
     * @param taskIds       待分配的任务 ID 列表
     * @param newAssigneeEmpId 新执行人员工工号
     * @param operatorEmpId    当前操作人员工工号，用于更新审计字段
     * @return 实际更新的任务数量
     */
    @Transactional
    public int batchAssign(List<String> taskIds, String newAssigneeEmpId, String operatorEmpId) {
        log.info("[TouchTaskService.batchAssign] taskCount={}, newAssigneeEmpId={}, operatorEmpId={}",
                taskIds.size(), newAssigneeEmpId, operatorEmpId);

        int updated = 0;
        List<String> orderedTaskIds = taskIds.stream()
                .distinct()
                .sorted(Comparator.nullsFirst(Comparator.naturalOrder()))
                .toList();
        for (String id : orderedTaskIds) {
            // 锁定在途任务，串行化与完成/取消等状态变更，避免并发改派覆盖最新状态。
            TouchTask task = taskMapper.selectByIdForUpdate(id);
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
            task.setUpdatedBy(operatorEmpId);
            task.setUpdatedTime(LocalDateTime.now());
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
                // 仅在状态有变化时执行更新；SLA 预警由 sla_status 派生，不再冗余落库。
                TouchTask updateEntity = new TouchTask();
                updateEntity.setId(task.getId());
                updateEntity.setSlaStatus(newSlaStatus);
                updateEntity.setUpdatedBy("SYSTEM");
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

    /**
     * 校验操作人是任务执行人本人或系统管理员，否则抛 CUST-40302（P1C 2026-04-29 落地）。
     * 系统管理员（isSystemAdmin=true）可绕过任意机构/任意执行人限制。
     *
     * @param task            触达任务实体
     * @param operatorEmpId   操作人员工工号
     * @param operatorIsAdmin 操作人是否为系统管理员
     */
    private void assertAssigneeOrAdmin(TouchTask task, String operatorEmpId, boolean operatorIsAdmin) {
        if (operatorIsAdmin) {
            return;
        }
        if (task.getAssigneeEmpId() == null || !task.getAssigneeEmpId().equals(operatorEmpId)) {
            log.warn("[TouchTaskService.assertAssigneeOrAdmin] taskId={}, assignee={}, operator={}, 拒绝 CUST-40302",
                    task.getId(), task.getAssigneeEmpId(), operatorEmpId);
            throw new BizException(CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN.getCode(),
                    CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN.getMessage());
        }
    }

    private Long parseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (Exception e) {
            throw new BizException(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode(),
                    CustomerErrorCode.CUSTOMER_NOT_FOUND.getMessage());
        }
    }
}
