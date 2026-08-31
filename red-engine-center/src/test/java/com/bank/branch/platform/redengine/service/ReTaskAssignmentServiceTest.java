package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.auth.api.UserApi;
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
import com.bank.branch.platform.redengine.mapper.ReTaskTargetMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTodoMapper;
import com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务发布分配单元测试：验证目标范围展开、支部去重、报送员待办和幂等生成。
 * <p>纯 Mockito，不连接数据库，也不依赖 Quartz。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReTaskAssignmentServiceTest {

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
        verify(userApi, never()).getEmpIdsByRoleCode("R_RE_REPORT");
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
