package com.bank.branch.platform.performance.event;

import com.bank.branch.platform.performance.support.PerfTestApp;
import com.bank.branch.platform.performance.support.PerfTestConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4 类领域事件 JSON 序列化契约测试 (V1.2 Q4.2, Red).
 *
 * <p>目的：事件 POJO 在未来切换到 Kafka/MQ 时需要 JSON 序列化。本测试固化 JSON 契约，
 * 防止字段改名破坏下游订阅者消费。
 *
 * <p>断言内容：
 * <ul>
 *   <li>基类字段 {@code eventId}（32 字符 UUID 去横线）</li>
 *   <li>基类字段 {@code traceId}（与构造入参一致）</li>
 *   <li>基类字段 {@code occurredAt}（非空 ISO-8601 字符串）</li>
 *   <li>基类方法 {@code topic()} → JSON 字段 {@code topic}（由 {@code @JsonProperty} 暴露）</li>
 *   <li>4 类事件各自的业务字段</li>
 * </ul>
 *
 * <p>使用项目 Spring Boot 默认装配的 ObjectMapper（JacksonAutoConfiguration 已注册 JavaTimeModule），
 * 不在测试内 new ObjectMapper。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {PerfTestApp.class, PerfTestConfig.class})
@ActiveProfiles("test")
class EventJsonSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("[Red] SysControlUpdatedEvent JSON 契约：topic + 基类字段 + 6 业务字段")
    void sysControlUpdatedEvent_jsonSchema() throws Exception {
        SysControlUpdatedEvent e = new SysControlUpdatedEvent(
                "trace-abc", "EMP", "v1", "v2", "MANUAL", "admin");

        String json = objectMapper.writeValueAsString(e);
        JsonNode node = objectMapper.readTree(json);

        // 基类契约
        assertThat(node.get("eventId")).isNotNull();
        assertThat(node.get("eventId").asText()).hasSize(32).doesNotContain("-");
        assertThat(node.get("traceId").asText()).isEqualTo("trace-abc");
        assertThat(node.get("occurredAt")).isNotNull();
        assertThat(node.get("occurredAt").isNull()).isFalse();
        assertThat(node.get("topic").asText()).isEqualTo("performance.sys-control.updated.v1");

        // 业务字段
        assertThat(node.get("scopeDim").asText()).isEqualTo("EMP");
        assertThat(node.get("oldVersion").asText()).isEqualTo("v1");
        assertThat(node.get("newVersion").asText()).isEqualTo("v2");
        assertThat(node.get("publishSource").asText()).isEqualTo("MANUAL");
        assertThat(node.get("publishBy").asText()).isEqualTo("admin");
    }

    @Test
    @DisplayName("[Red] KpiCalcCompletedEvent JSON 契约：topic + 基类字段 + 6 业务字段")
    void kpiCalcCompletedEvent_jsonSchema() throws Exception {
        KpiCalcCompletedEvent e = new KpiCalcCompletedEvent(
                "trace-k1", "KPI_001", "QUARTERLY",
                LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1),
                "V20260401", 120);

        String json = objectMapper.writeValueAsString(e);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("eventId").asText()).hasSize(32);
        assertThat(node.get("traceId").asText()).isEqualTo("trace-k1");
        assertThat(node.get("occurredAt")).isNotNull();
        assertThat(node.get("topic").asText()).isEqualTo("performance.kpi-calc.completed.v1");

        assertThat(node.get("schemeCode").asText()).isEqualTo("KPI_001");
        assertThat(node.get("cycleType").asText()).isEqualTo("QUARTERLY");
        assertThat(node.get("cycleDate")).isNotNull();
        assertThat(node.get("asOfDate")).isNotNull();
        assertThat(node.get("version").asText()).isEqualTo("V20260401");
        assertThat(node.get("empCount").asInt()).isEqualTo(120);
    }

    @Test
    @DisplayName("[Red] TargetAdjustmentApprovedEvent JSON 契约：topic + 基类字段 + 6 业务字段")
    void targetAdjustmentApprovedEvent_jsonSchema() throws Exception {
        TargetAdjustmentApprovedEvent e = new TargetAdjustmentApprovedEvent(
                "trace-t1", "APPLY_001", "PLAN_T1", "EMP", "E001", "2026Q2", "boss");

        String json = objectMapper.writeValueAsString(e);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("eventId").asText()).hasSize(32);
        assertThat(node.get("traceId").asText()).isEqualTo("trace-t1");
        assertThat(node.get("occurredAt")).isNotNull();
        assertThat(node.get("topic").asText()).isEqualTo("performance.target-adjustment.approved.v1");

        assertThat(node.get("applyId").asText()).isEqualTo("APPLY_001");
        assertThat(node.get("planId").asText()).isEqualTo("PLAN_T1");
        assertThat(node.get("subjectType").asText()).isEqualTo("EMP");
        assertThat(node.get("subjectId").asText()).isEqualTo("E001");
        assertThat(node.get("cycleKey").asText()).isEqualTo("2026Q2");
        assertThat(node.get("approvedBy").asText()).isEqualTo("boss");
    }

    @Test
    @DisplayName("[Red] AllocationAdjustmentApprovedEvent JSON 契约：topic + 基类字段 + 6 业务字段")
    void allocationAdjustmentApprovedEvent_jsonSchema() throws Exception {
        AllocationAdjustmentApprovedEvent e = new AllocationAdjustmentApprovedEvent(
                "trace-a1", "APPLY_002", "CUST_X", "OWNER", "DEPOSIT", 3, "approver1");

        String json = objectMapper.writeValueAsString(e);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("eventId").asText()).hasSize(32);
        assertThat(node.get("traceId").asText()).isEqualTo("trace-a1");
        assertThat(node.get("occurredAt")).isNotNull();
        assertThat(node.get("topic").asText()).isEqualTo("performance.allocation-adjustment.approved.v1");

        assertThat(node.get("applyId").asText()).isEqualTo("APPLY_002");
        assertThat(node.get("custId").asText()).isEqualTo("CUST_X");
        assertThat(node.get("allocDim").asText()).isEqualTo("OWNER");
        assertThat(node.get("bizKind").asText()).isEqualTo("DEPOSIT");
        assertThat(node.get("itemCount").asInt()).isEqualTo(3);
        assertThat(node.get("approvedBy").asText()).isEqualTo("approver1");
    }
}
