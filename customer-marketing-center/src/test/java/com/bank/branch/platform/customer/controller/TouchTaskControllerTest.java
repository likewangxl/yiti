package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.TouchLog;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.SlaStatus;
import com.bank.branch.platform.customer.enums.TouchTaskStatus;
import com.bank.branch.platform.customer.enums.TouchTaskType;
import com.bank.branch.platform.customer.service.TouchLogService;
import com.bank.branch.platform.customer.service.TouchTaskService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TouchTaskController 集成测试。
 * <p>
 * 继承 AbstractControllerIntegrationTest 基类，通过 @MockBean 替换真实业务逻辑。
 * </p>
 */
class TouchTaskControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TouchTaskService touchTaskService;

    @MockBean
    TouchLogService touchLogService;

    // ==================== GET /api/touch-tasks 分页列表 ====================

    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void listTasks_shouldReturn200() throws Exception {
        // given
        TouchTask t1 = buildPendingTask("task-001");
        TouchTask t2 = buildPendingTask("task-002");
        PageResult<TouchTask> page = PageResult.of(1, 20, 2L, Arrays.asList(t1, t2));

        when(touchTaskService.listPage(isNull(), isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        // when/then
        mockMvc.perform(get("/api/touch-tasks")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(2))
                .andExpect(jsonPath("$.page.records[0].id").value("task-001"));
    }

    // ==================== GET /api/touch-tasks/{id} 详情 ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void getTask_shouldReturn200() throws Exception {
        // given
        TouchTask task = buildPendingTask("task-001");
        when(touchTaskService.getById("task-001")).thenReturn(task);

        // when/then
        mockMvc.perform(get("/api/touch-tasks/task-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("task-001"))
                .andExpect(jsonPath("$.data.taskStatus").value(TouchTaskStatus.PENDING.getCode()));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void getTask_shouldReturn404WhenNotFound() throws Exception {
        // given: 任务不存在
        when(touchTaskService.getById("not-exist"))
                .thenThrow(new BizException(
                        CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getCode(),
                        CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getMessage()));

        // when/then
        mockMvc.perform(get("/api/touch-tasks/not-exist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getCode()));
    }

    // ==================== POST /api/touch-tasks/{id}/complete ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void completeTask_shouldReturn200() throws Exception {
        // given
        doNothing().when(touchTaskService).complete("task-001");

        // when/then
        mockMvc.perform(post("/api/touch-tasks/task-001/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void completeTask_shouldReturn400WhenNotPending() throws Exception {
        // given: 任务非 PENDING 状态
        doThrow(new BizException(
                CustomerErrorCode.TOUCH_TASK_NOT_PENDING.getCode(),
                CustomerErrorCode.TOUCH_TASK_NOT_PENDING.getMessage()))
                .when(touchTaskService).complete("task-done");

        // when/then
        mockMvc.perform(post("/api/touch-tasks/task-done/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CustomerErrorCode.TOUCH_TASK_NOT_PENDING.getCode()));
    }

    // ==================== POST /api/touch-tasks/{id}/cancel ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void cancelTask_shouldReturn200() throws Exception {
        // given
        doNothing().when(touchTaskService).cancel(eq("task-001"), anyString());

        String body = "{\"reason\":\"客户拒绝拜访\"}";

        // when/then
        mockMvc.perform(post("/api/touch-tasks/task-001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void cancelTask_shouldReturn400WhenReasonBlank() throws Exception {
        // given: 取消原因为空
        String body = "{\"reason\":\"\"}";

        // when/then
        mockMvc.perform(post("/api/touch-tasks/task-001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ==================== POST /api/touch-tasks/{id}/logs 添加触达日志 ====================

    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void addLog_shouldReturn200() throws Exception {
        // given
        TouchLog log = new TouchLog();
        log.setId("log-001");
        log.setTouchTaskId("task-001");
        log.setClientUuid("uuid-abc");
        log.setLogContent("今日拜访客户");

        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
        when(touchLogService.addLog(
                eq("task-001"), eq("uuid-abc"), eq("今日拜访客户"),
                any(), eq("E10001"), eq("ORG_SZ_001")))
                .thenReturn(log);

        String body = "{\"clientUuid\":\"uuid-abc\",\"logContent\":\"今日拜访客户\"}";

        // when/then
        mockMvc.perform(post("/api/touch-tasks/task-001/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("log-001"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void addLog_shouldReturn400WhenClientUuidBlank() throws Exception {
        // given: clientUuid 为空
        String body = "{\"clientUuid\":\"\",\"logContent\":\"今日拜访客户\"}";

        // when/then
        mockMvc.perform(post("/api/touch-tasks/task-001/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ==================== GET /api/touch-tasks/{id}/logs 查看日志列表 ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listLogs_shouldReturn200() throws Exception {
        // given
        TouchLog log1 = new TouchLog();
        log1.setId("log-001");
        log1.setTouchTaskId("task-001");
        log1.setLogContent("第一次拜访");
        TouchLog log2 = new TouchLog();
        log2.setId("log-002");
        log2.setTouchTaskId("task-001");
        log2.setLogContent("第二次拜访");

        when(touchLogService.listByTaskId("task-001")).thenReturn(Arrays.asList(log1, log2));

        // when/then
        mockMvc.perform(get("/api/touch-tasks/task-001/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("log-001"))
                .andExpect(jsonPath("$.data[1].id").value("log-002"));
    }

    // ==================== 测试辅助方法 ====================

    /**
     * 构建 PENDING 状态的测试用触达任务
     */
    private TouchTask buildPendingTask(String id) {
        TouchTask task = new TouchTask();
        task.setId(id);
        task.setTaskNo("TOUCH_" + id);
        task.setCustId("cust-001");
        task.setOrgId("ORG_SZ_001");
        task.setAssigneeEmpId("E10001");
        task.setTaskType(TouchTaskType.FIRST_TOUCH.getCode());
        task.setTaskStatus(TouchTaskStatus.PENDING.getCode());
        task.setSlaStatus(SlaStatus.GREEN.getCode());
        task.setPlanFinishTime(LocalDateTime.now().plusDays(7));
        task.setWarningTime(LocalDateTime.now().plusDays(5));
        task.setBusinessKey("TOUCH:" + id);
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        return task;
    }
}
