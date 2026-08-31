package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.redengine.service.ReCockpitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReCockpitController 单元测试。
 * <p>归档接口已由任务管理替代，测试确保历史 archive 路径不再注册；驾驶舱其余端点由既有兼容测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReCockpitControllerTest {

    @Mock
    private ReCockpitService reCockpitService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReCockpitController(reCockpitService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addDispatcherServletCustomizer(dispatcherServlet ->
                        dispatcherServlet.setThrowExceptionIfNoHandlerFound(false))
                .build();
    }

    @Test
    void archiveSettlement_endpointRemoved_returns404() throws Exception {
        mockMvc.perform(get("/api/re/cockpit/archive/settlement"))
                .andExpect(status().isNotFound());

        verify(reCockpitService, never()).archiveSettlement(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void annualArchiveEndpointRemoved_returns404() throws Exception {
        mockMvc.perform(post("/api/re/cockpit/archive/generate/2026"))
                .andExpect(status().isNotFound());

        verify(reCockpitService, never()).generateAnnualResult(anyInt());
    }

    @Test
    void exportControllerRemoved_directExportPathReturns404AndClassIsAbsent() throws Exception {
        mockMvc.perform(get("/api/re/export/submit"))
                .andExpect(status().isNotFound());

        assertThatThrownBy(() -> Class.forName(
                "com.bank.branch.platform.redengine.controller.ReExportController"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
