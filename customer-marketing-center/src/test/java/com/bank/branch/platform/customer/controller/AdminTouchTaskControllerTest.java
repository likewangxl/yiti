package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.service.TouchTaskService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminTouchTaskController 集成测试（TDD RED 阶段）。
 * <p>
 * 测试管理后台触达任务的全局列表、导出和批量分配端点。
 * </p>
 */
class AdminTouchTaskControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TouchTaskService touchTaskService;

    // ==================== GET /api/admin/touch-tasks ====================

    @Test
    @WithMockEmpContext(empId = "E10001", systemAdmin = true)
    void listAll_shouldReturn200WithPageResult() throws Exception {
        // given: 管理后台全局列表，不限机构
        TouchTask task1 = buildTask("task-001", "cust-001", "ORG001");
        TouchTask task2 = buildTask("task-002", "cust-002", "ORG002");
        PageResult<TouchTask> page = PageResult.of(1, 20, 2L, List.of(task1, task2));

        when(touchTaskService.listPageAdmin(isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/touch-tasks")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(2));
    }

    @Test
    @WithMockEmpContext(empId = "E10001", systemAdmin = true)
    void listAll_withFilters_shouldPassFiltersToService() throws Exception {
        // given: 带关键词、状态、机构过滤
        PageResult<TouchTask> page = PageResult.of(1, 20, 0L, List.of());

        when(touchTaskService.listPageAdmin(eq("TOUCH"), eq("PENDING"), isNull(), eq("ORG001"), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/touch-tasks")
                        .param("keyword", "TOUCH")
                        .param("status", "PENDING")
                        .param("orgId", "ORG001")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @WithMockEmpContext(empId = "M10001", orgCode = "ORG001")
    void listAll_nonAdminManager_shouldForceCurrentOrgScope() throws Exception {
        PageResult<TouchTask> page = PageResult.of(1, 20, 0L, List.of());
        when(touchTaskService.listPageAdmin(isNull(), isNull(), isNull(), eq("ORG001"), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/touch-tasks")
                        .param("orgId", "ORG_OTHER")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== GET /api/admin/touch-tasks/export ====================

    @Test
    @WithMockEmpContext(empId = "E10001", systemAdmin = true)
    void exportAll_shouldReturnCsvContent() throws Exception {
        // given: 导出数据
        TouchTask task1 = buildTask("task-001", "cust-001", "ORG001");
        when(touchTaskService.listAllForAdminExport(isNull(), isNull(), isNull(), eq(10000)))
                .thenReturn(List.of(task1));

        mockMvc.perform(get("/api/admin/touch-tasks/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("admin_touch_tasks_")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    // ==================== POST /api/admin/touch-tasks/batch-assign ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void batchAssign_shouldReturn200WithUpdatedCount() throws Exception {
        // given: 批量分配成功，更新了 2 条记录
        when(touchTaskService.batchAssign(any(), eq("E99999"), eq("E10001"))).thenReturn(2);

        String body = "{\"taskIds\":[\"task-001\",\"task-002\"],\"newAssigneeEmpId\":\"E99999\",\"reason\":\"原执行人休假，由新执行人接管在途任务\"}";

        mockMvc.perform(post("/api/admin/touch-tasks/batch-assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(2));

        verify(touchTaskService).batchAssign(any(), eq("E99999"), eq("E10001"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void batchAssign_missingReason_shouldReturn400() throws Exception {
        String body = "{\"taskIds\":[\"task-001\"],\"newAssigneeEmpId\":\"E99999\"}";

        mockMvc.perform(post("/api/admin/touch-tasks/batch-assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void batchAssign_emptyTaskIds_shouldReturn400() throws Exception {
        // given: taskIds 为空，校验失败
        String body = "{\"taskIds\":[],\"newAssigneeEmpId\":\"E99999\"}";

        mockMvc.perform(post("/api/admin/touch-tasks/batch-assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void batchAssign_missingNewAssigneeEmpId_shouldReturn400() throws Exception {
        // given: newAssigneeEmpId 为空，校验失败
        String body = "{\"taskIds\":[\"task-001\"],\"newAssigneeEmpId\":\"\"}";

        mockMvc.perform(post("/api/admin/touch-tasks/batch-assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ============================= 辅助方法 =============================

    private TouchTask buildTask(String id, String custId, String orgId) {
        TouchTask task = new TouchTask();
        task.setId(Math.abs((long) id.hashCode()) + 1L);
        task.setTaskNo("TOUCH_" + id);
        task.setCustId(Math.abs((long) custId.hashCode()) + 1L);
        task.setOrgId(orgId);
        task.setAssigneeEmpId("E10001");
        task.setTaskType("FIRST_TOUCH");
        task.setTaskStatus("PENDING");
        task.setSlaStatus("GREEN");
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        return task;
    }
}
