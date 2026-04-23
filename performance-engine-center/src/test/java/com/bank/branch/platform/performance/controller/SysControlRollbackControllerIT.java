package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.performance.controller.dto.RollbackReqDTO;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SysControlController.rollback 端点 IT (V1.2 Q1.2, Red).
 *
 * <p>覆盖：
 * <ul>
 *   <li>正常回滚：current_version=v2 → 指定 rollbackTo=v1 → 切换成功 + publishSource=ROLLBACK</li>
 *   <li>reason 缺失 → 400</li>
 *   <li>rollbackTo 版本不存在 → PERF-40012</li>
 *   <li>响应 DTO 不泄漏 entity 内部字段</li>
 * </ul>
 */
class SysControlRollbackControllerIT extends PerformanceControllerTestBase {

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
    @DisplayName("[Red] POST /api/perf/sys-control/rollback 回滚到历史版本，current_version 切换 + publishSource=ROLLBACK")
    void rollback_revertsCurrentVersionToPrevious() throws Exception {
        // Given：为 EMP 维度准备 v1 (失效) + v2 (当前生效)
        SysControl v1 = SysControlTestDataBuilder.buildTest(
                "RB01", "EMP", LocalDate.of(2099, 1, 1), "V_RB_1", 0);
        SysControl v2 = SysControlTestDataBuilder.buildTest(
                "RB02", "EMP", LocalDate.of(2099, 2, 1), "V_RB_2", 1);
        sysControlMapper.insert(v1);
        sysControlMapper.insert(v2);

        // When
        RollbackReqDTO req = new RollbackReqDTO();
        req.setScopeDim("EMP");
        req.setRollbackTo("V_RB_1");
        req.setReason("紧急回滚到 V_RB_1");

        mockMvc.perform(post("/api/perf/sys-control/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.scopeDim").value("EMP"))
                .andExpect(jsonPath("$.data.currentVersion").value("V_RB_1"));

        // Then：DB 验证新记录 publishSource=ROLLBACK，旧 v2 已失效
        SysControl curr = sysControlMapper.selectByScopeAndValid("EMP");
        assertThat(curr).isNotNull();
        assertThat(curr.getCurrentVersion()).isEqualTo("V_RB_1");
        assertThat(curr.getPublishSource()).isEqualTo("ROLLBACK");
        assertThat(curr.getRemark()).isEqualTo("紧急回滚到 V_RB_1");
    }

    @Test
    @DisplayName("[Red] POST /api/perf/sys-control/rollback reason 缺失返回 400")
    void rollback_requiresReason() throws Exception {
        RollbackReqDTO req = new RollbackReqDTO();
        req.setScopeDim("EMP");
        req.setRollbackTo("V_XX");
        // reason 不设

        mockMvc.perform(post("/api/perf/sys-control/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[Red] POST /api/perf/sys-control/rollback rollbackTo 不存在返回 PERF-40012")
    void rollback_whenRollbackToVersionNotFound_returns40012() throws Exception {
        // Given：为 ORG 准备一个当前版本，但不存在 V_NOT_EXIST
        SysControl v1 = SysControlTestDataBuilder.buildTest(
                "RB03", "ORG", LocalDate.of(2099, 3, 1), "V_RB_ORG_CURR", 1);
        sysControlMapper.insert(v1);

        RollbackReqDTO req = new RollbackReqDTO();
        req.setScopeDim("ORG");
        req.setRollbackTo("V_NOT_EXIST");
        req.setReason("测试不存在版本");

        mockMvc.perform(post("/api/perf/sys-control/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40012"));
    }

    @Test
    @DisplayName("[Red] POST /api/perf/sys-control/rollback 响应 DTO 不泄漏 entity 内部字段")
    void rollback_responseShouldNotExposeEntityFields() throws Exception {
        SysControl v1 = SysControlTestDataBuilder.buildTest(
                "RB04", "CUST", LocalDate.of(2099, 1, 1), "V_RB_CUST_1", 0);
        SysControl v2 = SysControlTestDataBuilder.buildTest(
                "RB05", "CUST", LocalDate.of(2099, 2, 1), "V_RB_CUST_2", 1);
        sysControlMapper.insert(v1);
        sysControlMapper.insert(v2);

        RollbackReqDTO req = new RollbackReqDTO();
        req.setScopeDim("CUST");
        req.setRollbackTo("V_RB_CUST_1");
        req.setReason("DTO 测试");

        String body = mockMvc.perform(post("/api/perf/sys-control/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(body).contains("\"currentVersion\"");
        assertThat(body).contains("\"scopeDim\"");
        assertThat(body).doesNotContain("\"createdTime\"");
        assertThat(body).doesNotContain("\"updatedTime\"");
        assertThat(body).doesNotContain("\"publishTime\"");
        assertThat(body).doesNotContain("\"publishBy\"");
        assertThat(body).doesNotContain("\"publishSource\"");
        assertThat(body).doesNotContain("\"updatedBy\"");
    }
}
