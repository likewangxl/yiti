package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.performance.controller.dto.RollbackReqDTO;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SysControlController.rollback 端点校验层 IT (V1.2 Q1.2).
 *
 * <p>本测试聚焦 Controller 的 Bean Validation：reason / scopeDim / rollbackTo 的 @NotBlank / @Pattern。
 * 完整业务路径（含分布式锁 / Service 落库 / 事件发布）见：
 * <ul>
 *   <li>{@code SysControlServiceRollbackTest} —— Service 层行为（@Transactional + @Rollback）</li>
 *   <li>{@code SysControlFacadeTest} —— Facade 分布式锁流程</li>
 *   <li>{@code SysControlServiceEventIT} —— 事件发布集成（V1.2 Q1.3）</li>
 * </ul>
 *
 * <p>本 IT 不走实际 Redis 路径，依赖 Bean Validation 在 Facade 之前拦截非法入参（与
 * {@link SysControlControllerIT#switchVersion_whenMissingReason_shouldReturn400} 同策略）。
 */
class SysControlRollbackControllerIT extends PerformanceControllerTestBase {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    @DisplayName("POST /api/perf/sys-control/rollback reason 缺失返回 400")
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
    @DisplayName("POST /api/perf/sys-control/rollback scopeDim 非法返回 400")
    void rollback_invalidScopeDim_returns400() throws Exception {
        RollbackReqDTO req = new RollbackReqDTO();
        req.setScopeDim("INVALID_DIM"); // 必须 EMP/ORG/CUST
        req.setRollbackTo("V_XX");
        req.setReason("测试");

        mockMvc.perform(post("/api/perf/sys-control/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/perf/sys-control/rollback rollbackTo 缺失返回 400")
    void rollback_requiresRollbackTo() throws Exception {
        RollbackReqDTO req = new RollbackReqDTO();
        req.setScopeDim("EMP");
        // rollbackTo 不设
        req.setReason("测试");

        mockMvc.perform(post("/api/perf/sys-control/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/perf/sys-control/rollback reason 超长返回 400")
    void rollback_reasonTooLong_returns400() throws Exception {
        RollbackReqDTO req = new RollbackReqDTO();
        req.setScopeDim("EMP");
        req.setRollbackTo("V_X");
        // 501 字符，超过 @Size(max=500)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            sb.append('a');
        }
        req.setReason(sb.toString());

        mockMvc.perform(post("/api/perf/sys-control/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
