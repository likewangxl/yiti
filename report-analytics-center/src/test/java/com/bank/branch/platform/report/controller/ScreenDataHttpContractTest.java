package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.service.screen.ScreenDatasourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/screen/data} 的 HTTP JSON 身份边界契约。
 *
 * <p>此测试刻意使用 standalone MockMvc：重复身份字段必须在请求体反序列化阶段 fail-close，
 * 因而绝不能进入数据源服务。它不启动 Spring 容器、DataSource 或数据库。</p>
 */
@ExtendWith(MockitoExtension.class)
class ScreenDataHttpContractTest {

    @Mock
    private ScreenDatasourceService datasourceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ScreenDataController(datasourceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void data_rejectsDuplicateSchemaVersionBeforeDatasourceService() throws Exception {
        assertDuplicateIdentityFieldRejected(
                "{\"schemaVersion\":1,\"schemaVersion\":1,\"screenCode\":\"SCR\",\"dsId\":12}");
    }

    @Test
    void data_rejectsDuplicateBlockIdBeforeDatasourceService() throws Exception {
        assertDuplicateIdentityFieldRejected(
                "{\"schemaVersion\":2,\"screenCode\":\"SCR\",\"blockId\":11,\"blockId\":11}");
    }

    @Test
    void data_rejectsDuplicateDsIdBeforeDatasourceService() throws Exception {
        assertDuplicateIdentityFieldRejected(
                "{\"schemaVersion\":1,\"screenCode\":\"SCR\",\"dsId\":12,\"dsId\":12}");
    }

    @Test
    void data_acceptsLegalNativeIntegersAndNormalContextAndDateFields() throws Exception {
        when(datasourceService.queryData(any(ScreenDataReqDTO.class)))
                .thenReturn(new ScreenDataRespDTO(List.of("metric"), List.of(List.<Object>of(1))));

        mockMvc.perform(post("/api/screen/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaVersion":2,"screenCode":"SCR","blockId":11,"period":"RANGE",
                                 "dateFrom":"2026-08-01","dateTo":"2026-08-11",
                                 "contextParams":{"orgCode":"ORG001","empId":"E001"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        ArgumentCaptor<ScreenDataReqDTO> captor = ArgumentCaptor.forClass(ScreenDataReqDTO.class);
        verify(datasourceService).queryData(captor.capture());
        ScreenDataReqDTO request = captor.getValue();
        assertThat(request.getSchemaVersion()).isEqualTo(2);
        assertThat(request.getBlockId()).isEqualTo(11L);
        assertThat(request.getScreenCode()).isEqualTo("SCR");
        assertThat(request.getPeriod()).isEqualTo("RANGE");
        assertThat(request.getDateFrom()).isEqualTo("2026-08-01");
        assertThat(request.getDateTo()).isEqualTo("2026-08-11");
        assertThat(request.getContextParams()).isEqualTo(Map.of("orgCode", "ORG001", "empId", "E001"));
    }

    @Test
    void data_acceptsExplicitDraftPreviewState() throws Exception {
        when(datasourceService.queryData(any(ScreenDataReqDTO.class)))
                .thenReturn(new ScreenDataRespDTO(List.of("metric"), List.of(List.<Object>of(1))));

        mockMvc.perform(post("/api/screen/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previewState\":\"draft\",\"schemaVersion\":2,"
                                + "\"screenCode\":\"SCR\",\"blockId\":11}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ScreenDataReqDTO> captor = ArgumentCaptor.forClass(ScreenDataReqDTO.class);
        verify(datasourceService).queryData(captor.capture());
        assertThat(captor.getValue().getPreviewState()).isEqualTo("draft");
    }

    private void assertDuplicateIdentityFieldRejected(String body) throws Exception {
        mockMvc.perform(post("/api/screen/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_005"));

        verifyNoInteractions(datasourceService);
    }
}
