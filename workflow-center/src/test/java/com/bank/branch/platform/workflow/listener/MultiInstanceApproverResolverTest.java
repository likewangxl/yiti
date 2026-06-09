package com.bank.branch.platform.workflow.listener;

import com.bank.branch.platform.auth.api.UserApi;
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

/**
 * MultiInstanceApproverResolver 单元测试（TDD）。
 * <p>
 * 验证会签节点进入时，审批人规则被正确展开并写入流程变量 approverEmpIds。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class MultiInstanceApproverResolverTest {

    @Mock
    private CandidateResolverService candidateResolverService;

    @Mock
    private UserApi userApi;

    @Mock
    private RepositoryService repositoryService;

    @InjectMocks
    private MultiInstanceApproverResolver resolver;

    // -----------------------------------------------------------------------
    // 工具方法：mock RepositoryService 把 processDefinitionId → KEY
    // -----------------------------------------------------------------------
    private void stubProcDefKey(String processDefinitionId, String key) {
        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getKey()).thenReturn(key);
        when(repositoryService.getProcessDefinition(processDefinitionId)).thenReturn(pd);
    }

    // -----------------------------------------------------------------------
    // Test 1: ROLE 候选展开成员工 ID 集合，set 到 approverEmpIds
    // -----------------------------------------------------------------------
    @Test
    void resolves_and_sets_collection() {
        // Arrange
        String pdId = "DSN_corp_review:1:abc";
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessDefinitionId()).thenReturn(pdId);
        when(execution.getCurrentActivityId()).thenReturn("mi_approve_node");
        stubProcDefKey(pdId, "DSN_x");

        when(candidateResolverService.resolveCandidates("DSN_x", "mi_approve_node"))
                .thenReturn(List.of("ROLE:CORP_DEPT"));
        when(userApi.getEmpIdsByRoleCode("CORP_DEPT"))
                .thenReturn(List.of("E1", "E2"));

        // Act
        resolver.notify(execution);

        // Assert：approverEmpIds 包含 E1、E2
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(execution).setVariable(eq("approverEmpIds"), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("E1", "E2");
    }

    // -----------------------------------------------------------------------
    // Test 2: resolveCandidates 返回空 → approverEmpIds 为空 List，不 NPE
    // -----------------------------------------------------------------------
    @Test
    void empty_resolves_to_empty_list_no_npe() {
        // Arrange
        String pdId = "DSN_empty:1:xyz";
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessDefinitionId()).thenReturn(pdId);
        when(execution.getCurrentActivityId()).thenReturn("mi_node");
        stubProcDefKey(pdId, "DSN_x");

        when(candidateResolverService.resolveCandidates("DSN_x", "mi_node"))
                .thenReturn(Collections.emptyList());

        // Act — 不应抛异常
        assertThatNoException().isThrownBy(() -> resolver.notify(execution));

        // Assert：set 的是空集合
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(execution).setVariable(eq("approverEmpIds"), captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Test 3: USER 前缀候选直接放进 approverEmpIds，不调 UserApi
    // -----------------------------------------------------------------------
    @Test
    void user_prefix_candidate_sets_directly_without_api_call() {
        // Arrange
        String pdId = "uuid-only-no-colon";
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessDefinitionId()).thenReturn(pdId);
        when(execution.getCurrentActivityId()).thenReturn("mi_node");
        stubProcDefKey(pdId, "DSN_x");

        when(candidateResolverService.resolveCandidates("DSN_x", "mi_node"))
                .thenReturn(List.of("USER:E99"));

        // Act
        resolver.notify(execution);

        // Assert：E99 进集合，UserApi 不被调用
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(execution).setVariable(eq("approverEmpIds"), captor.capture());
        assertThat(captor.getValue()).containsExactly("E99");
        verify(userApi, never()).getEmpIdsByRoleCode(anyString());
    }

    // -----------------------------------------------------------------------
    // Test 3b: VAR 候选 —— 从流程变量(列表)取审批人名单（会签原业绩所属人场景）
    // -----------------------------------------------------------------------
    @Test
    void var_candidate_resolves_from_process_variable_list() {
        String pdId = "DSN_owner:1:1";
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessDefinitionId()).thenReturn(pdId);
        when(execution.getCurrentActivityId()).thenReturn("owner_node");
        stubProcDefKey(pdId, "DSN_x");

        when(candidateResolverService.resolveCandidates("DSN_x", "owner_node"))
                .thenReturn(List.of("VAR:originalOwnerEmpIds"));
        when(execution.getVariable("originalOwnerEmpIds")).thenReturn(List.of("E1", "E2"));

        resolver.notify(execution);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(execution).setVariable(eq("approverEmpIds"), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("E1", "E2");
        verify(userApi, never()).getEmpIdsByRoleCode(anyString());
    }

    // -----------------------------------------------------------------------
    // Test 3c: VAR 候选 —— 流程变量为单个字符串 → 单元素集合
    // -----------------------------------------------------------------------
    @Test
    void var_candidate_single_string_value() {
        String pdId = "DSN_owner:1:2";
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessDefinitionId()).thenReturn(pdId);
        when(execution.getCurrentActivityId()).thenReturn("owner_node");
        stubProcDefKey(pdId, "DSN_x");

        when(candidateResolverService.resolveCandidates("DSN_x", "owner_node"))
                .thenReturn(List.of("VAR:ownerEmpId"));
        when(execution.getVariable("ownerEmpId")).thenReturn("E9");

        resolver.notify(execution);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(execution).setVariable(eq("approverEmpIds"), captor.capture());
        assertThat(captor.getValue()).containsExactly("E9");
    }

    // -----------------------------------------------------------------------
    // Test 4: 异常情况 — resolveCandidates 抛异常，吞掉后 set 空 List
    // -----------------------------------------------------------------------
    @Test
    void exception_in_resolve_sets_empty_list_does_not_propagate() {
        // Arrange
        String pdId = "DSN_err:1:zzz";
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessDefinitionId()).thenReturn(pdId);
        when(execution.getCurrentActivityId()).thenReturn("mi_node");
        stubProcDefKey(pdId, "DSN_x");

        when(candidateResolverService.resolveCandidates("DSN_x", "mi_node"))
                .thenThrow(new RuntimeException("DB 连接失败"));

        // Act — 不应抛异常
        assertThatNoException().isThrownBy(() -> resolver.notify(execution));

        // Assert：即使异常也要 set 空集合
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(execution).setVariable(eq("approverEmpIds"), captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }
}
