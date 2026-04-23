package com.bank.branch.platform.performance.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4 类具体领域事件 topic / 字段契约测试 (V1.2 Q1.1, Red).
 *
 * <p>topic 命名规范: performance.&lt;topic&gt;.&lt;verb&gt;.v&lt;version&gt;
 *
 * <p>字段对齐 V1.2 计划顶部的 "DDL 权威修订通知":
 * <ul>
 *   <li>TargetAdjustmentApprovedEvent 使用 subjectType/subjectId/cycleKey (非 empId/metricCode)</li>
 * </ul>
 */
class PerfDomainEventsTopicTest {

    @Test
    @DisplayName("[Red] SysControlUpdatedEvent topic = performance.sys-control.updated.v1")
    void sysControlUpdatedEvent_topic() {
        SysControlUpdatedEvent e = new SysControlUpdatedEvent(
                "trace-1", "EMP", "V1", "V2", "MANUAL", "admin");
        assertThat(e.topic()).isEqualTo("performance.sys-control.updated.v1");
        assertThat(e.getScopeDim()).isEqualTo("EMP");
        assertThat(e.getOldVersion()).isEqualTo("V1");
        assertThat(e.getNewVersion()).isEqualTo("V2");
        assertThat(e.getPublishSource()).isEqualTo("MANUAL");
        assertThat(e.getPublishBy()).isEqualTo("admin");
        assertThat(e.getTraceId()).isEqualTo("trace-1");
    }

    @Test
    @DisplayName("[Red] KpiCalcCompletedEvent topic = performance.kpi-calc.completed.v1")
    void kpiCalcCompletedEvent_topic() {
        KpiCalcCompletedEvent e = new KpiCalcCompletedEvent(
                "trace-1", "KPI_001", "DAY", java.time.LocalDate.of(2026, 4, 22),
                java.time.LocalDate.of(2026, 4, 22), "V20260422", 100);
        assertThat(e.topic()).isEqualTo("performance.kpi-calc.completed.v1");
        assertThat(e.getSchemeCode()).isEqualTo("KPI_001");
        assertThat(e.getCycleType()).isEqualTo("DAY");
        assertThat(e.getEmpCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("[Red] TargetAdjustmentApprovedEvent topic = performance.target-adjustment.approved.v1 字段使用 subjectType/subjectId/cycleKey")
    void targetAdjustmentApprovedEvent_topic() {
        TargetAdjustmentApprovedEvent e = new TargetAdjustmentApprovedEvent(
                "trace-1", "APPLY_001", "PLAN_001", "EMP", "E001", "2026Q2", "boss");
        assertThat(e.topic()).isEqualTo("performance.target-adjustment.approved.v1");
        assertThat(e.getApplyId()).isEqualTo("APPLY_001");
        assertThat(e.getPlanId()).isEqualTo("PLAN_001");
        assertThat(e.getSubjectType()).isEqualTo("EMP");
        assertThat(e.getSubjectId()).isEqualTo("E001");
        assertThat(e.getCycleKey()).isEqualTo("2026Q2");
        assertThat(e.getApprovedBy()).isEqualTo("boss");
    }

    @Test
    @DisplayName("[Red] AllocationAdjustmentApprovedEvent topic = performance.allocation-adjustment.approved.v1")
    void allocationAdjustmentApprovedEvent_topic() {
        AllocationAdjustmentApprovedEvent e = new AllocationAdjustmentApprovedEvent(
                "trace-1", "APPLY_002", "C001", "OWNER", "DEPOSIT", 3, "approver1");
        assertThat(e.topic()).isEqualTo("performance.allocation-adjustment.approved.v1");
        assertThat(e.getApplyId()).isEqualTo("APPLY_002");
        assertThat(e.getCustId()).isEqualTo("C001");
        assertThat(e.getAllocDim()).isEqualTo("OWNER");
        assertThat(e.getBizKind()).isEqualTo("DEPOSIT");
        assertThat(e.getItemCount()).isEqualTo(3);
        assertThat(e.getApprovedBy()).isEqualTo("approver1");
    }
}
