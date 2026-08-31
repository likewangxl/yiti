package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskBusinessType;
import com.bank.branch.platform.redengine.api.dto.ReTaskCreateReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskCycleType;
import com.bank.branch.platform.redengine.api.dto.ReTaskNature;
import com.bank.branch.platform.redengine.api.dto.ReTaskTargetDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskTargetType;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskStatusHistory;
import com.bank.branch.platform.redengine.entity.ReUserPartyMap;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskFileTypeMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskStatusHistoryMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionFileMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTargetMapper;
import com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 任务定义创建、发布和实体级权限单元测试。 */
@ExtendWith(MockitoExtension.class)
class ReTaskManagementServiceTest {

    @Mock
    private ReTaskMapper taskMapper;
    @Mock
    private ReTaskFileTypeMapper fileTypeMapper;
    @Mock
    private ReTaskTargetMapper targetMapper;
    @Mock
    private ReTaskInstanceMapper instanceMapper;
    @Mock
    private ReTaskBranchAssignmentMapper assignmentMapper;
    @Mock
    private ReTaskSubmissionMapper submissionMapper;
    @Mock
    private ReTaskSubmissionFileMapper submissionFileMapper;
    @Mock
    private ReTaskStatusHistoryMapper historyMapper;
    @Mock
    private RePartyOrgMapper partyOrgMapper;
    @Mock
    private ReUserPartyMapMapper userPartyMapMapper;
    @Mock
    private CurrentUserApi currentUserApi;
    @Mock
    private UserApi userApi;
    @Mock
    private ReTaskScheduleService scheduleService;
    @Mock
    private ReTaskAssignmentService assignmentService;

    @InjectMocks
    private ReTaskManagementServiceImpl service;

    @Test
    void orgReviewer_canCreateAndPublishTemporaryTask_andGenerateInstanceAssignments() {
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_ORGREV"));
        when(currentUserApi.getCurrentEmpId()).thenReturn("ORG-001");
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(partyOrgMapper.selectById(20L)).thenReturn(branch(20L));
        when(userApi.getUserByEmpId("E001")).thenReturn(user("E001"));
        when(userPartyMapMapper.selectOne(any())).thenReturn(mapping("E001", 20L));
        when(taskMapper.insert(any(ReTask.class))).thenAnswer(invocation -> {
            ReTask task = invocation.getArgument(0);
            task.setId(99L);
            return 1;
        });
        when(instanceMapper.insert(any(ReTaskInstance.class))).thenAnswer(invocation -> {
            ReTaskInstance instance = invocation.getArgument(0);
            instance.setId(100L);
            return 1;
        });
        when(assignmentService.ensureAssignments(any(ReTask.class), any(ReTaskInstance.class)))
                .thenReturn(List.of());

        ReTaskCreateReqDTO request = temporaryRequest();

        var result = service.createAndPublish(request, "ORG-001");

        assertThat(result.getTaskId()).isEqualTo(99L);
        ArgumentCaptor<ReTask> taskCaptor = ArgumentCaptor.forClass(ReTask.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(com.bank.branch.platform.redengine.api.dto.ReTaskStatus.PUBLISHED);
        assertThat(taskCaptor.getValue().getNature()).isEqualTo("TEMPORARY");
        assertThat(taskCaptor.getValue().getAudienceType()).isEqualTo("SPECIFIED_EMPLOYEES");
        verify(instanceMapper).insert(any(ReTaskInstance.class));
        verify(assignmentService).ensureAssignments(eq(taskCaptor.getValue()), any(ReTaskInstance.class));
        verify(historyMapper).insert(any(ReTaskStatusHistory.class));
    }

    @Test
    void reporterCannotCreateTask_andNoPersistenceIsAttempted() {
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_REPORT"));
        when(currentUserApi.isSystemAdmin()).thenReturn(false);
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");

        assertThatThrownBy(() -> service.createAndPublish(temporaryRequest(), "E001"))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .hasMessageContaining("无权新增或发布任务");

        verify(taskMapper, never()).insert(any(ReTask.class));
        verify(instanceMapper, never()).insert(any(ReTaskInstance.class));
    }

    @Test
    void scheduledTask_usesBeijingCurrentWindowAndStoresInclusiveWindow() {
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("SYS_ADMIN"));
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(scheduleService.calculateWindow(eq(ReTaskCycleType.MONTH_END), any(), eq(3)))
                .thenReturn(window(ReTaskCycleType.MONTH_END, "2026-08", 2026, 8, 29, 2026, 8, 31));
        when(taskMapper.insert(any(ReTask.class))).thenAnswer(invocation -> {
            ReTask task = invocation.getArgument(0);
            task.setId(199L);
            return 1;
        });
        when(instanceMapper.insert(any(ReTaskInstance.class))).thenAnswer(invocation -> {
            ReTaskInstance instance = invocation.getArgument(0);
            instance.setId(299L);
            return 1;
        });
        when(assignmentService.ensureAssignments(any(), any())).thenReturn(List.of());

        ReTaskCreateReqDTO request = scheduledRequest();
        service.createAndPublish(request, "admin");

        ArgumentCaptor<ReTaskInstance> instanceCaptor = ArgumentCaptor.forClass(ReTaskInstance.class);
        verify(instanceMapper).insert(instanceCaptor.capture());
        assertThat(instanceCaptor.getValue().getPeriodKey()).isEqualTo("2026-08");
        assertThat(instanceCaptor.getValue().getWindowStartAt()).isEqualTo(LocalDateTime.of(2026, 8, 29, 0, 0));
        assertThat(instanceCaptor.getValue().getWindowEndAt()).isEqualTo(LocalDateTime.of(2026, 8, 31, 23, 59, 59));
    }

    private ReTaskCreateReqDTO temporaryRequest() {
        ReTaskCreateReqDTO request = new ReTaskCreateReqDTO();
        request.setTitle("临时任务");
        request.setDescription("请填报");
        request.setTaskNature(ReTaskNature.TEMPORARY);
        request.setBusinessType(ReTaskBusinessType.GENERAL);
        request.setTemporaryStartTime(LocalDateTime.of(2026, 8, 31, 9, 0));
        request.setTemporaryEndTime(LocalDateTime.of(2026, 9, 2, 18, 0));
        request.setRequiresFile(false);
        ReTaskTargetDTO target = new ReTaskTargetDTO();
        target.setTargetType(ReTaskTargetType.SPECIFIED_EMPLOYEE);
        target.setEmployeeId("E001");
        request.setTargets(List.of(target));
        return request;
    }

    private ReTaskCreateReqDTO scheduledRequest() {
        ReTaskCreateReqDTO request = new ReTaskCreateReqDTO();
        request.setTitle("月底任务");
        request.setDescription("请按时上报");
        request.setTaskNature(ReTaskNature.SCHEDULED);
        request.setBusinessType(ReTaskBusinessType.GENERAL);
        request.setCycleType(ReTaskCycleType.MONTH_END);
        request.setDurationDays(3);
        request.setRequiresFile(false);
        ReTaskTargetDTO target = new ReTaskTargetDTO();
        target.setTargetType(ReTaskTargetType.ALL_BRANCH);
        request.setTargets(List.of(target));
        return request;
    }

    private static RePartyOrg branch(Long id) {
        RePartyOrg branch = new RePartyOrg();
        branch.setId(id);
        branch.setParentId(1L);
        branch.setOrgLevel(2);
        branch.setOrgName("第一支部");
        branch.setOrgCode("B-" + id);
        return branch;
    }

    private static ReUserPartyMap mapping(String userId, Long partyOrgId) {
        ReUserPartyMap mapping = new ReUserPartyMap();
        mapping.setUserId(userId);
        mapping.setPartyOrgId(partyOrgId);
        return mapping;
    }

    private static com.bank.branch.platform.auth.api.dto.UserDTO user(String empId) {
        com.bank.branch.platform.auth.api.dto.UserDTO user = new com.bank.branch.platform.auth.api.dto.UserDTO();
        user.setEmpId(empId);
        user.setDisplayName("张三");
        return user;
    }

    private static com.bank.branch.platform.redengine.api.dto.ReTaskScheduleWindowDTO window(
            ReTaskCycleType cycle, String period, int startYear, int startMonth, int startDay,
            int endYear, int endMonth, int endDay) {
        var result = new com.bank.branch.platform.redengine.api.dto.ReTaskScheduleWindowDTO();
        result.setCycleType(cycle);
        result.setPeriodKey(period);
        result.setStartDate(java.time.LocalDate.of(startYear, startMonth, startDay));
        result.setEndDate(java.time.LocalDate.of(endYear, endMonth, endDay));
        return result;
    }
}
