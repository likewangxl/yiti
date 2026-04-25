package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.JobConfDTO;
import com.bank.branch.platform.governance.api.dto.JobRunLogDTO;
import com.bank.branch.platform.governance.api.dto.JobTriggerRespDTO;
import com.bank.branch.platform.governance.service.JobService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JobController 单元测试
 *
 * <p>P3.2 起：triggerJob 端点从 SecurityContext（{@link DataScopeContext#current()}）
 * 取 operatorEmpId 透传给 service.triggerJob(jobId, reason, operatorEmpId)，
 * 因此本测试通过 {@link MockedStatic} mock DataScopeContext 注入测试 empId。
 */
@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock
    private JobService jobService;

    private MockMvc mockMvc;

    private MockedStatic<DataScopeContext> mockedDataScopeContext;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(jobService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        // Mock DataScopeContext.current() - 让 controller 取到测试 empId
        mockedDataScopeContext = mockStatic(DataScopeContext.class);
        DataScopeContext mockContext = mock(DataScopeContext.class);
        lenient().when(mockContext.getEmpId()).thenReturn("emp001");
        mockedDataScopeContext.when(DataScopeContext::current).thenReturn(mockContext);
    }

    @AfterEach
    void tearDown() {
        mockedDataScopeContext.close();
    }

    @Test
    void listJobs_shouldReturn200WithPageResult() throws Exception {
        // given
        JobConfDTO dto = new JobConfDTO();
        dto.setId("J_001");
        dto.setJobKey("DAILY_REPORT");
        PageResult<JobConfDTO> pageResult = PageResult.of(1, 20, 1L, List.of(dto));
        when(jobService.listJobs(any(), anyInt(), anyInt())).thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/admin/sys/jobs")
                .param("pageNo", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    @Test
    void listRunLogs_shouldReturn200WithPageResult() throws Exception {
        // given
        JobRunLogDTO dto = new JobRunLogDTO();
        dto.setId("JRL_001");
        dto.setJobId("J_001");
        PageResult<JobRunLogDTO> pageResult = PageResult.of(1, 20, 1L, List.of(dto));
        when(jobService.listRunLogs(anyString(), anyInt(), anyInt())).thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/admin/sys/jobs/J_001/logs")
                .param("pageNo", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    @Test
    void triggerJob_shouldReturn200() throws Exception {
        // given
        JobTriggerRespDTO resp = new JobTriggerRespDTO();
        resp.setJobId("J_001");
        resp.setJobKey("DAILY_REPORT");
        resp.setTriggerType("MANUAL");
        when(jobService.triggerJob(anyString(), anyString(), anyString())).thenReturn(resp);

        // when & then
        mockMvc.perform(post("/api/admin/sys/jobs/J_001/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"手动测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    /**
     * P3.2 关键测试：验证 controller 从 DataScopeContext 取 empId 后透传给 service.triggerJob.
     */
    @Test
    void trigger_returnsOk_passesEmpId() throws Exception {
        // given
        JobTriggerRespDTO resp = new JobTriggerRespDTO();
        resp.setJobId("J_001");
        resp.setTriggerType("MANUAL");
        when(jobService.triggerJob(anyString(), anyString(), anyString())).thenReturn(resp);

        // when
        mockMvc.perform(post("/api/admin/sys/jobs/J_001/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"手动测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        // then - 验证 service.triggerJob 被传入正确的 empId（来自 DataScopeContext）
        ArgumentCaptor<String> jobIdCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> reasonCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> empIdCap = ArgumentCaptor.forClass(String.class);
        verify(jobService).triggerJob(jobIdCap.capture(), reasonCap.capture(), empIdCap.capture());
        assertThat(jobIdCap.getValue()).isEqualTo("J_001");
        assertThat(reasonCap.getValue()).isEqualTo("手动测试");
        assertThat(empIdCap.getValue()).isEqualTo("emp001");
    }

    @Test
    void pauseJob_shouldReturn200() throws Exception {
        // given
        doNothing().when(jobService).pauseJob(anyString());

        // when & then
        mockMvc.perform(put("/api/admin/sys/jobs/J_001/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void resumeJob_shouldReturn200() throws Exception {
        // given
        doNothing().when(jobService).resumeJob(anyString());

        // when & then
        mockMvc.perform(put("/api/admin/sys/jobs/J_001/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ── L2 错误路径测试 ──────────────────────────────────────────

    @Test
    void triggerJob_notFound_returnsBizError() throws Exception {
        when(jobService.triggerJob(anyString(), anyString(), anyString()))
                .thenThrow(new BizException("GOV-40004", "任务不存在"));

        mockMvc.perform(post("/api/admin/sys/jobs/NOT_EXIST/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-40004"));
    }

    @Test
    void triggerJob_alreadyRunning_returnsBizError() throws Exception {
        when(jobService.triggerJob(anyString(), anyString(), anyString()))
                .thenThrow(new BizException("GOV-40903", "任务正在执行中"));

        mockMvc.perform(post("/api/admin/sys/jobs/J_001/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-40903"));
    }

    @Test
    void pauseJob_notFound_returnsBizError() throws Exception {
        doThrow(new BizException("GOV-40004", "任务不存在"))
            .when(jobService).pauseJob(anyString());

        mockMvc.perform(put("/api/admin/sys/jobs/NOT_EXIST/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-40004"));
    }

    @Test
    void resumeJob_notFound_returnsBizError() throws Exception {
        doThrow(new BizException("GOV-40004", "任务不存在"))
            .when(jobService).resumeJob(anyString());

        mockMvc.perform(put("/api/admin/sys/jobs/NOT_EXIST/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-40004"));
    }
}
