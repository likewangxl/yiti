package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.dto.ApprovalLogDTO;
import com.bank.branch.platform.workflow.api.dto.FormFieldDTO;
import com.bank.branch.platform.workflow.api.dto.TaskDetailRespDTO;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.entity.WfNodeFormConf;
import com.bank.branch.platform.workflow.enums.SlaStatus;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import com.bank.branch.platform.workflow.mapper.NodeFormConfMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TodoQueryService 单元测试。
 * <p>
 * 验证待办/已办查询服务的核心逻辑：
 * 1. 待办列表查询返回正确的任务数据及SLA状态
 * 2. 未签收任务的 claimable 标志为 true
 * 3. 已办列表使用 HistoryService 查询已完成任务
 * 4. 任务详情加载完整上下文（业务映射+表单配置+SLA+审批日志）
 * 5. 任务不存在时抛出 WF-40403 异常
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TodoQueryServiceTest {

    @Mock
    private TaskService taskService;

    @Mock
    private HistoryService historyService;

    @Mock
    private BizProcessMapMapper bizProcessMapMapper;

    @Mock
    private NodeFormConfMapper nodeFormConfMapper;

    @Mock
    private SlaCalculationService slaCalculationService;

    @InjectMocks
    private TodoQueryService todoQueryService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = mock(ObjectMapper.class);
        // 通过反射注入 mock objectMapper（TodoQueryService 依赖它进行 JSON 解析）
        Field field = TodoQueryService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(todoQueryService, objectMapper);
    }

    // ========== 辅助方法 ==========

    /**
     * 模拟 Flowable TaskQuery 的流式链式调用
     */
    private TaskQuery mockTaskQueryChain(long count, List<Task> tasks) {
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskCandidateOrAssigned(anyString())).thenReturn(tq);
        when(tq.orderByTaskCreateTime()).thenReturn(tq);
        when(tq.desc()).thenReturn(tq);
        when(tq.count()).thenReturn(count);
        when(tq.listPage(anyInt(), anyInt())).thenReturn(tasks);
        return tq;
    }

    /**
     * 模拟 Flowable HistoricTaskInstanceQuery 的流式链式调用
     */
    private HistoricTaskInstanceQuery mockHistoricTaskQueryChain(long count, List<HistoricTaskInstance> tasks) {
        HistoricTaskInstanceQuery htq = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(htq);
        when(htq.taskAssignee(anyString())).thenReturn(htq);
        when(htq.finished()).thenReturn(htq);
        when(htq.orderByHistoricTaskInstanceEndTime()).thenReturn(htq);
        when(htq.desc()).thenReturn(htq);
        when(htq.count()).thenReturn(count);
        when(htq.listPage(anyInt(), anyInt())).thenReturn(tasks);
        return htq;
    }

    /**
     * 构建模拟的 Flowable Task
     */
    private Task buildMockTask(String taskId, String taskName, String processInstanceId,
                               String assignee, String taskDefinitionKey, String processDefinitionId) {
        Task mockTask = mock(Task.class);
        lenient().when(mockTask.getId()).thenReturn(taskId);
        lenient().when(mockTask.getName()).thenReturn(taskName);
        lenient().when(mockTask.getProcessInstanceId()).thenReturn(processInstanceId);
        lenient().when(mockTask.getCreateTime()).thenReturn(new Date());
        lenient().when(mockTask.getAssignee()).thenReturn(assignee);
        lenient().when(mockTask.getTaskDefinitionKey()).thenReturn(taskDefinitionKey);
        lenient().when(mockTask.getProcessDefinitionId()).thenReturn(processDefinitionId);
        return mockTask;
    }

    /**
     * 构建模拟的 BizProcessMap
     */
    private BizProcessMap buildBizProcessMap(String processInstanceId, String bizType, String bizId) {
        BizProcessMap map = new BizProcessMap();
        map.setProcessInstanceId(processInstanceId);
        map.setBizType(bizType);
        map.setBizId(bizId);
        map.setBusinessKey(bizType + ":" + bizId);
        map.setStartUser("E10001");
        map.setProcessDefinitionKey("loan_approve");
        return map;
    }

    // ========== 测试方法 ==========

    /**
     * 查询待办列表：正常返回任务列表，SLA状态已计算
     */
    @Test
    void queryTodoList_returnsTasks() {
        // given
        Task mockTask = buildMockTask("TASK_001", "经理审批", "PID_001",
                "E10001", "userTask1", "loan_approve:1:123");
        mockTaskQueryChain(1L, List.of(mockTask));

        BizProcessMap map = buildBizProcessMap("PID_001", "LOAN", "LA001");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(map);
        when(slaCalculationService.calculateSlaStatus(eq("loan_approve"), eq("userTask1"), any(LocalDateTime.class)))
                .thenReturn(SlaStatus.GREEN);

        // when
        PageResult<TaskRespDTO> result = todoQueryService.queryTodoList("E10001", null, null, 1, 20);

        // then
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);

        TaskRespDTO dto = result.getRecords().get(0);
        assertThat(dto.getTaskId()).isEqualTo("TASK_001");
        assertThat(dto.getProcessInstanceId()).isEqualTo("PID_001");
        assertThat(dto.getBizType()).isEqualTo("LOAN");
        assertThat(dto.getBusinessKey()).isEqualTo("LOAN:LA001");
        assertThat(dto.getSlaStatus()).isEqualTo("GREEN");
        assertThat(dto.getTaskName()).isEqualTo("经理审批");

        verify(slaCalculationService).calculateSlaStatus(eq("loan_approve"), eq("userTask1"), any(LocalDateTime.class));
    }

    /**
     * 查询待办列表：未签收任务的 claimable 标志为 true
     */
    @Test
    void queryTodoList_setsClaimableFlag() {
        // given —— assignee 为 null 表示未签收
        Task unclaimedTask = buildMockTask("TASK_002", "主管审核", "PID_002",
                null, "userTask2", "loan_approve:1:123");
        mockTaskQueryChain(1L, List.of(unclaimedTask));

        BizProcessMap map = buildBizProcessMap("PID_002", "LOAN", "LA002");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_002")).thenReturn(map);
        when(slaCalculationService.calculateSlaStatus(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(SlaStatus.YELLOW);

        // when
        PageResult<TaskRespDTO> result = todoQueryService.queryTodoList("E10002", null, null, 1, 20);

        // then
        assertThat(result.getRecords()).hasSize(1);
        TaskRespDTO dto = result.getRecords().get(0);
        assertThat(dto.getClaimable()).isTrue();
        assertThat(dto.getAssignee()).isNull();
    }

    /**
     * 查询已办列表：使用 HistoryService 查询已完成任务
     */
    @Test
    void queryDoneList_returnsFinishedTasks() {
        // given
        HistoricTaskInstance hti = mock(HistoricTaskInstance.class);
        lenient().when(hti.getId()).thenReturn("TASK_003");
        lenient().when(hti.getName()).thenReturn("总经理审批");
        lenient().when(hti.getProcessInstanceId()).thenReturn("PID_003");
        lenient().when(hti.getCreateTime()).thenReturn(new Date());
        lenient().when(hti.getAssignee()).thenReturn("E10003");
        lenient().when(hti.getTaskDefinitionKey()).thenReturn("userTask3");
        lenient().when(hti.getProcessDefinitionId()).thenReturn("loan_approve:1:456");

        mockHistoricTaskQueryChain(1L, List.of(hti));

        BizProcessMap map = buildBizProcessMap("PID_003", "LOAN", "LA003");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_003")).thenReturn(map);
        when(slaCalculationService.calculateSlaStatus(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(SlaStatus.GREEN);

        // when
        PageResult<TaskRespDTO> result = todoQueryService.queryDoneList("E10003", null, null, 1, 20);

        // then
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);

        TaskRespDTO dto = result.getRecords().get(0);
        assertThat(dto.getTaskId()).isEqualTo("TASK_003");
        assertThat(dto.getBizType()).isEqualTo("LOAN");
        assertThat(dto.getAssignee()).isEqualTo("E10003");

        verify(historyService).createHistoricTaskInstanceQuery();
    }

    /**
     * 获取任务详情：加载完整上下文（业务映射 + 表单配置 + SLA + 审批日志）
     */
    @Test
    void getTaskDetail_loadsFullContext() {
        // given —— 模拟 TaskService.createTaskQuery 用于按 taskId 查询
        Task mockTask = buildMockTask("TASK_004", "风控审核", "PID_004",
                "E10004", "userTask4", "loan_approve:1:789");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("TASK_004")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(mockTask);

        BizProcessMap map = buildBizProcessMap("PID_004", "LOAN", "LA004");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_004")).thenReturn(map);
        when(slaCalculationService.calculateSlaStatus(eq("loan_approve"), eq("userTask4"), any(LocalDateTime.class)))
                .thenReturn(SlaStatus.RED);

        WfNodeFormConf formConf = new WfNodeFormConf();
        formConf.setFormFields("[{\"fieldKey\":\"amount\",\"fieldName\":\"金额\",\"fieldType\":\"number\"},{\"fieldKey\":\"customerName\",\"fieldName\":\"客户名称\",\"fieldType\":\"text\"}]");
        formConf.setEditableFields("[\"amount\"]");
        formConf.setRequiredFields("[\"amount\"]");
        when(nodeFormConfMapper.selectByProcessDefKeyAndNodeKey("loan_approve", "userTask4"))
                .thenReturn(formConf);

        // 模拟 ObjectMapper JSON 解析（使用 Answer 根据不同 JSON 返回不同结果）
        FormFieldDTO f1 = new FormFieldDTO();
        f1.setFieldKey("amount");
        f1.setFieldName("金额");
        f1.setFieldType("number");
        FormFieldDTO f2 = new FormFieldDTO();
        f2.setFieldKey("customerName");
        f2.setFieldName("客户名称");
        f2.setFieldType("text");
        List<FormFieldDTO> formFields = List.of(f1, f2);
        List<String> editableList = List.of("amount");
        List<String> requiredList = List.of("amount");
        try {
            when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenAnswer(inv -> {
                        String json = inv.getArgument(0);
                        if (json.contains("fieldKey")) {
                            return formFields;
                        } else if (json.contains("amount") && !json.contains("fieldKey")) {
                            return editableList;
                        } else {
                            return requiredList;
                        }
                    });
        } catch (Exception ignored) {
            // objectMapper 是 mock，这行不会真正执行，只为满足编译器
        }

        // 模拟审批日志
        Comment comment = mock(Comment.class);
        lenient().when(comment.getUserId()).thenReturn("E10001");
        lenient().when(comment.getFullMessage()).thenReturn("同意");
        lenient().when(comment.getTime()).thenReturn(new Date());
        when(taskService.getProcessInstanceComments("PID_004")).thenReturn(List.of(comment));

        // when
        TaskDetailRespDTO detail = todoQueryService.getTaskDetail("TASK_004", "E10004");

        // then
        assertThat(detail).isNotNull();
        assertThat(detail.getTaskInfo()).isNotNull();
        assertThat(detail.getTaskInfo().getTaskId()).isEqualTo("TASK_004");
        assertThat(detail.getTaskInfo().getSlaStatus()).isEqualTo("RED");
        assertThat(detail.getNodeFormConf()).isNotNull();
        assertThat(detail.getNodeFormConf().getFormFields()).hasSize(2);
        assertThat(detail.getApprovalLogs()).hasSize(1);
        assertThat(detail.getApprovalLogs().get(0).getOperator()).isEqualTo("E10001");
        assertThat(detail.getApprovalLogs().get(0).getOpinion()).isEqualTo("同意");

        verify(nodeFormConfMapper).selectByProcessDefKeyAndNodeKey("loan_approve", "userTask4");
        verify(taskService).getProcessInstanceComments("PID_004");
    }

    /**
     * 获取任务详情：任务不存在时抛出 WF-40403 异常
     */
    @Test
    void getTaskDetail_taskNotFound_throwsWf40403() {
        // given
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("NONEXISTENT")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> todoQueryService.getTaskDetail("NONEXISTENT", "E10001"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("WF-40403");
    }

    /**
     * 查询待办列表：按 bizType 过滤，只返回匹配的业务类型
     */
    @Test
    void queryTodoList_filterByBizType() {
        // given —— 两个任务，分属不同业务类型
        Task loanTask = buildMockTask("TASK_010", "贷款审批", "PID_010",
                "E10001", "userTask1", "loan_approve:1:123");
        Task leadTask = buildMockTask("TASK_011", "线索审批", "PID_011",
                "E10001", "userTask1", "lead_approve:1:456");
        mockTaskQueryChain(2L, List.of(loanTask, leadTask));

        BizProcessMap loanMap = buildBizProcessMap("PID_010", "LOAN", "LA010");
        BizProcessMap leadMap = buildBizProcessMap("PID_011", "LEAD", "LD011");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_010")).thenReturn(loanMap);
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_011")).thenReturn(leadMap);
        when(slaCalculationService.calculateSlaStatus(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(SlaStatus.GREEN);

        // when —— 仅查询 LOAN 类型
        PageResult<TaskRespDTO> result = todoQueryService.queryTodoList("E10001", "LOAN", null, 1, 20);

        // then —— 仅返回 LOAN 类型的任务
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getBizType()).isEqualTo("LOAN");
    }

    // ── L1 补全测试 ──────────────────────────────────────────────

    /**
     * 测试 queryTodoList：convertTaskToDTO 返回 null 时（biz map 不存在）应静默跳过
     */
    @Test
    void queryTodoList_bizMapNull_skipsTask() {
        // given —— 任务的 biz map 不存在
        Task mockTask = buildMockTask("TASK_020", "孤立的审批", "PID_020",
                "E10001", "userTask1", "loan_approve:1:123");
        mockTaskQueryChain(1L, List.of(mockTask));
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_020")).thenReturn(null);

        // when
        PageResult<TaskRespDTO> result = todoQueryService.queryTodoList("E10001", null, null, 1, 20);

        // then —— 该任务被跳过，返回空列表
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).isEmpty();
    }

    /**
     * 测试 queryDoneList：按 bizType 过滤
     */
    @Test
    void queryDoneList_filterByBizType() {
        // given —— 两个已办任务，分属不同业务类型
        HistoricTaskInstance hti1 = mock(HistoricTaskInstance.class);
        lenient().when(hti1.getId()).thenReturn("TASK_021");
        lenient().when(hti1.getName()).thenReturn("贷款审批");
        lenient().when(hti1.getProcessInstanceId()).thenReturn("PID_021");
        lenient().when(hti1.getCreateTime()).thenReturn(new Date());
        lenient().when(hti1.getAssignee()).thenReturn("E10001");
        lenient().when(hti1.getTaskDefinitionKey()).thenReturn("userTask1");
        lenient().when(hti1.getProcessDefinitionId()).thenReturn("loan_approve:1:123");

        HistoricTaskInstance hti2 = mock(HistoricTaskInstance.class);
        lenient().when(hti2.getId()).thenReturn("TASK_022");
        lenient().when(hti2.getName()).thenReturn("线索审批");
        lenient().when(hti2.getProcessInstanceId()).thenReturn("PID_022");
        lenient().when(hti2.getCreateTime()).thenReturn(new Date());
        lenient().when(hti2.getAssignee()).thenReturn("E10001");
        lenient().when(hti2.getTaskDefinitionKey()).thenReturn("userTask1");
        lenient().when(hti2.getProcessDefinitionId()).thenReturn("lead_approve:1:456");

        mockHistoricTaskQueryChain(2L, List.of(hti1, hti2));

        BizProcessMap map1 = buildBizProcessMap("PID_021", "LOAN", "LA021");
        BizProcessMap map2 = buildBizProcessMap("PID_022", "LEAD", "LD022");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_021")).thenReturn(map1);
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_022")).thenReturn(map2);
        when(slaCalculationService.calculateSlaStatus(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(SlaStatus.GREEN);

        // when —— 仅查询 LOAN 类型
        PageResult<TaskRespDTO> result = todoQueryService.queryDoneList("E10001", "LOAN", null, 1, 20);

        // then —— 仅返回 LOAN
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getBizType()).isEqualTo("LOAN");
    }

    /**
     * 测试 convertCommentsToLogs：空 comments 列表返回空列表
     */
    @Test
    void getTaskDetail_noComments_returnsEmptyLogs() {
        Task mockTask = buildMockTask("TASK_030", "无评论任务", "PID_030",
                "E10001", "userTask1", "loan_approve:1:123");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("TASK_030")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(mockTask);

        BizProcessMap map = buildBizProcessMap("PID_030", "LOAN", "LA030");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_030")).thenReturn(map);
        when(slaCalculationService.calculateSlaStatus(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(SlaStatus.GREEN);
        when(nodeFormConfMapper.selectByProcessDefKeyAndNodeKey(anyString(), anyString())).thenReturn(null);
        when(taskService.getProcessInstanceComments("PID_030")).thenReturn(Collections.emptyList());

        TaskDetailRespDTO detail = todoQueryService.getTaskDetail("TASK_030", "E10001");

        assertThat(detail.getApprovalLogs()).isEmpty();
    }

    /**
     * 测试 extractProcessDefinitionKey：null 输入返回 null
     */
    @Test
    void convertTaskToDTO_nullProcessDefinitionId_returnsNullKey() {
        // given —— processDefinitionId 为 null
        Task mockTask = buildMockTask("TASK_040", "测试", "PID_040",
                "E10001", "userTask1", null);
        mockTaskQueryChain(1L, List.of(mockTask));

        BizProcessMap map = buildBizProcessMap("PID_040", "LOAN", "LA040");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_040")).thenReturn(map);
        // processDefinitionKey 为 null 时，SLA 计算应能处理
        when(slaCalculationService.calculateSlaStatus(isNull(), eq("userTask1"), any(LocalDateTime.class)))
                .thenReturn(SlaStatus.GREEN);

        PageResult<TaskRespDTO> result = todoQueryService.queryTodoList("E10001", null, null, 1, 20);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getTaskId()).isEqualTo("TASK_040");
    }

    /**
     * 测试 convertToLocalDateTime：null date 返回 null
     */
    @Test
    void convertToLocalDateTime_nullDate_returnsNull() {
        Task mockTask = buildMockTask("TASK_050", "测试", "PID_050",
                "E10001", "userTask1", "loan_approve:1:123");
        lenient().when(mockTask.getCreateTime()).thenReturn(null);
        mockTaskQueryChain(1L, List.of(mockTask));

        BizProcessMap map = buildBizProcessMap("PID_050", "LOAN", "LA050");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_050")).thenReturn(map);
        when(slaCalculationService.calculateSlaStatus(eq("loan_approve"), eq("userTask1"), isNull()))
                .thenReturn(SlaStatus.GREEN);

        PageResult<TaskRespDTO> result = todoQueryService.queryTodoList("E10001", null, null, 1, 20);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getTaskCreateTime()).isNull();
    }

    /**
     * 测试 convertHistoricTaskToDTO：biz map 为 null 时静默跳过
     */
    @Test
    void queryDoneList_bizMapNull_skipsTask() {
        HistoricTaskInstance hti = mock(HistoricTaskInstance.class);
        lenient().when(hti.getId()).thenReturn("TASK_060");
        lenient().when(hti.getName()).thenReturn("孤立已办");
        lenient().when(hti.getProcessInstanceId()).thenReturn("PID_060");
        lenient().when(hti.getCreateTime()).thenReturn(new Date());
        lenient().when(hti.getAssignee()).thenReturn("E10001");
        lenient().when(hti.getTaskDefinitionKey()).thenReturn("userTask1");
        lenient().when(hti.getProcessDefinitionId()).thenReturn("loan_approve:1:123");

        mockHistoricTaskQueryChain(1L, List.of(hti));
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_060")).thenReturn(null);

        PageResult<TaskRespDTO> result = todoQueryService.queryDoneList("E10001", null, null, 1, 20);

        assertThat(result.getRecords()).isEmpty();
    }

    /**
     * 测试 queryTodoList：空任务列表返回空结果
     */
    @Test
    void queryTodoList_emptyList_returnsEmptyResult() {
        mockTaskQueryChain(0L, List.of());

        PageResult<TaskRespDTO> result = todoQueryService.queryTodoList("E10001", null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getRecords()).isEmpty();
    }
}
