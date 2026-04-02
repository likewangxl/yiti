package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.dto.JobConfDTO;
import com.bank.branch.platform.governance.api.dto.JobRunLogDTO;
import com.bank.branch.platform.governance.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JobController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock
    private JobService jobService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(jobService)).build();
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
        when(jobService.startJobRun(anyString(), anyString(), anyString())).thenReturn("JRL_NEW");

        // when & then
        mockMvc.perform(post("/api/admin/sys/jobs/J_001/trigger")
                .param("operatorEmpId", "emp001")
                .param("reason", "手动测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
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
}
