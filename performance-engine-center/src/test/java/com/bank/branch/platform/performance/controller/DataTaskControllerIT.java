package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.performance.api.DataTaskApi;
import com.bank.branch.platform.performance.api.dto.DataTaskReportResultDTO;
import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DataTaskController IT（V1.1 Task P6.1）.
 *
 * <p>覆盖 POST {@code /api/data-task/status} 的核心场景 + 注解约束：
 * <ol>
 *   <li>success 上报 → 200 + {@code accepted=true} + 回显 taskId + 非空 perfRunTaskId；
 *       Controller 成功委托 {@link DataTaskApi#reportDataTaskStatus(DataTaskStatusCmd)}</li>
 *   <li>dataDate 缺失 / status 非法 / taskId 空白 → 400 由 jakarta.validation 兜底</li>
 *   <li>FAILED 状态亦正常受理（幂等键 taskId 的落库语义），不要求触发计算（由 P6.2/P6.3 负责）</li>
 *   <li>注解约束：无 {@code @BizAuth}（API Token 通道），
 *       {@code @AuditLog(action="DATA_TASK_STATUS_REPORT", resourceType="EXT_DATA_TASK")}</li>
 * </ol>
 *
 * <p><strong>隔离策略</strong>：用 {@code @MockBean DataTaskApi}，避免下游 Service 触发真实计算；
 * 本 IT 只覆盖 Controller 职责（DTO 校验 + 注解 + Facade 调用）。
 */
class DataTaskControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.DataTaskController";

    @MockBean
    private DataTaskApi dataTaskApi;

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void resetMocks() {
        Mockito.reset(dataTaskApi);
        // 默认 mock 成功路径，个别用例可 override
        Mockito.when(dataTaskApi.reportDataTaskStatus(Mockito.any(DataTaskStatusCmd.class)))
                .thenAnswer(inv -> {
                    DataTaskStatusCmd c = inv.getArgument(0);
                    return DataTaskReportResultDTO.builder()
                            .taskId(c.getTaskId())
                            .accepted(true)
                            .perfRunTaskId("RT_" + c.getTaskId())
                            .build();
                });
    }

    // =================== success ===================

    @Test
    void reportStatus_successPayload_returnsAcceptedWithTaskId() throws Exception {
        DataTaskStatusReqDTOFixture req = DataTaskStatusReqDTOFixture.valid();

        mockMvc.perform(post("/api/data-task/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req.toMap())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.taskId").value(req.getTaskId()))
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.perfRunTaskId").isNotEmpty());

        // 校验 Controller 正确转换 DTO 为 Cmd 并下传
        ArgumentCaptor<DataTaskStatusCmd> captor = ArgumentCaptor.forClass(DataTaskStatusCmd.class);
        Mockito.verify(dataTaskApi).reportDataTaskStatus(captor.capture());
        DataTaskStatusCmd cmd = captor.getValue();
        assertThat(cmd.getTaskId()).isEqualTo(req.getTaskId());
        assertThat(cmd.getDataType()).isEqualTo(req.getDataType());
        assertThat(cmd.getDataDate()).isEqualTo(req.getDataDate());
        assertThat(cmd.getStatus()).isEqualTo(req.getStatus());
        assertThat(cmd.getVersion()).isEqualTo(req.getVersion());
    }

    @Test
    void reportStatus_failedPayloadWithErrorMsg_accepted() throws Exception {
        DataTaskStatusReqDTOFixture req = DataTaskStatusReqDTOFixture.valid();
        req.setStatus("FAILED");
        req.setErrorMsg("上游 ETL 超时");
        req.setTaskId("EXT_FAIL_001");

        mockMvc.perform(post("/api/data-task/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req.toMap())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.taskId").value("EXT_FAIL_001"));

        Mockito.verify(dataTaskApi).reportDataTaskStatus(Mockito.any(DataTaskStatusCmd.class));
    }

    // =================== validation ===================

    @Test
    void reportStatus_missingDataDate_returns400() throws Exception {
        DataTaskStatusReqDTOFixture req = DataTaskStatusReqDTOFixture.valid();
        req.setDataDate(null);

        mockMvc.perform(post("/api/data-task/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req.toMap())))
                .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(dataTaskApi);
    }

    @Test
    void reportStatus_invalidStatus_returns400() throws Exception {
        DataTaskStatusReqDTOFixture req = DataTaskStatusReqDTOFixture.valid();
        req.setStatus("PARTIAL"); // 非法 → Pattern 校验

        mockMvc.perform(post("/api/data-task/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req.toMap())))
                .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(dataTaskApi);
    }

    @Test
    void reportStatus_missingTaskId_returns400() throws Exception {
        DataTaskStatusReqDTOFixture req = DataTaskStatusReqDTOFixture.valid();
        req.setTaskId(""); // NotBlank

        mockMvc.perform(post("/api/data-task/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req.toMap())))
                .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(dataTaskApi);
    }

    @Test
    void reportStatus_invalidDataType_returns400() throws Exception {
        DataTaskStatusReqDTOFixture req = DataTaskStatusReqDTOFixture.valid();
        req.setDataType("UNKNOWN_TYPE");

        mockMvc.perform(post("/api/data-task/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req.toMap())))
                .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(dataTaskApi);
    }

    // =================== 注解约束 ===================

    @Test
    void reportStatusMethod_shouldNotDeclareBizAuth() throws Exception {
        // 03 §G.1: 外部 API Token 通道, 不走前端登录鉴权链
        Method m = Class.forName(CONTROLLER_FQCN)
                .getDeclaredMethod("reportStatus",
                        com.bank.branch.platform.performance.controller.dto.DataTaskStatusReqDTO.class);
        assertThat(m.getAnnotation(BizAuth.class))
                .as("外部上报端点走 API Token, 不应声明 @BizAuth")
                .isNull();
    }

    @Test
    void reportStatusMethod_shouldDeclareAuditLog() throws Exception {
        Method m = Class.forName(CONTROLLER_FQCN)
                .getDeclaredMethod("reportStatus",
                        com.bank.branch.platform.performance.controller.dto.DataTaskStatusReqDTO.class);
        AuditLog al = m.getAnnotation(AuditLog.class);
        assertThat(al).as("外部上报关键事件必须记审计").isNotNull();
        assertThat(al.action()).isEqualTo("DATA_TASK_STATUS_REPORT");
        assertThat(al.resourceType()).isEqualTo("EXT_DATA_TASK");
    }

    // =================== fixture ===================

    /**
     * 小型构造器：生成合规 JSON 请求体 + 提供可变字段用于各用例.
     * 用 LinkedHashMap 保证序列化字段顺序可预测，便于断言排查.
     */
    static final class DataTaskStatusReqDTOFixture {
        private String taskId = "EXT_20260401_CORE_01";
        private String dataType = "EMP_INDEX_RESULT";
        private LocalDate dataDate = LocalDate.of(2026, 4, 1);
        private String version = "20260401-01";
        private String status = "SUCCESS";
        private Integer rowCount = 1000;
        private String errorMsg;
        private String sourceSystem = "CORE_BANK";

        static DataTaskStatusReqDTOFixture valid() {
            return new DataTaskStatusReqDTOFixture();
        }

        java.util.Map<String, Object> toMap() {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("taskId", taskId);
            m.put("dataType", dataType);
            if (dataDate != null) {
                m.put("dataDate", dataDate.toString());
            }
            m.put("version", version);
            m.put("status", status);
            m.put("rowCount", rowCount);
            m.put("errorMsg", errorMsg);
            m.put("sourceSystem", sourceSystem);
            return m;
        }

        public String getTaskId() { return taskId; }
        public void setTaskId(String v) { this.taskId = v; }
        public String getDataType() { return dataType; }
        public void setDataType(String v) { this.dataType = v; }
        public LocalDate getDataDate() { return dataDate; }
        public void setDataDate(LocalDate v) { this.dataDate = v; }
        public String getVersion() { return version; }
        public String getStatus() { return status; }
        public void setStatus(String v) { this.status = v; }
        public void setErrorMsg(String v) { this.errorMsg = v; }
    }
}
