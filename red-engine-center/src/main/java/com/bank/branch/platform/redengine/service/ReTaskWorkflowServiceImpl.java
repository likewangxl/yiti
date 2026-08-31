package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.redengine.api.dto.ReTaskApproveReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskBusinessType;
import com.bank.branch.platform.redengine.api.dto.ReTaskCycleType;
import com.bank.branch.platform.redengine.api.dto.ReTaskNature;
import com.bank.branch.platform.redengine.api.dto.ReTaskRejectReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowActionRespDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowAssignmentDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowPageQueryDTO;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskFileType;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskStatusHistory;
import com.bank.branch.platform.redengine.entity.ReTaskSubmission;
import com.bank.branch.platform.redengine.entity.ReTaskSubmissionFile;
import com.bank.branch.platform.redengine.entity.ReTaskTodo;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskFileTypeMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskStatusHistoryMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionFileMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTargetMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTodoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务报送与两级审核领域服务。
 *
 * <p>提交版本按 assignment 递增，assignment 本身始终复用；状态历史同时承担
 * 审计与 clientRequestId 的持久化幂等标记。所有状态推进都使用“当前状态/版本”条件更新，
 * 多节点并发时失败的一方会以业务冲突结束，不会静默覆盖另一方的审核结果。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReTaskWorkflowServiceImpl implements ReTaskWorkflowService {

    static final String REPORTER_TODO_ROLE = "REPORTER";
    static final String BRANCH_SECRETARY_ROLE = "R_RE_SECR";
    static final String ORG_REVIEWER_ROLE = "R_RE_ORGREV";
    static final String ORG_TODO_ROLE = "ORG_REVIEWER";
    static final String SYSTEM_ADMIN_ROLE = "SYS_ADMIN";

    private final ReTaskMapper taskMapper;
    private final ReTaskInstanceMapper instanceMapper;
    private final ReTaskBranchAssignmentMapper assignmentMapper;
    private final ReTaskSubmissionMapper submissionMapper;
    private final ReTaskSubmissionFileMapper submissionFileMapper;
    private final ReTaskTodoMapper todoMapper;
    private final ReTaskStatusHistoryMapper historyMapper;
    private final ReTaskTargetMapper targetMapper;
    private final ReTaskFileTypeMapper fileTypeMapper;
    private final RePartyOrgMapper partyOrgMapper;
    private final com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper userPartyMapMapper;
    private final CurrentUserApi currentUserApi;
    private final UserApi userApi;
    private final FileApi fileApi;
    private final ReTaskFourDimensionAdapter fourDimensionAdapter;

    /** 查询当前报送员可见的任务分配。 */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ReTaskWorkflowAssignmentDTO> listMyAssignments(ReTaskWorkflowPageQueryDTO query,
                                                                      String operatorId) {
        requireCurrentUser(operatorId);
        List<ReTaskTodo> todos = todoMapper.selectList(new LambdaQueryWrapper<ReTaskTodo>()
                .eq(ReTaskTodo::getEmployeeId, operatorId)
                .eq(ReTaskTodo::getRoleCode, REPORTER_TODO_ROLE)
                .ne(ReTaskTodo::getStatus, "CANCELLED")
                .orderByAsc(ReTaskTodo::getAvailableAt)
                .orderByAsc(ReTaskTodo::getId));
        Set<Long> assignmentIds = todos == null ? Set.of() : todos.stream()
                .map(ReTaskTodo::getAssignmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (assignmentIds.isEmpty()) {
            return emptyPage(query);
        }
        return pageAssignments(assignmentIds, query, null, operatorId);
    }

    /** 查询一条 assignment；服务层会根据实体关系执行报送员/支部/组织归属校验。 */
    @Override
    @Transactional(readOnly = true)
    public ReTaskWorkflowAssignmentDTO getAssignment(Long assignmentId, String operatorId) {
        requireCurrentUser(operatorId);
        ReTaskBranchAssignment assignment = requireAssignment(assignmentId);
        ReTask task = requireTaskForAssignment(assignment);
        ReTaskInstance instance = requireInstance(task, assignment);
        requireViewAccess(assignment, operatorId);
        return toAssignment(task, instance, assignment);
    }

    /** 报送员提交新版本；驳回后的 assignment 继续使用同一主键。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReTaskWorkflowActionRespDTO submit(ReTaskSubmissionReqDTO request, String operatorId) {
        requireCurrentUser(operatorId);
        if (request == null || request.getAssignmentId() == null
                || !hasText(request.getClientRequestId())) {
            throw new BizException("RE-40020", "任务提交参数不完整");
        }
        String clientRequestId = request.getClientRequestId().trim();
        if (clientRequestId.length() > 128) {
            throw new BizException("RE-40021", "请求幂等号过长");
        }

        ReTaskBranchAssignment assignment = requireAssignment(request.getAssignmentId());
        ReTask task = requireTaskForAssignment(assignment);
        ReTaskInstance instance = requireInstance(task, assignment);
        ReTaskTodo reporterTodo = findReporterTodo(assignment.getId(), operatorId);
        if (reporterTodo == null || "CANCELLED".equalsIgnoreCase(reporterTodo.getStatus())) {
            throw new BizException("RE-40304", "当前用户不是该任务的处理人");
        }

        String actionCode = idempotencyActionCode(clientRequestId);
        ReTaskStatusHistory idempotency = historyMapper.selectOne(
                new LambdaQueryWrapper<ReTaskStatusHistory>()
                        .eq(ReTaskStatusHistory::getAssignmentId, assignment.getId())
                        .eq(ReTaskStatusHistory::getActionCode, actionCode));
        if (idempotency != null && idempotency.getSubmissionId() != null) {
            ReTaskSubmission existing = submissionMapper.selectById(idempotency.getSubmissionId());
            if (existing == null) {
                throw new BizException("RE-40902", "任务幂等记录不完整");
            }
            return action(existing, assignment, true);
        }

        ReTaskSubmission current = latestSubmission(assignment.getId());
        ReTaskSubmissionStatus currentStatus = current == null ? null : current.getStatus();
        if (current != null && currentStatus != ReTaskSubmissionStatus.REJECTED_BY_BRANCH
                && currentStatus != ReTaskSubmissionStatus.REJECTED_BY_ORG) {
            throw new BizException("RE-40903", "当前任务状态不允许重复提交");
        }

        int oldVersion = assignment.getCurrentVersion() == null ? 0 : assignment.getCurrentVersion();
        int nextVersion = current == null ? oldVersion + 1
                : Math.max(oldVersion, current.getVersionNo() == null ? 0 : current.getVersionNo()) + 1;
        validateSubmissionContent(task, request);
        List<ReTaskFileSnapshot> files = validateFiles(task, request.getFileObjectIds());

        LocalDateTime now = LocalDateTime.now();
        int assignmentUpdated = assignmentMapper.update(null, new LambdaUpdateWrapper<ReTaskBranchAssignment>()
                .eq(ReTaskBranchAssignment::getId, assignment.getId())
                .eq(ReTaskBranchAssignment::getCurrentVersion, oldVersion)
                .in(ReTaskBranchAssignment::getStatus,
                        ReTaskAssignmentStatus.UNREPORTED.name(),
                        ReTaskAssignmentStatus.REJECTED_BY_BRANCH.name(),
                        ReTaskAssignmentStatus.REJECTED_BY_ORG.name())
                .set(ReTaskBranchAssignment::getCurrentVersion, nextVersion)
                .set(ReTaskBranchAssignment::getStatus, ReTaskAssignmentStatus.BRANCH_PENDING.name())
                .set(ReTaskBranchAssignment::getLastSubmittedAt, now)
                .set(ReTaskBranchAssignment::getLastSubmitterId, operatorId)
                .set(ReTaskBranchAssignment::getUpdateTime, now));
        if (assignmentUpdated != 1) {
            throw new BizException("RE-40901", "任务已被其他用户更新，请刷新后重试");
        }

        ReTaskSubmission submission = new ReTaskSubmission();
        submission.setTaskId(task.getId());
        submission.setTaskInstanceId(instance.getId());
        submission.setAssignmentId(assignment.getId());
        submission.setBranchId(assignment.getBranchId());
        submission.setVersionNo(nextVersion);
        submission.setStatus(ReTaskSubmissionStatus.BRANCH_PENDING);
        submission.setDimensionCode(request.getDimensionCode());
        submission.setItemCode(request.getItemCode());
        submission.setContentText(trimToNull(request.getContent()));
        submission.setFormData(trimToNull(request.getFormData()));
        submission.setSubmitterId(operatorId);
        submission.setSubmittedAt(now);
        submission.setDeleted(0);
        submission.setCreateTime(now);
        submission.setUpdateTime(now);
        try {
            submissionMapper.insert(submission);
        } catch (DuplicateKeyException duplicate) {
            throw new BizException("RE-40901", "任务已被其他用户更新，请刷新后重试", duplicate);
        }
        if (submission.getId() == null) {
            throw new BizException("RE-50012", "任务提交保存失败");
        }
        saveSubmissionFiles(submission, files, operatorId, now);

        ReTaskStatusHistory history = new ReTaskStatusHistory();
        history.setTaskId(task.getId());
        history.setTaskInstanceId(instance.getId());
        history.setAssignmentId(assignment.getId());
        history.setSubmissionId(submission.getId());
        history.setActionCode(actionCode);
        history.setFromStatus(currentStatus == null ? ReTaskAssignmentStatus.UNREPORTED.name()
                : currentStatus.name());
        history.setToStatus(ReTaskSubmissionStatus.BRANCH_PENDING.name());
        history.setOperatorId(operatorId);
        history.setOccurredAt(now);
        history.setCreateTime(now);
        historyMapper.insert(history);

        todoMapper.update(null, new LambdaUpdateWrapper<ReTaskTodo>()
                .eq(ReTaskTodo::getAssignmentId, assignment.getId())
                .eq(ReTaskTodo::getEmployeeId, operatorId)
                .eq(ReTaskTodo::getRoleCode, REPORTER_TODO_ROLE)
                .eq(ReTaskTodo::getStatus, "PENDING")
                .set(ReTaskTodo::getStatus, "COMPLETED")
                .set(ReTaskTodo::getCompletedAt, now)
                .set(ReTaskTodo::getUpdateTime, now));

        if (isFourDimension(task) && fourDimensionAdapter != null) {
            fourDimensionAdapter.recordTaskUpload(task, instance, assignment, submission,
                    request.getDimensionCode(), request.getItemCode(), operatorId);
        }
        return action(submission, assignment, false);
    }

    /** 查询支部书记工作台；支部范围由当前实体书记关系和 R_RE_SECR 双重确定。 */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ReTaskWorkflowAssignmentDTO> listBranchReviews(ReTaskWorkflowPageQueryDTO query,
                                                                      String operatorId) {
        requireCurrentUser(operatorId);
        requireRole(BRANCH_SECRETARY_ROLE);
        Set<Long> branchIds = secretaryBranchIds(operatorId);
        if (branchIds.isEmpty()) {
            return emptyPage(query);
        }
        return pageAssignments(null, query, branchIds, operatorId);
    }

    /** 查询支部审核详情。 */
    @Override
    @Transactional(readOnly = true)
    public ReTaskWorkflowAssignmentDTO getBranchReview(Long assignmentId, String operatorId) {
        requireCurrentUser(operatorId);
        requireRole(BRANCH_SECRETARY_ROLE);
        ReTaskBranchAssignment assignment = requireAssignment(assignmentId);
        requireSecretaryForBranch(assignment.getBranchId(), operatorId);
        ReTask task = requireTaskForAssignment(assignment);
        ReTaskInstance instance = requireInstance(task, assignment);
        return toAssignment(task, instance, assignment);
    }

    /** 支部书记通过当前版本；通过动作不直接进入组织审核。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReTaskWorkflowActionRespDTO approveBranch(Long assignmentId, ReTaskApproveReqDTO request,
                                                      String operatorId) {
        requireCurrentUser(operatorId);
        requireRole(BRANCH_SECRETARY_ROLE);
        ReTaskContext context = requireReviewContext(assignmentId, operatorId, true);
        ReTaskSubmission submission = requireCurrentSubmission(context.assignment().getId(),
                ReTaskSubmissionStatus.BRANCH_PENDING);
        rejectSelfReview(submission, operatorId);
        LocalDateTime now = LocalDateTime.now();
        transitionSubmission(context, submission, ReTaskSubmissionStatus.BRANCH_APPROVED,
                operatorId, normalizeOpinion(request == null ? null : request.getFeedback()), now,
                true, false, true);
        return action(submission, context.assignment(), false, ReTaskSubmissionStatus.BRANCH_APPROVED,
                ReTaskAssignmentStatus.BRANCH_PENDING);
    }

    /** 支部书记驳回当前版本，意见必填并将报送员待办重新置为待处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReTaskWorkflowActionRespDTO rejectBranch(Long assignmentId, ReTaskRejectReqDTO request,
                                                     String operatorId) {
        requireCurrentUser(operatorId);
        requireRole(BRANCH_SECRETARY_ROLE);
        String opinion = requiredOpinion(request == null ? null : request.getFeedback());
        ReTaskContext context = requireReviewContext(assignmentId, operatorId, true);
        ReTaskSubmission submission = requireCurrentSubmission(context.assignment().getId(),
                ReTaskSubmissionStatus.BRANCH_PENDING);
        rejectSelfReview(submission, operatorId);
        LocalDateTime now = LocalDateTime.now();
        transitionSubmission(context, submission, ReTaskSubmissionStatus.REJECTED_BY_BRANCH,
                operatorId, opinion, now, true, false, true);
        reopenReporterTodos(context.assignment().getId(), now);
        return action(submission, context.assignment(), false, ReTaskSubmissionStatus.REJECTED_BY_BRANCH,
                ReTaskAssignmentStatus.REJECTED_BY_BRANCH);
    }

    /** 支部书记把已经通过的版本单独提交至组织审核。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReTaskWorkflowActionRespDTO submitToOrg(Long assignmentId, ReTaskApproveReqDTO request,
                                                    String operatorId) {
        requireCurrentUser(operatorId);
        requireRole(BRANCH_SECRETARY_ROLE);
        ReTaskContext context = requireReviewContext(assignmentId, operatorId, true);
        ReTaskSubmission submission = requireCurrentSubmission(context.assignment().getId(),
                ReTaskSubmissionStatus.BRANCH_APPROVED);
        rejectSelfReview(submission, operatorId);
        LocalDateTime now = LocalDateTime.now();
        transitionSubmission(context, submission, ReTaskSubmissionStatus.ORG_PENDING,
                operatorId, normalizeOpinion(request == null ? null : request.getFeedback()), now,
                true, true, false);
        ensureOrgReviewerTodos(context.assignment().getId(), context.instance(), now);
        return action(submission, context.assignment(), false, ReTaskSubmissionStatus.ORG_PENDING,
                ReTaskAssignmentStatus.ORG_PENDING);
    }

    /** 查询组织审核工作台。 */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ReTaskWorkflowAssignmentDTO> listOrgReviews(ReTaskWorkflowPageQueryDTO query,
                                                                   String operatorId) {
        requireCurrentUser(operatorId);
        requireOrgReviewer();
        return pageAssignments(null, query, null, operatorId);
    }

    /** 查询组织审核详情。 */
    @Override
    @Transactional(readOnly = true)
    public ReTaskWorkflowAssignmentDTO getOrgReview(Long assignmentId, String operatorId) {
        requireCurrentUser(operatorId);
        requireOrgReviewer();
        ReTaskBranchAssignment assignment = requireAssignment(assignmentId);
        ReTask task = requireTaskForAssignment(assignment);
        ReTaskInstance instance = requireInstance(task, assignment);
        return toAssignment(task, instance, assignment);
    }

    /** 组织审核通过当前版本并关闭组织审核待办。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReTaskWorkflowActionRespDTO approveOrg(Long assignmentId, ReTaskApproveReqDTO request,
                                                   String operatorId) {
        requireCurrentUser(operatorId);
        requireOrgReviewer();
        ReTaskContext context = requireReviewContext(assignmentId, operatorId, false);
        ReTaskSubmission submission = requireCurrentSubmission(context.assignment().getId(),
                ReTaskSubmissionStatus.ORG_PENDING);
        rejectSelfReview(submission, operatorId);
        LocalDateTime now = LocalDateTime.now();
        transitionSubmission(context, submission, ReTaskSubmissionStatus.APPROVED,
                operatorId, normalizeOpinion(request == null ? null : request.getFeedback()), now,
                false, true, true);
        completeOrgReviewerTodos(context.assignment().getId(), now);
        return action(submission, context.assignment(), false, ReTaskSubmissionStatus.APPROVED,
                ReTaskAssignmentStatus.APPROVED);
    }

    /** 组织审核驳回当前版本，意见必填并退回报送员。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReTaskWorkflowActionRespDTO rejectOrg(Long assignmentId, ReTaskRejectReqDTO request,
                                                  String operatorId) {
        requireCurrentUser(operatorId);
        requireOrgReviewer();
        String opinion = requiredOpinion(request == null ? null : request.getFeedback());
        ReTaskContext context = requireReviewContext(assignmentId, operatorId, false);
        ReTaskSubmission submission = requireCurrentSubmission(context.assignment().getId(),
                ReTaskSubmissionStatus.ORG_PENDING);
        rejectSelfReview(submission, operatorId);
        LocalDateTime now = LocalDateTime.now();
        transitionSubmission(context, submission, ReTaskSubmissionStatus.REJECTED_BY_ORG,
                operatorId, opinion, now, false, true, true);
        completeOrgReviewerTodos(context.assignment().getId(), now);
        reopenReporterTodos(context.assignment().getId(), now);
        return action(submission, context.assignment(), false, ReTaskSubmissionStatus.REJECTED_BY_ORG,
                ReTaskAssignmentStatus.REJECTED_BY_ORG);
    }

    /** 执行工作台列表查询；授权 ID 先收窄，后续筛选只作用于该集合。 */
    private PageResult<ReTaskWorkflowAssignmentDTO> pageAssignments(Set<Long> authorizedAssignmentIds,
                                                                     ReTaskWorkflowPageQueryDTO query,
                                                                     Set<Long> branchIds,
                                                                     String operatorId) {
        ReTaskWorkflowPageQueryDTO normalized = query == null ? new ReTaskWorkflowPageQueryDTO() : query;
        int pageNo = normalizePageNo(normalized.getPageNo());
        int pageSize = normalizePageSize(normalized.getPageSize());
        LambdaQueryWrapper<ReTaskBranchAssignment> wrapper = new LambdaQueryWrapper<ReTaskBranchAssignment>()
                .in(authorizedAssignmentIds != null && !authorizedAssignmentIds.isEmpty(),
                        ReTaskBranchAssignment::getId, authorizedAssignmentIds == null ? List.of() : authorizedAssignmentIds)
                .in(branchIds != null && !branchIds.isEmpty(), ReTaskBranchAssignment::getBranchId,
                        branchIds == null ? List.of() : branchIds)
                .ge(normalized.getSubmittedStartAt() != null,
                        ReTaskBranchAssignment::getLastSubmittedAt, normalized.getSubmittedStartAt())
                .le(normalized.getSubmittedEndAt() != null,
                        ReTaskBranchAssignment::getLastSubmittedAt, normalized.getSubmittedEndAt())
                .orderByDesc(ReTaskBranchAssignment::getLastSubmittedAt)
                .orderByDesc(ReTaskBranchAssignment::getId);
        List<ReTaskBranchAssignment> assignments = assignmentMapper.selectList(wrapper);
        if (assignments == null || assignments.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, List.of());
        }

        String keyword = trimToNull(normalized.resolvedKeyword());
        ReTaskNature nature = normalized.resolvedTaskNature();
        ReTaskCycleType cycle = normalized.resolvedCycleType();
        List<ReTaskWorkflowAssignmentDTO> rows = new ArrayList<>();
        for (ReTaskBranchAssignment assignment : assignments) {
            if (assignment == null || assignment.getId() == null) {
                continue;
            }
            ReTask task = taskMapper.selectById(taskIdForAssignment(assignment));
            if (task == null || task.getStatus() != ReTaskStatus.PUBLISHED) {
                continue;
            }
            ReTaskInstance instance = instanceMapper.selectById(assignment.getTaskInstanceId());
            if (instance == null || !Objects.equals(instance.getTaskId(), task.getId())) {
                continue;
            }
            if (nature != null && !natureMatches(task, nature)) {
                continue;
            }
            if (cycle != null && cycle != ReTaskCycleType.NONE
                    && !cycleMatches(task, cycle)) {
                continue;
            }
            if (normalized.getBusinessType() != null
                    && !businessTypeMatches(task, normalized.getBusinessType())) {
                continue;
            }
            ReTaskWorkflowAssignmentDTO dto = toAssignment(task, instance, assignment);
            if (normalized.getAssignmentStatus() != null
                    && dto.getStatus() != normalized.getAssignmentStatus()) {
                continue;
            }
            if (normalized.getSubmissionStatus() != null
                    && dto.getSubmissionStatus() != normalized.getSubmissionStatus()) {
                continue;
            }
            if (!matchesKeyword(dto, keyword)) {
                continue;
            }
            if (isBranchQueue(operatorId, branchIds) && !branchQueueStatus(dto.getSubmissionStatus())) {
                continue;
            }
            if (isOrgQueue(operatorId, branchIds) && !orgQueueStatus(dto.getSubmissionStatus())) {
                continue;
            }
            rows.add(dto);
        }
        long total = rows.size();
        int from = Math.min((pageNo - 1) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return PageResult.of(pageNo, pageSize, total, rows.subList(from, to));
    }

    /** 将 assignment 的实例反查任务；不信任请求路径或前端传入的任务 ID。 */
    private Long taskIdForAssignment(ReTaskBranchAssignment assignment) {
        if (assignment.getTaskInstanceId() == null) {
            return null;
        }
        ReTaskInstance instance = instanceMapper.selectById(assignment.getTaskInstanceId());
        return instance == null ? null : instance.getTaskId();
    }

    private ReTaskWorkflowAssignmentDTO toAssignment(ReTask task, ReTaskInstance instance,
                                                     ReTaskBranchAssignment assignment) {
        ReTaskWorkflowAssignmentDTO dto = new ReTaskWorkflowAssignmentDTO();
        dto.setTaskId(task.getId());
        dto.setTaskNo(task.getTaskNo());
        dto.setTaskTitle(task.getTitle());
        dto.setTaskDescription(task.getDescription());
        dto.setTaskNature(parseNature(task.getNature()));
        dto.setBusinessType(parseBusinessType(task.getTypeCode()));
        dto.setCycleType(parseCycle(task.getCycle()));
        dto.setDurationDays(task.getDurationDays());
        dto.setTaskInstanceId(instance.getId());
        dto.setAssignmentId(assignment.getId());
        dto.setBranchId(assignment.getBranchId());
        RePartyOrg branch = assignment.getBranchId() == null ? null : partyOrgMapper.selectById(assignment.getBranchId());
        dto.setBranchName(branch == null ? null : branch.getOrgName());
        ReTaskSubmission submission = latestSubmission(assignment.getId());
        ReTaskSubmissionStatus submissionStatus = submission == null ? null : submission.getStatus();
        dto.setSubmissionStatus(submissionStatus);
        dto.setStatus(resolveAssignmentStatus(assignment, submissionStatus));
        dto.setIsUnreported(submission == null && dto.getStatus() == ReTaskAssignmentStatus.UNREPORTED);
        dto.setBranchApproved(submissionStatus == ReTaskSubmissionStatus.BRANCH_APPROVED);
        if (submission != null) {
            dto.setSubmitterId(submission.getSubmitterId());
            dto.setSubmitterName(hasText(submission.getSubmitterId())
                    ? userApi.getUserName(submission.getSubmitterId()) : null);
            dto.setSubmittedAt(submission.getSubmittedAt());
            dto.setContent(submission.getContentText());
            dto.setFormData(submission.getFormData());
            dto.setFiles(loadAttachments(submission.getId()));
        } else {
            dto.setFiles(List.of());
        }
        dto.setWindowStartAt(instance.getWindowStartAt());
        dto.setWindowEndAt(instance.getWindowEndAt());
        dto.setRequiresFile(Integer.valueOf(1).equals(task.getRequiresFile()));
        dto.setAllowedFileTypes(loadAllowedFileTypes(task.getId()));
        return dto;
    }

    private List<com.bank.branch.platform.redengine.api.dto.ReTaskAttachmentDTO> loadAttachments(Long submissionId) {
        if (submissionId == null) {
            return List.of();
        }
        List<ReTaskSubmissionFile> files = submissionFileMapper.selectList(
                new LambdaQueryWrapper<ReTaskSubmissionFile>()
                        .eq(ReTaskSubmissionFile::getSubmissionId, submissionId)
                        .orderByAsc(ReTaskSubmissionFile::getSortNo)
                        .orderByAsc(ReTaskSubmissionFile::getId));
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream().map(file -> {
            com.bank.branch.platform.redengine.api.dto.ReTaskAttachmentDTO dto =
                    new com.bank.branch.platform.redengine.api.dto.ReTaskAttachmentDTO();
            dto.setId(file.getId());
            dto.setFileId(file.getFileObjectId());
            dto.setFileName(file.getFileName());
            dto.setFileSize(file.getFileSize());
            dto.setFileType(file.getFileType());
            dto.setSortNo(file.getSortNo());
            return dto;
        }).toList();
    }

    private List<String> loadAllowedFileTypes(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        List<ReTaskFileType> types = fileTypeMapper.selectList(
                new LambdaQueryWrapper<ReTaskFileType>().eq(ReTaskFileType::getTaskId, taskId)
                        .eq(ReTaskFileType::getEnabled, 1)
                        .orderByAsc(ReTaskFileType::getSortNo)
                        .orderByAsc(ReTaskFileType::getId));
        if (types == null) {
            return List.of();
        }
        return types.stream().map(type -> hasText(type.getFileExtension())
                        ? type.getFileExtension() : type.getFileTypeCode())
                .filter(this::hasText).toList();
    }

    private ReTaskContext requireReviewContext(Long assignmentId, String operatorId,
                                               boolean branchReview) {
        ReTaskBranchAssignment assignment = requireAssignment(assignmentId);
        ReTask task = requireTaskForAssignment(assignment);
        ReTaskInstance instance = requireInstance(task, assignment);
        if (branchReview) {
            requireSecretaryForBranch(assignment.getBranchId(), operatorId);
        }
        return new ReTaskContext(task, instance, assignment);
    }

    private ReTaskBranchAssignment requireAssignment(Long assignmentId) {
        if (assignmentId == null) {
            throw new BizException("RE-40010", "任务分配不存在");
        }
        ReTaskBranchAssignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null || assignment.getId() == null) {
            throw new BizException("RE-40010", "任务分配不存在");
        }
        return assignment;
    }

    private ReTask requireTaskForAssignment(ReTaskBranchAssignment assignment) {
        if (assignment.getTaskInstanceId() == null) {
            throw new BizException("RE-40011", "任务实例不存在");
        }
        ReTaskInstance instance = instanceMapper.selectById(assignment.getTaskInstanceId());
        if (instance == null || instance.getTaskId() == null) {
            throw new BizException("RE-40011", "任务实例不存在");
        }
        ReTask task = taskMapper.selectById(instance.getTaskId());
        if (task == null || task.getId() == null) {
            throw new BizException("RE-40010", "任务不存在");
        }
        if (task.getStatus() != ReTaskStatus.PUBLISHED) {
            throw new BizException("RE-40303", "任务未发布");
        }
        return task;
    }

    private ReTaskInstance requireInstance(ReTask task, ReTaskBranchAssignment assignment) {
        ReTaskInstance instance = instanceMapper.selectById(assignment.getTaskInstanceId());
        if (instance == null || !Objects.equals(task.getId(), instance.getTaskId())) {
            throw new BizException("RE-40011", "任务实例与任务不匹配");
        }
        return instance;
    }

    private void requireViewAccess(ReTaskBranchAssignment assignment, String operatorId) {
        if (isOrgReviewer()) {
            return;
        }
        if (hasRole(BRANCH_SECRETARY_ROLE)) {
            requireSecretaryForBranch(assignment.getBranchId(), operatorId);
            return;
        }
        if (findReporterTodo(assignment.getId(), operatorId) != null) {
            return;
        }
        throw new BizException("RE-40304", "无权查看该任务");
    }

    private Set<Long> secretaryBranchIds(String operatorId) {
        List<RePartyOrg> orgs = partyOrgMapper.selectList(null);
        if (orgs == null) {
            return Set.of();
        }
        return orgs.stream().filter(org -> org != null && Integer.valueOf(2).equals(org.getOrgLevel())
                        && operatorId.equals(org.getSecretaryId()))
                .map(RePartyOrg::getId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void requireSecretaryForBranch(Long branchId, String operatorId) {
        if (branchId == null || !hasRole(BRANCH_SECRETARY_ROLE)) {
            throw new BizException("RE-40302", "无支部审核权限");
        }
        RePartyOrg branch = partyOrgMapper.selectById(branchId);
        if (branch == null || !Integer.valueOf(2).equals(branch.getOrgLevel())
                || !operatorId.equals(branch.getSecretaryId())) {
            throw new BizException("RE-40302", "无权审核该党支部任务");
        }
    }

    private void requireOrgReviewer() {
        if (!isOrgReviewer()) {
            throw new BizException("RE-40302", "无组织审核权限");
        }
    }

    private boolean isOrgReviewer() {
        return currentUserApi.isSystemAdmin() || hasRole(ORG_REVIEWER_ROLE)
                || hasRole(SYSTEM_ADMIN_ROLE);
    }

    private void requireRole(String roleCode) {
        if (!hasRole(roleCode)) {
            throw new BizException("RE-40302", "无权执行该任务操作");
        }
    }

    private boolean hasRole(String roleCode) {
        Set<String> roles = currentUserApi.getCurrentRoleCodes();
        return roles != null && roles.stream().filter(Objects::nonNull)
                .map(String::trim).anyMatch(roleCode::equalsIgnoreCase);
    }

    private void requireCurrentUser(String operatorId) {
        String current = currentUserApi.getCurrentEmpId();
        if (!hasText(operatorId) || !hasText(current) || !operatorId.trim().equals(current.trim())) {
            throw new BizException("RE-40301", "当前用户上下文缺失或不匹配");
        }
    }

    private ReTaskSubmission requireCurrentSubmission(Long assignmentId, ReTaskSubmissionStatus expected) {
        ReTaskSubmission submission = latestSubmission(assignmentId);
        if (submission == null || submission.getStatus() != expected) {
            throw new BizException("RE-40904", "当前任务状态不允许执行该操作");
        }
        if (submission.getId() == null) {
            throw new BizException("RE-40904", "任务提交版本不存在");
        }
        return submission;
    }

    private void rejectSelfReview(ReTaskSubmission submission, String operatorId) {
        if (submission != null && Objects.equals(submission.getSubmitterId(), operatorId)) {
            throw new BizException("RE-40008", "禁止审核本人提交的记录");
        }
    }

    private void transitionSubmission(ReTaskContext context, ReTaskSubmission submission,
                                      ReTaskSubmissionStatus target, String operatorId,
                                      String opinion, LocalDateTime now,
                                      boolean branchAction, boolean updateAssignmentStatus,
                                      boolean stampReviewer) {
        ReTaskSubmissionStatus from = submission.getStatus();
        if (from == null || !from.canTransitionTo(target)) {
            throw new BizException("RE-40904", "任务状态不允许流转");
        }
        LambdaUpdateWrapper<ReTaskSubmission> submissionUpdate = new LambdaUpdateWrapper<ReTaskSubmission>()
                .eq(ReTaskSubmission::getId, submission.getId())
                .eq(ReTaskSubmission::getStatus, from)
                .set(ReTaskSubmission::getStatus, target)
                .set(ReTaskSubmission::getReviewOpinion, opinion)
                .set(ReTaskSubmission::getUpdateTime, now);
        if (stampReviewer && branchAction) {
            submissionUpdate.set(ReTaskSubmission::getBranchReviewerId, operatorId)
                    .set(ReTaskSubmission::getBranchReviewedAt, now);
        } else if (stampReviewer) {
            submissionUpdate.set(ReTaskSubmission::getOrgReviewerId, operatorId)
                    .set(ReTaskSubmission::getOrgReviewedAt, now);
        }
        if (submissionMapper.update(null, submissionUpdate) != 1) {
            throw new BizException("RE-40901", "任务已被其他用户更新，请刷新后重试");
        }

        ReTaskAssignmentStatus expectedAssignment = branchAction
                ? ReTaskAssignmentStatus.BRANCH_PENDING
                : ReTaskAssignmentStatus.ORG_PENDING;
        ReTaskAssignmentStatus targetAssignment = updateAssignmentStatus
                ? assignmentStatusFor(target) : expectedAssignment;
        LambdaUpdateWrapper<ReTaskBranchAssignment> assignmentUpdate =
                new LambdaUpdateWrapper<ReTaskBranchAssignment>()
                        .eq(ReTaskBranchAssignment::getId, context.assignment().getId())
                        .eq(ReTaskBranchAssignment::getCurrentVersion,
                                context.assignment().getCurrentVersion())
                        .eq(ReTaskBranchAssignment::getStatus, expectedAssignment.name())
                        .set(ReTaskBranchAssignment::getStatus, targetAssignment.name())
                        .set(ReTaskBranchAssignment::getUpdateTime, now);
        if (targetAssignment == ReTaskAssignmentStatus.APPROVED) {
            assignmentUpdate.set(ReTaskBranchAssignment::getCompletedAt, now);
        }
        if (assignmentMapper.update(null, assignmentUpdate) != 1) {
            throw new BizException("RE-40901", "任务已被其他用户更新，请刷新后重试");
        }

        ReTaskStatusHistory history = new ReTaskStatusHistory();
        history.setTaskId(context.task().getId());
        history.setTaskInstanceId(context.instance().getId());
        history.setAssignmentId(context.assignment().getId());
        history.setSubmissionId(submission.getId());
        history.setActionCode(actionCodeFor(target, branchAction));
        history.setFromStatus(from.name());
        history.setToStatus(target.name());
        history.setOpinion(opinion);
        history.setOperatorId(operatorId);
        history.setOccurredAt(now);
        history.setCreateTime(now);
        historyMapper.insert(history);
    }

    private void reopenReporterTodos(Long assignmentId, LocalDateTime now) {
        todoMapper.update(null, new LambdaUpdateWrapper<ReTaskTodo>()
                .eq(ReTaskTodo::getAssignmentId, assignmentId)
                .eq(ReTaskTodo::getRoleCode, REPORTER_TODO_ROLE)
                .ne(ReTaskTodo::getStatus, "CANCELLED")
                .set(ReTaskTodo::getStatus, "PENDING")
                .set(ReTaskTodo::getCompletedAt, null)
                .set(ReTaskTodo::getAvailableAt, now)
                .set(ReTaskTodo::getUpdateTime, now));
    }

    private void completeOrgReviewerTodos(Long assignmentId, LocalDateTime now) {
        todoMapper.update(null, new LambdaUpdateWrapper<ReTaskTodo>()
                .eq(ReTaskTodo::getAssignmentId, assignmentId)
                .eq(ReTaskTodo::getRoleCode, ORG_TODO_ROLE)
                .eq(ReTaskTodo::getStatus, "PENDING")
                .set(ReTaskTodo::getStatus, "COMPLETED")
                .set(ReTaskTodo::getCompletedAt, now)
                .set(ReTaskTodo::getUpdateTime, now));
    }

    private void ensureOrgReviewerTodos(Long assignmentId, ReTaskInstance instance, LocalDateTime now) {
        List<String> reviewers = userApi.getEmpIdsByRoleCode(ORG_REVIEWER_ROLE);
        if (reviewers == null) {
            return;
        }
        for (String reviewerId : reviewers.stream().filter(this::hasText).distinct().toList()) {
            ReTaskTodo existing = todoMapper.selectOne(new LambdaQueryWrapper<ReTaskTodo>()
                    .eq(ReTaskTodo::getAssignmentId, assignmentId)
                    .eq(ReTaskTodo::getEmployeeId, reviewerId)
                    .eq(ReTaskTodo::getRoleCode, ORG_TODO_ROLE));
            if (existing != null) {
                if (!"CANCELLED".equalsIgnoreCase(existing.getStatus())) {
                    todoMapper.update(null, new LambdaUpdateWrapper<ReTaskTodo>()
                            .eq(ReTaskTodo::getId, existing.getId())
                            .set(ReTaskTodo::getStatus, "PENDING")
                            .set(ReTaskTodo::getAvailableAt, instance.getWindowStartAt())
                            .set(ReTaskTodo::getCompletedAt, null)
                            .set(ReTaskTodo::getUpdateTime, now));
                }
                continue;
            }
            ReTaskTodo todo = new ReTaskTodo();
            todo.setAssignmentId(assignmentId);
            todo.setEmployeeId(reviewerId);
            todo.setRoleCode(ORG_TODO_ROLE);
            todo.setStatus("PENDING");
            todo.setAvailableAt(instance.getWindowStartAt());
            todo.setCreateTime(now);
            todo.setUpdateTime(now);
            try {
                todoMapper.insert(todo);
            } catch (DuplicateKeyException duplicate) {
                log.debug("组织审核待办已由并发节点创建 assignmentId={} employeeId={}", assignmentId, reviewerId);
            }
        }
    }

    private ReTaskTodo findReporterTodo(Long assignmentId, String operatorId) {
        return todoMapper.selectOne(new LambdaQueryWrapper<ReTaskTodo>()
                .eq(ReTaskTodo::getAssignmentId, assignmentId)
                .eq(ReTaskTodo::getEmployeeId, operatorId)
                .eq(ReTaskTodo::getRoleCode, REPORTER_TODO_ROLE));
    }

    private ReTaskSubmission latestSubmission(Long assignmentId) {
        if (assignmentId == null) {
            return null;
        }
        List<ReTaskSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<ReTaskSubmission>()
                        .eq(ReTaskSubmission::getAssignmentId, assignmentId)
                        .orderByDesc(ReTaskSubmission::getVersionNo)
                        .orderByDesc(ReTaskSubmission::getId));
        if (submissions != null && !submissions.isEmpty()) {
            return submissions.stream().max(Comparator
                    .comparing(ReTaskSubmission::getVersionNo, Comparator.nullsFirst(Integer::compareTo))
                    .thenComparing(ReTaskSubmission::getId, Comparator.nullsFirst(Long::compareTo))).orElse(null);
        }
        // 纯 Mockito 单测及部分旧 Mapper 实现只 stub 了 selectOne；生产路径优先使用上面的 selectList。
        return submissionMapper.selectOne(new LambdaQueryWrapper<ReTaskSubmission>()
                .eq(ReTaskSubmission::getAssignmentId, assignmentId)
                .orderByDesc(ReTaskSubmission::getVersionNo)
                .orderByDesc(ReTaskSubmission::getId));
    }

    private void validateSubmissionContent(ReTask task, ReTaskSubmissionReqDTO request) {
        if (isFourDimension(task)) {
            if (!hasText(request.getDimensionCode()) && !hasText(request.getItemCode())) {
                // 四维旧页面可只从 RE_SUBMIT 传 dimension/item；新任务接口没有这些字段时仍允许正文提交，
                // 由 FourDimensionAdapter 在关联旧记录时执行更严格的明细校验。
                return;
            }
            return;
        }
        if (!hasText(request.getContent()) && !hasText(request.getFormData())) {
            throw new BizException("RE-40023", "任务填报内容不能为空");
        }
    }

    private List<ReTaskFileSnapshot> validateFiles(ReTask task, List<String> fileObjectIds) {
        List<String> ids = fileObjectIds == null ? List.of() : fileObjectIds.stream()
                .filter(this::hasText).map(String::trim).toList();
        if (new LinkedHashSet<>(ids).size() != ids.size()) {
            throw new BizException("RE-40025", "附件不能重复");
        }
        boolean requiresFile = Integer.valueOf(1).equals(task.getRequiresFile());
        if (requiresFile && ids.isEmpty()) {
            throw new BizException("RE-40026", "该任务必须上传附件");
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ReTaskFileType> types = fileTypeMapper.selectList(
                new LambdaQueryWrapper<ReTaskFileType>().eq(ReTaskFileType::getTaskId, task.getId())
                        .eq(ReTaskFileType::getEnabled, 1));
        List<ReTaskFileType> enabledTypes = types == null ? List.of() : types.stream()
                .filter(Objects::nonNull).toList();
        if (requiresFile && enabledTypes.isEmpty()) {
            throw new BizException("RE-40027", "任务未配置允许的附件类型");
        }
        Map<String, String> names = fileApi.getFileNames(ids);
        Map<String, Long> sizes = fileApi.getFileSizes(ids);
        List<ReTaskFileSnapshot> snapshots = new ArrayList<>();
        for (String id : ids) {
            String name = names == null ? null : names.get(id);
            if (!hasText(name)) {
                name = fileApi.getFileName(id);
            }
            if (!hasText(name)) {
                throw new BizException("RE-40028", "附件不存在");
            }
            Long size = sizes == null ? null : sizes.get(id);
            if (size == null) {
                throw new BizException("RE-40028", "附件不存在");
            }
            ReTaskFileType matched = matchFileType(name, size, enabledTypes);
            if (requiresFile && matched == null) {
                throw new BizException("RE-40029", "附件类型或大小不符合任务要求");
            }
            snapshots.add(new ReTaskFileSnapshot(id, name, size, extension(name), matched));
        }
        return snapshots;
    }

    private ReTaskFileType matchFileType(String name, Long size, List<ReTaskFileType> types) {
        String extension = extension(name);
        for (ReTaskFileType type : types) {
            if (type == null) {
                continue;
            }
            boolean extensionMatches = !hasText(type.getFileExtension())
                    || normalizeExtension(type.getFileExtension()).equals(extension)
                    || (hasText(type.getFileTypeCode())
                    && type.getFileTypeCode().trim().equalsIgnoreCase(extension));
            boolean sizeMatches = type.getMaxSizeBytes() == null || size <= type.getMaxSizeBytes();
            if (extensionMatches && sizeMatches) {
                return type;
            }
        }
        return null;
    }

    private void saveSubmissionFiles(ReTaskSubmission submission, List<ReTaskFileSnapshot> files,
                                     String operatorId, LocalDateTime now) {
        int sortNo = 1;
        for (ReTaskFileSnapshot file : files) {
            ReTaskSubmissionFile entity = new ReTaskSubmissionFile();
            entity.setSubmissionId(submission.getId());
            entity.setFileObjectId(file.fileId());
            entity.setFileName(file.fileName());
            entity.setFileSize(file.fileSize());
            entity.setFileType(file.fileType());
            entity.setSortNo(sortNo++);
            entity.setCreatedBy(operatorId);
            entity.setDeleted(0);
            entity.setCreateTime(now);
            submissionFileMapper.insert(entity);
            fileApi.bindFile("RE_TASK_SUBMISSION", String.valueOf(submission.getId()),
                    file.fileId(), "ATTACHMENT");
        }
    }

    private ReTaskWorkflowActionRespDTO action(ReTaskSubmission submission,
                                               ReTaskBranchAssignment assignment,
                                               boolean idempotent) {
        return action(submission, assignment, idempotent, submission.getStatus(),
                parseAssignmentStatus(assignment.getStatus()));
    }

    private ReTaskWorkflowActionRespDTO action(ReTaskSubmission submission,
                                               ReTaskBranchAssignment assignment,
                                               boolean idempotent,
                                               ReTaskSubmissionStatus submissionStatus,
                                               ReTaskAssignmentStatus assignmentStatus) {
        ReTaskWorkflowActionRespDTO response = new ReTaskWorkflowActionRespDTO();
        response.setAssignmentId(assignment.getId());
        response.setSubmissionId(submission.getId());
        response.setSubmissionStatus(submissionStatus);
        response.setAssignmentStatus(assignmentStatus);
        response.setIdempotent(idempotent);
        return response;
    }

    private ReTaskAssignmentStatus resolveAssignmentStatus(ReTaskBranchAssignment assignment,
                                                            ReTaskSubmissionStatus submissionStatus) {
        if (submissionStatus == null) {
            return parseAssignmentStatus(assignment.getStatus());
        }
        return assignmentStatusForSubmission(submissionStatus, assignment.getStatus());
    }

    private ReTaskAssignmentStatus assignmentStatusForSubmission(ReTaskSubmissionStatus status,
                                                                  String fallback) {
        return switch (status) {
            case BRANCH_PENDING, BRANCH_APPROVED -> ReTaskAssignmentStatus.BRANCH_PENDING;
            case ORG_PENDING -> ReTaskAssignmentStatus.ORG_PENDING;
            case APPROVED -> ReTaskAssignmentStatus.APPROVED;
            case REJECTED_BY_BRANCH -> ReTaskAssignmentStatus.REJECTED_BY_BRANCH;
            case REJECTED_BY_ORG -> ReTaskAssignmentStatus.REJECTED_BY_ORG;
            case DRAFT -> parseAssignmentStatus(fallback);
        };
    }

    private ReTaskAssignmentStatus assignmentStatusFor(ReTaskSubmissionStatus status) {
        return assignmentStatusForSubmission(status, ReTaskAssignmentStatus.UNREPORTED.name());
    }

    private ReTaskAssignmentStatus parseAssignmentStatus(String value) {
        if (!hasText(value)) {
            return ReTaskAssignmentStatus.UNREPORTED;
        }
        try {
            return ReTaskAssignmentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ReTaskAssignmentStatus.UNREPORTED;
        }
    }

    private String actionCodeFor(ReTaskSubmissionStatus target, boolean branchAction) {
        return switch (target) {
            case BRANCH_APPROVED -> "APPROVE_BRANCH";
            case REJECTED_BY_BRANCH -> "REJECT_BRANCH";
            case ORG_PENDING -> "SUBMIT_TO_ORG";
            case APPROVED -> "APPROVE_ORG";
            case REJECTED_BY_ORG -> "REJECT_ORG";
            default -> branchAction ? "BRANCH_UPDATE" : "ORG_UPDATE";
        };
    }

    private String idempotencyActionCode(String clientRequestId) {
        String id = clientRequestId.trim();
        if (id.length() <= 40) {
            return "SUBMIT:" + id;
        }
        return "SUBMIT:" + shortHash(id);
    }

    private String shortHash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                builder.append(String.format(Locale.ROOT, "%02x", bytes[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private boolean matchesKeyword(ReTaskWorkflowAssignmentDTO dto, String keyword) {
        if (!hasText(keyword)) {
            return true;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return contains(dto.getTaskTitle(), normalized)
                || contains(dto.getTaskDescription(), normalized)
                || contains(dto.getBranchName(), normalized)
                || contains(dto.getSubmitterName(), normalized);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean natureMatches(ReTask task, ReTaskNature nature) {
        return nature == parseNature(task.getNature());
    }

    private boolean cycleMatches(ReTask task, ReTaskCycleType cycle) {
        return cycle == parseCycle(task.getCycle());
    }

    private boolean businessTypeMatches(ReTask task, ReTaskBusinessType type) {
        return type == parseBusinessType(task.getTypeCode());
    }

    /** 支部工作台可查当前提交及后续审核历史；无提交版本的未上报 assignment 不进入工作台。 */
    private boolean branchQueueStatus(ReTaskSubmissionStatus status) {
        return status == ReTaskSubmissionStatus.BRANCH_PENDING
                || status == ReTaskSubmissionStatus.BRANCH_APPROVED
                || status == ReTaskSubmissionStatus.ORG_PENDING
                || status == ReTaskSubmissionStatus.APPROVED
                || status == ReTaskSubmissionStatus.REJECTED_BY_BRANCH
                || status == ReTaskSubmissionStatus.REJECTED_BY_ORG;
    }

    /** 组织工作台只接收已进入组织链路或已形成终态的提交版本。 */
    private boolean orgQueueStatus(ReTaskSubmissionStatus status) {
        return status == ReTaskSubmissionStatus.ORG_PENDING
                || status == ReTaskSubmissionStatus.APPROVED
                || status == ReTaskSubmissionStatus.REJECTED_BY_BRANCH
                || status == ReTaskSubmissionStatus.REJECTED_BY_ORG;
    }

    private boolean isBranchQueue(String operatorId, Set<Long> branchIds) {
        return branchIds != null && !branchIds.isEmpty() && hasRole(BRANCH_SECRETARY_ROLE)
                && !isOrgReviewer();
    }

    private boolean isOrgQueue(String operatorId, Set<Long> branchIds) {
        return (branchIds == null || branchIds.isEmpty()) && isOrgReviewer()
                && !hasRole(BRANCH_SECRETARY_ROLE);
    }

    private PageResult<ReTaskWorkflowAssignmentDTO> emptyPage(ReTaskWorkflowPageQueryDTO query) {
        ReTaskWorkflowPageQueryDTO normalized = query == null ? new ReTaskWorkflowPageQueryDTO() : query;
        return PageResult.of(normalizePageNo(normalized.getPageNo()), normalizePageSize(normalized.getPageSize()),
                0L, List.of());
    }

    private int normalizePageNo(long pageNo) {
        return (int) Math.max(1, pageNo);
    }

    private int normalizePageSize(long pageSize) {
        return (int) Math.min(200, Math.max(1, pageSize));
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
            return ReTaskBusinessType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ReTaskCycleType parseCycle(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return ReTaskCycleType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isFourDimension(ReTask task) {
        return task != null && "FOUR_DIMENSION".equalsIgnoreCase(task.getTypeCode());
    }

    private String requiredOpinion(String opinion) {
        String normalized = normalizeOpinion(opinion);
        if (!hasText(normalized)) {
            throw new BizException("RE-40030", "驳回意见不能为空");
        }
        return normalized;
    }

    private String normalizeOpinion(String opinion) {
        return trimToNull(opinion);
    }

    private String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String extension(String fileName) {
        if (!hasText(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : normalizeExtension(fileName.substring(dot));
    }

    private String normalizeExtension(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }

    private record ReTaskContext(ReTask task, ReTaskInstance instance,
                                 ReTaskBranchAssignment assignment) {
    }

    private record ReTaskFileSnapshot(String fileId, String fileName, Long fileSize,
                                      String fileType, ReTaskFileType fileTypeConfig) {
    }
}
