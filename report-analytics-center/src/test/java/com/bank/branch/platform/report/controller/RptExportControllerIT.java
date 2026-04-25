package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.report.BaseControllerIT;
import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.service.export.RptExportService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RptExportController 端到端 IT（Task M5.3.1 / M5.3.2 / M5.3.3 合并）.
 *
 * <p>覆盖 3 接口：
 * <ul>
 *   <li>E.1 GET  /api/reports/export-tasks/{taskId}             status 查询</li>
 *   <li>E.2 DELETE /api/reports/export-tasks/{taskId}           取消</li>
 *   <li>E.3 GET  /api/reports/export-tasks/{taskId}/download    下载 302</li>
 * </ul>
 */
class RptExportControllerIT extends BaseControllerIT {

    @MockBean
    private RptExportService exportService;

    @MockBean
    private FileApi fileApi;

    // =====================================================================
    // E.1 GET /export-tasks/{taskId} status
    // =====================================================================

    @Test
    void getStatus_existingTask_shouldReturn200() throws Exception {
        RptExportTask task = new RptExportTask();
        task.setId("EXP001");
        task.setStatus("RUNNING");
        task.setExportType("DYNAMIC_QUERY");
        task.setOperatorId("E001");
        task.setCreatedTime(LocalDateTime.now());
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(exportService.getTaskForOwner("EXP001", "E001")).thenReturn(task);

        mvc.perform(get("/api/reports/export-tasks/EXP001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data.taskId").value("EXP001"))
            .andExpect(jsonPath("$.data.status").value("RUNNING"))
            .andExpect(jsonPath("$.data.exportType").value("DYNAMIC_QUERY"));
    }

    @Test
    void getStatus_nonOwner_shouldReturnBizError42209() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E002");
        when(exportService.getTaskForOwner("EXP001", "E002"))
            .thenThrow(new RptException(RptErrorCode.EXPORT_DOWNLOAD_FORBIDDEN));

        // common-web 契约：BizException 走 200 + code 字段携带具体错误码
        mvc.perform(get("/api/reports/export-tasks/EXP001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("RPT-42209"));
    }

    @Test
    void getStatus_notFound_shouldReturnBizError40009() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(exportService.getTaskForOwner("NOEXIST", "E001"))
            .thenThrow(new RptException(RptErrorCode.EXPORT_TASK_NOT_FOUND));

        mvc.perform(get("/api/reports/export-tasks/NOEXIST"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("RPT-40009"));
    }

    // =====================================================================
    // E.2 DELETE /export-tasks/{taskId} cancel
    // =====================================================================

    @Test
    void cancel_pendingTask_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        doNothing().when(exportService).cancelTask("EXP001", "E001");

        mvc.perform(delete("/api/reports/export-tasks/EXP001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"));

        verify(exportService).cancelTask(eq("EXP001"), eq("E001"));
    }

    @Test
    void cancel_alreadySuccess_shouldReturnBizError40010() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        doThrow(new RptException(RptErrorCode.EXPORT_TASK_NOT_READY))
            .when(exportService).cancelTask("EXP001", "E001");

        mvc.perform(delete("/api/reports/export-tasks/EXP001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("RPT-40010"));
    }

    // =====================================================================
    // E.3 GET /export-tasks/{taskId}/download
    // =====================================================================

    @Test
    void download_successTask_shouldRedirect302() throws Exception {
        RptExportTask t = new RptExportTask();
        t.setId("EXP001");
        t.setStatus("SUCCESS");
        t.setFileKey("rpt/export/EXP001/dq.xlsx");
        t.setOperatorId("E001");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(exportService.getTaskForOwner("EXP001", "E001")).thenReturn(t);
        when(fileApi.getDownloadUrl("rpt/export/EXP001/dq.xlsx"))
            .thenReturn("https://minio.example/sig?token=xxx");

        mvc.perform(get("/api/reports/export-tasks/EXP001/download"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://minio.example/sig?token=xxx"));
    }

    @Test
    void download_runningTask_shouldReturnBizError40010() throws Exception {
        RptExportTask t = new RptExportTask();
        t.setId("EXP001");
        t.setStatus("RUNNING");
        t.setOperatorId("E001");
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(exportService.getTaskForOwner("EXP001", "E001")).thenReturn(t);

        mvc.perform(get("/api/reports/export-tasks/EXP001/download"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("RPT-40010"));
    }
}
