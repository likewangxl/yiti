package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.redengine.api.dto.ReTaskEligibleUserDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskEligibleUserPageQueryDTO;
import com.bank.branch.platform.redengine.service.ReTaskEligibleUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 任务可选员工 REST 入口契约测试。 */
@ExtendWith(MockitoExtension.class)
class ReTaskEligibleUserControllerTest {

    @Mock
    private ReTaskEligibleUserService service;
    @Mock
    private CurrentUserApi currentUserApi;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReTaskEligibleUserController(service, currentUserApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void page_usesCurrentEmployeeAndReturnsPageEnvelope() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("ORG-REVIEWER");
        ReTaskEligibleUserDTO row = new ReTaskEligibleUserDTO();
        row.setEmployeeId("U-1");
        row.setUsername("zhang");
        row.setDisplayName("张三");
        when(service.page(any(ReTaskEligibleUserPageQueryDTO.class), eq("ORG-REVIEWER")))
                .thenReturn(PageResult.of(1, 20, 1, java.util.List.of(row)));

        mockMvc.perform(get("/api/re/tasks/eligible-users")
                        .param("keyword", "张")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].employeeId").value("U-1"));

        verify(service).page(any(ReTaskEligibleUserPageQueryDTO.class), eq("ORG-REVIEWER"));
    }

    @Test
    void page_rejectsPageSizeAboveContractLimit() throws Exception {
        mockMvc.perform(get("/api/re/tasks/eligible-users")
                        .param("pageNo", "1")
                        .param("pageSize", "201"))
                .andExpect(status().isBadRequest());
    }
}
