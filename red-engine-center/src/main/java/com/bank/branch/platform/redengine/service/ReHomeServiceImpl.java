package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.ReHomeBranchRankingDTO;
import com.bank.branch.platform.redengine.api.dto.ReHomeSummaryDTO;
import com.bank.branch.platform.redengine.api.dto.ReHomeTaskItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReHomeTodoItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReOverduePageQueryDTO;
import com.bank.branch.platform.redengine.api.dto.ReQuarterWarningDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskDeductionActionRespDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskDeductionExecuteReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskDeductionStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskOverdueItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskTodoStatus;
import com.bank.branch.platform.redengine.api.dto.ReWarningPoolDTO;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReScore;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskDeduction;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskStatusHistory;
import com.bank.branch.platform.redengine.entity.ReTaskSubmission;
import com.bank.branch.platform.redengine.entity.ReTaskTodo;
import com.bank.branch.platform.redengine.entity.ReUserPartyMap;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReScoreMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskDeductionMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskStatusHistoryMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTodoMapper;
import com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
 * 首页与预警领域实现。
 *
 * <p>本服务不依赖旧年度归档或旧驾驶舱的全局统计。季度排名直接以当前自然季度的
 * {@code RE_SCORE} 聚合，并把全部支部补齐为零分，从而在部分支部没有数据时仍能给出稳定的
 * dense rank。逾期任务则以任务实例窗口为唯一截止事实，不再读取旧 {@code RE_SUBMIT} 的日期。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReHomeServiceImpl implements ReHomeService {

    static final String SYSTEM_ADMIN_ROLE = "SYS_ADMIN";
    static final String ORG_REVIEWER_ROLE = "R_RE_ORGREV";
    static final String REPORTER_ROLE = "R_RE_REPORT";
    static final String BRANCH_SECRETARY_ROLE = "R_RE_SECR";
    static final String REPORTER_TODO_ROLE = "REPORTER";
    static final String ORG_REVIEWER_TODO_ROLE = "ORG_REVIEWER";
    static final String OVERDUE_ACTION = "OVERDUE_DEDUCTION";
    static final BigDecimal DEFAULT_DEDUCTION_POINTS = new BigDecimal("5");
    static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CurrentUserApi currentUserApi;
    private final UserApi userApi;
    private final RePartyOrgMapper partyOrgMapper;
    private final ReScoreMapper scoreMapper;
    private final ReTaskDeductionMapper deductionMapper;
    private final ReTaskMapper taskMapper;
    private final ReTaskInstanceMapper instanceMapper;
    private final ReTaskBranchAssignmentMapper assignmentMapper;
    private final ReTaskSubmissionMapper submissionMapper;
    private final ReTaskTodoMapper todoMapper;
    private final ReTaskStatusHistoryMapper historyMapper;
    private final ReUserPartyMapMapper userPartyMapMapper;

    /**
     * 按当前登录人的角色返回首页汇总。
     *
     * @param operatorId 当前登录人平台用户 ID
     * @return 组织视角或所在支部视角首页数据
     */
    @Override
    @Transactional(readOnly = true)
    public ReHomeSummaryDTO getSummary(String operatorId) {
        ViewMode mode = resolveViewMode(operatorId);
        List<ReHomeBranchRankingDTO> rankings = getCurrentQuarterRanking();
        ReHomeSummaryDTO summary = new ReHomeSummaryDTO();
        summary.setMode(mode.name());
        summary.setQuarter(currentQuarter().key());
        summary.setBranchRankings(mode == ViewMode.ORGANIZATION ? rankings : List.of());
        summary.setBranchCount(rankings.size());

        if (mode == ViewMode.ORGANIZATION) {
            List<ReHomeTodoItemDTO> todo = loadTodoItems(operatorId, ORG_REVIEWER_TODO_ROLE,
                    ReTaskTodoStatus.PENDING.name());
            List<ReHomeTodoItemDTO> completed = loadTodoItems(operatorId, ORG_REVIEWER_TODO_ROLE,
                    ReTaskTodoStatus.COMPLETED.name());
            List<ReHomeTaskItemDTO> tasks = loadOrganizationTasks();
            summary.setTodoItems(todo);
            summary.setCompletedItems(completed);
            summary.setTodoCount(todo.size());
            summary.setCompletedCount(completed.size());
            summary.setOrganizationTasks(tasks);
            summary.setOrganizationTaskCount(tasks.size());
            return summary;
        }

        Long branchId = requireCurrentBranch(operatorId);
        RePartyOrg branch = partyOrgMapper.selectById(branchId);
        summary.setBranchId(branchId);
        summary.setBranchName(branch == null ? null : branch.getOrgName());
        ReHomeBranchRankingDTO branchRanking = rankings.stream()
                .filter(item -> branchId.equals(item.getBranchId()))
                .findFirst().orElse(null);
        if (branchRanking != null) {
            summary.setBranchScore(branchRanking.getScore());
            summary.setBranchRank(branchRanking.getRank());
        }

        boolean reporter = hasRole(REPORTER_ROLE);
        List<ReHomeTodoItemDTO> todo = reporter
                ? loadTodoItems(operatorId, REPORTER_TODO_ROLE, ReTaskTodoStatus.PENDING.name())
                : loadSecretaryTodoItems(branchId);
        List<ReHomeTodoItemDTO> completed = reporter
                ? loadTodoItems(operatorId, REPORTER_TODO_ROLE, ReTaskTodoStatus.COMPLETED.name())
                : List.of();
        summary.setTodoItems(todo);
        summary.setCompletedItems(completed);
        summary.setTodoCount(todo.size());
        summary.setCompletedCount(completed.size());
        return summary;
    }

    /**
     * 查询当前自然季度支部密集排名。
     *
     * @return 按得分降序、组织 ID 升序的排名
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReHomeBranchRankingDTO> getCurrentQuarterRanking() {
        Quarter current = currentQuarter();
        return calculateRanking(current, loadBranches(), loadScores(), loadDeductions());
    }

    /**
     * 查询当前用户可见的季度排名，防止支部角色通过独立排名入口读取其他支部得分。
     *
     * @param operatorId 当前登录人平台用户 ID
     * @return 组织角色可见全量，支部角色仅可见所在支部
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReHomeBranchRankingDTO> getCurrentQuarterRanking(String operatorId) {
        ViewMode mode = resolveViewMode(operatorId);
        List<ReHomeBranchRankingDTO> ranking = getCurrentQuarterRanking();
        if (mode == ViewMode.ORGANIZATION) {
            return ranking;
        }
        Long branchId = requireCurrentBranch(operatorId);
        return ranking.stream().filter(item -> branchId.equals(item.getBranchId())).toList();
    }

    /**
     * 查询连续当前季度与上一已完成季度的红黄牌预警。
     *
     * @return 红牌优先且不重复的预警池
     */
    @Override
    @Transactional(readOnly = true)
    public ReWarningPoolDTO getWarningPool() {
        Quarter current = currentQuarter();
        Quarter previous = current.previous();
        List<RePartyOrg> branches = loadBranches();
        List<ReScore> scores = loadScores();
        List<ReTaskDeduction> deductions = loadDeductions();
        if (!hasScoreData(scores, current) || !hasScoreData(scores, previous)) {
            ReWarningPoolDTO empty = new ReWarningPoolDTO();
            empty.setQuarter(current.key());
            empty.setPreviousQuarter(previous.key());
            return empty;
        }

        List<ReHomeBranchRankingDTO> currentRanking = calculateRanking(current, branches, scores, deductions);
        List<ReHomeBranchRankingDTO> previousRanking = calculateRanking(previous, branches, scores, deductions);
        Map<Long, ReHomeBranchRankingDTO> currentByBranch = byBranchId(currentRanking);
        Map<Long, ReHomeBranchRankingDTO> previousByBranch = byBranchId(previousRanking);
        int currentLastRank = currentRanking.stream().mapToInt(ReHomeBranchRankingDTO::getRank).max().orElse(0);
        int previousLastRank = previousRanking.stream().mapToInt(ReHomeBranchRankingDTO::getRank).max().orElse(0);

        Set<Long> redIds = new LinkedHashSet<>();
        List<ReQuarterWarningDTO> red = new ArrayList<>();
        for (Long branchId : orderedBranchIds(currentRanking)) {
            ReHomeBranchRankingDTO now = currentByBranch.get(branchId);
            ReHomeBranchRankingDTO before = previousByBranch.get(branchId);
            if (before == null || now.getRank() != currentLastRank || before.getRank() != previousLastRank) {
                continue;
            }
            redIds.add(branchId);
            red.add(toWarning(now, before, "red", current, previous));
        }

        List<ReQuarterWarningDTO> yellow = new ArrayList<>();
        for (Long branchId : orderedBranchIds(currentRanking)) {
            if (redIds.contains(branchId)) {
                // 红牌优先：同一支部不能同时出现在两个板块。
                continue;
            }
            ReHomeBranchRankingDTO now = currentByBranch.get(branchId);
            ReHomeBranchRankingDTO before = previousByBranch.get(branchId);
            if (before == null || now.getRank() < Math.max(1, currentLastRank - 4)
                    || before.getRank() < Math.max(1, previousLastRank - 4)) {
                continue;
            }
            yellow.add(toWarning(now, before, "yellow", current, previous));
        }

        ReWarningPoolDTO pool = new ReWarningPoolDTO();
        pool.setQuarter(current.key());
        pool.setPreviousQuarter(previous.key());
        pool.setRedBranches(red);
        pool.setYellowBranches(yellow);
        return pool;
    }

    /**
     * 分页查询已到截止时间、尚未执行扣分的任务分配。
     *
     * @param query      查询条件
     * @param operatorId 当前登录人平台用户 ID
     * @return 逾期待执行扣分页
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ReTaskOverdueItemDTO> pageOverdue(ReOverduePageQueryDTO query, String operatorId) {
        requireOrganizationAdministrator(operatorId);
        ReOverduePageQueryDTO normalized = query == null ? new ReOverduePageQueryDTO() : query;
        int pageNo = normalizePageNo(normalized.getPageNo());
        int pageSize = normalizePageSize(normalized.getPageSize());
        LocalDateTime now = now();

        List<ReTask> tasks = nullToEmpty(taskMapper.selectList(new LambdaQueryWrapper<ReTask>()));
        Map<Long, ReTask> tasksById = tasks.stream().filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ReTask::getId, Function.identity(), (first, ignored) -> first));
        List<ReTaskInstance> instances = nullToEmpty(instanceMapper.selectList(new LambdaQueryWrapper<ReTaskInstance>()));
        Map<Long, ReTaskInstance> instancesById = instances.stream().filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ReTaskInstance::getId, Function.identity(), (first, ignored) -> first));
        Set<Long> instanceIds = instances.stream().map(ReTaskInstance::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (instanceIds.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, List.of());
        }
        List<ReTaskBranchAssignment> assignments = nullToEmpty(assignmentMapper.selectList(
                new LambdaQueryWrapper<ReTaskBranchAssignment>().in(ReTaskBranchAssignment::getTaskInstanceId, instanceIds)));
        if (assignments.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, List.of());
        }

        List<Long> assignmentIds = assignments.stream().map(ReTaskBranchAssignment::getId)
                .filter(Objects::nonNull).toList();
        Map<Long, ReTaskDeduction> deductionsByAssignment = deductionsByAssignment(assignmentIds);
        Map<Long, ReTaskSubmission> submissionsByAssignment = latestSubmissions(assignmentIds);
        Map<Long, RePartyOrg> branchById = loadBranchMap(assignments.stream()
                .map(ReTaskBranchAssignment::getBranchId).filter(Objects::nonNull).toList());
        String keyword = normalizeText(normalized.getKeyword());
        List<ReTaskOverdueItemDTO> rows = new ArrayList<>();
        for (ReTaskBranchAssignment assignment : assignments) {
            ReTaskInstance instance = instancesById.get(assignment.getTaskInstanceId());
            ReTask task = instance == null ? null : tasksById.get(instance.getTaskId());
            if (instance == null || task == null || task.getStatus() != ReTaskStatus.PUBLISHED
                    || instance.getWindowEndAt() == null
                    || !instance.getWindowEndAt().isBefore(now)) {
                continue;
            }
            ReTaskDeduction deduction = deductionsByAssignment.get(assignment.getId());
            if (deduction != null && (deduction.getStatus() == ReTaskDeductionStatus.EXECUTED
                    || deduction.getStatus() == ReTaskDeductionStatus.CANCELLED)) {
                continue;
            }
            RePartyOrg branch = branchById.get(assignment.getBranchId());
            ReTaskSubmission submission = submissionsByAssignment.get(assignment.getId());
            // 只有未上报或截止后补报才是逾期候选；已在截止时间前提交的记录不能被误扣分。
            // 若扫描器已经创建 PENDING 记录，则即使之后补报也必须保留，直到管理员执行或取消。
            if (deduction == null && submission != null && submission.getSubmittedAt() != null
                    && !submission.getSubmittedAt().isAfter(instance.getWindowEndAt())) {
                continue;
            }
            ReTaskOverdueItemDTO row = toOverdueRow(task, instance, assignment, submission, deduction, branch);
            if (!matchesOverdueQuery(row, normalized, keyword)) {
                continue;
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing(ReTaskOverdueItemDTO::getTaskEndAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ReTaskOverdueItemDTO::getAssignmentId,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        int from = Math.min((pageNo - 1) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return PageResult.of(pageNo, pageSize, rows.size(), rows.subList(from, to));
    }

    /**
     * 执行一条任务分配的逾期扣分，按 assignment 唯一幂等。
     *
     * @param request    扣分请求
     * @param operatorId 当前登录人平台用户 ID
     * @return 扣分结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReTaskDeductionActionRespDTO executeOverdue(ReTaskDeductionExecuteReqDTO request, String operatorId) {
        requireOrganizationAdministrator(operatorId);
        if (request == null || request.getAssignmentId() == null) {
            throw new BizException("RE-40030", "任务分配不能为空");
        }
        String reason = normalizeText(request.getReason());
        if (reason == null) {
            throw new BizException("RE-40031", "扣分原因不能为空");
        }
        if (reason.length() > 255) {
            throw new BizException("RE-40031", "扣分原因不能超过255个字符");
        }
        BigDecimal points = request.getDeductionPoints() == null
                ? DEFAULT_DEDUCTION_POINTS : request.getDeductionPoints();
        if (points.compareTo(BigDecimal.ZERO) <= 0 || points.scale() > 2) {
            throw new BizException("RE-40032", "扣分分值必须大于0且最多保留两位小数");
        }

        Long assignmentId = request.getAssignmentId();
        ReTaskBranchAssignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null || assignment.getTaskInstanceId() == null || assignment.getBranchId() == null) {
            throw new BizException("RE-40033", "任务分配不存在");
        }
        ReTaskInstance instance = instanceMapper.selectById(assignment.getTaskInstanceId());
        ReTask task = instance == null ? null : taskMapper.selectById(instance.getTaskId());
        RePartyOrg branch = partyOrgMapper.selectById(assignment.getBranchId());
        if (instance == null || task == null || !isBranch(branch)) {
            throw new BizException("RE-40034", "任务分配数据不完整");
        }
        if (task.getStatus() != ReTaskStatus.PUBLISHED) {
            throw new BizException("RE-40303", "任务未发布，不能执行逾期扣分");
        }
        if (instance.getWindowEndAt() == null || !instance.getWindowEndAt().isBefore(now())) {
            throw new BizException("RE-40035", "任务尚未超过截止时间");
        }

        ReTaskDeduction existing = deductionMapper.selectOne(new LambdaQueryWrapper<ReTaskDeduction>()
                .eq(ReTaskDeduction::getAssignmentId, assignmentId));
        if (existing != null && existing.getStatus() == ReTaskDeductionStatus.EXECUTED) {
            return toDeductionResponse(existing, true);
        }
        if (existing != null && existing.getStatus() == ReTaskDeductionStatus.CANCELLED) {
            throw new BizException("RE-40903", "该任务分配的扣分已取消，不能重复执行");
        }
        // 执行接口不能只依赖列表查询：直接构造请求时仍需确认当前版本没有在截止前完成上报。
        // 已由扫描器建立的 PENDING 记录必须保留，后续补报不能绕过既定扣分决定。
        if (existing == null) {
            ReTaskSubmission submission = latestSubmissions(List.of(assignmentId)).get(assignmentId);
            if (submission != null && submission.getSubmittedAt() != null
                    && !submission.getSubmittedAt().isAfter(instance.getWindowEndAt())) {
                throw new BizException("RE-40036", "任务已在截止时间前上报，不能执行逾期扣分");
            }
        }

        LocalDateTime now = now();
        ReTaskDeduction saved;
        boolean created = existing == null;
        if (created) {
            saved = new ReTaskDeduction();
            saved.setTaskId(task.getId());
            saved.setTaskInstanceId(instance.getId());
            saved.setAssignmentId(assignmentId);
            saved.setBranchId(branch.getId());
            saved.setDeductionPoints(points);
            saved.setDeductionReason(reason);
            saved.setStatus(ReTaskDeductionStatus.EXECUTED);
            saved.setExecutedBy(operatorId);
            saved.setExecutedAt(now);
            saved.setCreateTime(now);
            saved.setUpdateTime(now);
            try {
                deductionMapper.insert(saved);
            } catch (DuplicateKeyException duplicate) {
                ReTaskDeduction winner = findDeduction(assignmentId);
                if (winner == null) {
                    throw duplicate;
                }
                if (winner.getStatus() == ReTaskDeductionStatus.EXECUTED) {
                    return toDeductionResponse(winner, true);
                }
                throw new BizException("RE-40901", "任务扣分正在被其他请求处理，请刷新后重试");
            }
        } else {
            saved = existing;
            LambdaUpdateWrapper<ReTaskDeduction> update = new LambdaUpdateWrapper<ReTaskDeduction>()
                    .eq(ReTaskDeduction::getId, existing.getId())
                    .eq(ReTaskDeduction::getStatus, ReTaskDeductionStatus.PENDING)
                    .set(ReTaskDeduction::getDeductionPoints, points)
                    .set(ReTaskDeduction::getDeductionReason, reason)
                    .set(ReTaskDeduction::getStatus, ReTaskDeductionStatus.EXECUTED)
                    .set(ReTaskDeduction::getExecutedBy, operatorId)
                    .set(ReTaskDeduction::getExecutedAt, now)
                    .set(ReTaskDeduction::getUpdateTime, now);
            if (deductionMapper.update(null, update) != 1) {
                ReTaskDeduction winner = findDeduction(assignmentId);
                if (winner != null && winner.getStatus() == ReTaskDeductionStatus.EXECUTED) {
                    return toDeductionResponse(winner, true);
                }
                throw new BizException("RE-40901", "任务扣分正在被其他请求处理，请刷新后重试");
            }
            saved.setDeductionPoints(points);
            saved.setDeductionReason(reason);
            saved.setStatus(ReTaskDeductionStatus.EXECUTED);
            saved.setExecutedBy(operatorId);
            saved.setExecutedAt(now);
        }

        ReTaskStatusHistory history = new ReTaskStatusHistory();
        history.setTaskId(task.getId());
        history.setTaskInstanceId(instance.getId());
        history.setAssignmentId(assignmentId);
        history.setActionCode(OVERDUE_ACTION);
        history.setFromStatus(assignment.getStatus());
        history.setToStatus(assignment.getStatus());
        history.setOpinion(reason);
        history.setOperatorId(operatorId);
        history.setOccurredAt(now);
        history.setCreateTime(now);
        historyMapper.insert(history);
        log.info("[ReHomeService.executeOverdue] taskId={}, assignmentId={}, branchId={}, points={}, operatorId={}",
                task.getId(), assignmentId, branch.getId(), points, operatorId);
        return toDeductionResponse(saved, false);
    }

    /** 加载全部有效支部；空结果按空列表处理，不能让排名接口中断。 */
    private List<RePartyOrg> loadBranches() {
        return nullToEmpty(partyOrgMapper.selectList(new LambdaQueryWrapper<RePartyOrg>()
                .eq(RePartyOrg::getOrgLevel, 2)
                .orderByAsc(RePartyOrg::getId)));
    }

    /** 读取当前领域的评分记录，季度归属由服务层解析 scorePeriod。 */
    private List<ReScore> loadScores() {
        return nullToEmpty(scoreMapper.selectList(new LambdaQueryWrapper<ReScore>()));
    }

    /** 读取任务扣分记录；只有 EXECUTED 记录参与得分扣减。 */
    private List<ReTaskDeduction> loadDeductions() {
        return nullToEmpty(deductionMapper.selectList(new LambdaQueryWrapper<ReTaskDeduction>()
                .eq(ReTaskDeduction::getStatus, ReTaskDeductionStatus.EXECUTED)));
    }

    /** 按指定季度聚合支部得分并生成 dense rank。 */
    private List<ReHomeBranchRankingDTO> calculateRanking(Quarter quarter,
                                                           List<RePartyOrg> branches,
                                                           List<ReScore> scores,
                                                           List<ReTaskDeduction> deductions) {
        Map<Long, String> branchNames = new LinkedHashMap<>();
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        for (RePartyOrg branch : nullToEmpty(branches)) {
            if (branch != null && branch.getId() != null) {
                branchNames.put(branch.getId(), branch.getOrgName());
                totals.put(branch.getId(), BigDecimal.ZERO);
            }
        }
        for (ReScore score : nullToEmpty(scores)) {
            if (score == null || score.getOrgId() == null || !totals.containsKey(score.getOrgId())
                    || !quarter.matches(score.getScorePeriod())) {
                continue;
            }
            BigDecimal value = score.getFinalScore() == null ? BigDecimal.ZERO : score.getFinalScore();
            totals.computeIfPresent(score.getOrgId(), (ignored, current) -> current.add(value));
        }

        // 已执行的任务扣分归属于支部，并只影响扣分执行所在自然季度。
        for (ReTaskDeduction deduction : nullToEmpty(deductions)) {
            if (deduction == null || deduction.getBranchId() == null
                    || deduction.getDeductionPoints() == null || !totals.containsKey(deduction.getBranchId())
                    || !quarter.matches(deduction.getExecutedAt())) {
                continue;
            }
            totals.computeIfPresent(deduction.getBranchId(), (ignored, current) -> current
                    .subtract(deduction.getDeductionPoints()).max(BigDecimal.ZERO));
        }

        List<ReHomeBranchRankingDTO> rows = totals.entrySet().stream()
                .map(entry -> {
                    ReHomeBranchRankingDTO dto = new ReHomeBranchRankingDTO();
                    dto.setBranchId(entry.getKey());
                    dto.setBranchName(branchNames.get(entry.getKey()));
                    dto.setScore(entry.getValue().max(BigDecimal.ZERO));
                    dto.setQuarter(quarter.key());
                    return dto;
                })
                .sorted(Comparator.comparing(ReHomeBranchRankingDTO::getScore,
                                Comparator.nullsFirst(Comparator.reverseOrder()))
                        .thenComparing(ReHomeBranchRankingDTO::getBranchId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        BigDecimal previousScore = null;
        int denseRank = 0;
        for (ReHomeBranchRankingDTO row : rows) {
            if (previousScore == null || row.getScore().compareTo(previousScore) != 0) {
                denseRank++;
                previousScore = row.getScore();
            }
            row.setRank(denseRank);
        }
        return rows;
    }

    /** 只有两个季度都有真实评分时才产生红黄牌，避免空数据把全部支部误判为预警。 */
    private boolean hasScoreData(List<ReScore> scores, Quarter quarter) {
        return nullToEmpty(scores).stream().anyMatch(score -> score != null
                && score.getFinalScore() != null && quarter.matches(score.getScorePeriod()));
    }

    private ReQuarterWarningDTO toWarning(ReHomeBranchRankingDTO current,
                                          ReHomeBranchRankingDTO previous,
                                          String level,
                                          Quarter currentQuarter,
                                          Quarter previousQuarter) {
        ReQuarterWarningDTO dto = new ReQuarterWarningDTO();
        dto.setBranchId(current.getBranchId());
        dto.setBranchName(current.getBranchName());
        dto.setLevel(level);
        dto.setScore(current.getScore());
        dto.setPreviousScore(previous.getScore());
        dto.setRank(current.getRank());
        dto.setPreviousRank(previous.getRank());
        dto.setQuarter(currentQuarter.key());
        dto.setPreviousQuarter(previousQuarter.key());
        return dto;
    }

    /** 生成组织角色首页的任务列表。 */
    private List<ReHomeTaskItemDTO> loadOrganizationTasks() {
        List<ReTask> tasks = nullToEmpty(taskMapper.selectList(new LambdaQueryWrapper<ReTask>()
                .eq(ReTask::getStatus, ReTaskStatus.PUBLISHED)
                .orderByDesc(ReTask::getPublishedAt).orderByDesc(ReTask::getId)));
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<ReTaskInstance> instances = nullToEmpty(instanceMapper.selectList(new LambdaQueryWrapper<ReTaskInstance>()));
        Map<Long, ReTaskInstance> currentInstanceByTask = instances.stream()
                .filter(item -> item.getTaskId() != null && currentQuarter().matches(item.getPeriodKey()))
                .collect(Collectors.toMap(ReTaskInstance::getTaskId, Function.identity(),
                        (first, second) -> laterInstance(first, second)));
        return tasks.stream().map(task -> {
            ReHomeTaskItemDTO dto = new ReHomeTaskItemDTO();
            dto.setTaskId(task.getId());
            dto.setTaskNo(task.getTaskNo());
            dto.setTitle(task.getTitle());
            dto.setDescription(task.getDescription());
            dto.setTaskType(task.getTypeCode());
            dto.setTaskNature(task.getNature());
            dto.setCycle(task.getCycle());
            dto.setStatus(task.getStatus() == null ? null : task.getStatus().name());
            dto.setPublishedAt(task.getPublishedAt());
            ReTaskInstance instance = currentInstanceByTask.get(task.getId());
            if (instance != null) {
                dto.setCurrentWindowStartAt(instance.getWindowStartAt());
                dto.setCurrentWindowEndAt(instance.getWindowEndAt());
            }
            return dto;
        }).toList();
    }

    /** 读取指定员工的待办/已办，并批量从本域任务表补齐展示字段。 */
    private List<ReHomeTodoItemDTO> loadTodoItems(String operatorId, String roleCode, String status) {
        LocalDateTime now = now();
        List<ReTaskTodo> todos = nullToEmpty(todoMapper.selectList(new LambdaQueryWrapper<ReTaskTodo>()
                .eq(ReTaskTodo::getEmployeeId, operatorId)
                .eq(ReTaskTodo::getRoleCode, roleCode)
                .eq(ReTaskTodo::getStatus, status)
                .le(ReTaskTodo::getAvailableAt, now)
                .orderByAsc(ReTaskTodo::getAvailableAt).orderByAsc(ReTaskTodo::getId)));
        return toTodoItems(todos.stream()
                .filter(todo -> isTodoAvailable(todo, now))
                .toList());
    }

    /** 支部书记的待处理任务由支部 assignment 状态派生，不依赖未生成的秘书待办行。 */
    private List<ReHomeTodoItemDTO> loadSecretaryTodoItems(Long branchId) {
        List<ReTaskBranchAssignment> assignments = nullToEmpty(assignmentMapper.selectList(
                new LambdaQueryWrapper<ReTaskBranchAssignment>()
                        .eq(ReTaskBranchAssignment::getBranchId, branchId)
                        .eq(ReTaskBranchAssignment::getStatus, ReTaskAssignmentStatus.BRANCH_PENDING.name())
                        .orderByAsc(ReTaskBranchAssignment::getLastSubmittedAt)
                        .orderByAsc(ReTaskBranchAssignment::getId)));
        return assignments.stream().map(this::toTodoItem).filter(Objects::nonNull).toList();
    }

    private List<ReHomeTodoItemDTO> toTodoItems(List<ReTaskTodo> todos) {
        return nullToEmpty(todos).stream().map(this::toTodoItem).filter(Objects::nonNull).toList();
    }

    private ReHomeTodoItemDTO toTodoItem(ReTaskTodo todo) {
        if (todo == null || todo.getAssignmentId() == null) {
            return null;
        }
        ReTaskBranchAssignment assignment = assignmentMapper.selectById(todo.getAssignmentId());
        return toTodoItem(assignment, todo.getStatus(), todo.getCompletedAt());
    }

    private ReHomeTodoItemDTO toTodoItem(ReTaskBranchAssignment assignment) {
        return toTodoItem(assignment, ReTaskTodoStatus.PENDING.name(), null);
    }

    private ReHomeTodoItemDTO toTodoItem(ReTaskBranchAssignment assignment, String status,
                                         LocalDateTime completedAt) {
        if (assignment == null || assignment.getTaskInstanceId() == null) {
            return null;
        }
        ReTaskInstance instance = instanceMapper.selectById(assignment.getTaskInstanceId());
        if (instance == null || instance.getTaskId() == null) {
            return null;
        }
        if (instance.getWindowStartAt() != null && now().isBefore(instance.getWindowStartAt())) {
            return null;
        }
        ReTask task = taskMapper.selectById(instance.getTaskId());
        if (task == null) {
            return null;
        }
        ReHomeTodoItemDTO dto = new ReHomeTodoItemDTO();
        dto.setTaskId(task.getId());
        dto.setTaskInstanceId(instance.getId());
        dto.setAssignmentId(assignment.getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setTaskType(task.getTypeCode());
        dto.setTaskNature(task.getNature());
        dto.setCycle(task.getCycle());
        dto.setStatus(status);
        dto.setWindowStartAt(instance.getWindowStartAt());
        dto.setWindowEndAt(instance.getWindowEndAt());
        dto.setCompletedAt(completedAt);
        return dto;
    }

    private ReTaskOverdueItemDTO toOverdueRow(ReTask task, ReTaskInstance instance,
                                              ReTaskBranchAssignment assignment,
                                              ReTaskSubmission submission,
                                              ReTaskDeduction deduction,
                                              RePartyOrg branch) {
        ReTaskOverdueItemDTO row = new ReTaskOverdueItemDTO();
        row.setTaskId(task.getId());
        row.setTaskInstanceId(instance.getId());
        row.setAssignmentId(assignment.getId());
        row.setTaskType(task.getTypeCode());
        row.setTaskName(task.getTitle());
        row.setTaskContent(task.getDescription());
        row.setBranchId(assignment.getBranchId());
        row.setBranchName(branch == null ? null : branch.getOrgName());
        row.setTaskStartAt(instance.getWindowStartAt());
        row.setTaskEndAt(instance.getWindowEndAt());
        row.setIsUnreported(submission == null);
        if (submission != null) {
            row.setSubmitterId(submission.getSubmitterId());
            if (userApi != null && hasText(submission.getSubmitterId())) {
                row.setSubmitterName(userApi.getUserName(submission.getSubmitterId()));
            }
            row.setSubmittedAt(submission.getSubmittedAt());
        }
        if (deduction != null) {
            row.setDeductionPoints(deduction.getDeductionPoints());
            row.setDeductionStatus(deduction.getStatus());
        } else {
            row.setDeductionStatus(ReTaskDeductionStatus.PENDING);
        }
        return row;
    }

    private boolean matchesOverdueQuery(ReTaskOverdueItemDTO row, ReOverduePageQueryDTO query, String keyword) {
        if (query.getBranchId() != null && !query.getBranchId().equals(row.getBranchId())) {
            return false;
        }
        if (hasText(query.getTaskType()) && !query.getTaskType().trim().equalsIgnoreCase(row.getTaskType())) {
            return false;
        }
        LocalDateTime startAt = query.getStartAt() != null ? query.getStartAt() : query.getTaskStartAt();
        LocalDateTime endAt = query.getEndAt() != null ? query.getEndAt() : query.getTaskEndAt();
        if (startAt != null && (row.getTaskEndAt() == null || row.getTaskEndAt().isBefore(startAt))) {
            return false;
        }
        if (endAt != null && (row.getTaskEndAt() == null || row.getTaskEndAt().isAfter(endAt))) {
            return false;
        }
        if (hasText(query.getTaskName()) && !contains(row.getTaskName(), query.getTaskName().trim())) {
            return false;
        }
        if (hasText(query.getBranchName()) && !contains(row.getBranchName(), query.getBranchName().trim())) {
            return false;
        }
        if (hasText(query.getSubmitterName())
                && !contains(row.getSubmitterName(), query.getSubmitterName().trim())
                && !contains(row.getSubmitterId(), query.getSubmitterName().trim())) {
            return false;
        }
        if (keyword == null) {
            return true;
        }
        return contains(row.getTaskName(), keyword) || contains(row.getTaskContent(), keyword)
                || contains(row.getBranchName(), keyword) || contains(row.getSubmitterId(), keyword)
                || contains(row.getSubmitterName(), keyword);
    }

    private Map<Long, ReTaskDeduction> deductionsByAssignment(Collection<Long> assignmentIds) {
        if (assignmentIds == null || assignmentIds.isEmpty()) {
            return Map.of();
        }
        List<ReTaskDeduction> deductions = nullToEmpty(deductionMapper.selectList(
                new LambdaQueryWrapper<ReTaskDeduction>().in(ReTaskDeduction::getAssignmentId, assignmentIds)));
        return deductions.stream().filter(item -> item.getAssignmentId() != null)
                .collect(Collectors.toMap(ReTaskDeduction::getAssignmentId, Function.identity(),
                        (first, second) -> laterDeduction(first, second)));
    }

    private Map<Long, ReTaskSubmission> latestSubmissions(Collection<Long> assignmentIds) {
        if (assignmentIds == null || assignmentIds.isEmpty()) {
            return Map.of();
        }
        List<ReTaskSubmission> submissions = nullToEmpty(submissionMapper.selectList(
                new LambdaQueryWrapper<ReTaskSubmission>().in(ReTaskSubmission::getAssignmentId, assignmentIds)));
        return submissions.stream().filter(item -> item.getAssignmentId() != null)
                .collect(Collectors.toMap(ReTaskSubmission::getAssignmentId, Function.identity(),
                        (first, second) -> newerSubmission(first, second)));
    }

    private Map<Long, RePartyOrg> loadBranchMap(Collection<Long> branchIds) {
        Map<Long, RePartyOrg> result = new HashMap<>();
        if (branchIds == null) {
            return result;
        }
        for (Long branchId : new LinkedHashSet<>(branchIds)) {
            if (branchId == null) {
                continue;
            }
            RePartyOrg branch = partyOrgMapper.selectById(branchId);
            if (branch != null) {
                result.put(branchId, branch);
            }
        }
        return result;
    }

    private ReTaskDeduction findDeduction(Long assignmentId) {
        return deductionMapper.selectOne(new LambdaQueryWrapper<ReTaskDeduction>()
                .eq(ReTaskDeduction::getAssignmentId, assignmentId));
    }

    private ReTaskDeductionActionRespDTO toDeductionResponse(ReTaskDeduction deduction, boolean idempotent) {
        ReTaskDeductionActionRespDTO dto = new ReTaskDeductionActionRespDTO();
        dto.setDeductionId(deduction.getId());
        dto.setAssignmentId(deduction.getAssignmentId());
        dto.setTaskId(deduction.getTaskId());
        dto.setTaskInstanceId(deduction.getTaskInstanceId());
        dto.setBranchId(deduction.getBranchId());
        dto.setDeductionPoints(deduction.getDeductionPoints());
        dto.setStatus(deduction.getStatus());
        dto.setIdempotent(idempotent);
        return dto;
    }

    private ViewMode resolveViewMode(String operatorId) {
        requireCurrentUser(operatorId);
        Set<String> roleCodes = currentUserApi.getCurrentRoleCodes();
        if (currentUserApi.isSystemAdmin() || hasRole(roleCodes, SYSTEM_ADMIN_ROLE)
                || hasRole(roleCodes, ORG_REVIEWER_ROLE)) {
            return ViewMode.ORGANIZATION;
        }
        if (hasRole(roleCodes, REPORTER_ROLE) || hasRole(roleCodes, BRANCH_SECRETARY_ROLE)) {
            return ViewMode.INSTITUTION;
        }
        throw new BizException("RE-40302", "无权访问红色引擎首页");
    }

    private void requireOrganizationAdministrator(String operatorId) {
        requireCurrentUser(operatorId);
        Set<String> roleCodes = currentUserApi.getCurrentRoleCodes();
        // 逾期扣分属于组织管理员专属的高风险写操作；组织审核员虽然可以查看组织首页，
        // 但不能进入扣分列表或执行扣分，避免把“首页可见”误扩展为数据修正权限。
        if (!currentUserApi.isSystemAdmin() && !hasRole(roleCodes, SYSTEM_ADMIN_ROLE)) {
            throw new BizException("RE-40302", "仅组织管理员可以执行任务逾期扣分");
        }
    }

    private void requireCurrentUser(String operatorId) {
        String current = currentUserApi.getCurrentEmpId();
        if (!hasText(operatorId) || !hasText(current) || !operatorId.trim().equals(current.trim())) {
            throw new BizException("RE-40301", "当前用户与操作人不匹配");
        }
    }

    private Long requireCurrentBranch(String operatorId) {
        ReUserPartyMap mapping = userPartyMapMapper.selectOne(new LambdaQueryWrapper<ReUserPartyMap>()
                .eq(ReUserPartyMap::getUserId, operatorId));
        if (mapping == null || mapping.getPartyOrgId() == null) {
            throw new BizException("RE-40001", "当前用户未绑定党组织");
        }
        Long branchId = resolveBranchId(mapping.getPartyOrgId());
        if (branchId == null) {
            throw new BizException("RE-40001", "当前用户未绑定党支部");
        }
        return branchId;
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

    private boolean hasRole(String roleCode) {
        return hasRole(currentUserApi.getCurrentRoleCodes(), roleCode);
    }

    private boolean hasRole(Set<String> roleCodes, String roleCode) {
        return roleCodes != null && roleCodes.stream().filter(Objects::nonNull)
                .map(String::trim).anyMatch(roleCode::equalsIgnoreCase);
    }

    private Quarter currentQuarter() {
        return Quarter.of(YearMonth.now(BEIJING_ZONE));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BEIJING_ZONE);
    }

    private static ReTaskInstance laterInstance(ReTaskInstance first, ReTaskInstance second) {
        LocalDateTime firstStart = first == null ? null : first.getWindowStartAt();
        LocalDateTime secondStart = second == null ? null : second.getWindowStartAt();
        if (firstStart == null) {
            return second;
        }
        if (secondStart == null) {
            return first;
        }
        return secondStart.isAfter(firstStart) ? second : first;
    }

    private static ReTaskDeduction laterDeduction(ReTaskDeduction first, ReTaskDeduction second) {
        LocalDateTime firstAt = first == null ? null : first.getExecutedAt();
        LocalDateTime secondAt = second == null ? null : second.getExecutedAt();
        if (firstAt == null) {
            return second;
        }
        return secondAt != null && secondAt.isAfter(firstAt) ? second : first;
    }

    private static ReTaskSubmission newerSubmission(ReTaskSubmission first, ReTaskSubmission second) {
        int firstVersion = first.getVersionNo() == null ? 0 : first.getVersionNo();
        int secondVersion = second.getVersionNo() == null ? 0 : second.getVersionNo();
        if (secondVersion != firstVersion) {
            return secondVersion > firstVersion ? second : first;
        }
        Long firstId = first.getId();
        Long secondId = second.getId();
        return secondId != null && (firstId == null || secondId > firstId) ? second : first;
    }

    private static Map<Long, ReHomeBranchRankingDTO> byBranchId(List<ReHomeBranchRankingDTO> rows) {
        return rows.stream().filter(row -> row.getBranchId() != null)
                .collect(Collectors.toMap(ReHomeBranchRankingDTO::getBranchId, Function.identity(),
                        (first, ignored) -> first, LinkedHashMap::new));
    }

    private static List<Long> orderedBranchIds(List<ReHomeBranchRankingDTO> rows) {
        return rows.stream().map(ReHomeBranchRankingDTO::getBranchId).filter(Objects::nonNull).toList();
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    /** 待办必须拥有明确的可用时间，并且只能在北京时区的开始时刻后展示。 */
    private static boolean isTodoAvailable(ReTaskTodo todo, LocalDateTime now) {
        return todo != null && todo.getAvailableAt() != null && !todo.getAvailableAt().isAfter(now);
    }

    private static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int normalizePageNo(int pageNo) {
        return pageNo < 1 ? 1 : pageNo;
    }

    private static int normalizePageSize(int pageSize) {
        return pageSize < 1 ? 20 : Math.min(pageSize, 200);
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private enum ViewMode {
        ORGANIZATION,
        INSTITUTION
    }

    /** 以自然季度为单位的不可变周期值对象。 */
    static final class Quarter {
        private final int year;
        private final int quarter;

        private Quarter(int year, int quarter) {
            this.year = year;
            this.quarter = quarter;
        }

        static Quarter of(YearMonth month) {
            return new Quarter(month.getYear(), ((month.getMonthValue() - 1) / 3) + 1);
        }

        Quarter previous() {
            return of(YearMonth.of(year, (quarter - 1) * 3 + 1).minusMonths(1));
        }

        String key() {
            return year + "-Q" + quarter;
        }

        boolean matches(String period) {
            if (!hasText(period)) {
                return false;
            }
            String normalized = period.trim().toUpperCase(Locale.ROOT);
            if (normalized.equals(key())) {
                return true;
            }
            try {
                return of(YearMonth.parse(normalized, MONTH_FORMAT)).equals(this);
            } catch (DateTimeParseException ignored) {
                return false;
            }
        }

        boolean matches(LocalDateTime dateTime) {
            return dateTime != null && of(YearMonth.from(dateTime.toLocalDate())).equals(this);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Quarter that)) {
                return false;
            }
            return year == that.year && quarter == that.quarter;
        }

        @Override
        public int hashCode() {
            return Objects.hash(year, quarter);
        }
    }
}
