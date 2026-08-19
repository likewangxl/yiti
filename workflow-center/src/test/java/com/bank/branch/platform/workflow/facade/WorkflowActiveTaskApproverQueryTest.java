package com.bank.branch.platform.workflow.facade;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.workflow.api.dto.TaskCandidateUserDTO;
import com.bank.branch.platform.workflow.service.ProcessQueryService;
import com.bank.branch.platform.workflow.service.ProcessStartService;
import com.bank.branch.platform.workflow.service.TodoQueryService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 当前活动节点可审批员工查询契约。 */
@ExtendWith(MockitoExtension.class)
class WorkflowActiveTaskApproverQueryTest {

    @Mock private TodoQueryService todoQueryService;
    @Mock private ProcessQueryService processQueryService;
    @Mock private ProcessStartService processStartService;
    @Mock private HistoryService historyService;
    @Mock private TaskService taskService;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private UserApi userApi;

    @InjectMocks private WorkflowQueryFacade facade;

    @Test
    void getActiveTaskCandidates_expandsActualCandidateUsersAndGroups() {
        Task task = mockActiveTask("TASK_1", null);
        IdentityLink direct = candidateUser("E001");
        IdentityLink role = candidateGroup("ROLE:R_REVIEW");
        IdentityLink org = candidateGroup("ORG:ORG02");
        IdentityLink userGroup = candidateGroup("USER:E004");
        when(taskService.getIdentityLinksForTask("TASK_1"))
                .thenReturn(List.of(direct, role, org, userGroup));
        when(userApi.getEmpIdsByRoleCode("R_REVIEW")).thenReturn(List.of("E002", "E001"));
        when(userApi.getEmpIdsByOrg("ORG02")).thenReturn(List.of("E003"));
        when(userApi.getUserByEmpIds(List.of("E001", "E002", "E003", "E004")))
                .thenReturn(List.of(
                        user("E001", "10001", "张三"),
                        user("E002", "10002", "李四"),
                        user("E003", "10003", "王五"),
                        user("E004", "10004", "赵六")));

        List<TaskCandidateUserDTO> result = facade.getActiveTaskCandidates("PI_1");

        assertThat(result).extracting(TaskCandidateUserDTO::getEmpId)
                .containsExactly("E001", "E002", "E003", "E004");
        assertThat(result).extracting(TaskCandidateUserDTO::getEmployeeNo)
                .containsExactly("10001", "10002", "10003", "10004");
        assertThat(result).extracting(TaskCandidateUserDTO::getEmployeeName)
                .containsExactly("张三", "李四", "王五", "赵六");
    }

    @Test
    void getActiveTaskCandidates_claimedTaskOnlyReturnsAssignee() {
        mockActiveTask("TASK_2", "E009");
        when(userApi.getUserByEmpIds(List.of("E009")))
                .thenReturn(List.of(user("E009", "10009", "审核人")));

        List<TaskCandidateUserDTO> result = facade.getActiveTaskCandidates("PI_2");

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.getEmployeeNo()).isEqualTo("10009");
            assertThat(candidate.getEmployeeName()).isEqualTo("审核人");
        });
        verify(taskService, never()).getIdentityLinksForTask("TASK_2");
    }

    @Test
    void getActiveTaskCandidates_reviewedNodeHasNoRuntimeTask_returnsEmpty() {
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.processInstanceId("PI_DONE")).thenReturn(query);
        when(query.active()).thenReturn(query);
        when(query.list()).thenReturn(List.of());

        assertThat(facade.getActiveTaskCandidates("PI_DONE")).isEmpty();
        verify(userApi, never()).getUserByEmpIds(org.mockito.ArgumentMatchers.anyList());
    }

    private Task mockActiveTask(String taskId, String assignee) {
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.processInstanceId(org.mockito.ArgumentMatchers.anyString())).thenReturn(query);
        when(query.active()).thenReturn(query);
        Task task = mock(Task.class);
        if (assignee == null) {
            when(task.getId()).thenReturn(taskId);
        }
        when(task.getAssignee()).thenReturn(assignee);
        when(query.list()).thenReturn(List.of(task));
        return task;
    }

    private IdentityLink candidateUser(String empId) {
        IdentityLink link = mock(IdentityLink.class);
        when(link.getType()).thenReturn("candidate");
        when(link.getUserId()).thenReturn(empId);
        return link;
    }

    private IdentityLink candidateGroup(String groupId) {
        IdentityLink link = mock(IdentityLink.class);
        when(link.getType()).thenReturn("candidate");
        when(link.getGroupId()).thenReturn(groupId);
        return link;
    }

    private UserDTO user(String empId, String employeeNo, String name) {
        UserDTO user = new UserDTO();
        user.setEmpId(empId);
        user.setUsername(employeeNo);
        user.setDisplayName(name);
        return user;
    }
}
