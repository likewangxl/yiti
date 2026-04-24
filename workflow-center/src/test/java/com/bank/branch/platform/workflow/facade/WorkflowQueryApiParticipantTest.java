package com.bank.branch.platform.workflow.facade;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.service.ProcessQueryService;
import com.bank.branch.platform.workflow.service.ProcessStartService;
import com.bank.branch.platform.workflow.service.TodoQueryService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkflowQueryApi.queryParticipatedBusinessKeys 单元测试（V1.4 Task S1.1 Red）。
 *
 * <p>验证 V1.4 新增的跨模块查询 API：按员工工号 + 流程定义 key 前缀 + 时间窗口 + 上限，
 * 查询该员工参与过的流程实例 businessKey 集合（去重）。
 *
 * <p>"参与"定义（模式 B 简化版）：
 * <ul>
 *   <li>主路径：{@link HistoricProcessInstanceQuery#involvedUser} 覆盖 assignee / owner /
 *       显式 addUserIdentityLink 的用户（Flowable 在任务 claim/complete 时会自动登记）</li>
 *   <li>辅助路径：若 empId 为当前登录用户（{@link CurrentUserApi#getCurrentEmpId}），
 *       补充查询 {@link TaskService#createTaskQuery}.taskCandidateGroupIn 拿到"当前还在候选组中
 *       未领取"的 businessKey</li>
 * </ul>
 *
 * <p>processDefinitionKey 前缀过滤在 Java 侧 stream.filter 实现（Flowable 7
 * HistoricProcessInstanceQuery 无 processDefinitionKeyLike 方法）。
 *
 * <p>单测使用 Mock Flowable Service 验证编排，不起容器。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowQueryApiParticipantTest {

    @Mock
    private TodoQueryService todoQueryService;

    @Mock
    private ProcessQueryService processQueryService;

    @Mock
    private ProcessStartService processStartService;

    @Mock
    private HistoryService historyService;

    @Mock
    private TaskService taskService;

    @Mock
    private CurrentUserApi currentUserApi;

    @InjectMocks
    private WorkflowQueryFacade workflowQueryFacade;

    private WorkflowQueryApi api;

    @BeforeEach
    void setUp() {
        api = workflowQueryFacade;
    }

    @Test
    @DisplayName("empId 空 → 返回空集，不调 Flowable")
    void queryParticipatedBusinessKeys_blankEmpId_returnsEmpty() {
        Set<String> keys = api.queryParticipatedBusinessKeys("", "perf_alloc_adjust_", 30, 100);
        assertThat(keys).isEmpty();

        verify(historyService, never()).createHistoricProcessInstanceQuery();
        verify(taskService, never()).createTaskQuery();
    }

    @Test
    @DisplayName("null empId → 返回空集")
    void queryParticipatedBusinessKeys_nullEmpId_returnsEmpty() {
        Set<String> keys = api.queryParticipatedBusinessKeys(null, "perf_alloc_adjust_", 30, 100);
        assertThat(keys).isEmpty();
    }

    @Test
    @DisplayName("involvedUser 返回 2 条 + 前缀匹配 → Set 去重保留顺序")
    void queryParticipatedBusinessKeys_returnsInvolvedKeys() {
        HistoricProcessInstanceQuery histQ = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQ);
        when(histQ.involvedUser("E001")).thenReturn(histQ);
        when(histQ.startedAfter(any())).thenReturn(histQ);

        HistoricProcessInstance h1 = mock(HistoricProcessInstance.class);
        when(h1.getBusinessKey()).thenReturn("BK_001");
        when(h1.getProcessDefinitionKey()).thenReturn("perf_alloc_adjust_corp_v1");
        HistoricProcessInstance h2 = mock(HistoricProcessInstance.class);
        when(h2.getBusinessKey()).thenReturn("BK_002");
        when(h2.getProcessDefinitionKey()).thenReturn("perf_alloc_adjust_retail_v1");
        when(histQ.listPage(eq(0), anyInt())).thenReturn(Arrays.asList(h1, h2));

        // empId ≠ current 用户 → 跳过候选组补齐
        when(currentUserApi.getCurrentEmpId()).thenReturn("OTHER_USER");

        Set<String> keys = api.queryParticipatedBusinessKeys(
                "E001", "perf_alloc_adjust_", 30, 100);

        assertThat(keys).containsExactly("BK_001", "BK_002");
    }

    @Test
    @DisplayName("prefix 非空 → Java 侧按 processDefinitionKey 前缀过滤")
    void queryParticipatedBusinessKeys_filteredByPrefix() {
        HistoricProcessInstanceQuery histQ = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQ);
        when(histQ.involvedUser("E001")).thenReturn(histQ);
        when(histQ.startedAfter(any())).thenReturn(histQ);

        HistoricProcessInstance hAlloc = mock(HistoricProcessInstance.class);
        when(hAlloc.getBusinessKey()).thenReturn("BK_ALLOC");
        when(hAlloc.getProcessDefinitionKey()).thenReturn("perf_alloc_adjust_corp_v1");
        HistoricProcessInstance hTarget = mock(HistoricProcessInstance.class);
        when(hTarget.getBusinessKey()).thenReturn("BK_TARGET");
        when(hTarget.getProcessDefinitionKey()).thenReturn("perf_target_adjust_v1");
        when(histQ.listPage(eq(0), anyInt())).thenReturn(Arrays.asList(hAlloc, hTarget));

        when(currentUserApi.getCurrentEmpId()).thenReturn("OTHER_USER");

        Set<String> keys = api.queryParticipatedBusinessKeys(
                "E001", "perf_alloc_adjust_", 30, 100);

        // 仅 perf_alloc_adjust_ 前缀命中
        assertThat(keys).containsExactly("BK_ALLOC");
    }

    @Test
    @DisplayName("timeWindowDays 为 null → 不加 startedAfter 过滤")
    void queryParticipatedBusinessKeys_nullTimeWindow_noStartedAfter() {
        HistoricProcessInstanceQuery histQ = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQ);
        when(histQ.involvedUser("E001")).thenReturn(histQ);

        when(histQ.listPage(eq(0), anyInt())).thenReturn(Collections.emptyList());
        when(currentUserApi.getCurrentEmpId()).thenReturn("OTHER_USER");

        api.queryParticipatedBusinessKeys("E001", "perf_", null, 100);

        verify(histQ, never()).startedAfter(any());
    }

    @Test
    @DisplayName("limit 约束：参数 <=0 时兜底为 10000；>10000 时截断为 10000")
    void queryParticipatedBusinessKeys_limitEnforced() {
        HistoricProcessInstanceQuery histQ = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQ);
        when(histQ.involvedUser(any())).thenReturn(histQ);
        when(histQ.startedAfter(any())).thenReturn(histQ);
        when(histQ.listPage(eq(0), anyInt())).thenReturn(Collections.emptyList());
        when(currentUserApi.getCurrentEmpId()).thenReturn("OTHER_USER");

        // 用户传 5 → listPage(0, 5)
        api.queryParticipatedBusinessKeys("E001", "perf_", 30, 5);
        verify(histQ).listPage(0, 5);

        // 用户传 0 → 兜底 10000
        api.queryParticipatedBusinessKeys("E001", "perf_", 30, 0);
        // 用户传 99999 → 截断 10000
        api.queryParticipatedBusinessKeys("E001", "perf_", 30, 99999);
        // 共 2 次 listPage(0, 10000)
        verify(histQ, times(2)).listPage(0, 10000);
    }

    @Test
    @DisplayName("empId == 当前登录用户 → 合并候选组未领取任务的 businessKey")
    void queryParticipatedBusinessKeys_mergesCandidateGroupTasks_whenCurrentUser() {
        HistoricProcessInstanceQuery histQ = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQ);
        when(histQ.involvedUser("E001")).thenReturn(histQ);
        when(histQ.startedAfter(any())).thenReturn(histQ);

        HistoricProcessInstance h1 = mock(HistoricProcessInstance.class);
        when(h1.getBusinessKey()).thenReturn("BK_HIST_001");
        when(h1.getProcessDefinitionKey()).thenReturn("perf_alloc_adjust_corp_v1");
        when(histQ.listPage(eq(0), anyInt())).thenReturn(Collections.singletonList(h1));

        // empId == 当前登录用户 → 触发候选组合并
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(currentUserApi.getCurrentCandidateGroupKeys()).thenReturn(Set.of("GROUP_A"));

        // TaskQuery mock
        TaskQuery taskQ = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQ);
        when(taskQ.taskCandidateGroupIn(any())).thenReturn(taskQ);
        when(taskQ.taskUnassigned()).thenReturn(taskQ);
        Task t1 = mock(Task.class);
        when(t1.getProcessInstanceId()).thenReturn("PI_CAND_001");
        when(taskQ.list()).thenReturn(Collections.singletonList(t1));

        // 候选任务 → processInstance 反查
        HistoricProcessInstanceQuery histQ2 = mock(HistoricProcessInstanceQuery.class);
        // 第二次 createHistoricProcessInstanceQuery() 为候选任务反查使用
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQ, histQ2);
        when(histQ2.processInstanceId("PI_CAND_001")).thenReturn(histQ2);
        HistoricProcessInstance hCand = mock(HistoricProcessInstance.class);
        when(hCand.getBusinessKey()).thenReturn("BK_CAND_001");
        when(hCand.getProcessDefinitionKey()).thenReturn("perf_alloc_adjust_corp_v1");
        when(histQ2.singleResult()).thenReturn(hCand);

        Set<String> keys = api.queryParticipatedBusinessKeys(
                "E001", "perf_alloc_adjust_", 30, 100);

        // 包含 involvedUser 的历史 BK + 候选组的 BK
        assertThat(keys).contains("BK_HIST_001", "BK_CAND_001");
    }

    @Test
    @DisplayName("businessKey 为 null/blank 的历史实例被过滤掉")
    void queryParticipatedBusinessKeys_filtersBlankBusinessKeys() {
        HistoricProcessInstanceQuery histQ = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQ);
        when(histQ.involvedUser("E001")).thenReturn(histQ);
        when(histQ.startedAfter(any())).thenReturn(histQ);

        HistoricProcessInstance h1 = mock(HistoricProcessInstance.class);
        when(h1.getBusinessKey()).thenReturn(null);
        when(h1.getProcessDefinitionKey()).thenReturn("perf_a");
        HistoricProcessInstance h2 = mock(HistoricProcessInstance.class);
        when(h2.getBusinessKey()).thenReturn("");
        when(h2.getProcessDefinitionKey()).thenReturn("perf_a");
        HistoricProcessInstance h3 = mock(HistoricProcessInstance.class);
        when(h3.getBusinessKey()).thenReturn("BK_OK");
        when(h3.getProcessDefinitionKey()).thenReturn("perf_a");

        when(histQ.listPage(eq(0), anyInt())).thenReturn(Arrays.asList(h1, h2, h3));
        when(currentUserApi.getCurrentEmpId()).thenReturn("OTHER_USER");

        Set<String> keys = api.queryParticipatedBusinessKeys("E001", "perf_", 30, 100);
        assertThat(keys).containsExactly("BK_OK");
    }
}
