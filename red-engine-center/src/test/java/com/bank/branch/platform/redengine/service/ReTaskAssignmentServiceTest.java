package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentPageQueryDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskStatus;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskTarget;
import com.bank.branch.platform.redengine.entity.ReTaskTodo;
import com.bank.branch.platform.redengine.entity.ReUserPartyMap;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionFileMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTargetMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTodoMapper;
import com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务发布分配单元测试：验证目标范围展开、支部去重、报送员待办和幂等生成。
 * <p>纯 Mockito，不连接数据库，也不依赖 Quartz。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReTaskAssignmentServiceTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ReTask.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskInstance.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskTarget.class);
        TableInfoHelper.initTableInfo(assistant, RePartyOrg.class);
        TableInfoHelper.initTableInfo(assistant, ReUserPartyMap.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskBranchAssignment.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskTodo.class);
    }

    @Mock
    private ReTaskTargetMapper targetMapper;
    @Mock
    private RePartyOrgMapper partyOrgMapper;
    @Mock
    private ReUserPartyMapMapper userPartyMapMapper;
    @Mock
    private ReTaskBranchAssignmentMapper assignmentMapper;
    @Mock
    private ReTaskTodoMapper todoMapper;
    @Mock
    private ReTaskInstanceMapper instanceMapper;
    @Mock
    private ReTaskMapper taskMapper;
    @Mock
    private ReTaskSubmissionMapper submissionMapper;
    @Mock
    private ReTaskSubmissionFileMapper submissionFileMapper;
    @Mock
    private UserApi userApi;

    @InjectMocks
    private ReTaskAssignmentServiceImpl service;

    @Test
    void allBranches_createsOneAssignmentAndReporterTodoPerBranch() {
        ReTask task = task("ALL_BRANCHES");
        ReTaskInstance instance = instance(10L);
        RePartyOrg root = org(1L, null, 1, "党委");
        RePartyOrg branch = org(2L, 1L, 2, "第一支部");
        ReUserPartyMap reporter = mapping("E001", 2L);
        when(targetMapper.selectList(any())).thenReturn(List.of());
        when(partyOrgMapper.selectList(any())).thenReturn(List.of(root, branch));
        when(userApi.getEmpIdsByRoleCode("R_RE_REPORT")).thenReturn(List.of("E001"));
        when(userPartyMapMapper.selectOne(any())).thenReturn(reporter);
        when(partyOrgMapper.selectById(2L)).thenReturn(branch);
        when(assignmentMapper.selectOne(any())).thenReturn(null);
        when(todoMapper.selectOne(any())).thenReturn(null);
        when(assignmentMapper.insert(any(ReTaskBranchAssignment.class))).thenAnswer(invocation -> {
            ReTaskBranchAssignment value = invocation.getArgument(0);
            value.setId(100L);
            return 1;
        });
        when(todoMapper.insert(any(ReTaskTodo.class))).thenAnswer(invocation -> {
            ReTaskTodo value = invocation.getArgument(0);
            value.setId(200L);
            return 1;
        });

        List<ReTaskBranchAssignment> assignments = service.ensureAssignments(task, instance);

        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).getBranchId()).isEqualTo(2L);
        assertThat(assignments.get(0).getStatus()).isEqualTo(ReTaskAssignmentStatus.UNREPORTED.name());
        ArgumentCaptor<ReTaskTodo> todoCaptor = ArgumentCaptor.forClass(ReTaskTodo.class);
        verify(todoMapper).insert(todoCaptor.capture());
        assertThat(todoCaptor.getValue().getAssignmentId()).isEqualTo(100L);
        assertThat(todoCaptor.getValue().getEmployeeId()).isEqualTo("E001");
        assertThat(todoCaptor.getValue().getRoleCode()).isEqualTo("REPORTER");
        assertThat(todoCaptor.getValue().getAvailableAt()).isEqualTo(instance.getWindowStartAt());
    }

    @Test
    void specifiedEmployees_deduplicatesSameBranch_andOnlyTargetsEmployees() {
        ReTask task = task("SPECIFIED_EMPLOYEES");
        ReTaskInstance instance = instance(11L);
        ReTaskTarget first = employeeTarget("E001");
        ReTaskTarget second = employeeTarget("E002");
        RePartyOrg branch = org(2L, 1L, 2, "第一支部");
        when(targetMapper.selectList(any())).thenReturn(List.of(first, second));
        when(userApi.getEmpIdsByRoleCode("R_RE_REPORT")).thenReturn(List.of("E001", "E002"));
        when(userPartyMapMapper.selectOne(any())).thenReturn(mapping("E001", 2L), mapping("E002", 2L));
        when(partyOrgMapper.selectById(2L)).thenReturn(branch);
        when(assignmentMapper.selectOne(any())).thenReturn(null);
        when(todoMapper.selectOne(any())).thenReturn(null);
        when(assignmentMapper.insert(any(ReTaskBranchAssignment.class))).thenAnswer(invocation -> {
            ReTaskBranchAssignment value = invocation.getArgument(0);
            value.setId(101L);
            return 1;
        });
        when(todoMapper.insert(any(ReTaskTodo.class))).thenReturn(1);

        List<ReTaskBranchAssignment> assignments = service.ensureAssignments(task, instance);

        assertThat(assignments).hasSize(1);
        ArgumentCaptor<ReTaskTodo> todoCaptor = ArgumentCaptor.forClass(ReTaskTodo.class);
        verify(todoMapper, org.mockito.Mockito.times(2)).insert(todoCaptor.capture());
        assertThat(todoCaptor.getAllValues()).extracting(ReTaskTodo::getEmployeeId)
                .containsExactlyInAnyOrder("E001", "E002");
        verify(userApi).getEmpIdsByRoleCode("R_RE_REPORT");
    }

    @Test
    void existingAssignment_doesNotInsertDuplicateOrDuplicateTodo() {
        ReTask task = task("SPECIFIED_BRANCHES");
        ReTaskInstance instance = instance(12L);
        ReTaskTarget target = new ReTaskTarget();
        target.setTargetType("BRANCH");
        target.setBranchId(2L);
        RePartyOrg branch = org(2L, 1L, 2, "第一支部");
        ReTaskBranchAssignment existing = new ReTaskBranchAssignment();
        existing.setId(102L);
        existing.setTaskInstanceId(12L);
        existing.setBranchId(2L);
        existing.setStatus(ReTaskAssignmentStatus.UNREPORTED.name());
        when(targetMapper.selectList(any())).thenReturn(List.of(target));
        when(partyOrgMapper.selectById(2L)).thenReturn(branch);
        when(userApi.getEmpIdsByRoleCode("R_RE_REPORT")).thenReturn(List.of());
        when(assignmentMapper.selectOne(any())).thenReturn(existing);

        List<ReTaskBranchAssignment> assignments = service.ensureAssignments(task, instance);

        assertThat(assignments).containsExactly(existing);
        verify(assignmentMapper, never()).insert(any(ReTaskBranchAssignment.class));
        verify(todoMapper, never()).insert(any(ReTaskTodo.class));
    }

    @Test
    void synchronizeReporterAssignments_movesAllBranchTodoToCurrentMappingAndCancelsOldPendingTodo() {
        String employeeId = "E001";
        Long oldBranchId = 2L;
        Long newBranchId = 3L;
        ReTask task = task(20L, "ALL_BRANCHES");
        ReTaskInstance instance = activeInstance(21L, task.getId());
        ReTaskBranchAssignment oldAssignment = assignment(22L, instance.getId(), oldBranchId,
                ReTaskAssignmentStatus.UNREPORTED.name());
        ReTaskBranchAssignment currentAssignment = assignment(24L, instance.getId(), newBranchId,
                ReTaskAssignmentStatus.UNREPORTED.name());
        RePartyOrg oldBranch = org(oldBranchId, 1L, 2, "旧支部");
        RePartyOrg newBranch = org(newBranchId, 1L, 2, "新支部");
        ReTaskTodo oldPendingTodo = todo(23L, oldAssignment.getId(), employeeId, "REPORTER", "PENDING");

        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));
        when(targetMapper.selectList(any())).thenReturn(List.of());
        when(partyOrgMapper.selectById(oldBranchId)).thenReturn(oldBranch);
        when(partyOrgMapper.selectById(newBranchId)).thenReturn(newBranch);
        when(assignmentMapper.selectList(any())).thenReturn(List.of(oldAssignment, currentAssignment));
        when(assignmentMapper.selectOne(any())).thenReturn(currentAssignment);
        when(todoMapper.selectOne(any())).thenReturn(null);
        when(userApi.getEmpIdsByRoleCode("R_RE_REPORT")).thenReturn(List.of(employeeId));
        when(todoMapper.insert(any(ReTaskTodo.class))).thenAnswer(invocation -> {
            ReTaskTodo value = invocation.getArgument(0);
            value.setId(25L);
            return 1;
        });
        when(todoMapper.update(any(), any())).thenReturn(1);

        service.synchronizeReporterAssignments(employeeId, oldBranchId, newBranchId);

        verify(todoMapper).update(any(), any());
        verify(assignmentMapper, never()).insert(any(ReTaskBranchAssignment.class));
        verify(todoMapper).insert(org.mockito.ArgumentMatchers.<ReTaskTodo>argThat(value ->
                value.getAssignmentId().equals(currentAssignment.getId())
                        && employeeId.equals(value.getEmployeeId())
                        && "REPORTER".equals(value.getRoleCode())
                        && "PENDING".equals(value.getStatus())));
    }

    @Test
    void synchronizeReporterAssignments_preparesFutureOpenInstanceWithoutExposingItBeforeAvailableAt() {
        String employeeId = "E001";
        Long newBranchId = 3L;
        ReTask task = task(26L, "ALL_BRANCHES");
        ReTaskInstance instance = activeInstance(27L, task.getId());
        instance.setWindowStartAt(LocalDateTime.now().plusDays(1));
        instance.setWindowEndAt(LocalDateTime.now().plusDays(2));
        ReTaskBranchAssignment currentAssignment = assignment(28L, instance.getId(), newBranchId,
                ReTaskAssignmentStatus.UNREPORTED.name());

        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));
        when(targetMapper.selectList(any())).thenReturn(List.of());
        when(partyOrgMapper.selectById(newBranchId)).thenReturn(org(newBranchId, 1L, 2, "新支部"));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(currentAssignment));
        when(assignmentMapper.selectOne(any())).thenReturn(currentAssignment);
        when(todoMapper.selectOne(any())).thenReturn(null);
        when(userApi.getEmpIdsByRoleCode("R_RE_REPORT")).thenReturn(List.of(employeeId));
        when(todoMapper.insert(any(ReTaskTodo.class))).thenReturn(1);

        service.synchronizeReporterAssignments(employeeId, null, newBranchId);

        verify(todoMapper).insert(org.mockito.ArgumentMatchers.<ReTaskTodo>argThat(value ->
                value.getAssignmentId().equals(currentAssignment.getId())
                        && employeeId.equals(value.getEmployeeId())
                        && instance.getWindowStartAt().equals(value.getAvailableAt())));
    }

    @Test
    void synchronizeReporterAssignments_forSpecifiedEmployeeDoesNotGrantTodoToOtherEmployee() {
        ReTask task = task(30L, "SPECIFIED_EMPLOYEES");
        ReTaskInstance instance = activeInstance(31L, task.getId());
        ReTaskTarget target = employeeTarget("E001");
        RePartyOrg branch = org(3L, 1L, 2, "当前支部");

        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));
        when(targetMapper.selectList(any())).thenReturn(List.of(target));
        when(partyOrgMapper.selectById(2L)).thenReturn(org(2L, 1L, 2, "旧支部"));
        when(partyOrgMapper.selectById(3L)).thenReturn(branch);
        when(assignmentMapper.selectList(any())).thenReturn(List.of());
        when(userApi.getEmpIdsByRoleCode("R_RE_REPORT")).thenReturn(List.of("E002"));

        service.synchronizeReporterAssignments("E002", 2L, 3L);

        verify(assignmentMapper, never()).insert(any(ReTaskBranchAssignment.class));
        verify(todoMapper, never()).insert(any(ReTaskTodo.class));
    }

    @Test
    void synchronizeReporterAssignments_doesNotGrantTodoToMappedSecretaryWithoutReporterPlatformRole() {
        String employeeId = "SECRETARY-1";
        ReTask task = task(40L, "ALL_BRANCHES");
        ReTaskInstance instance = activeInstance(41L, task.getId());
        ReTaskBranchAssignment oldAssignment = assignment(42L, instance.getId(), 2L,
                ReTaskAssignmentStatus.UNREPORTED.name());
        ReTaskTodo oldPendingTodo = todo(43L, oldAssignment.getId(), employeeId, "REPORTER", "PENDING");

        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));
        when(targetMapper.selectList(any())).thenReturn(List.of());
        when(partyOrgMapper.selectById(2L)).thenReturn(org(2L, 1L, 2, "旧支部"));
        when(partyOrgMapper.selectById(3L)).thenReturn(org(3L, 1L, 2, "新支部"));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(oldAssignment));
        when(userApi.getEmpIdsByRoleCode("R_RE_REPORT")).thenReturn(List.of("OTHER-REPORTER"));
        when(todoMapper.update(any(), any())).thenReturn(1);

        service.synchronizeReporterAssignments(employeeId, 2L, 3L);

        verify(todoMapper).update(any(), any());
        verify(assignmentMapper, never()).insert(any(ReTaskBranchAssignment.class));
        verify(todoMapper, never()).insert(any(ReTaskTodo.class));
    }

    @Test
    void synchronizeReporterAssignments_forSpecifiedEmployeeWithoutReporterPlatformRoleDoesNotCreateTodo() {
        String employeeId = "E001";
        ReTask task = task(50L, "SPECIFIED_EMPLOYEES");
        ReTaskInstance instance = activeInstance(51L, task.getId());
        ReTaskTarget target = employeeTarget(employeeId);

        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));
        when(targetMapper.selectList(any())).thenReturn(List.of(target));
        when(partyOrgMapper.selectById(3L)).thenReturn(org(3L, 1L, 2, "当前支部"));
        when(assignmentMapper.selectList(any())).thenReturn(List.of());
        when(userApi.getEmpIdsByRoleCode("R_RE_REPORT")).thenReturn(List.of("OTHER-REPORTER"));

        service.synchronizeReporterAssignments(employeeId, null, 3L);

        verify(assignmentMapper, never()).insert(any(ReTaskBranchAssignment.class));
        verify(todoMapper, never()).insert(any(ReTaskTodo.class));
    }

    @Test
    void pageAssignmentsUsesDatabasePageAndKeepsDatabaseTotal() {
        ReTaskBranchAssignment assignment = new ReTaskBranchAssignment();
        assignment.setId(301L);
        assignment.setTaskInstanceId(302L);
        assignment.setBranchId(2L);
        assignment.setStatus(ReTaskAssignmentStatus.ORG_PENDING.name());
        Page<ReTaskBranchAssignment> databasePage = new Page<>(2, 1);
        databasePage.setTotal(3L);
        databasePage.setRecords(List.of(assignment));

        ReTaskAssignmentPageQueryDTO query = new ReTaskAssignmentPageQueryDTO();
        query.setPageNo(2);
        query.setPageSize(1);
        query.setKeyword("第一支部");
        query.setBranchId(2L);
        query.setStatus(ReTaskAssignmentStatus.ORG_PENDING);
        query.setSubmittedStartAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        query.setSubmittedEndAt(LocalDateTime.of(2026, 8, 31, 23, 59, 59));

        ReTaskInstance instance = instance(302L);
        when(assignmentMapper.selectTaskAssignmentPage(any(), eq(1L), eq(2L),
                eq(ReTaskAssignmentStatus.ORG_PENDING.name()), eq("第一支部"), any(),
                eq(query.getSubmittedStartAt()), eq(query.getSubmittedEndAt())))
                .thenReturn(databasePage);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));
        when(submissionMapper.selectList(any())).thenReturn(List.of());
        when(partyOrgMapper.selectById(2L)).thenReturn(org(2L, 1L, 2, "第一支部"));

        PageResult<ReTaskAssignmentDTO> result = service.pageAssignments(1L, query);

        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(1);
        assertThat(result.getTotal()).isEqualTo(3L);
        assertThat(result.getRecords()).extracting(ReTaskAssignmentDTO::getAssignmentId)
                .containsExactly(301L);
        ArgumentCaptor<IPage> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        verify(assignmentMapper).selectTaskAssignmentPage(pageCaptor.capture(), eq(1L), eq(2L),
                eq(ReTaskAssignmentStatus.ORG_PENDING.name()), eq("第一支部"), any(),
                eq(query.getSubmittedStartAt()), eq(query.getSubmittedEndAt()));
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(1L);
        verify(assignmentMapper, never()).selectList(any());
    }

    private static ReTask task(String audienceType) {
        ReTask task = new ReTask();
        task.setId(1L);
        task.setAudienceType(audienceType);
        task.setStatus(ReTaskStatus.PUBLISHED);
        return task;
    }

    private static ReTaskInstance instance(Long id) {
        ReTaskInstance instance = new ReTaskInstance();
        instance.setId(id);
        instance.setTaskId(1L);
        instance.setWindowStartAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        instance.setWindowEndAt(LocalDateTime.of(2026, 8, 5, 23, 59, 59));
        return instance;
    }

    private static ReTaskInstance activeInstance(Long id, Long taskId) {
        ReTaskInstance instance = new ReTaskInstance();
        instance.setId(id);
        instance.setTaskId(taskId);
        instance.setStatus(com.bank.branch.platform.redengine.api.dto.ReTaskInstanceStatus.OPEN);
        instance.setWindowStartAt(LocalDateTime.now().minusDays(1));
        instance.setWindowEndAt(LocalDateTime.now().plusDays(1));
        return instance;
    }

    private static ReTask task(Long id, String audienceType) {
        ReTask task = new ReTask();
        task.setId(id);
        task.setAudienceType(audienceType);
        task.setStatus(ReTaskStatus.PUBLISHED);
        task.setDeleted(0);
        return task;
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

    private static RePartyOrg org(Long id, Long parentId, int level, String name) {
        RePartyOrg org = new RePartyOrg();
        org.setId(id);
        org.setParentId(parentId);
        org.setOrgLevel(level);
        org.setOrgName(name);
        return org;
    }

    private static ReUserPartyMap mapping(String employeeId, Long partyOrgId) {
        ReUserPartyMap mapping = new ReUserPartyMap();
        mapping.setUserId(employeeId);
        mapping.setPartyOrgId(partyOrgId);
        mapping.setPartyRole("REPORTER");
        return mapping;
    }

    private static ReTaskTarget employeeTarget(String employeeId) {
        ReTaskTarget target = new ReTaskTarget();
        target.setTargetType("EMPLOYEE");
        target.setEmployeeId(employeeId);
        return target;
    }
}
