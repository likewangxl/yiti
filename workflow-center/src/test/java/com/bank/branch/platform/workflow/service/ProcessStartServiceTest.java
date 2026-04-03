package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.enums.ProcessStatus;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ProcessStartService 单元测试。
 * <p>
 * 验证流程启动服务的核心逻辑：
 * 1. 流程定义不存在时抛 WF-40401
 * 2. 业务键已有运行中流程时抛 WF-40901
 * 3. 正常启动流程时写入映射记录并返回响应
 * 4. 启动成功后发布事件
 * 5. 按业务键查询映射记录
 * 6. 按业务键查询不到时抛 WF-40402
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ProcessStartServiceTest {

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private TaskService taskService;

    @Mock
    private BizProcessMapMapper bizProcessMapMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProcessStartService processStartService;

    /**
     * 构建标准的启动命令对象
     */
    private StartProcessCmd buildCmd() {
        StartProcessCmd cmd = new StartProcessCmd();
        cmd.setBizType("LOAN");
        cmd.setBizId("LA202603060001");
        cmd.setBusinessKey("LOAN:LA202603060001");
        cmd.setProcessDefinitionKey("loan_approve_v1");
        cmd.setStartUser("E001");
        cmd.setStartOrgId("ORG001");
        cmd.setTitle("资产投放申请 - XX科技有限公司");
        Map<String, Object> vars = new HashMap<>();
        vars.put("customerType", "CORPORATE");
        cmd.setVariables(vars);
        return cmd;
    }

    /**
     * 模拟流程定义查询链 —— 返回指定的流程定义（可为 null）
     */
    private void mockProcessDefinitionQuery(ProcessDefinition result) {
        ProcessDefinitionQuery pdq = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdq);
        when(pdq.processDefinitionKey(anyString())).thenReturn(pdq);
        when(pdq.latestVersion()).thenReturn(pdq);
        when(pdq.singleResult()).thenReturn(result);
    }

    /**
     * 模拟任务查询链 —— 返回指定的任务（可为 null）
     */
    private void mockTaskQuery(Task result) {
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceId(anyString())).thenReturn(tq);
        when(tq.singleResult()).thenReturn(result);
    }

    /**
     * 流程定义不存在时，应抛出 WF-40401 异常
     */
    @Test
    void startProcess_defNotFound_throwsWf40401() {
        // given
        StartProcessCmd cmd = buildCmd();
        mockProcessDefinitionQuery(null);

        // when & then
        assertThatThrownBy(() -> processStartService.startProcess(cmd))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40401");
    }

    /**
     * 业务键已存在运行中流程时，应抛出 WF-40901 异常
     */
    @Test
    void startProcess_businessKeyRunning_throwsWf40901() {
        // given
        StartProcessCmd cmd = buildCmd();
        ProcessDefinition pd = mock(ProcessDefinition.class);
        mockProcessDefinitionQuery(pd);
        when(bizProcessMapMapper.existsRunningByBusinessKey(cmd.getBusinessKey())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> processStartService.startProcess(cmd))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40901");
    }

    /**
     * 正常启动流程时，应写入 biz_process_map 映射记录并返回正确响应
     */
    @Test
    void startProcess_success_insertsMapAndReturns() {
        // given
        StartProcessCmd cmd = buildCmd();
        ProcessDefinition pd = mock(ProcessDefinition.class);
        mockProcessDefinitionQuery(pd);
        when(bizProcessMapMapper.existsRunningByBusinessKey(cmd.getBusinessKey())).thenReturn(false);

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn("PID_001");
        when(runtimeService.startProcessInstanceByKey(
                cmd.getProcessDefinitionKey(), cmd.getBusinessKey(), cmd.getVariables()))
                .thenReturn(pi);

        Task task = mock(Task.class);
        when(task.getId()).thenReturn("TASK_001");
        mockTaskQuery(task);

        // when
        WorkflowLaunchResp resp = processStartService.startProcess(cmd);

        // then
        assertThat(resp.getProcessInstanceId()).isEqualTo("PID_001");
        assertThat(resp.getBusinessKey()).isEqualTo("LOAN:LA202603060001");
        assertThat(resp.getFirstTaskId()).isEqualTo("TASK_001");

        ArgumentCaptor<BizProcessMap> captor = ArgumentCaptor.forClass(BizProcessMap.class);
        verify(bizProcessMapMapper).insert(captor.capture());
        BizProcessMap inserted = captor.getValue();
        assertThat(inserted.getBizType()).isEqualTo("LOAN");
        assertThat(inserted.getBizId()).isEqualTo("LA202603060001");
        assertThat(inserted.getBusinessKey()).isEqualTo("LOAN:LA202603060001");
        assertThat(inserted.getProcessDefinitionKey()).isEqualTo("loan_approve_v1");
        assertThat(inserted.getProcessInstanceId()).isEqualTo("PID_001");
        assertThat(inserted.getProcessStatus()).isEqualTo(ProcessStatus.RUNNING.getCode());
        assertThat(inserted.getStartUser()).isEqualTo("E001");
        assertThat(inserted.getStartTime()).isNotNull();
        assertThat(inserted.getId()).isNotNull();
    }

    /**
     * 正常启动流程后，应发布事件
     */
    @Test
    void startProcess_success_publishesEvent() {
        // given
        StartProcessCmd cmd = buildCmd();
        ProcessDefinition pd = mock(ProcessDefinition.class);
        mockProcessDefinitionQuery(pd);
        when(bizProcessMapMapper.existsRunningByBusinessKey(cmd.getBusinessKey())).thenReturn(false);

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn("PID_001");
        when(runtimeService.startProcessInstanceByKey(
                cmd.getProcessDefinitionKey(), cmd.getBusinessKey(), cmd.getVariables()))
                .thenReturn(pi);

        Task task = mock(Task.class);
        lenient().when(task.getId()).thenReturn("TASK_001");
        mockTaskQuery(task);

        // when
        processStartService.startProcess(cmd);

        // then
        verify(eventPublisher).publishEvent(any(ProcessStartService.ProcessStartedEvent.class));
    }

    /**
     * 按业务键查询到映射记录时，应返回对应的 DTO
     */
    @Test
    void getProcessByBusinessKey_found() {
        // given
        BizProcessMap entity = new BizProcessMap();
        entity.setId("MAP_001");
        entity.setBizType("LOAN");
        entity.setBizId("LA202603060001");
        entity.setBusinessKey("LOAN:LA202603060001");
        entity.setProcessDefinitionKey("loan_approve_v1");
        entity.setProcessInstanceId("PID_001");
        entity.setProcessStatus(ProcessStatus.RUNNING.getCode());
        entity.setStartUser("E001");

        when(bizProcessMapMapper.selectByBusinessKey("LOAN:LA202603060001")).thenReturn(entity);

        // when
        BizProcessMapDTO dto = processStartService.getProcessByBusinessKey("LOAN:LA202603060001");

        // then
        assertThat(dto.getId()).isEqualTo("MAP_001");
        assertThat(dto.getBizType()).isEqualTo("LOAN");
        assertThat(dto.getProcessInstanceId()).isEqualTo("PID_001");
        assertThat(dto.getProcessStatus()).isEqualTo("RUNNING");
    }

    /**
     * 按业务键查询不到映射记录时，应抛出 WF-40402 异常
     */
    @Test
    void getProcessByBusinessKey_notFound_throwsWf40402() {
        // given
        when(bizProcessMapMapper.selectByBusinessKey("NONEXISTENT")).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> processStartService.getProcessByBusinessKey("NONEXISTENT"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40402");
    }

    /**
     * 按业务类型和业务ID查询到映射记录时，应返回对应的 DTO
     */
    @Test
    void getProcessByBizTypeAndBizId_found() {
        // given
        BizProcessMap entity = new BizProcessMap();
        entity.setId("MAP_002");
        entity.setBizType("LEAD");
        entity.setBizId("LD001");
        entity.setBusinessKey("LEAD:LD001");
        entity.setProcessDefinitionKey("lead_approve_v1");
        entity.setProcessInstanceId("PID_002");
        entity.setProcessStatus(ProcessStatus.COMPLETED.getCode());

        when(bizProcessMapMapper.selectByBizTypeAndBizId("LEAD", "LD001")).thenReturn(entity);

        // when
        BizProcessMapDTO dto = processStartService.getProcessByBizTypeAndBizId("LEAD", "LD001");

        // then
        assertThat(dto.getId()).isEqualTo("MAP_002");
        assertThat(dto.getProcessStatus()).isEqualTo("COMPLETED");
    }

    /**
     * 按业务类型和业务ID查询不到映射记录时，应抛出 WF-40402 异常
     */
    @Test
    void getProcessByBizTypeAndBizId_notFound_throwsWf40402() {
        // given
        when(bizProcessMapMapper.selectByBizTypeAndBizId("LOAN", "NONEXIST")).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> processStartService.getProcessByBizTypeAndBizId("LOAN", "NONEXIST"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40402");
    }

    // ── L1 补全测试 ──────────────────────────────────────────────

    /**
     * 测试 startProcess：首个节点为自动任务时 firstTask 为 null，不应报错
     */
    @Test
    void startProcess_firstTaskNull_returnsNullTaskId() {
        // given —— 首个节点是自动任务，无用户任务
        StartProcessCmd cmd = buildCmd();
        ProcessDefinition pd = mock(ProcessDefinition.class);
        mockProcessDefinitionQuery(pd);
        when(bizProcessMapMapper.existsRunningByBusinessKey(cmd.getBusinessKey())).thenReturn(false);

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn("PID_AUTO");
        when(runtimeService.startProcessInstanceByKey(
                cmd.getProcessDefinitionKey(), cmd.getBusinessKey(), cmd.getVariables()))
                .thenReturn(pi);

        mockTaskQuery(null);

        // when
        WorkflowLaunchResp resp = processStartService.startProcess(cmd);

        // then —— firstTaskId 应为 null
        assertThat(resp.getProcessInstanceId()).isEqualTo("PID_AUTO");
        assertThat(resp.getFirstTaskId()).isNull();
    }

    /**
     * 测试 startProcess：variables 为 null 时仍能正常启动
     */
    @Test
    void startProcess_nullVariables_succeeds() {
        StartProcessCmd cmd = buildCmd();
        cmd.setVariables(null);

        ProcessDefinition pd = mock(ProcessDefinition.class);
        mockProcessDefinitionQuery(pd);
        when(bizProcessMapMapper.existsRunningByBusinessKey(cmd.getBusinessKey())).thenReturn(false);

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn("PID_NOVAR");
        when(runtimeService.startProcessInstanceByKey(
                eq(cmd.getProcessDefinitionKey()), eq(cmd.getBusinessKey()), isNull()))
                .thenReturn(pi);
        mockTaskQuery(null);

        WorkflowLaunchResp resp = processStartService.startProcess(cmd);

        assertThat(resp.getProcessInstanceId()).isEqualTo("PID_NOVAR");
    }
}
