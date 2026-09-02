package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentPageQueryDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskBusinessType;
import com.bank.branch.platform.redengine.api.dto.ReTaskCreateReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskCreateRespDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskCycleType;
import com.bank.branch.platform.redengine.api.dto.ReTaskDetailDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskFileTypeDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskInstanceStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskListItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskNature;
import com.bank.branch.platform.redengine.api.dto.ReTaskPageQueryDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskScheduleWindowDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskTargetDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskTargetType;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskFileType;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskStatusHistory;
import com.bank.branch.platform.redengine.entity.ReTaskTarget;
import com.bank.branch.platform.redengine.entity.ReTaskTodo;
import com.bank.branch.platform.redengine.entity.ReUserPartyMap;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskFileTypeMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskStatusHistoryMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTargetMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTodoMapper;
import com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务管理服务实现。
 *
 * <p>任务新增和发布在同一事务完成，任务定义一旦进入 PUBLISHED 就没有编辑入口；
 * 定时实例由本服务按北京时间生成，Quartz 只负责触发当前窗口补偿。组织管理员使用
 * 平台真实角色 {@code SYS_ADMIN}，组织审核员使用 {@code R_RE_ORGREV}，两者均需
 * 在服务层再次通过实体和目标范围校验。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReTaskManagementServiceImpl implements ReTaskManagementService {

    static final String ORG_REVIEWER_ROLE = "R_RE_ORGREV";
    static final String SYSTEM_ADMIN_ROLE = "SYS_ADMIN";
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    private final ReTaskMapper taskMapper;
    private final ReTaskFileTypeMapper fileTypeMapper;
    private final ReTaskTargetMapper targetMapper;
    private final ReTaskInstanceMapper instanceMapper;
    private final ReTaskBranchAssignmentMapper assignmentMapper;
    private final ReTaskSubmissionMapper submissionMapper;
    private final ReTaskStatusHistoryMapper historyMapper;
    private final RePartyOrgMapper partyOrgMapper;
    private final ReUserPartyMapMapper userPartyMapMapper;
    private final CurrentUserApi currentUserApi;
    private final UserApi userApi;
    private final ReTaskScheduleService scheduleService;
    private final ReTaskAssignmentService assignmentService;
    private final ReTaskTodoMapper todoMapper;

    /**
     * 分页查询任务定义及支部分配进度。
     *
     * @param query      查询条件
     * @param operatorId 当前操作人平台用户 ID
     * @return 任务列表页
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ReTaskListItemDTO> page(ReTaskPageQueryDTO query, String operatorId) {
        requireManagementAccess(operatorId);
        ReTaskPageQueryDTO normalized = query == null ? new ReTaskPageQueryDTO() : query;
        int pageNo = normalizePageNo(normalized.getPageNo());
        int pageSize = normalizePageSize(normalized.getPageSize());

        ReTaskStatus status = normalized.getStatus() == null
                ? ReTaskStatus.PUBLISHED : normalized.getStatus();
        LambdaQueryWrapper<ReTask> wrapper = new LambdaQueryWrapper<ReTask>()
                .like(hasText(normalized.getTitle()), ReTask::getTitle, normalizeText(normalized.getTitle()))
                .eq(normalized.getTaskNature() != null, ReTask::getNature,
                        normalized.getTaskNature() == null ? null : normalized.getTaskNature().getValue())
                .eq(normalized.getBusinessType() != null, ReTask::getTypeCode,
                        normalized.getBusinessType() == null ? null : normalized.getBusinessType().name())
                .eq(normalized.getCycleType() != null && normalized.getCycleType() != ReTaskCycleType.NONE,
                        ReTask::getCycle, normalized.getCycleType() == null ? null : normalized.getCycleType().name())
                .eq(ReTask::getStatus, status)
                .ge(normalized.getPublishStartDate() != null, ReTask::getPublishedAt,
                        atStartOfDay(normalized.getPublishStartDate()))
                .le(normalized.getPublishEndDate() != null, ReTask::getPublishedAt,
                        atEndOfDay(normalized.getPublishEndDate()))
                .orderByDesc(ReTask::getPublishedAt)
                .orderByDesc(ReTask::getId);
        IPage<ReTask> taskPage = taskMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ReTask> tasks = taskPage == null || taskPage.getRecords() == null
                ? List.of() : taskPage.getRecords();
        if (tasks.isEmpty()) {
            return PageResult.of(pageNo, pageSize, taskPage == null ? 0L : taskPage.getTotal(), List.of());
        }

        Map<Long, List<ReTaskBranchAssignment>> assignmentByTask = assignmentsByTask(tasks);
        List<ReTaskListItemDTO> records = tasks.stream()
                .map(task -> toListItem(task, assignmentByTask.getOrDefault(task.getId(), List.of())))
                .toList();
        return PageResult.of(pageNo, pageSize, taskPage.getTotal(), records);
    }

    /**
     * 查询任务详情及其对象/文件配置快照。
     *
     * @param taskId     任务定义 ID
     * @param operatorId 当前操作人平台用户 ID
     * @return 任务详情；任务不存在时返回 null
     */
    @Override
    @Transactional(readOnly = true)
    public ReTaskDetailDTO getDetail(Long taskId, String operatorId) {
        requireManagementAccess(operatorId);
        if (taskId == null) {
            return null;
        }
        ReTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return null;
        }
        List<ReTaskBranchAssignment> assignments = assignmentsByTask(List.of(task))
                .getOrDefault(taskId, List.of());
        ReTaskListItemDTO base = toListItem(task, assignments);
        ReTaskDetailDTO detail = new ReTaskDetailDTO();
        copyListFields(base, detail);

        List<ReTaskTarget> targets = targetMapper.selectList(
                new LambdaQueryWrapper<ReTaskTarget>().eq(ReTaskTarget::getTaskId, taskId)
                        .orderByAsc(ReTaskTarget::getId));
        detail.setTargets(toTargetDTOs(task, targets));
        List<ReTaskFileType> fileTypes = fileTypeMapper.selectList(
                new LambdaQueryWrapper<ReTaskFileType>().eq(ReTaskFileType::getTaskId, taskId)
                        .orderByAsc(ReTaskFileType::getSortNo).orderByAsc(ReTaskFileType::getId));
        detail.setFileTypes(fileTypes == null ? List.of() : fileTypes.stream().map(this::toFileTypeDTO).toList());
        List<ReTaskInstance> instances = instanceMapper.selectList(
                new LambdaQueryWrapper<ReTaskInstance>().eq(ReTaskInstance::getTaskId, taskId)
                        .orderByDesc(ReTaskInstance::getWindowStartAt).orderByDesc(ReTaskInstance::getId));
        detail.setCurrentInstanceId(instances == null || instances.isEmpty() ? null : instances.get(0).getId());
        return detail;
    }

    /**
     * 校验并立即发布任务，同时生成首个实例和分配。
     *
     * @param request    任务新增请求
     * @param operatorId 当前操作人平台用户 ID
     * @return 发布结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReTaskCreateRespDTO createAndPublish(ReTaskCreateReqDTO request, String operatorId) {
        requireManagementAccess(operatorId);
        validateCreateRequest(request, operatorId);
        LocalDateTime now = now();
        ReTaskTargetType audienceType = canonicalTargetType(request.getTargets().get(0).getTargetType());

        ReTask task = new ReTask();
        task.setTaskNo(generateTaskNo());
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription().trim());
        task.setNature(request.getTaskNature().getValue());
        task.setTypeCode(request.getBusinessType().name());
        task.setAudienceType(audienceType.getAudienceType());
        task.setCycle(request.getTaskNature() == ReTaskNature.SCHEDULED
                ? request.getCycleType().name() : null);
        task.setDurationDays(request.getTaskNature() == ReTaskNature.SCHEDULED
                ? request.getDurationDays() : null);
        task.setStartAt(request.getTaskNature() == ReTaskNature.TEMPORARY
                ? request.getTemporaryStartTime() : null);
        task.setEndAt(request.getTaskNature() == ReTaskNature.TEMPORARY
                ? request.getTemporaryEndTime() : null);
        task.setEffectiveFrom(request.getEffectiveFrom());
        task.setEffectiveTo(request.getEffectiveTo());
        task.setRequiresFile(Boolean.TRUE.equals(request.getRequiresFile()) ? 1 : 0);
        task.setStatus(ReTaskStatus.PUBLISHED);
        task.setPublishedAt(now);
        task.setPublishedBy(operatorId);
        task.setCreatedBy(operatorId);
        task.setUpdatedBy(operatorId);
        task.setVersionNo(0);
        task.setDeleted(0);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        taskMapper.insert(task);
        if (task.getId() == null) {
            throw new BizException("RE-50010", "任务保存失败");
        }

        saveFileTypes(task, request, operatorId, now);
        saveTargets(task, request, operatorId, now);
        ReTaskInstance instance = createInstance(task, request, now);
        List<ReTaskBranchAssignment> assignments = List.of();
        int todoCount = 0;
        if (instance != null) {
            instanceMapper.insert(instance);
            if (instance.getId() == null) {
                throw new BizException("RE-50011", "任务实例保存失败");
            }
            assignments = assignmentService.ensureAssignments(task, instance);
            todoCount = countReporterTodos(assignments);
        }
        saveStatusHistory(task, instance, operatorId, now);

        ReTaskCreateRespDTO response = new ReTaskCreateRespDTO();
        response.setTaskId(task.getId());
        response.setTaskNo(task.getTaskNo());
        response.setStatus(task.getStatus());
        response.setInstanceId(instance == null ? null : instance.getId());
        response.setAssignmentCount(assignments == null ? 0 : assignments.size());
        response.setTodoCount(todoCount);
        return response;
    }

    /**
     * 查询任务详情中的支部填报汇总。
     *
     * @param taskId     任务定义 ID
     * @param query      分页和筛选条件
     * @param operatorId 当前操作人平台用户 ID
     * @return 支部填报汇总页
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ReTaskAssignmentDTO> pageAssignments(Long taskId,
                                                            ReTaskAssignmentPageQueryDTO query,
                                                            String operatorId) {
        requireManagementAccess(operatorId);
        if (taskId == null || taskMapper.selectById(taskId) == null) {
            throw new BizException("RE-40010", "任务不存在");
        }
        return assignmentService.pageAssignments(taskId, query);
    }

    /**
     * 定时补偿当前有效窗口。
     * <p>不接受外部 jobKey/class 参数；Quartz Job 只能调用这个固定入口。已错过的窗口
     * 不回填，实例唯一键和 assignment 唯一键共同保证多节点幂等。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcileCurrentWindows() {
        List<ReTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<ReTask>()
                .eq(ReTask::getStatus, ReTaskStatus.PUBLISHED)
                .eq(ReTask::getNature, ReTaskNature.SCHEDULED.getValue())
                .orderByAsc(ReTask::getId));
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now(BEIJING_ZONE);
        for (ReTask task : tasks) {
            try {
                if (!isWithinEffectiveRange(task, today) || task.getCycle() == null || task.getDurationDays() == null) {
                    continue;
                }
                ReTaskScheduleWindowDTO window = scheduleService.calculateWindow(
                        ReTaskCycleType.valueOf(task.getCycle()), today, task.getDurationDays());
                // 只补当前有效窗口；调度漏跑不回写已经结束的周期。
                if (!scheduleService.isCurrentWindow(window, today)) {
                    continue;
                }
                ReTaskInstance instance = findInstance(task.getId(), window.getPeriodKey());
                if (instance == null) {
                    instance = newInstance(task, window, now());
                    try {
                        instanceMapper.insert(instance);
                    } catch (DuplicateKeyException duplicate) {
                        instance = findInstance(task.getId(), window.getPeriodKey());
                    }
                }
                if (instance != null && instance.getId() != null) {
                    assignmentService.ensureAssignments(task, instance);
                }
            } catch (Exception ex) {
                // 单任务失败不能阻断同一轮其它任务；下轮 Quartz 会再次补偿。
                log.error("任务当前窗口补偿失败 taskId={}", task.getId(), ex);
            }
        }
    }

    /** 管理端入口统一采用实体服务层角色二次校验，未知角色/空上下文一律拒绝。 */
    private void requireManagementAccess(String operatorId) {
        if (!hasText(operatorId)) {
            throw new BizException("RE-40301", "当前用户上下文缺失");
        }
        String currentEmpId = currentUserApi.getCurrentEmpId();
        if (!hasText(currentEmpId) || !operatorId.trim().equals(currentEmpId.trim())) {
            throw new BizException("RE-40301", "当前用户与操作人不匹配");
        }
        Set<String> roleCodes = currentUserApi.getCurrentRoleCodes();
        boolean systemAdmin = currentUserApi.isSystemAdmin();
        boolean permitted = systemAdmin || (roleCodes != null && roleCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .anyMatch(role -> SYSTEM_ADMIN_ROLE.equalsIgnoreCase(role)
                        || ORG_REVIEWER_ROLE.equalsIgnoreCase(role)));
        if (!permitted) {
            throw new BizException("RE-40302", "无权新增或发布任务");
        }
    }

    /** 新增请求的分支约束在 Service 再次执行，不依赖 Controller 的 Bean Validation。 */
    private void validateCreateRequest(ReTaskCreateReqDTO request, String operatorId) {
        if (request == null || !hasText(request.getTitle()) || !hasText(request.getDescription())
                || request.getTaskNature() == null || request.getBusinessType() == null
                || request.getRequiresFile() == null || request.getTargets() == null
                || request.getTargets().isEmpty()) {
            throw new BizException("RE-40020", "任务基础信息不完整");
        }
        validateNature(request);
        validateFiles(request);
        List<ReTaskTargetDTO> targets = request.getTargets();
        ReTaskTargetDTO firstTarget = targets.get(0);
        if (firstTarget == null || firstTarget.getTargetType() == null) {
            throw new BizException("RE-40022", "任务对象类型不能为空");
        }
        ReTaskTargetType type = canonicalTargetType(firstTarget.getTargetType());
        if (targets.stream().anyMatch(item -> item == null || item.getTargetType() == null
                || canonicalTargetType(item.getTargetType()) != type)) {
            throw new BizException("RE-40022", "任务对象类型不能混用");
        }
        if (type == ReTaskTargetType.ALL_BRANCH && targets.size() != 1) {
            throw new BizException("RE-40022", "全部党支部不需要重复配置对象");
        }
        Set<String> targetKeys = new HashSet<>();
        for (ReTaskTargetDTO target : targets) {
            if (target == null || target.getTargetType() == null) {
                throw new BizException("RE-40022", "任务对象类型不能为空");
            }
            if (type == ReTaskTargetType.ALL_BRANCH) {
                if (target.getPartyOrgId() != null || hasText(target.getEmployeeId())) {
                    throw new BizException("RE-40022", "全部党支部不能携带指定对象");
                }
                continue;
            }
            if (type == ReTaskTargetType.SPECIFIED_BRANCH) {
                if (target.getPartyOrgId() == null || hasText(target.getEmployeeId())) {
                    throw new BizException("RE-40023", "指定党支部参数不完整");
                }
                RePartyOrg org = partyOrgMapper.selectById(target.getPartyOrgId());
                if (!isBranch(org)) {
                    throw new BizException("RE-40023", "指定对象不是党支部");
                }
                if (!targetKeys.add("BRANCH:" + target.getPartyOrgId())) {
                    throw new BizException("RE-40023", "党支部对象不能重复");
                }
            } else {
                if (!hasText(target.getEmployeeId()) || target.getPartyOrgId() != null) {
                    throw new BizException("RE-40024", "指定员工参数不完整");
                }
                String employeeId = target.getEmployeeId().trim();
                UserDTO user = userApi.getUserByEmpId(employeeId);
                if (user == null) {
                    throw new BizException("RE-40024", "指定员工不存在");
                }
                ReUserPartyMap mapping = userPartyMapMapper.selectOne(
                        new LambdaQueryWrapper<ReUserPartyMap>().eq(ReUserPartyMap::getUserId, employeeId));
                if (mapping == null || resolveBranchId(mapping.getPartyOrgId()) == null) {
                    throw new BizException("RE-40024", "指定员工未绑定党支部");
                }
                if (!targetKeys.add("EMPLOYEE:" + employeeId)) {
                    throw new BizException("RE-40024", "员工对象不能重复");
                }
            }
        }
    }

    private void validateNature(ReTaskCreateReqDTO request) {
        if (request.getTaskNature() == ReTaskNature.SCHEDULED) {
            if (request.getCycleType() == null || request.getCycleType() == ReTaskCycleType.NONE
                    || request.getDurationDays() == null || request.getDurationDays() < 1
                    || request.getTemporaryStartTime() != null || request.getTemporaryEndTime() != null) {
                throw new BizException("RE-40021", "定时任务周期参数不完整");
            }
            if (request.getEffectiveFrom() != null && request.getEffectiveTo() != null
                    && request.getEffectiveFrom().isAfter(request.getEffectiveTo())) {
                throw new BizException("RE-40021", "定时任务生效日期范围无效");
            }
            if (request.getBusinessType() == ReTaskBusinessType.FOUR_DIMENSION
                    && request.getCycleType() == ReTaskCycleType.NONE) {
                throw new BizException("RE-40021", "四大维度任务必须为定时任务");
            }
            validateScheduledDuration(request);
        } else {
            if (request.getTemporaryStartTime() == null || request.getTemporaryEndTime() == null
                    || request.getTemporaryEndTime().isBefore(request.getTemporaryStartTime())
                    || request.getCycleType() != null || request.getDurationDays() != null
                    || request.getBusinessType() == ReTaskBusinessType.FOUR_DIMENSION) {
                throw new BizException("RE-40021", "临时任务时间参数无效");
            }
        }
    }

    /**
     * 在发布前验证周期窗口长度；生效日期未到时也必须校验，不能依赖首个实例生成路径。
     *
     * <p>以任务生效日作为周期定位日，未配置生效日时使用北京时间当天。实际周期长度由
     * {@link ReTaskScheduleService} 统一计算，因而月末/月初和季度边界遵循同一套自然日口径。</p>
     */
    private void validateScheduledDuration(ReTaskCreateReqDTO request) {
        LocalDate periodDate = request.getEffectiveFrom() == null
                ? LocalDate.now(BEIJING_ZONE) : request.getEffectiveFrom();
        try {
            scheduleService.calculateWindow(request.getCycleType(), periodDate, request.getDurationDays());
        } catch (IllegalArgumentException ex) {
            throw new BizException("RE-40021", ex.getMessage());
        }
    }

    private void validateFiles(ReTaskCreateReqDTO request) {
        List<String> codes = request.getFileTypeCodes() == null ? List.of() : request.getFileTypeCodes();
        if (Boolean.TRUE.equals(request.getRequiresFile()) && codes.stream().noneMatch(this::hasText)) {
            throw new BizException("RE-40025", "要求上传文件时至少选择一种文件类型");
        }
        if (!Boolean.TRUE.equals(request.getRequiresFile()) && codes.stream().anyMatch(this::hasText)) {
            throw new BizException("RE-40025", "未要求上传文件时不能配置文件类型");
        }
        long distinctCount = codes.stream().filter(this::hasText).map(String::trim).distinct().count();
        if (distinctCount != codes.stream().filter(this::hasText).count()) {
            throw new BizException("RE-40025", "允许文件类型不能重复");
        }
    }

    private void saveFileTypes(ReTask task, ReTaskCreateReqDTO request, String operatorId, LocalDateTime now) {
        if (!Boolean.TRUE.equals(request.getRequiresFile()) || request.getFileTypeCodes() == null) {
            return;
        }
        int sortNo = 1;
        for (String code : request.getFileTypeCodes()) {
            if (!hasText(code)) {
                continue;
            }
            String normalizedCode = code.trim();
            ReTaskFileType type = new ReTaskFileType();
            type.setTaskId(task.getId());
            type.setFileTypeCode(normalizedCode);
            // 文件类型名称和扩展名由任务配置快照保留；字典详情由后续文件策略服务补齐。
            type.setFileTypeName(normalizedCode);
            type.setFileExtension(normalizedCode.startsWith(".") ? normalizedCode.substring(1) : normalizedCode);
            type.setSortNo(sortNo++);
            type.setEnabled(1);
            type.setCreatedBy(operatorId);
            type.setCreateTime(now);
            type.setUpdatedBy(operatorId);
            type.setUpdateTime(now);
            fileTypeMapper.insert(type);
        }
    }

    private void saveTargets(ReTask task, ReTaskCreateReqDTO request, String operatorId, LocalDateTime now) {
        ReTaskTargetType type = canonicalTargetType(request.getTargets().get(0).getTargetType());
        if (type == ReTaskTargetType.ALL_BRANCH) {
            return;
        }
        for (ReTaskTargetDTO source : request.getTargets()) {
            ReTaskTarget target = new ReTaskTarget();
            target.setTaskId(task.getId());
            target.setTargetType(type.getStorageType());
            target.setBranchId(type == ReTaskTargetType.SPECIFIED_BRANCH ? source.getPartyOrgId() : null);
            target.setEmployeeId(type == ReTaskTargetType.SPECIFIED_EMPLOYEE
                    ? source.getEmployeeId().trim() : null);
            target.setTargetKey(type == ReTaskTargetType.SPECIFIED_BRANCH
                    ? "BRANCH:" + source.getPartyOrgId() : "EMPLOYEE:" + source.getEmployeeId().trim());
            target.setTargetLabel(type == ReTaskTargetType.SPECIFIED_BRANCH
                    ? targetLabel(source.getPartyOrgId()) : employeeLabel(source.getEmployeeId().trim()));
            target.setCreatedBy(operatorId);
            target.setCreateTime(now);
            targetMapper.insert(target);
        }
    }

    private ReTaskInstance createInstance(ReTask task, ReTaskCreateReqDTO request, LocalDateTime now) {
        if (request.getTaskNature() == ReTaskNature.TEMPORARY) {
            ReTaskInstance instance = new ReTaskInstance();
            instance.setTaskId(task.getId());
            instance.setPeriodKey("TEMPORARY-" + task.getId());
            instance.setWindowStartAt(request.getTemporaryStartTime());
            instance.setWindowEndAt(request.getTemporaryEndTime());
            instance.setStatus(resolveInstanceStatus(instance.getWindowStartAt(), instance.getWindowEndAt(), now));
            instance.setGeneratedAt(now);
            instance.setVersionNo(0);
            instance.setCreateTime(now);
            instance.setUpdateTime(now);
            return instance;
        }
        LocalDate today = LocalDate.now(BEIJING_ZONE);
        if (!isWithinEffectiveRange(task, today)) {
            return null;
        }
        ReTaskScheduleWindowDTO window = scheduleService.calculateWindow(
                request.getCycleType(), today, request.getDurationDays());
        // 发布时只创建当前有效窗口；尚未进入窗口的定时任务由调度补偿首次生成，
        // 避免给员工提前展示未来 assignment/todo。
        if (!scheduleService.isCurrentWindow(window, today)) {
            return null;
        }
        return newInstance(task, window, now);
    }

    private ReTaskInstance newInstance(ReTask task, ReTaskScheduleWindowDTO window, LocalDateTime now) {
        LocalDateTime start = window.getStartDate().atStartOfDay();
        LocalDateTime end = window.getEndDate().atTime(LocalTime.MAX.withNano(0));
        ReTaskInstance instance = new ReTaskInstance();
        instance.setTaskId(task.getId());
        instance.setPeriodKey(window.getPeriodKey());
        instance.setWindowStartAt(start);
        instance.setWindowEndAt(end);
        instance.setStatus(resolveInstanceStatus(start, end, now));
        instance.setGeneratedAt(now);
        instance.setVersionNo(0);
        instance.setCreateTime(now);
        instance.setUpdateTime(now);
        return instance;
    }

    private ReTaskInstanceStatus resolveInstanceStatus(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (now.isBefore(start)) {
            return ReTaskInstanceStatus.PENDING;
        }
        return now.isAfter(end) ? ReTaskInstanceStatus.CLOSED : ReTaskInstanceStatus.OPEN;
    }

    private void saveStatusHistory(ReTask task, ReTaskInstance instance, String operatorId, LocalDateTime now) {
        ReTaskStatusHistory history = new ReTaskStatusHistory();
        history.setTaskId(task.getId());
        history.setTaskInstanceId(instance == null ? null : instance.getId());
        history.setActionCode("PUBLISH");
        history.setFromStatus(ReTaskStatus.DRAFT.name());
        history.setToStatus(ReTaskStatus.PUBLISHED.name());
        history.setOperatorId(operatorId);
        history.setOccurredAt(now);
        history.setCreateTime(now);
        historyMapper.insert(history);
    }

    /** 统计本次发布实际创建的报送员待办，避免把组织审核员待办混入返回值。 */
    private int countReporterTodos(List<ReTaskBranchAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return 0;
        }
        List<Long> assignmentIds = assignments.stream().map(ReTaskBranchAssignment::getId)
                .filter(Objects::nonNull).toList();
        if (assignmentIds.isEmpty()) {
            return 0;
        }
        Long count = todoMapper.selectCount(new LambdaQueryWrapper<ReTaskTodo>()
                .in(ReTaskTodo::getAssignmentId, assignmentIds)
                .eq(ReTaskTodo::getRoleCode, "REPORTER"));
        return count == null ? 0 : Math.toIntExact(count);
    }

    private Map<Long, List<ReTaskBranchAssignment>> assignmentsByTask(Collection<ReTask> tasks) {
        List<Long> taskIds = tasks.stream().map(ReTask::getId).filter(Objects::nonNull).toList();
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        List<ReTaskInstance> instances = instanceMapper.selectList(
                new LambdaQueryWrapper<ReTaskInstance>().in(ReTaskInstance::getTaskId, taskIds));
        if (instances == null || instances.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> taskIdByInstance = instances.stream().filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ReTaskInstance::getId, ReTaskInstance::getTaskId,
                        (first, ignored) -> first));
        List<Long> instanceIds = new ArrayList<>(taskIdByInstance.keySet());
        List<ReTaskBranchAssignment> assignments = assignmentMapper.selectList(
                new LambdaQueryWrapper<ReTaskBranchAssignment>().in(
                        ReTaskBranchAssignment::getTaskInstanceId, instanceIds));
        if (assignments == null || assignments.isEmpty()) {
            return Map.of();
        }
        return assignments.stream()
                .filter(item -> taskIdByInstance.containsKey(item.getTaskInstanceId()))
                .collect(Collectors.groupingBy(item -> taskIdByInstance.get(item.getTaskInstanceId()),
                        LinkedHashMap::new, Collectors.toList()));
    }

    private ReTaskListItemDTO toListItem(ReTask task, List<ReTaskBranchAssignment> assignments) {
        ReTaskListItemDTO dto = new ReTaskListItemDTO();
        dto.setTaskId(task.getId());
        dto.setTaskNo(task.getTaskNo());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setTaskNature(parseNature(task.getNature()));
        dto.setBusinessType(parseBusinessType(task.getTypeCode()));
        dto.setCycleType(parseCycle(task.getCycle()));
        dto.setDurationDays(task.getDurationDays());
        dto.setStartAt(task.getStartAt());
        dto.setEndAt(task.getEndAt());
        dto.setEffectiveFrom(task.getEffectiveFrom());
        dto.setEffectiveTo(task.getEffectiveTo());
        dto.setRequiresFile(Integer.valueOf(1).equals(task.getRequiresFile()));
        dto.setAudienceType(parseTargetType(task.getAudienceType()));
        dto.setStatus(task.getStatus());
        dto.setPublishedAt(task.getPublishedAt());
        List<ReTaskFileType> fileTypes = fileTypeMapper.selectList(new LambdaQueryWrapper<ReTaskFileType>()
                .eq(ReTaskFileType::getTaskId, task.getId()).orderByAsc(ReTaskFileType::getSortNo));
        dto.setFileTypeCodes(fileTypes == null ? List.of() : fileTypes.stream()
                .map(ReTaskFileType::getFileTypeCode).filter(Objects::nonNull).toList());
        dto.setItemCodes(List.of());
        long targetCount = assignments.size();
        long submittedCount = assignments.stream().filter(item -> !"UNREPORTED".equals(item.getStatus())).count();
        long approvedCount = assignments.stream().filter(item -> "APPROVED".equals(item.getStatus())).count();
        long rejectedCount = assignments.stream().filter(item -> "REJECTED_BY_BRANCH".equals(item.getStatus())
                || "REJECTED_BY_ORG".equals(item.getStatus())).count();
        long unreportedCount = assignments.stream().filter(item -> "UNREPORTED".equals(item.getStatus())).count();
        dto.setTargetCount(targetCount);
        dto.setSubmittedCount(submittedCount);
        dto.setApprovedCount(approvedCount);
        dto.setRejectedCount(rejectedCount);
        dto.setUnreportedCount(unreportedCount);
        return dto;
    }

    private void copyListFields(ReTaskListItemDTO source, ReTaskDetailDTO target) {
        target.setTaskId(source.getTaskId());
        target.setTaskNo(source.getTaskNo());
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setTaskNature(source.getTaskNature());
        target.setBusinessType(source.getBusinessType());
        target.setCycleType(source.getCycleType());
        target.setDurationDays(source.getDurationDays());
        target.setStartAt(source.getStartAt());
        target.setEndAt(source.getEndAt());
        target.setEffectiveFrom(source.getEffectiveFrom());
        target.setEffectiveTo(source.getEffectiveTo());
        target.setRequiresFile(source.getRequiresFile());
        target.setFileTypeCodes(source.getFileTypeCodes());
        target.setItemCodes(source.getItemCodes());
        target.setAudienceType(source.getAudienceType());
        target.setStatus(source.getStatus());
        target.setPublishedAt(source.getPublishedAt());
        target.setTargetCount(source.getTargetCount());
        target.setSubmittedCount(source.getSubmittedCount());
        target.setApprovedCount(source.getApprovedCount());
        target.setRejectedCount(source.getRejectedCount());
        target.setUnreportedCount(source.getUnreportedCount());
    }

    private List<ReTaskTargetDTO> toTargetDTOs(ReTask task, List<ReTaskTarget> targets) {
        if ("ALL_BRANCHES".equalsIgnoreCase(task.getAudienceType())
                || "ALL_BRANCH".equalsIgnoreCase(task.getAudienceType())) {
            ReTaskTargetDTO all = new ReTaskTargetDTO();
            all.setTargetType(ReTaskTargetType.ALL_BRANCH);
            return List.of(all);
        }
        if (targets == null) {
            return List.of();
        }
        return targets.stream().map(target -> {
            ReTaskTargetDTO dto = new ReTaskTargetDTO();
            if ("BRANCH".equalsIgnoreCase(target.getTargetType())) {
                dto.setTargetType(ReTaskTargetType.SPECIFIED_BRANCH);
                dto.setPartyOrgId(target.getBranchId());
            } else {
                dto.setTargetType(ReTaskTargetType.SPECIFIED_EMPLOYEE);
                dto.setEmployeeId(target.getEmployeeId());
            }
            return dto;
        }).toList();
    }

    private ReTaskFileTypeDTO toFileTypeDTO(ReTaskFileType source) {
        ReTaskFileTypeDTO dto = new ReTaskFileTypeDTO();
        dto.setId(source.getId());
        dto.setFileTypeCode(source.getFileTypeCode());
        dto.setFileTypeName(source.getFileTypeName());
        dto.setFileExtension(source.getFileExtension());
        dto.setMimeType(source.getMimeType());
        dto.setMaxSizeBytes(source.getMaxSizeBytes());
        dto.setSortNo(source.getSortNo());
        dto.setEnabled(Integer.valueOf(1).equals(source.getEnabled()));
        return dto;
    }

    private ReTaskInstance findInstance(Long taskId, String periodKey) {
        return instanceMapper.selectOne(new LambdaQueryWrapper<ReTaskInstance>()
                .eq(ReTaskInstance::getTaskId, taskId)
                .eq(ReTaskInstance::getPeriodKey, periodKey));
    }

    private boolean isWithinEffectiveRange(ReTask task, LocalDate today) {
        return (task.getEffectiveFrom() == null || !today.isBefore(task.getEffectiveFrom()))
                && (task.getEffectiveTo() == null || !today.isAfter(task.getEffectiveTo()));
    }

    private String targetLabel(Long partyOrgId) {
        RePartyOrg org = partyOrgMapper.selectById(partyOrgId);
        return org == null ? String.valueOf(partyOrgId) : org.getOrgName();
    }

    private String employeeLabel(String employeeId) {
        UserDTO user = userApi.getUserByEmpId(employeeId);
        if (user == null) {
            return employeeId;
        }
        return hasText(user.getDisplayName()) ? user.getDisplayName() : employeeId;
    }

    private Long resolveBranchId(Long partyOrgId) {
        Set<Long> visited = new HashSet<>();
        Long current = partyOrgId;
        while (current != null && visited.add(current)) {
            RePartyOrg org = partyOrgMapper.selectById(current);
            if (org == null) {
                return null;
            }
            if (isBranch(org)) {
                return org.getId();
            }
            current = org.getParentId();
        }
        return null;
    }

    private boolean isBranch(RePartyOrg org) {
        return org != null && org.getId() != null && Integer.valueOf(2).equals(org.getOrgLevel());
    }

    private ReTaskTargetType canonicalTargetType(ReTaskTargetType type) {
        if (type == null) {
            throw new BizException("RE-40022", "任务对象类型不能为空");
        }
        return type;
    }

    private ReTaskTargetType parseTargetType(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return ReTaskTargetType.fromValue(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ReTaskNature parseNature(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return ReTaskNature.fromValue(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ReTaskBusinessType parseBusinessType(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return ReTaskBusinessType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ReTaskCycleType parseCycle(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return ReTaskCycleType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String generateTaskNo() {
        return "RT-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int normalizePageNo(long pageNo) {
        return pageNo < 1 ? 1 : (int) Math.min(Integer.MAX_VALUE, pageNo);
    }

    private int normalizePageSize(long pageSize) {
        return pageSize < 1 ? 20 : (int) Math.min(200, pageSize);
    }

    private LocalDateTime atStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime atEndOfDay(LocalDate date) {
        return date == null ? null : date.atTime(LocalTime.MAX.withNano(0));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BEIJING_ZONE);
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim();
    }
}
