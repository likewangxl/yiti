package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.redengine.api.dto.ReHomeBranchRankingDTO;
import com.bank.branch.platform.redengine.api.dto.ReHomeSummaryDTO;
import com.bank.branch.platform.redengine.api.dto.ReOverduePageQueryDTO;
import com.bank.branch.platform.redengine.api.dto.ReQuarterWarningDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskDeductionActionRespDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskDeductionExecuteReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskDeductionStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskOverdueItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskStatus;
import com.bank.branch.platform.redengine.api.dto.ReWarningPoolDTO;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReScore;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskDeduction;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 首页、季度排名、预警与任务逾期扣分服务的纯单元测试。 */
@ExtendWith(MockitoExtension.class)
class ReHomeServiceTest {

    @Mock
    private CurrentUserApi currentUserApi;
    @Mock
    private UserApi userApi;
    @Mock
    private RePartyOrgMapper partyOrgMapper;
    @Mock
    private ReScoreMapper scoreMapper;
    @Mock
    private ReTaskDeductionMapper deductionMapper;
    @Mock
    private ReTaskMapper taskMapper;
    @Mock
    private ReTaskInstanceMapper instanceMapper;
    @Mock
    private ReTaskBranchAssignmentMapper assignmentMapper;
    @Mock
    private ReTaskSubmissionMapper submissionMapper;
    @Mock
    private ReTaskTodoMapper todoMapper;
    @Mock
    private ReTaskStatusHistoryMapper historyMapper;
    @Mock
    private ReUserPartyMapMapper userPartyMapMapper;

    @InjectMocks
    private ReHomeServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RePartyOrg.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ReScore.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ReTaskDeduction.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ReTask.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ReTaskInstance.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ReTaskBranchAssignment.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ReTaskSubmission.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ReTaskTodo.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ReUserPartyMap.class);
    }

    @Test
    void currentQuarterRanking_includesNoDataBranches_denseRanks_andUsesOrgIdAsTieBreaker() {
        List<RePartyOrg> branches = List.of(branch(2L, "乙支部"), branch(1L, "甲支部"), branch(3L, "丙支部"));
        when(partyOrgMapper.selectList(any())).thenReturn(branches);
        when(scoreMapper.selectList(any())).thenReturn(List.of(
                score(1L, currentMonth(), "90"),
                score(2L, currentMonth(), "90"),
                score(1L, currentMonth().minusMonths(4), "99")
        ));
        when(deductionMapper.selectList(any())).thenReturn(List.of());

        List<ReHomeBranchRankingDTO> ranking = service.getCurrentQuarterRanking();

        assertThat(ranking).extracting(ReHomeBranchRankingDTO::getBranchId)
                .containsExactly(1L, 2L, 3L);
        assertThat(ranking).extracting(ReHomeBranchRankingDTO::getRank)
                .containsExactly(1, 1, 2);
        assertThat(ranking.get(2).getScore()).isEqualByComparingTo("0");
    }

    @Test
    void currentQuarterRanking_onlyAggregatesKnownBranchOrganizations() {
        when(partyOrgMapper.selectList(any())).thenReturn(List.of(branch(1L, "一支部")));
        when(scoreMapper.selectList(any())).thenReturn(List.of(
                score(99L, currentMonth(), "100"), score(1L, currentMonth(), "80")));
        when(deductionMapper.selectList(any())).thenReturn(List.of());

        List<ReHomeBranchRankingDTO> ranking = service.getCurrentQuarterRanking();

        assertThat(ranking).extracting(ReHomeBranchRankingDTO::getBranchId).containsExactly(1L);
        assertThat(ranking.get(0).getScore()).isEqualByComparingTo("80");
    }

    @Test
    void warningPool_requiresTwoQuarters_andRedBranchesAreNotRepeatedAsYellow() {
        List<RePartyOrg> branches = List.of(
                branch(1L, "一支部"), branch(2L, "二支部"), branch(3L, "三支部"),
                branch(4L, "四支部"), branch(5L, "五支部"), branch(6L, "六支部"));
        when(partyOrgMapper.selectList(any())).thenReturn(branches);
        YearMonth current = currentMonth();
        YearMonth previous = current.minusMonths(3);
        when(scoreMapper.selectList(any())).thenReturn(List.of(
                score(1L, current, "10"), score(2L, current, "20"), score(3L, current, "30"),
                score(4L, current, "40"), score(5L, current, "50"), score(6L, current, "60"),
                score(1L, previous, "11"), score(2L, previous, "21"), score(3L, previous, "31"),
                score(4L, previous, "41"), score(5L, previous, "51"), score(6L, previous, "61")
        ));
        when(deductionMapper.selectList(any())).thenReturn(List.of());

        ReWarningPoolDTO pool = service.getWarningPool();

        assertThat(pool.getRedBranches()).extracting(ReQuarterWarningDTO::getBranchId)
                .containsExactly(1L);
        assertThat(pool.getYellowBranches()).extracting(ReQuarterWarningDTO::getBranchId)
                .containsExactlyInAnyOrder(2L, 3L, 4L, 5L);
    }

    @Test
    void warningPool_withoutOneQuarterData_returnsEmpty_withoutBreaking() {
        when(partyOrgMapper.selectList(any())).thenReturn(List.of(branch(1L, "一支部")));
        when(scoreMapper.selectList(any())).thenReturn(List.of(score(1L, currentMonth(), "90")));
        when(deductionMapper.selectList(any())).thenReturn(List.of());

        ReWarningPoolDTO pool = service.getWarningPool();

        assertThat(pool.getRedBranches()).isEmpty();
        assertThat(pool.getYellowBranches()).isEmpty();
    }

    @Test
    void overduePage_includesCompletelyUnreportedAssignment_andUsesTaskDescriptionAsContent() {
        mockAdmin("admin-1");
        ReTask task = task(10L, "季度走访", "请填写走访情况");
        ReTaskInstance instance = instance(20L, 10L, LocalDateTime.now().minusDays(1));
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 1L, "UNREPORTED");
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(assignment));
        when(submissionMapper.selectList(any())).thenReturn(List.of());
        when(deductionMapper.selectList(any())).thenReturn(List.of());
        when(partyOrgMapper.selectById(1L)).thenReturn(branch(1L, "一支部"));

        var result = service.pageOverdue(new ReOverduePageQueryDTO(), "admin-1");

        assertThat(result.getTotal()).isEqualTo(1L);
        ReTaskOverdueItemDTO row = result.getRecords().get(0);
        assertThat(row.getAssignmentId()).isEqualTo(30L);
        assertThat(row.getIsUnreported()).isTrue();
        assertThat(row.getSubmitterId()).isNull();
        assertThat(row.getSubmittedAt()).isNull();
        assertThat(row.getTaskContent()).isEqualTo("请填写走访情况");
    }

    @Test
    void overduePage_honorsTaskNameFilter() {
        mockAdmin("admin-1");
        when(taskMapper.selectList(any())).thenReturn(List.of(task(10L, "季度走访", "任务内容")));
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance(20L, 10L,
                LocalDateTime.now().minusDays(1))));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(assignment(30L, 20L, 1L, "UNREPORTED")));
        when(submissionMapper.selectList(any())).thenReturn(List.of());
        when(deductionMapper.selectList(any())).thenReturn(List.of());
        when(partyOrgMapper.selectById(1L)).thenReturn(branch(1L, "一支部"));
        ReOverduePageQueryDTO query = new ReOverduePageQueryDTO();
        query.setTaskName("不存在");

        var result = service.pageOverdue(query, "admin-1");

        assertThat(result.getTotal()).isZero();
    }

    @Test
    void overduePage_resolvesSubmitterName_whenAssignmentWasSubmitted() {
        mockAdmin("admin-1");
        ReTask task = task(10L, "季度走访", "任务内容");
        ReTaskInstance instance = instance(20L, 10L, LocalDateTime.now().minusDays(1));
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 1L, "BRANCH_PENDING");
        ReTaskSubmission submission = new ReTaskSubmission();
        submission.setId(40L);
        submission.setAssignmentId(30L);
        submission.setSubmitterId("reporter-1");
        submission.setSubmittedAt(LocalDateTime.now());
        submission.setVersionNo(1);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(assignment));
        when(submissionMapper.selectList(any())).thenReturn(List.of(submission));
        when(deductionMapper.selectList(any())).thenReturn(List.of());
        when(partyOrgMapper.selectById(1L)).thenReturn(branch(1L, "一支部"));
        when(userApi.getUserName("reporter-1")).thenReturn("报送员一");

        var result = service.pageOverdue(new ReOverduePageQueryDTO(), "admin-1");

        assertThat(result.getRecords().get(0).getSubmitterName()).isEqualTo("报送员一");
    }

    @Test
    void overduePage_excludesSubmissionCompletedBeforeDeadline() {
        mockAdmin("admin-1");
        ReTask task = task(10L, "季度走访", "任务内容");
        LocalDateTime deadline = LocalDateTime.now().minusDays(1);
        ReTaskInstance instance = instance(20L, 10L, deadline);
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 1L, "APPROVED");
        ReTaskSubmission submission = new ReTaskSubmission();
        submission.setId(40L);
        submission.setAssignmentId(30L);
        submission.setSubmitterId("reporter-1");
        submission.setSubmittedAt(deadline.minusMinutes(1));
        submission.setVersionNo(1);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(assignment));
        when(submissionMapper.selectList(any())).thenReturn(List.of(submission));
        when(deductionMapper.selectList(any())).thenReturn(List.of());
        when(partyOrgMapper.selectById(1L)).thenReturn(branch(1L, "一支部"));

        var result = service.pageOverdue(new ReOverduePageQueryDTO(), "admin-1");

        assertThat(result.getTotal()).isZero();
    }

    @Test
    void overduePage_keepsPendingDeductionAfterLaterSubmission() {
        mockAdmin("admin-1");
        ReTask task = task(10L, "季度走访", "任务内容");
        LocalDateTime deadline = LocalDateTime.now().minusDays(1);
        ReTaskInstance instance = instance(20L, 10L, deadline);
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 1L, "APPROVED");
        ReTaskSubmission submission = new ReTaskSubmission();
        submission.setId(40L);
        submission.setAssignmentId(30L);
        submission.setSubmitterId("reporter-1");
        submission.setSubmittedAt(deadline.minusMinutes(1));
        submission.setVersionNo(1);
        ReTaskDeduction deduction = new ReTaskDeduction();
        deduction.setId(50L);
        deduction.setAssignmentId(30L);
        deduction.setStatus(ReTaskDeductionStatus.PENDING);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(assignment));
        when(submissionMapper.selectList(any())).thenReturn(List.of(submission));
        when(deductionMapper.selectList(any())).thenReturn(List.of(deduction));
        when(partyOrgMapper.selectById(1L)).thenReturn(branch(1L, "一支部"));

        var result = service.pageOverdue(new ReOverduePageQueryDTO(), "admin-1");

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords().get(0).getDeductionStatus()).isEqualTo(ReTaskDeductionStatus.PENDING);
    }

    @Test
    void executeOverdue_forUnreportedAssignment_insertsOneBranchOwnedExecutedDeduction() {
        mockAdmin("admin-1");
        ReTask task = task(10L, "季度走访", "任务内容");
        ReTaskInstance instance = instance(20L, 10L, LocalDateTime.now().minusDays(1));
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 8L, "UNREPORTED");
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(deductionMapper.selectOne(any())).thenReturn(null);
        when(partyOrgMapper.selectById(8L)).thenReturn(branch(8L, "八支部"));

        ReTaskDeductionExecuteReqDTO request = new ReTaskDeductionExecuteReqDTO();
        request.setAssignmentId(30L);
        request.setDeductionPoints(new BigDecimal("5"));
        request.setReason("逾期未上报");
        request.setClientRequestId("req-30");

        ReTaskDeductionActionRespDTO response = service.executeOverdue(request, "admin-1");

        assertThat(response.getAssignmentId()).isEqualTo(30L);
        assertThat(response.isIdempotent()).isFalse();
        ArgumentCaptor<ReTaskDeduction> captor = ArgumentCaptor.forClass(ReTaskDeduction.class);
        verify(deductionMapper).insert(captor.capture());
        assertThat(captor.getValue().getBranchId()).isEqualTo(8L);
        assertThat(captor.getValue().getDeductionPoints()).isEqualByComparingTo("5");
        assertThat(captor.getValue().getExecutedBy()).isEqualTo("admin-1");
        verify(historyMapper).insert(any(com.bank.branch.platform.redengine.entity.ReTaskStatusHistory.class));
    }

    @Test
    void executeOverdue_whenAlreadyExecuted_isIdempotent_andDoesNotInsertAgain() {
        mockAdmin("admin-1");
        ReTask task = task(10L, "季度走访", "任务内容");
        ReTaskInstance instance = instance(20L, 10L, LocalDateTime.now().minusDays(1));
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 8L, "BRANCH_PENDING");
        ReTaskDeduction existing = new ReTaskDeduction();
        existing.setId(99L);
        existing.setAssignmentId(30L);
        existing.setBranchId(8L);
        existing.setDeductionPoints(new BigDecimal("4"));
        existing.setStatus(com.bank.branch.platform.redengine.api.dto.ReTaskDeductionStatus.EXECUTED);
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(partyOrgMapper.selectById(8L)).thenReturn(branch(8L, "八支部"));
        when(deductionMapper.selectOne(any())).thenReturn(existing);

        ReTaskDeductionExecuteReqDTO request = new ReTaskDeductionExecuteReqDTO();
        request.setAssignmentId(30L);
        request.setDeductionPoints(new BigDecimal("5"));
        request.setReason("重复请求");

        ReTaskDeductionActionRespDTO response = service.executeOverdue(request, "admin-1");

        assertThat(response.isIdempotent()).isTrue();
        assertThat(response.getDeductionId()).isEqualTo(99L);
        verify(deductionMapper, never()).insert(any(ReTaskDeduction.class));
        verify(historyMapper, never()).insert(any(com.bank.branch.platform.redengine.entity.ReTaskStatusHistory.class));
    }

    @Test
    void overdueActions_forOrganizationReviewer_areRejectedAndDoNotReadTaskData() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("reviewer-1");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_ORGREV"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.pageOverdue(new ReOverduePageQueryDTO(), "reviewer-1"))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .hasMessageContaining("组织管理员");

        verify(taskMapper, never()).selectList(any());
        verify(instanceMapper, never()).selectList(any());
    }

    @Test
    void summary_forReporter_requiresPartyMapping_andReturnsBranchMetric() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("reporter-1");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_REPORT"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        ReUserPartyMap mapping = new ReUserPartyMap();
        mapping.setUserId("reporter-1");
        mapping.setPartyOrgId(7L);
        when(userPartyMapMapper.selectOne(any())).thenReturn(mapping);
        when(partyOrgMapper.selectById(7L)).thenReturn(branch(7L, "七支部"));
        when(partyOrgMapper.selectList(any())).thenReturn(List.of(branch(7L, "七支部")));
        when(scoreMapper.selectList(any())).thenReturn(List.of(score(7L, currentMonth(), "88")));
        when(deductionMapper.selectList(any())).thenReturn(List.of());
        when(todoMapper.selectList(any())).thenReturn(List.of());

        ReHomeSummaryDTO summary = service.getSummary("reporter-1");

        assertThat(summary.getMode()).isEqualTo("INSTITUTION");
        assertThat(summary.getBranchId()).isEqualTo(7L);
        assertThat(summary.getBranchScore()).isEqualByComparingTo("88");
        assertThat(summary.getBranchRank()).isEqualTo(1);
    }

    @Test
    void summary_unknownRole_failsClosed() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("user-1");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("UNKNOWN"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.getSummary("user-1"))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .hasMessageContaining("无权");
    }

    private void mockAdmin(String operatorId) {
        when(currentUserApi.getCurrentEmpId()).thenReturn(operatorId);
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("SYS_ADMIN"));
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
    }

    private static RePartyOrg branch(Long id, String name) {
        RePartyOrg org = new RePartyOrg();
        org.setId(id);
        org.setOrgName(name);
        org.setOrgLevel(2);
        return org;
    }

    private static ReScore score(Long orgId, YearMonth month, String value) {
        return score(orgId, month.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")), value);
    }

    private static ReScore score(Long orgId, String period, String value) {
        ReScore score = new ReScore();
        score.setOrgId(orgId);
        score.setScorePeriod(period);
        score.setFinalScore(new BigDecimal(value));
        return score;
    }

    private static YearMonth currentMonth() {
        return YearMonth.now(java.time.ZoneId.of("Asia/Shanghai"));
    }

    private static ReTask task(Long id, String title, String description) {
        ReTask task = new ReTask();
        task.setId(id);
        task.setTitle(title);
        task.setDescription(description);
        task.setTypeCode("TEMPORARY");
        task.setNature("TEMPORARY");
        task.setStatus(ReTaskStatus.PUBLISHED);
        task.setRequiresFile(0);
        return task;
    }

    private static ReTaskInstance instance(Long id, Long taskId, LocalDateTime endAt) {
        ReTaskInstance instance = new ReTaskInstance();
        instance.setId(id);
        instance.setTaskId(taskId);
        instance.setPeriodKey("2026-Q3");
        instance.setWindowStartAt(endAt.minusDays(5));
        instance.setWindowEndAt(endAt);
        return instance;
    }

    private static ReTaskBranchAssignment assignment(Long id, Long instanceId, Long branchId, String status) {
        ReTaskBranchAssignment assignment = new ReTaskBranchAssignment();
        assignment.setId(id);
        assignment.setTaskInstanceId(instanceId);
        assignment.setBranchId(branchId);
        assignment.setStatus(status);
        assignment.setCurrentVersion(0);
        return assignment;
    }
}
