package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.redengine.api.dto.ReTaskApproveReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskRejectReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionStatus;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskSubmission;
import com.bank.branch.platform.redengine.entity.ReTaskStatusHistory;
import com.bank.branch.platform.redengine.entity.ReTaskTodo;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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
}
