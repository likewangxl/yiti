package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.performance.controller.dto.MetricTrialRespDTO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MetricTrialRespDTO 字段对齐 03 §A.5 测试（V1.3 R3.2 Red → Green / V1.5 P1.1 兼容清理）.
 *
 * <p>V1.3 R3.2 为保障兼容保留的 {@code @Deprecated getSamples()} 与
 * {@code @JsonAlias({"samples"})} 在 V1.5 P1.1 彻底删除。本测试改写为"守护删除后状态"：
 * <ul>
 *   <li>反射守护 {@code getSamples()} 方法不再存在</li>
 *   <li>FAIL_ON_UNKNOWN_PROPERTIES 守护 {@code samples} 字段不再被反序列化（别名已移除）</li>
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
    @DisplayName("V1.5 P1.1：MetricTrialRespDTO 不再暴露 getSamples() 方法")
    void respDTO_legacyGetSamplesMethod_hasBeenRemoved() throws Exception {
        // 反射守护：getSamples() 不应再存在（V1.3→V1.4→V1.5 节奏兑现的兼容期结束承诺）
        boolean hasLegacyGetter = Arrays.stream(MetricTrialRespDTO.class.getMethods())
                .anyMatch(m -> m.getName().equals("getSamples"));
        assertThat(hasLegacyGetter)
                .as("V1.5 删除 @Deprecated getSamples() 兼容方法")
                .isFalse();
    }

    @Test
    @DisplayName("V1.5 P1.1：legacy samples JSON 字段不再反序列化（@JsonAlias 已移除）")
    void respDTO_legacySamplesAlias_notDeserializedAnymore() throws Exception {
        // V1.3 R3.2 @JsonAlias({"samples"}) 已在 V1.5 一并删除；用 fail-on-unknown 模式守护
        ObjectMapper strict = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        String legacyJson = "{\"taskId\":\"T\",\"samples\":[{\"base_key\":\"E1\"}]}";
        assertThatThrownBy(() -> strict.readValue(legacyJson, MetricTrialRespDTO.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }
}
