package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.service.ReReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 旧材料审核队列 tab 与当前操作人传递契约测试。 */
@ExtendWith(MockitoExtension.class)
class ReReviewControllerTest {

    @Mock
    private ReReviewService service;
    @Mock
    private CurrentUserApi currentUserApi;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReReviewController(service, currentUserApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void queue_acceptsTabAndPassesCurrentEmployeeToService() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("SECRETARY-1");
        ReSubmit row = new ReSubmit();
        row.setId(4L);
        when(service.getReviewQueue(1, 10, "PASSED", "SECRETARY-1"))
                .thenReturn(PageResult.of(1, 10, 1L, List.of(row)));

        mockMvc.perform(get("/api/re/reviews/queue")
                        .param("pageNo", "1")
                .param("pageSize", "10")
                        .param("tab", "PASSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(4));

        verify(service).getReviewQueue(1, 10, "PASSED", "SECRETARY-1");
    }
}
