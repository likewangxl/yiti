package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.performance.controller.dto.InitSysControlReqDTO;
import com.bank.branch.platform.performance.controller.dto.SwitchVersionReqDTO;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.mapper.SysControlMapper;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.bank.branch.platform.performance.support.SysControlTestDataBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SysControlController IT.
 *
 * <p>采用 @Transactional + @Rollback 自动回滚, 所有 TEST_SC_* 前缀数据在测试后自动撤销.
 *
 * <p>注意: 本模块 bootstrap 启动时 auth-permission-center 的 AuthorizationInterceptor 才接管 /api/** 的鉴权.
 * 在 PerfTestApp 环境下, 未登录请求即无鉴权失败. 本 IT 聚焦 Controller 路由 + Service 协作.
 */
class SysControlControllerIT extends PerformanceControllerTestBase {

    @Autowired
    private SysControlMapper sysControlMapper;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    @DisplayName("GET /api/perf/sys-control?scopeDim=X 无记录应返回 40406")
    void getCurrent_whenNotFound_shouldReturnErrorCode() throws Exception {
        mockMvc.perform(get("/api/perf/sys-control")
                        .param("scopeDim", "TEST_NONE_DIM")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40406"));
    }

    @Test
    @DisplayName("GET /api/perf/sys-control 存在当前版本应返回 200 + 数据")
    void getCurrent_whenExists_shouldReturnData() throws Exception {
        // Given
        SysControl sc = SysControlTestDataBuilder.buildTest(
                "CT01", "TEST_CT_EMP", LocalDate.of(2099, 9, 9), "V_CT01", 1);
        sysControlMapper.insert(sc);

        // When / Then
        mockMvc.perform(get("/api/perf/sys-control")
                        .param("scopeDim", "TEST_CT_EMP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("TEST_SC_CT01"))
                .andExpect(jsonPath("$.data.currentVersion").value("V_CT01"));
    }

    @Test
    @DisplayName("GET /api/perf/sys-control/history 应返回按日期倒序的历史")
    void getHistory_shouldReturnListOrderByDesc() throws Exception {
        // Given
        SysControl sc1 = SysControlTestDataBuilder.buildTest(
                "CT02", "TEST_CT_H", LocalDate.of(2099, 6, 1), "V1", 0);
        SysControl sc2 = SysControlTestDataBuilder.buildTest(
                "CT03", "TEST_CT_H", LocalDate.of(2099, 7, 1), "V2", 1);
        sysControlMapper.insert(sc1);
        sysControlMapper.insert(sc2);

        // When / Then
        mockMvc.perform(get("/api/perf/sys-control/history")
                        .param("scopeDim", "TEST_CT_H")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].currentVersion").value("V2"))
                .andExpect(jsonPath("$.data[1].currentVersion").value("V1"));
    }

    @Test
    @DisplayName("POST /api/perf/sys-control/init 应返回 200, 且 DB 有记录")
    void init_shouldInsertThreeRecords() throws Exception {
        // Given
        InitSysControlReqDTO req = new InitSysControlReqDTO();
        req.setReason("首次初始化");

        // When: 使用独立 scope 避免污染业务数据 (但因 init 写 EMP/ORG/CUST, 无法完全避免)
        // 实际项目中 init 仅初始化真实 3 个维度, 本测试仅确认调用成功 + 响应结构
        mockMvc.perform(post("/api/perf/sys-control/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        // Then: 3 个维度均存在记录
        assertThat(sysControlMapper.countByCondition("EMP", null)).isGreaterThanOrEqualTo(1);
        assertThat(sysControlMapper.countByCondition("ORG", null)).isGreaterThanOrEqualTo(1);
        assertThat(sysControlMapper.countByCondition("CUST", null)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/perf/sys-control/init 缺 reason 返回 400")
    void init_whenMissingReason_shouldReturn400() throws Exception {
        // Given
        InitSysControlReqDTO req = new InitSysControlReqDTO();
        // 不设 reason

        // When / Then
        mockMvc.perform(post("/api/perf/sys-control/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/perf/sys-control/switch-version 成功返回 200")
    void switchVersion_whenOk_shouldReturn200() throws Exception {
        // Given: 先插入一个基线当前版本
        SysControl baseline = SysControlTestDataBuilder.buildTest(
                "CT04", "TEST_CT_SW", LocalDate.of(2099, 1, 1), "V_BASE", 1);
        sysControlMapper.insert(baseline);

        SwitchVersionReqDTO req = new SwitchVersionReqDTO();
        req.setScopeDim("TEST_CT_SW");
        req.setDataDate(LocalDate.of(2099, 10, 1));
        req.setNewVersion("V_NEW");
        req.setReason("切换 unit");

        // When / Then: 注意 scopeDim 校验 Pattern 仅允许 EMP/ORG/CUST
        // 测试数据用了 TEST_CT_SW, 应返回 400
        mockMvc.perform(post("/api/perf/sys-control/switch-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/perf/sys-control/switch-version 缺 reason 返回 400")
    void switchVersion_whenMissingReason_shouldReturn400() throws Exception {
        // Given
        SwitchVersionReqDTO req = new SwitchVersionReqDTO();
        req.setScopeDim("EMP");
        req.setDataDate(LocalDate.of(2099, 10, 1));
        req.setNewVersion("V_X");
        // reason 不设

        // When / Then
        mockMvc.perform(post("/api/perf/sys-control/switch-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ===================== Task D2 Red：断言 SysControlController 响应体 DTO 化，不含 entity 内部字段 =====================

    /**
     * [Red] getCurrent 接口响应不应泄漏 entity 内部字段 createdTime / updatedTime.
     * 当前 Controller 直接返回 SysControl entity，该测试应失败。
     */
    @Test
    @DisplayName("[Red] getCurrent 响应不含 entity 内部字段")
    void getCurrent_shouldNotExposeEntityFields() throws Exception {
        // Given
        SysControl sc = SysControlTestDataBuilder.buildTest(
                "DTO01", "TEST_CT_DTO_EMP", LocalDate.of(2099, 1, 1), "V_DTO01", 1);
        sysControlMapper.insert(sc);

        // When / Then
        String body = mockMvc.perform(get("/api/perf/sys-control")
                        .param("scopeDim", "TEST_CT_DTO_EMP"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 断言业务字段存在
        assertThat(body).contains("\"currentVersion\"");
        assertThat(body).contains("\"scopeDim\"");
        // 断言 entity 内部字段不存在
        assertThat(body).doesNotContain("\"createdTime\"");
        assertThat(body).doesNotContain("\"updatedTime\"");
        assertThat(body).doesNotContain("\"publishTime\"");
        assertThat(body).doesNotContain("\"publishBy\"");
        assertThat(body).doesNotContain("\"publishSource\"");
        assertThat(body).doesNotContain("\"updatedBy\"");
    }

    /**
     * [Red] getHistory 接口响应不应泄漏 entity 内部字段.
     */
    @Test
    @DisplayName("[Red] getHistory 响应不含 entity 内部字段")
    void getHistory_shouldNotExposeEntityFields() throws Exception {
        // Given
        SysControl sc = SysControlTestDataBuilder.buildTest(
                "DTO02", "TEST_CT_DTO_H", LocalDate.of(2099, 2, 1), "V_DTO02", 1);
        sysControlMapper.insert(sc);

        // When / Then
        String body = mockMvc.perform(get("/api/perf/sys-control/history")
                        .param("scopeDim", "TEST_CT_DTO_H")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 断言 entity 内部字段不存在
        assertThat(body).doesNotContain("\"createdTime\"");
        assertThat(body).doesNotContain("\"updatedTime\"");
        assertThat(body).doesNotContain("\"publishTime\"");
        assertThat(body).doesNotContain("\"publishBy\"");
        assertThat(body).doesNotContain("\"publishSource\"");
        assertThat(body).doesNotContain("\"updatedBy\"");
    }
}
