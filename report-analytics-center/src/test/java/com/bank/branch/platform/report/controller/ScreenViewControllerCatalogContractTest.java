package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.report.dto.resp.ScreenEntryRespDTO;
import com.bank.branch.platform.report.service.screen.ScreenConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /catalog 必须命中字面目录方法，不得落入 /{screenCode} 动态运行时方法。 */
@ExtendWith(MockitoExtension.class)
class ScreenViewControllerCatalogContractTest {

    @Mock private ScreenConfigService configService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ScreenViewController(configService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void catalog_literalRouteReturnsOnlyCatalogDtoFields() throws Exception {
        ScreenEntryRespDTO entry = new ScreenEntryRespDTO();
        entry.setScreenCode("SCR_A");
        entry.setScreenName("省分行经营总览");
        entry.setViewLevel("PROVINCE");
        entry.setBizLine("COMMON");
        entry.setTemplate("branch-overview-v1");
        entry.setDataMode("TEST");
        when(configService.listAuthorizedCodeScreens()).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/screen/view/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].screenCode").value("SCR_A"))
                .andExpect(jsonPath("$.data[0].screenName").value("省分行经营总览"))
                .andExpect(jsonPath("$.data[0].viewLevel").value("PROVINCE"))
                .andExpect(jsonPath("$.data[0].bizLine").value("COMMON"))
                .andExpect(jsonPath("$.data[0].template").value("branch-overview-v1"))
                .andExpect(jsonPath("$.data[0].dataMode").value("TEST"))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].allowedRoleCodes").doesNotExist());

        verify(configService).listAuthorizedCodeScreens();
        verify(configService, never()).getRenderByCode(anyString(), anyString());
    }
}
