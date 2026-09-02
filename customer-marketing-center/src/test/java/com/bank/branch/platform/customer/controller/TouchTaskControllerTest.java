package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.resp.TouchWorklogVO;
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
        TouchTask t1 = buildPendingTask("1");
        TouchTask t2 = buildPendingTask("2");
        PageResult<TouchTask> page = PageResult.of(1, 20, 2L, Arrays.asList(t1, t2));

        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(touchTaskService.listPage(isNull(), isNull(), eq("E10001"), eq(1), eq(20))).thenReturn(page);

        // when/then
        mockMvc.perform(get("/api/touch-tasks")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(2))
                .andExpect(jsonPath("$.page.records[0].id").value(1));
    }

    // ==================== GET /api/touch-tasks/{id} 详情 ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void getTask_shouldReturn200() throws Exception {
        // given
        TouchTask task = buildPendingTask("1");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
        when(touchTaskService.getVisibleById("1", "E10001", "ORG_SZ_001", false)).thenReturn(task);

        // when/then
        mockMvc.perform(get("/api/touch-tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.taskStatus").value(TouchTaskStatus.PENDING.getCode()));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void getTask_shouldReturn404WhenNotFound() throws Exception {
        // given: 任务不存在
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(touchTaskService.getVisibleById(eq("999"), eq("E10001"), any(), eq(false)))
                .thenThrow(new BizException(
                        CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getCode(),
                        CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getMessage()));

        // when/then
        mockMvc.perform(get("/api/touch-tasks/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getCode()));
    }

    // ==================== POST /api/touch-tasks/{id}/success ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void markSuccessTask_shouldReturn200() throws Exception {
        // given
        doNothing().when(touchTaskService).markSuccess(eq("1"), eq("E10001"), eq(false));

        // when/then
        mockMvc.perform(post("/api/touch-tasks/1/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void markSuccessTask_shouldReturn400WhenIllegalTransition() throws Exception {
        // given: 非法状态转移（如已完成任务再次标记成功）
        doThrow(new BizException(
                CustomerErrorCode.TOUCH_TASK_ILLEGAL_TRANSITION.getCode(),
                CustomerErrorCode.TOUCH_TASK_ILLEGAL_TRANSITION.getMessage()))
                .when(touchTaskService).markSuccess(eq("3"), eq("E10001"), eq(false));

        // when/then
        mockMvc.perform(post("/api/touch-tasks/3/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CustomerErrorCode.TOUCH_TASK_ILLEGAL_TRANSITION.getCode()));
    }

    // ==================== POST /api/touch-tasks/{id}/cancel ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void cancelTask_shouldReturn200() throws Exception {
        // given
        doNothing().when(touchTaskService).cancel(eq("1"), anyString(), eq("E10001"), eq(false));

        String body = "{\"reason\":\"客户拒绝拜访\"}";

        // when/then
        mockMvc.perform(post("/api/touch-tasks/1/cancel")
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
        mockMvc.perform(post("/api/touch-tasks/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ==================== POST /api/touch-tasks/{id}/logs 添加触达日志 ====================

    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void addLog_shouldReturn200() throws Exception {
        // given
        TouchWorklogVO log = new TouchWorklogVO();
        log.setId("11");
        log.setTouchTaskId("1");
        log.setClientUuid("uuid-abc");
        log.setLogContent("今日拜访客户");

        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
        when(touchLogService.addLog(
                eq("1"), eq("uuid-abc"), eq("今日拜访客户"),
                any(), any(), eq("VISIT"), any(), any(), any(),
                eq("E10001"), eq("ORG_SZ_001"), eq(false)))
                .thenReturn(log);

        String body = "{\"clientUuid\":\"uuid-abc\",\"logContent\":\"今日拜访客户\"," +
                "\"touchTime\":\"2026-08-11T10:00:00\",\"touchMethod\":\"VISIT\"," +
                "\"photoGroups\":{\"keyPerson\":[\"http://minio/a.jpg\"],\"doorplate\":[],\"workplace\":[]}}";

        // when/then
        mockMvc.perform(post("/api/touch-tasks/1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("11"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void addLog_shouldReturn400WhenClientUuidBlank() throws Exception {
        // given: clientUuid 为空
        String body = "{\"clientUuid\":\"\",\"logContent\":\"今日拜访客户\"}";

        // when/then
        mockMvc.perform(post("/api/touch-tasks/1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ==================== GET /api/touch-tasks/{id}/logs 查看日志列表 ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listLogs_shouldReturn200() throws Exception {
        // given
        TouchWorklogVO log1 = new TouchWorklogVO();
        log1.setId("11");
        log1.setTouchTaskId("1");
        log1.setLogContent("第一次拜访");

        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
        when(touchLogService.listVisibleByTaskId("1", "ORG_SZ_001", false))
                .thenReturn(List.of(log1));

        // when/then
        mockMvc.perform(get("/api/touch-tasks/1/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("11"));
    }

    // ==================== 测试辅助方法 ====================

    /**
     * 构建 PENDING 状态的测试用触达任务
     */
    private TouchTask buildPendingTask(String id) {
        TouchTask task = new TouchTask();
        task.setId(Long.valueOf(id));
        task.setTaskNo("TOUCH_" + id);
        task.setCustId(101L);
        task.setOrgId("ORG_SZ_001");
        task.setAssigneeEmpId("E10001");
        task.setTaskType(TouchTaskType.FIRST_TOUCH.getCode());
        task.setTaskStatus(TouchTaskStatus.PENDING.getCode());
        task.setSlaStatus(SlaStatus.GREEN.getCode());
        task.setPlanFinishTime(LocalDateTime.now().plusDays(7));
        task.setWarningTime(LocalDateTime.now().plusDays(5));
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        return task;
    }
}
