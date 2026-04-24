package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.performance.controller.dto.MetricTrialRespDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetricTrialRespDTO 字段对齐 03 §A.5 测试（V1.3 R3.2 Red → Green）.
 *
 * <p>V1.1 P3 Reviewer 提出的字段对齐诉求：
 * <ul>
 *   <li>{@code samples} → {@code sampleRows}（命名对齐 03 §A.5）</li>
 *   <li>新增 5 字段：{@code taskId}、{@code status}、{@code startedAt}、{@code endedAt}、{@code errorMsg}</li>
 *   <li>破坏性兼容：保留 {@code @Deprecated getSamples()} 让旧客户端代码可编译；
 *       反序列化时 {@code @JsonAlias({"samples"})} 让旧前端请求仍可解析</li>
 * </ul>
 */
class MetricTrialRespDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void respDTO_hasSampleRows_andAllV13Fields() throws Exception {
        MetricTrialRespDTO dto = MetricTrialRespDTO.builder()
                .taskId("TASK_001")
                .metricCode("M_TRIAL_X")
                .sampleSize(1)
                .totalRows(1)
                .status("SUCCESS")
                .startedAt(LocalDateTime.of(2026, 4, 24, 10, 0, 0))
                .endedAt(LocalDateTime.of(2026, 4, 24, 10, 0, 1))
                .errorMsg(null)
                .exprResult(new BigDecimal("42"))
                .executionMillis(1500L)
                .sampleRows(List.of(Map.of("base_key", "E1", "metric_value", "100")))
                .build();

        String json = objectMapper.writeValueAsString(dto);
        // V1.3 新增字段必须出现在 JSON 中（序列化契约）
        assertThat(json).contains("\"taskId\"");
        assertThat(json).contains("\"status\"");
        assertThat(json).contains("\"startedAt\"");
        assertThat(json).contains("\"endedAt\"");
        assertThat(json).contains("\"sampleRows\"");
        // V1.3 改名：旧 samples 字段不再作为序列化输出字段
        assertThat(json).doesNotContain("\"samples\":");
    }

    @Test
    void respDTO_deserializeLegacySamples_viaJsonAlias() throws Exception {
        // 兼容旧前端：请求体使用旧字段名 samples，反序列化到 sampleRows
        String legacyJson = "{\"taskId\":\"T\",\"samples\":[{\"base_key\":\"E1\"}]}";
        MetricTrialRespDTO parsed = objectMapper.readValue(legacyJson, MetricTrialRespDTO.class);
        assertThat(parsed.getTaskId()).isEqualTo("T");
        assertThat(parsed.getSampleRows()).hasSize(1);
        assertThat(parsed.getSampleRows().get(0)).containsEntry("base_key", "E1");
    }

    @Test
    void respDTO_deprecatedGetSamples_stillReturnsSampleRows() {
        // 兼容旧客户端代码：getSamples() 仍返回 sampleRows 实例（@Deprecated 方法，1 版本后删除）
        List<Map<String, Object>> rows = List.of(Map.of("base_key", "E1"));
        MetricTrialRespDTO dto = MetricTrialRespDTO.builder()
                .sampleRows(rows)
                .build();
        assertThat(dto.getSamples()).isEqualTo(rows);
    }
}
