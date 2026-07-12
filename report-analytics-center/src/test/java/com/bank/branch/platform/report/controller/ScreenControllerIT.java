package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.report.BaseControllerIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 大屏 4 Controller 端到端 IT（MockMvc，鉴权拦截器 Mock 放行，业务落真库）.
 */
class ScreenControllerIT extends BaseControllerIT {

    @org.springframework.beans.factory.annotation.Autowired
    private com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper dsMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private com.bank.branch.platform.report.mapper.RptScreenMapper screenMapper;

    @BeforeEach
    void stubCurrentUser() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("TEST_SCR_E9");
    }

    @org.junit.jupiter.api.AfterEach
    void cleanupTestData() {
        dsMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
                <com.bank.branch.platform.report.entity.RptScreenDatasource>()
                .likeRight(com.bank.branch.platform.report.entity.RptScreenDatasource::getDsName, "TEST_SCR_"));
        screenMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
                <com.bank.branch.platform.report.entity.RptScreen>()
                .likeRight(com.bank.branch.platform.report.entity.RptScreen::getScreenName, "TEST_SCR_"));
    }

    @Test
    void saveKpiDatasource_thenListContainsIt() throws Exception {
        String body = """
                {"dsName":"TEST_SCR_KPI源","sourceKind":"KPI_RESULT",
                 "configJson":"{\\"cycleType\\":\\"MONTHLY\\"}"}
                """;
        mvc.perform(post("/api/screen/admin/datasources")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isNumber());

        mvc.perform(get("/api/screen/admin/datasources").param("keyword", "TEST_SCR_KPI源"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dsType").value("TIMESERIES"));
    }

    @Test
    void saveScreen_lineTrendOnMissingDs_returns43001() throws Exception {
        String body = """
                {"screenName":"TEST_SCR_坏屏","viewLevel":"BRANCH",
                 "blocks":[{"region":"LEFT","rowNo":1,"colNo":1,"widthPct":100,"heightPct":50,
                            "componentType":"LINE_TREND","bindJson":"{\\"dsId\\":987654999}"}]}
                """;
        mvc.perform(post("/api/screen/admin/screens")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value("RPT-43001"));
    }

    @Test
    void viewUnknownScreen_returns43004() throws Exception {
        mvc.perform(get("/api/screen/view/SCR_NOT_EXIST"))
                .andExpect(jsonPath("$.code").value("RPT-43004"));
    }

    @Test
    void queryData_unknownDs_returns43001() throws Exception {
        mvc.perform(post("/api/screen/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dsId\":987654999}"))
                .andExpect(jsonPath("$.code").value("RPT-43001"));
    }
}
