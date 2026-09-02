package com.bank.branch.platform.workflow.listener;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.workflow.api.dto.ApproverGroupDTO;
import com.bank.branch.platform.workflow.service.CandidateResolverService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 机构分组顺序会签入口监听器契约（先写红测，再实现）。
 */
@ExtendWith(MockitoExtension.class)
class MultiInstanceApproverGroupResolverTest {

    @Mock
    private CandidateResolverService candidateResolverService;

    @Mock
    private UserApi userApi;

    @Mock
    private RepositoryService repositoryService;

    @InjectMocks
    private MultiInstanceApproverGroupResolver resolver;

    @Test
    void resolves_var_groups_to_runtime_collection_and_initializes_rejected() {
        String processDefinitionId = "PD_GROUP_1";
        DelegateExecution execution = mock(DelegateExecution.class);
        stubProcessDefinition(execution, processDefinitionId, "DSN_alloc");
        when(execution.getCurrentActivityId()).thenReturn("original_owner_approve");
        when(execution.getVariable("rejected")).thenReturn(null);
        when(candidateResolverService.resolveCandidates("DSN_alloc", "original_owner_approve"))
                .thenReturn(List.of("VAR:originalOwnerOrgApprovalGroups"));
        List<ApproverGroupDTO> groups = List.of(
                new ApproverGroupDTO("ORG_A", "机构A", List.of("A1", "A2")),
                new ApproverGroupDTO("ORG_B", "机构B", List.of("B1")));
        when(execution.getVariable("originalOwnerOrgApprovalGroups")).thenReturn(groups);

        resolver.notify(execution);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ApproverGroupDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(execution).setVariable(eq(MultiInstanceApproverGroupResolver.VAR_APPROVER_GROUPS), captor.capture());
        assertThat(captor.getValue()).containsExactlyElementsOf(groups);
        verify(execution).setVariable("rejected", false);
        verify(userApi, never()).getEmpIdsByRoleCode(anyString());
    }

    @Test
    void accepts_serialized_map_groups_but_rejects_blank_group_or_approver() {
        String processDefinitionId = "PD_GROUP_2";
        DelegateExecution execution = mock(DelegateExecution.class);
        stubProcessDefinition(execution, processDefinitionId, "DSN_alloc");
        when(execution.getCurrentActivityId()).thenReturn("approval");
        when(execution.getVariable("rejected")).thenReturn(false);
        when(candidateResolverService.resolveCandidates("DSN_alloc", "approval"))
                .thenReturn(List.of("VAR:groups"));
        when(execution.getVariable("groups")).thenReturn(List.of(
                Map.of("groupKey", "", "groupName", "机构A", "approverEmpIds", List.of("A1"))));

        assertThatThrownBy(() -> resolver.notify(execution))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("机构审批分组");
        verify(execution, never()).setVariable(eq(MultiInstanceApproverGroupResolver.VAR_APPROVER_GROUPS), any());
    }

    @Test
    void missing_or_non_var_configuration_fails_closed() {
        String processDefinitionId = "PD_GROUP_3";
        DelegateExecution execution = mock(DelegateExecution.class);
        stubProcessDefinition(execution, processDefinitionId, "DSN_alloc");
        when(execution.getCurrentActivityId()).thenReturn("approval");
        when(execution.getVariable("rejected")).thenReturn(false);
        when(candidateResolverService.resolveCandidates("DSN_alloc", "approval"))
                .thenReturn(List.of("ROLE:BRANCH_HEAD"));

        assertThatThrownBy(() -> resolver.notify(execution))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VAR");
    }

    private void stubProcessDefinition(DelegateExecution execution, String processDefinitionId, String key) {
        when(execution.getProcessDefinitionId()).thenReturn(processDefinitionId);
        ProcessDefinition processDefinition = mock(ProcessDefinition.class);
        when(processDefinition.getKey()).thenReturn(key);
        when(repositoryService.getProcessDefinition(processDefinitionId)).thenReturn(processDefinition);
    }
}
