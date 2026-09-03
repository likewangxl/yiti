package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.redengine.api.dto.ReTaskApproveReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskRejectReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowAssignmentDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowPageQueryDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowTab;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskSubmission;
import com.bank.branch.platform.redengine.entity.ReTaskStatusHistory;
import com.bank.branch.platform.redengine.entity.ReTaskTodo;
import com.bank.branch.platform.redengine.entity.ReUserPartyMap;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskDimensionProgressMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskFileTypeMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskReSubmitRelMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskStatusHistoryMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionFileMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTargetMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTodoMapper;
import com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务报送及两级审核的领域契约测试。
 *
 * <p>这些用例只使用 Mockito，不连接数据库；先以失败测试锁定状态机和权限边界，
 * 再由工作流服务实现最小行为。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReTaskWorkflowServiceTest {

    @Mock private ReTaskMapper taskMapper;
    @Mock private ReTaskInstanceMapper instanceMapper;
    @Mock private ReTaskBranchAssignmentMapper assignmentMapper;
    @Mock private ReTaskSubmissionMapper submissionMapper;
    @Mock private ReTaskSubmissionFileMapper submissionFileMapper;
    @Mock private ReTaskTodoMapper todoMapper;
    @Mock private ReTaskStatusHistoryMapper historyMapper;
    @Mock private ReTaskTargetMapper targetMapper;
    @Mock private ReTaskFileTypeMapper fileTypeMapper;
    @Mock private ReTaskDimensionProgressMapper progressMapper;
    @Mock private ReTaskReSubmitRelMapper relMapper;
    @Mock private RePartyOrgMapper partyOrgMapper;
    @Mock private ReUserPartyMapMapper userPartyMapMapper;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private UserApi userApi;
    @Mock private FileApi fileApi;
    @Mock private ReTaskFourDimensionAdapter fourDimensionAdapter;

    @InjectMocks private ReTaskWorkflowServiceImpl service;

    /** 预热本测试会渲染的 MyBatis-Plus Lambda 元数据；真实运行时由 Mapper 配置完成。 */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ReTask.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskInstance.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskBranchAssignment.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskSubmission.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskStatusHistory.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskTodo.class);
        TableInfoHelper.initTableInfo(assistant, RePartyOrg.class);
    }

    @BeforeEach
    void currentUser() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("REPORTER-1");
    }

    @Test
    void submit_createsBranchPendingVersionAndCompletesReporterTodo() {
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        ReTaskBranchAssignment assignment = assignment(30L, instance.getId(), 40L, "UNREPORTED", 0);
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(todoMapper.selectOne(any())).thenReturn(todo(50L, 30L, "REPORTER-1", "REPORTER", "PENDING"));
        when(submissionMapper.selectOne(any())).thenReturn(null);
        when(historyMapper.selectOne(any())).thenReturn(null);
        when(assignmentMapper.update(any(), any())).thenReturn(1);
        when(submissionMapper.insert(any(ReTaskSubmission.class))).thenAnswer(invocation -> {
            ReTaskSubmission submission = invocation.getArgument(0);
            submission.setId(60L);
            return 1;
        });
        when(historyMapper.insert(any(ReTaskStatusHistory.class))).thenReturn(1);
        when(todoMapper.update(any(), any())).thenReturn(1);

        ReTaskSubmissionReqDTO request = request(30L, "client-1");
        var result = service.submit(request, "REPORTER-1");

        assertThat(result.getSubmissionId()).isEqualTo(60L);
        assertThat(result.getSubmissionStatus()).isEqualTo(ReTaskSubmissionStatus.BRANCH_PENDING);
        verify(submissionMapper).insert(org.mockito.ArgumentMatchers.<ReTaskSubmission>argThat(value ->
                value.getAssignmentId().equals(30L)
                        && value.getVersionNo().equals(1)
                        && value.getStatus() == ReTaskSubmissionStatus.BRANCH_PENDING
                        && value.getSubmitterId().equals("REPORTER-1")));
        verify(todoMapper).update(any(), any());
    }

    @Test
    void submit_beforeTaskWindowStart_isRejectedWithoutChangingAssignment() {
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        LocalDateTime start = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1);
        instance.setWindowStartAt(start);
        instance.setWindowEndAt(start.plusDays(2));
        ReTaskBranchAssignment assignment = assignment(30L, instance.getId(), 40L, "UNREPORTED", 0);
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(todoMapper.selectOne(any())).thenReturn(todo(50L, 30L, "REPORTER-1", "REPORTER", "PENDING"));

        assertThatThrownBy(() -> service.submit(request(30L, "client-before-start"), "REPORTER-1"))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .hasMessageContaining("尚未开始");
        verify(assignmentMapper, never()).update(any(), any());
        verify(submissionMapper, never()).insert(any(ReTaskSubmission.class));
    }

    @Test
    void listMyAssignments_doesNotExposeTodoBeforeAvailableAt() {
        String operatorId = "REPORTER-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        LocalDateTime start = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1);
        instance.setWindowStartAt(start);
        instance.setWindowEndAt(start.plusDays(2));
        ReTaskBranchAssignment assignment = assignment(30L, instance.getId(), 40L, "UNREPORTED", 0);
        ReTaskTodo futureTodo = todo(50L, 30L, operatorId, "REPORTER", "PENDING");
        futureTodo.setAvailableAt(start);
        when(todoMapper.selectList(any())).thenReturn(List.of(futureTodo));

        var result = service.listMyAssignments(new ReTaskWorkflowPageQueryDTO(), operatorId);

        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    void listMyAssignments_includesSubmittedAssignmentWhenReporterTodoWasCompleted() {
        String operatorId = "REPORTER-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L, "BRANCH_PENDING", 1);
        ReTaskSubmission submitted = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, operatorId);
        when(todoMapper.selectList(any())).thenReturn(List.of());
        when(submissionMapper.selectList(any())).thenReturn(List.of(submitted));
        when(assignmentMapper.selectWorkflowPage(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            IPage<ReTaskBranchAssignment> page = (IPage<ReTaskBranchAssignment>) invocation.getArgument(0);
            page.setRecords(List.of(assignment));
            page.setTotal(1L);
            return page;
        });
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(instanceMapper.selectById(instance.getId())).thenReturn(instance);
        when(partyOrgMapper.selectById(assignment.getBranchId())).thenReturn(branch(40L, "SECRETARY-1"));
        when(submissionFileMapper.selectList(any())).thenReturn(List.of());
        when(fileTypeMapper.selectList(any())).thenReturn(List.of());

        var result = service.listMyAssignments(new ReTaskWorkflowPageQueryDTO(), operatorId);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).singleElement()
                .extracting(ReTaskWorkflowAssignmentDTO::getAssignmentId)
                .isEqualTo(30L);
        verify(assignmentMapper).selectWorkflowPage(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void getAssignment_allowsReporterToOpenOwnSubmissionWithoutActiveTodo() {
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L, "BRANCH_PENDING", 1);
        ReTaskSubmission submitted = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1");
        submitted.setReviewOpinion("请补充支部说明");
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(todoMapper.selectOne(any())).thenReturn(null);
        when(submissionMapper.selectList(any())).thenReturn(List.of(submitted));
        when(partyOrgMapper.selectById(40L)).thenReturn(branch(40L, "SECRETARY-1"));
        when(submissionFileMapper.selectList(any())).thenReturn(List.of());
        when(fileTypeMapper.selectList(any())).thenReturn(List.of());

        var result = service.getAssignment(30L, "REPORTER-1");

        assertThat(result.getAssignmentId()).isEqualTo(30L);
        assertThat(result.getSubmitterId()).isEqualTo("REPORTER-1");
        assertThat(result.getSubmissionStatus()).isEqualTo(ReTaskSubmissionStatus.BRANCH_PENDING);
        assertThat(result.getReviewFeedback()).isEqualTo("请补充支部说明");
    }

    @Test
    void getAssignment_rejectsReporterWhoseOldBranchTodoWasCancelled() {
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L, "UNREPORTED", 0);
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_REPORT"));
        // 取消状态由 findReporterTodo 的 status <> CANCELLED 条件在数据库侧过滤，
        // 因此 Mockito 应模拟真实 Mapper 语义：查询不到可处理的报送员待办。
        when(todoMapper.selectOne(any())).thenReturn(null);
        when(submissionMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.getAssignment(30L, "REPORTER-1"))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .hasMessage("无权查看该任务");
    }

    @Test
    void getBranchReview_populatesFourDimensionFieldsFromCurrentTaskSubmission() {
        String operatorId = "SECRETARY-1";
        ReTask task = task(10L, "FOUR_DIMENSION");
        ReTaskInstance instance = instance(20L, task.getId());
        RePartyOrg branch = branch(40L, operatorId);
        ReTaskBranchAssignment assignment = assignment(30L, instance.getId(), branch.getId(),
                "BRANCH_PENDING", 1);
        ReTaskSubmission current = submission(60L, task.getId(), instance.getId(), assignment.getId(), 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1");
        current.setDimensionCode("DIM_2");
        current.setItemCode("2.3");
        current.setFormData("{\"evidence\":true}");

        when(currentUserApi.getCurrentEmpId()).thenReturn(operatorId);
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_SECR"));
        when(assignmentMapper.selectById(assignment.getId())).thenReturn(assignment);
        when(instanceMapper.selectById(instance.getId())).thenReturn(instance);
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(partyOrgMapper.selectById(branch.getId())).thenReturn(branch);
        when(submissionMapper.selectOne(any())).thenReturn(current);
        when(submissionFileMapper.selectList(any())).thenReturn(List.of());
        when(fileTypeMapper.selectList(any())).thenReturn(List.of());
        when(relMapper.selectOne(any())).thenReturn(null);

        ReTaskWorkflowAssignmentDTO result = service.getBranchReview(assignment.getId(), operatorId);

        assertThat(result.getDimensionCode()).isEqualTo("DIM_2");
        assertThat(result.getItemCode()).isEqualTo("2.3");
        assertThat(result.getFormData()).isEqualTo("{\"evidence\":true}");
    }

    @Test
    void workflowPage_filtersBeforePaginationAndPreservesDatabaseTotal() {
        String operatorId = "SECRETARY-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        RePartyOrg branch = branch(40L, operatorId);
        when(currentUserApi.getCurrentEmpId()).thenReturn(operatorId);
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L, "APPROVED", 1);
        ReTaskSubmission submitted = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.APPROVED, "REPORTER-1");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_ORGREV"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(assignmentMapper.selectWorkflowPage(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            IPage<ReTaskBranchAssignment> page = (IPage<ReTaskBranchAssignment>) invocation.getArgument(0);
            page.setRecords(List.of(assignment));
            page.setTotal(9L);
            return page;
        });
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(instanceMapper.selectById(instance.getId())).thenReturn(instance);
        when(partyOrgMapper.selectById(assignment.getBranchId())).thenReturn(branch);
        when(submissionMapper.selectList(any())).thenReturn(List.of(submitted));
        when(submissionFileMapper.selectList(any())).thenReturn(List.of());
        when(fileTypeMapper.selectList(any())).thenReturn(List.of());
        ReTaskWorkflowPageQueryDTO query = new ReTaskWorkflowPageQueryDTO();
        query.setPageNo(2);
        query.setPageSize(1);
        query.setTab(ReTaskWorkflowTab.PASSED);

        var result = service.listOrgReviews(query, operatorId);

        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(1);
        assertThat(result.getTotal()).isEqualTo(9L);
        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    void branchQueue_branchFilterFurtherNarrowsAuthorizedAssignments() {
        String operatorId = "SECRETARY-1";
        RePartyOrg branch = branch(40L, operatorId);
        ReTaskBranchAssignment first = assignment(331L, 20L, 40L, "BRANCH_PENDING", 1);
        ReTaskBranchAssignment second = assignment(332L, 20L, 41L, "BRANCH_PENDING", 1);
        when(currentUserApi.getCurrentEmpId()).thenReturn(operatorId);
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_SECR"));
        when(partyOrgMapper.selectList(org.mockito.ArgumentMatchers.isNull())).thenReturn(List.of(branch));
        ReTaskWorkflowPageQueryDTO query = new ReTaskWorkflowPageQueryDTO();
        query.setBranchId(41L);

        var result = service.listBranchReviews(query, operatorId);

        assertThat(result.getRecords()).isEmpty();
        verify(taskMapper, never()).selectById(anyLong());
    }

    @Test
    void branchQueue_usesUserPartyMappingWhenLegacySecretaryIdIsNull() {
        String operatorId = "SECRETARY-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        RePartyOrg branch = branch(40L, null);
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L, "BRANCH_PENDING", 1);
        ReTaskSubmission submitted = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1");
        stubWorkflowListing(operatorId, Set.of("R_RE_SECR"), branch, task, instance,
                List.of(assignment), List.of(submitted));
        // partyRole is only a compatibility description; the platform role above grants the action.
        when(userPartyMapMapper.selectList(any())).thenReturn(
                List.of(userPartyMap(operatorId, 40L, "REPORTER")));

        var result = service.listBranchReviews(new ReTaskWorkflowPageQueryDTO(), operatorId);

        assertThat(result.getRecords()).singleElement()
                .extracting(ReTaskWorkflowAssignmentDTO::getAssignmentId)
                .isEqualTo(30L);
    }

    @Test
    void branchReviewActions_acceptMappedBranchWithoutLegacySecretaryId() {
        String operatorId = "SECRETARY-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        RePartyOrg branch = branch(40L, null);
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L, "BRANCH_PENDING", 1);
        ReTaskSubmission submission = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1");
        stubBranchAction(operatorId, branch, task, instance, assignment, submission);

        ReTaskApproveReqDTO approve = new ReTaskApproveReqDTO();
        approve.setFeedback("材料完整");
        var approved = service.approveBranch(30L, approve, operatorId);

        assertThat(approved.getSubmissionStatus()).isEqualTo(ReTaskSubmissionStatus.BRANCH_APPROVED);

        submission.setStatus(ReTaskSubmissionStatus.BRANCH_APPROVED);
        assignment.setStatus("BRANCH_PENDING");
        var toOrg = service.submitToOrg(30L, approve, operatorId);

        assertThat(toOrg.getSubmissionStatus()).isEqualTo(ReTaskSubmissionStatus.ORG_PENDING);
    }

    @Test
    void branchReject_acceptsMappedBranchWithoutLegacySecretaryId() {
        String operatorId = "SECRETARY-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        RePartyOrg branch = branch(40L, null);
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L, "BRANCH_PENDING", 1);
        ReTaskSubmission submission = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1");
        stubBranchAction(operatorId, branch, task, instance, assignment, submission);

        ReTaskRejectReqDTO reject = new ReTaskRejectReqDTO();
        reject.setFeedback("请补充说明");
        var rejected = service.rejectBranch(30L, reject, operatorId);

        assertThat(rejected.getSubmissionStatus()).isEqualTo(ReTaskSubmissionStatus.REJECTED_BY_BRANCH);
    }

    @Test
    void branchReviewer_mappingToDifferentBranchIsRejected() {
        String operatorId = "SECRETARY-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        RePartyOrg branch = branch(40L, null);
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L, "BRANCH_PENDING", 1);
        ReTaskSubmission submission = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1");
        when(currentUserApi.getCurrentEmpId()).thenReturn(operatorId);
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_SECR"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(assignmentMapper.selectById(assignment.getId())).thenReturn(assignment);
        when(instanceMapper.selectById(instance.getId())).thenReturn(instance);
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(partyOrgMapper.selectById(branch.getId())).thenReturn(branch);
        when(userPartyMapMapper.selectList(any())).thenReturn(
                List.of(userPartyMap(operatorId, 41L, "SECRETARY")));

        assertThatThrownBy(() -> service.approveBranch(30L, new ReTaskApproveReqDTO(), operatorId))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .hasMessage("无权审核该党支部任务");
        verify(submissionMapper, never()).update(any(), any());
    }

    @Test
    void systemAdminCanUseBranchReviewQueueAcrossAllBranches() {
        String operatorId = "ADMIN-1";
        when(currentUserApi.getCurrentEmpId()).thenReturn(operatorId);
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("SYS_ADMIN"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(partyOrgMapper.selectList(org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of(branch(40L, "SECRETARY-1"), branch(41L, "SECRETARY-2")));
        when(assignmentMapper.selectWorkflowPage(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.listBranchReviews(new ReTaskWorkflowPageQueryDTO(), operatorId);

        assertThat(result.getRecords()).isEmpty();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> branchIds = ArgumentCaptor.forClass(Set.class);
        verify(assignmentMapper).selectWorkflowPage(any(), any(), any(), branchIds.capture(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
        assertThat(branchIds.getValue()).containsExactlyInAnyOrder(40L, 41L);
    }

    @Test
    void systemAdminCanRejectSubmissionFromAnyBranch() {
        String operatorId = "ADMIN-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L, "BRANCH_PENDING", 1);
        ReTaskSubmission submission = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1");
        when(currentUserApi.getCurrentEmpId()).thenReturn(operatorId);
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("SYS_ADMIN"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(partyOrgMapper.selectById(40L)).thenReturn(branch(40L, "SECRETARY-1"));
        when(submissionMapper.selectOne(any())).thenReturn(submission);
        when(submissionMapper.update(any(), any())).thenReturn(1);
        when(assignmentMapper.update(any(), any())).thenReturn(1);
        when(historyMapper.insert(any(ReTaskStatusHistory.class))).thenReturn(1);
        when(todoMapper.update(any(), any())).thenReturn(1);

        ReTaskRejectReqDTO reject = new ReTaskRejectReqDTO();
        reject.setFeedback("请补充材料");
        var result = service.rejectBranch(30L, reject, operatorId);

        assertThat(result.getSubmissionStatus()).isEqualTo(ReTaskSubmissionStatus.REJECTED_BY_BRANCH);
        verify(submissionMapper).update(any(), any());
    }

    @Test
    void submit_sameClientRequestId_returnsExistingSubmissionWithoutSecondWrite() {
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        ReTaskBranchAssignment assignment = assignment(30L, instance.getId(), 40L, "BRANCH_PENDING", 1);
        ReTaskSubmission existing = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1");
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(todoMapper.selectOne(any())).thenReturn(todo(50L, 30L, "REPORTER-1", "REPORTER", "COMPLETED"));
        when(historyMapper.selectOne(any())).thenReturn(history(70L, 30L, 60L, "SUBMIT:client-1"));
        when(submissionMapper.selectById(60L)).thenReturn(existing);

        var result = service.submit(request(30L, "client-1"), "REPORTER-1");

        assertThat(result.getSubmissionId()).isEqualTo(60L);
        assertThat(result.isIdempotent()).isTrue();
        verify(submissionMapper, never()).insert(any(ReTaskSubmission.class));
        verify(assignmentMapper, never()).update(any(), any());
    }

    @Test
    void submit_uuidClientRequestId_persistsBoundedNonTruncatedActionCode() {
        String clientRequestId = "123e4567-e89b-12d3-a456-426614174000";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        ReTaskBranchAssignment assignment = assignment(30L, instance.getId(), 40L, "UNREPORTED", 0);
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(todoMapper.selectOne(any())).thenReturn(todo(50L, 30L, "REPORTER-1", "REPORTER", "PENDING"));
        when(submissionMapper.selectOne(any())).thenReturn(null);
        when(historyMapper.selectOne(any())).thenReturn(null);
        when(assignmentMapper.update(any(), any())).thenReturn(1);
        when(submissionMapper.insert(any(ReTaskSubmission.class))).thenAnswer(invocation -> {
            ReTaskSubmission submission = invocation.getArgument(0);
            submission.setId(60L);
            return 1;
        });
        when(historyMapper.insert(any(ReTaskStatusHistory.class))).thenReturn(1);
        when(todoMapper.update(any(), any())).thenReturn(1);

        service.submit(request(30L, clientRequestId), "REPORTER-1");

        ArgumentCaptor<ReTaskStatusHistory> historyCaptor = ArgumentCaptor.forClass(ReTaskStatusHistory.class);
        verify(historyMapper).insert(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getActionCode())
                .isEqualTo("SUBMIT:986c0dc956dc822b5d8f698661b9eb1e")
                .hasSizeLessThanOrEqualTo(40);
    }

    @Test
    void submit_uuidClientRequestId_matchesPersistedHistoryWithoutSecondWrite() {
        String clientRequestId = "123e4567-e89b-12d3-a456-426614174000";
        String actionCode = "SUBMIT:986c0dc956dc822b5d8f698661b9eb1e";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        ReTaskBranchAssignment assignment = assignment(30L, instance.getId(), 40L, "BRANCH_PENDING", 1);
        ReTaskSubmission existing = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1");
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(todoMapper.selectOne(any())).thenReturn(todo(50L, 30L, "REPORTER-1", "REPORTER", "COMPLETED"));
        when(historyMapper.selectOne(any())).thenReturn(history(70L, 30L, 60L, actionCode));
        when(submissionMapper.selectById(60L)).thenReturn(existing);

        var result = service.submit(request(30L, clientRequestId), "REPORTER-1");

        assertThat(result.getSubmissionId()).isEqualTo(60L);
        assertThat(result.isIdempotent()).isTrue();
        assertThat(invokeIdempotencyActionCode(clientRequestId)).isEqualTo(actionCode);
        verify(submissionMapper, never()).insert(any(ReTaskSubmission.class));
        verify(assignmentMapper, never()).update(any(), any());
    }

    @Test
    void idempotencyActionCode_differentLongIdsWithSamePrefixDoNotCollide() {
        String commonPrefix = "123456789012345678901234567890123";
        assertThat(currentUserApi.getCurrentEmpId()).isEqualTo("REPORTER-1");

        String firstActionCode = invokeIdempotencyActionCode(commonPrefix + "A");
        String secondActionCode = invokeIdempotencyActionCode(commonPrefix + "B");

        assertThat(firstActionCode).hasSizeLessThanOrEqualTo(40);
        assertThat(secondActionCode).hasSizeLessThanOrEqualTo(40);
        assertThat(firstActionCode).isNotEqualTo(secondActionCode);
    }

    @Test
    void branchReject_requiresOpinionAndReturnsAssignmentToReporter() {
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L, "BRANCH_PENDING", 1);
        ReTaskSubmission submission = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1");
        RePartyOrg branch = branch(40L, "SECRETARY-1");
        when(currentUserApi.getCurrentEmpId()).thenReturn("SECRETARY-1");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_SECR"));
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(submissionMapper.selectOne(any())).thenReturn(submission);
        when(partyOrgMapper.selectById(40L)).thenReturn(branch);
        when(assignmentMapper.update(any(), any())).thenReturn(1);
        when(submissionMapper.update(any(), any())).thenReturn(1);
        when(historyMapper.insert(any(ReTaskStatusHistory.class))).thenReturn(1);

        ReTaskRejectReqDTO reject = new ReTaskRejectReqDTO();
        reject.setFeedback("请补充附件");
        var result = service.rejectBranch(30L, reject, "SECRETARY-1");

        assertThat(result.getAssignmentStatus().name()).isEqualTo("REJECTED_BY_BRANCH");
        ArgumentCaptor<LambdaUpdateWrapper<ReTaskBranchAssignment>> assignmentUpdate =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(assignmentMapper).update(any(), assignmentUpdate.capture());
        assertThat(assignmentUpdate.getValue().getParamNameValuePairs().values())
                .contains(ReTaskAssignmentStatus.REJECTED_BY_BRANCH.name());
        verify(todoMapper).update(any(), any());
        verify(historyMapper).insert(org.mockito.ArgumentMatchers.<ReTaskStatusHistory>argThat(value ->
                "REJECT_BRANCH".equals(value.getActionCode())
                        && ReTaskSubmissionStatus.REJECTED_BY_BRANCH.name().equals(value.getToStatus())));
    }

    @Test
    void branchApproveAndSubmitToOrg_areTwoSeparateTransitions() {
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L, "BRANCH_PENDING", 1);
        ReTaskSubmission submission = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1");
        when(currentUserApi.getCurrentEmpId()).thenReturn("SECRETARY-1");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_SECR"));
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(submissionMapper.selectOne(any())).thenReturn(submission);
        when(partyOrgMapper.selectById(40L)).thenReturn(branch(40L, "SECRETARY-1"));
        when(assignmentMapper.update(any(), any())).thenReturn(1);
        when(submissionMapper.update(any(), any())).thenReturn(1);
        when(historyMapper.insert(any(ReTaskStatusHistory.class))).thenReturn(1);

        ReTaskApproveReqDTO approve = new ReTaskApproveReqDTO();
        approve.setFeedback("材料完整");
        var approved = service.approveBranch(30L, approve, "SECRETARY-1");
        assertThat(approved.getSubmissionStatus()).isEqualTo(ReTaskSubmissionStatus.BRANCH_APPROVED);
        assertThat(approved.getAssignmentStatus().name()).isEqualTo("BRANCH_PENDING");

        submission.setStatus(ReTaskSubmissionStatus.BRANCH_APPROVED);
        assignment.setStatus("BRANCH_PENDING");
        var toOrg = service.submitToOrg(30L, approve, "SECRETARY-1");
        assertThat(toOrg.getSubmissionStatus()).isEqualTo(ReTaskSubmissionStatus.ORG_PENDING);
        assertThat(toOrg.getAssignmentStatus().name()).isEqualTo("ORG_PENDING");
    }

    @Test
    void orgReject_requiresOrgReviewerAndMovesBackToReporter() {
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L, "ORG_PENDING", 1);
        ReTaskSubmission submission = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.ORG_PENDING, "REPORTER-1");
        when(currentUserApi.getCurrentEmpId()).thenReturn("ORG-REVIEWER-1");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_ORGREV"));
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(submissionMapper.selectOne(any())).thenReturn(submission);
        when(submissionMapper.update(any(), any())).thenReturn(1);
        when(assignmentMapper.update(any(), any())).thenReturn(1);
        when(historyMapper.insert(any(ReTaskStatusHistory.class))).thenReturn(1);

        ReTaskRejectReqDTO reject = new ReTaskRejectReqDTO();
        reject.setFeedback("请补充说明");
        var result = service.rejectOrg(30L, reject, "ORG-REVIEWER-1");

        assertThat(result.getSubmissionStatus()).isEqualTo(ReTaskSubmissionStatus.REJECTED_BY_ORG);
        verify(todoMapper, times(2)).update(any(), any());
    }

    @Test
    void branchReviewerCannotReviewOwnSubmission_failClosed() {
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L, "BRANCH_PENDING", 1);
        ReTaskSubmission submission = submission(60L, 10L, 20L, 30L, 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, "SECRETARY-1");
        when(currentUserApi.getCurrentEmpId()).thenReturn("SECRETARY-1");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_SECR"));
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(submissionMapper.selectOne(any())).thenReturn(submission);
        when(partyOrgMapper.selectById(40L)).thenReturn(branch(40L, "SECRETARY-1"));

        assertThatThrownBy(() -> service.approveBranch(30L, new ReTaskApproveReqDTO(), "SECRETARY-1"))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .hasMessage("禁止审核本人提交的记录");
        verify(submissionMapper, never()).update(any(), any());
    }

    @Test
    void branchQueue_includesPendingAndAllHistoricalStatuses() {
        String operatorId = "SECRETARY-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        RePartyOrg branch = branch(40L, operatorId);
        List<ReTaskBranchAssignment> assignments = List.of(
                assignment(301L, 20L, 40L, "BRANCH_PENDING", 1),
                assignment(302L, 20L, 40L, "BRANCH_PENDING", 1),
                assignment(303L, 20L, 40L, "ORG_PENDING", 1),
                assignment(304L, 20L, 40L, "APPROVED", 1),
                assignment(305L, 20L, 40L, "REJECTED_BY_BRANCH", 1),
                assignment(306L, 20L, 40L, "REJECTED_BY_ORG", 1));
        List<ReTaskSubmission> submissions = List.of(
                submission(401L, 10L, 20L, 301L, 1, ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1"),
                submission(402L, 10L, 20L, 302L, 1, ReTaskSubmissionStatus.BRANCH_APPROVED, "REPORTER-1"),
                submission(403L, 10L, 20L, 303L, 1, ReTaskSubmissionStatus.ORG_PENDING, "REPORTER-1"),
                submission(404L, 10L, 20L, 304L, 1, ReTaskSubmissionStatus.APPROVED, "REPORTER-1"),
                submission(405L, 10L, 20L, 305L, 1, ReTaskSubmissionStatus.REJECTED_BY_BRANCH, "REPORTER-1"),
                submission(406L, 10L, 20L, 306L, 1, ReTaskSubmissionStatus.REJECTED_BY_ORG, "REPORTER-1"));
        stubWorkflowListing(operatorId, Set.of("R_RE_SECR"), branch, task, instance,
                assignments, submissions);

        var result = service.listBranchReviews(new ReTaskWorkflowPageQueryDTO(), operatorId);

        assertThat(result.getRecords()).extracting(ReTaskWorkflowAssignmentDTO::getSubmissionStatus)
                .containsExactly(ReTaskSubmissionStatus.BRANCH_PENDING,
                        ReTaskSubmissionStatus.BRANCH_APPROVED,
                        ReTaskSubmissionStatus.ORG_PENDING,
                        ReTaskSubmissionStatus.APPROVED,
                        ReTaskSubmissionStatus.REJECTED_BY_BRANCH,
                        ReTaskSubmissionStatus.REJECTED_BY_ORG);
        assertThat(result.getRecords()).extracting(ReTaskWorkflowAssignmentDTO::getStatus)
                .containsExactly(ReTaskAssignmentStatus.BRANCH_PENDING,
                        ReTaskAssignmentStatus.BRANCH_PENDING,
                        ReTaskAssignmentStatus.ORG_PENDING,
                        ReTaskAssignmentStatus.APPROVED,
                        ReTaskAssignmentStatus.REJECTED_BY_BRANCH,
                        ReTaskAssignmentStatus.REJECTED_BY_ORG);
    }

    @Test
    void branchQueue_usesAssignmentCurrentVersionInsteadOfOlderSubmission() {
        String operatorId = "SECRETARY-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        RePartyOrg branch = branch(40L, operatorId);
        ReTaskBranchAssignment assignment = assignment(30L, instance.getId(), branch.getId(),
                "BRANCH_PENDING", 2);
        ReTaskSubmission olderSubmission = submission(60L, task.getId(), instance.getId(),
                assignment.getId(), 1, ReTaskSubmissionStatus.REJECTED_BY_BRANCH, "REPORTER-1");
        olderSubmission.setContentText("旧版本内容");
        ReTaskSubmission currentSubmission = submission(61L, task.getId(), instance.getId(),
                assignment.getId(), 2, ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1");
        currentSubmission.setContentText("重提版本内容");

        when(currentUserApi.getCurrentEmpId()).thenReturn(operatorId);
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_SECR"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(partyOrgMapper.selectList(org.mockito.ArgumentMatchers.isNull())).thenReturn(List.of(branch));
        when(assignmentMapper.selectWorkflowPage(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            IPage<ReTaskBranchAssignment> page = (IPage<ReTaskBranchAssignment>) invocation.getArgument(0);
            page.setRecords(List.of(assignment));
            page.setTotal(1L);
            return page;
        });
        when(instanceMapper.selectById(instance.getId())).thenReturn(instance);
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(partyOrgMapper.selectById(branch.getId())).thenReturn(branch);
        // 模拟旧实现拿到旧列表的场景；当前版本查询必须命中 version 2。
        org.mockito.Mockito.lenient().when(submissionMapper.selectList(any()))
                .thenReturn(List.of(olderSubmission));
        when(submissionMapper.selectOne(any())).thenReturn(currentSubmission);
        when(submissionFileMapper.selectList(any())).thenReturn(List.of());
        when(fileTypeMapper.selectList(any())).thenReturn(List.of());

        var result = service.listBranchReviews(new ReTaskWorkflowPageQueryDTO(), operatorId);

        assertThat(result.getRecords()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getSubmissionStatus()).isEqualTo(ReTaskSubmissionStatus.BRANCH_PENDING);
                    assertThat(row.getContent()).isEqualTo("重提版本内容");
                });
        verify(submissionMapper).selectOne(any());
    }

    @Test
    void orgQueue_includesPendingAndHistoricalApprovedOrRejectedStatuses() {
        String operatorId = "ORG-REVIEWER-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        RePartyOrg branch = branch(40L, "SECRETARY-1");
        List<ReTaskBranchAssignment> assignments = List.of(
                assignment(311L, 20L, 40L, "ORG_PENDING", 1),
                assignment(312L, 20L, 40L, "APPROVED", 1),
                assignment(313L, 20L, 40L, "REJECTED_BY_BRANCH", 1),
                assignment(314L, 20L, 40L, "REJECTED_BY_ORG", 1));
        List<ReTaskSubmission> submissions = List.of(
                submission(411L, 10L, 20L, 311L, 1, ReTaskSubmissionStatus.ORG_PENDING, "REPORTER-1"),
                submission(412L, 10L, 20L, 312L, 1, ReTaskSubmissionStatus.APPROVED, "REPORTER-1"),
                submission(413L, 10L, 20L, 313L, 1, ReTaskSubmissionStatus.REJECTED_BY_BRANCH, "REPORTER-1"),
                submission(414L, 10L, 20L, 314L, 1, ReTaskSubmissionStatus.REJECTED_BY_ORG, "REPORTER-1"));
        stubWorkflowListing(operatorId, Set.of("R_RE_ORGREV"), branch, task, instance,
                assignments, submissions);

        var result = service.listOrgReviews(new ReTaskWorkflowPageQueryDTO(), operatorId);

        assertThat(result.getRecords()).extracting(ReTaskWorkflowAssignmentDTO::getSubmissionStatus)
                .containsExactly(ReTaskSubmissionStatus.ORG_PENDING,
                        ReTaskSubmissionStatus.APPROVED,
                        ReTaskSubmissionStatus.REJECTED_BY_BRANCH,
                        ReTaskSubmissionStatus.REJECTED_BY_ORG);
    }

    @Test
    void branchQueue_explicitOrgPendingStatus_isNotDroppedByBranchDefaultFilter() {
        String operatorId = "SECRETARY-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        RePartyOrg branch = branch(40L, operatorId);
        ReTaskBranchAssignment assignment = assignment(321L, 20L, 40L, "ORG_PENDING", 1);
        ReTaskSubmission submission = submission(421L, 10L, 20L, 321L, 1,
                ReTaskSubmissionStatus.ORG_PENDING, "REPORTER-1");
        stubWorkflowListing(operatorId, Set.of("R_RE_SECR"), branch, task, instance,
                List.of(assignment), List.of(submission));
        ReTaskWorkflowPageQueryDTO query = new ReTaskWorkflowPageQueryDTO();
        query.setAssignmentStatus(ReTaskAssignmentStatus.ORG_PENDING);

        var result = service.listBranchReviews(query, operatorId);

        assertThat(result.getRecords()).singleElement()
                .extracting(ReTaskWorkflowAssignmentDTO::getStatus)
                .isEqualTo(ReTaskAssignmentStatus.ORG_PENDING);
    }

    @Test
    void orgQueue_explicitApprovedStatus_isNotDroppedByOrgDefaultFilter() {
        String operatorId = "ORG-REVIEWER-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        RePartyOrg branch = branch(40L, "SECRETARY-1");
        ReTaskBranchAssignment assignment = assignment(322L, 20L, 40L, "APPROVED", 1);
        ReTaskSubmission submission = submission(422L, 10L, 20L, 322L, 1,
                ReTaskSubmissionStatus.APPROVED, "REPORTER-1");
        stubWorkflowListing(operatorId, Set.of("R_RE_ORGREV"), branch, task, instance,
                List.of(assignment), List.of(submission));
        ReTaskWorkflowPageQueryDTO query = new ReTaskWorkflowPageQueryDTO();
        query.setAssignmentStatus(ReTaskAssignmentStatus.APPROVED);

        var result = service.listOrgReviews(query, operatorId);

        assertThat(result.getRecords()).singleElement()
                .extracting(ReTaskWorkflowAssignmentDTO::getStatus)
                .isEqualTo(ReTaskAssignmentStatus.APPROVED);
    }

    @Test
    void orgQueue_explicitBranchPendingStatus_doesNotExpandOrganizationScope() {
        String operatorId = "ORG-REVIEWER-1";
        ReTask task = task(10L, "GENERAL");
        ReTaskInstance instance = instance(20L, task.getId());
        RePartyOrg branch = branch(40L, "SECRETARY-1");
        ReTaskBranchAssignment assignment = assignment(323L, 20L, 40L, "BRANCH_PENDING", 1);
        ReTaskSubmission submission = submission(423L, 10L, 20L, 323L, 1,
                ReTaskSubmissionStatus.BRANCH_PENDING, "REPORTER-1");
        stubWorkflowListing(operatorId, Set.of("R_RE_ORGREV"), branch, task, instance,
                List.of(assignment), List.of(submission));
        ReTaskWorkflowPageQueryDTO query = new ReTaskWorkflowPageQueryDTO();
        query.setAssignmentStatus(ReTaskAssignmentStatus.BRANCH_PENDING);

        var result = service.listOrgReviews(query, operatorId);

        assertThat(result.getRecords()).isEmpty();
    }

    /** 为列表契约测试准备同一任务下的多个 assignment 和当前版本。 */
    private void stubWorkflowListing(String operatorId, Set<String> roles, RePartyOrg branch,
                                     ReTask task, ReTaskInstance instance,
                                     List<ReTaskBranchAssignment> assignments,
                                     List<ReTaskSubmission> submissions) {
        org.mockito.Mockito.lenient().when(currentUserApi.getCurrentEmpId()).thenReturn(operatorId);
        org.mockito.Mockito.lenient().when(currentUserApi.getCurrentRoleCodes()).thenReturn(roles);
        org.mockito.Mockito.lenient().when(currentUserApi.isSystemAdmin()).thenReturn(false);
        org.mockito.Mockito.lenient().when(assignmentMapper.selectWorkflowPage(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            IPage<ReTaskBranchAssignment> page = (IPage<ReTaskBranchAssignment>) invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            Set<Long> requestedAssignmentIds = (Set<Long>) invocation.getArgument(1);
            @SuppressWarnings("unchecked")
            Set<Long> requestedBranchIds = (Set<Long>) invocation.getArgument(3);
            @SuppressWarnings("unchecked")
            Set<String> requestedStatuses = (Set<String>) invocation.getArgument(4);
            @SuppressWarnings("unchecked")
            Set<String> requestedSubmissionStatuses = (Set<String>) invocation.getArgument(5);
            List<ReTaskBranchAssignment> filtered = assignments.stream()
                    .filter(item -> requestedAssignmentIds == null || requestedAssignmentIds.isEmpty()
                            || requestedAssignmentIds.contains(item.getId()))
                    .filter(item -> requestedBranchIds == null || requestedBranchIds.isEmpty()
                            || requestedBranchIds.contains(item.getBranchId()))
                    .filter(item -> requestedStatuses == null || requestedStatuses.isEmpty()
                            || requestedStatuses.contains(item.getStatus()))
                    .filter(item -> {
                        if (requestedSubmissionStatuses == null || requestedSubmissionStatuses.isEmpty()) {
                            return true;
                        }
                        return submissions.stream()
                                .filter(submission -> item.getId().equals(submission.getAssignmentId()))
                                .anyMatch(submission -> requestedSubmissionStatuses.contains(
                                        submission.getStatus().name()));
                    })
                    .toList();
            page.setRecords(filtered);
            page.setTotal(filtered.size());
            return page;
        });
        org.mockito.Mockito.lenient().when(instanceMapper.selectById(instance.getId())).thenReturn(instance);
        org.mockito.Mockito.lenient().when(taskMapper.selectById(task.getId())).thenReturn(task);
        org.mockito.Mockito.lenient().when(partyOrgMapper.selectById(anyLong())).thenReturn(branch);
        org.mockito.Mockito.lenient().when(submissionFileMapper.selectList(any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(fileTypeMapper.selectList(any())).thenReturn(List.of());
        AtomicInteger submissionIndex = new AtomicInteger();
        org.mockito.Mockito.lenient().when(submissionMapper.selectList(any())).thenAnswer(invocation -> {
            int index = submissionIndex.getAndIncrement();
            return index < submissions.size() ? List.of(submissions.get(index)) : List.of();
        });
        if (roles.contains("R_RE_SECR")) {
            org.mockito.Mockito.lenient().when(partyOrgMapper.selectList(org.mockito.ArgumentMatchers.isNull()))
                    .thenReturn(List.of(branch));
        }
    }

    private void stubBranchAction(String operatorId, RePartyOrg branch, ReTask task,
                                  ReTaskInstance instance, ReTaskBranchAssignment assignment,
                                  ReTaskSubmission submission) {
        when(currentUserApi.getCurrentEmpId()).thenReturn(operatorId);
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_SECR"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(assignmentMapper.selectById(assignment.getId())).thenReturn(assignment);
        when(instanceMapper.selectById(instance.getId())).thenReturn(instance);
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(submissionMapper.selectOne(any())).thenReturn(submission);
        when(partyOrgMapper.selectById(branch.getId())).thenReturn(branch);
        when(userPartyMapMapper.selectList(any())).thenReturn(
                List.of(userPartyMap(operatorId, branch.getId(), "REPORTER")));
        when(assignmentMapper.update(any(), any())).thenReturn(1);
        when(submissionMapper.update(any(), any())).thenReturn(1);
        when(historyMapper.insert(any(ReTaskStatusHistory.class))).thenReturn(1);
    }

    private static ReTask task(Long id, String typeCode) {
        ReTask task = new ReTask();
        task.setId(id);
        task.setTitle("临时任务");
        task.setDescription("请完成填报");
        task.setNature("TEMPORARY");
        task.setTypeCode(typeCode);
        task.setStatus(com.bank.branch.platform.redengine.api.dto.ReTaskStatus.PUBLISHED);
        task.setRequiresFile(0);
        return task;
    }

    private static ReTaskInstance instance(Long id, Long taskId) {
        ReTaskInstance instance = new ReTaskInstance();
        instance.setId(id);
        instance.setTaskId(taskId);
        instance.setWindowStartAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        instance.setWindowEndAt(LocalDateTime.of(2026, 8, 31, 23, 59, 59));
        return instance;
    }

    private static ReTaskBranchAssignment assignment(Long id, Long instanceId, Long branchId,
                                                     String status, int version) {
        ReTaskBranchAssignment assignment = new ReTaskBranchAssignment();
        assignment.setId(id);
        assignment.setTaskInstanceId(instanceId);
        assignment.setBranchId(branchId);
        assignment.setStatus(status);
        assignment.setCurrentVersion(version);
        return assignment;
    }

    private static ReTaskSubmission submission(Long id, Long taskId, Long instanceId,
                                               Long assignmentId, int version,
                                               ReTaskSubmissionStatus status, String submitterId) {
        ReTaskSubmission submission = new ReTaskSubmission();
        submission.setId(id);
        submission.setTaskId(taskId);
        submission.setTaskInstanceId(instanceId);
        submission.setAssignmentId(assignmentId);
        submission.setVersionNo(version);
        submission.setStatus(status);
        submission.setSubmitterId(submitterId);
        submission.setSubmittedAt(LocalDateTime.now());
        return submission;
    }

    private static ReTaskTodo todo(Long id, Long assignmentId, String employeeId,
                                   String role, String status) {
        ReTaskTodo todo = new ReTaskTodo();
        todo.setId(id);
        todo.setAssignmentId(assignmentId);
        todo.setEmployeeId(employeeId);
        todo.setRoleCode(role);
        todo.setStatus(status);
        return todo;
    }

    private static RePartyOrg branch(Long id, String secretaryId) {
        RePartyOrg branch = new RePartyOrg();
        branch.setId(id);
        branch.setOrgLevel(2);
        branch.setSecretaryId(secretaryId);
        branch.setOrgName("第一党支部");
        return branch;
    }

    private static ReUserPartyMap userPartyMap(String userId, Long partyOrgId, String partyRole) {
        ReUserPartyMap mapping = new ReUserPartyMap();
        mapping.setUserId(userId);
        mapping.setPartyOrgId(partyOrgId);
        mapping.setPartyRole(partyRole);
        mapping.setDeleted(0);
        return mapping;
    }

    private static ReTaskSubmissionReqDTO request(Long assignmentId, String clientRequestId) {
        ReTaskSubmissionReqDTO request = new ReTaskSubmissionReqDTO();
        request.setAssignmentId(assignmentId);
        request.setContent("已完成");
        request.setClientRequestId(clientRequestId);
        return request;
    }

    private static com.bank.branch.platform.redengine.entity.ReTaskStatusHistory history(
            Long id, Long assignmentId, Long submissionId, String actionCode) {
        var history = new com.bank.branch.platform.redengine.entity.ReTaskStatusHistory();
        history.setId(id);
        history.setAssignmentId(assignmentId);
        history.setSubmissionId(submissionId);
        history.setActionCode(actionCode);
        return history;
    }

    private String invokeIdempotencyActionCode(String clientRequestId) {
        try {
            var method = ReTaskWorkflowServiceImpl.class
                    .getDeclaredMethod("idempotencyActionCode", String.class);
            method.setAccessible(true);
            return (String) method.invoke(service, clientRequestId);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法调用幂等编码生成器", exception);
        }
    }
}
