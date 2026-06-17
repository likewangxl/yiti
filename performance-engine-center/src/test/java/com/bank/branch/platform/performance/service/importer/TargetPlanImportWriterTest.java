package com.bank.branch.platform.performance.service.importer;

import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TargetPlanImportWriter 落库层单测（2026-06-17）.
 *
 * <p>覆盖：
 * <ul>
 *   <li>方案不存在 → 新建 1 个方案 + upsertBatch 收到全部 value，planId 回填正确</li>
 *   <li>方案已存在 → 复用既有 id，never 调 plan insert</li>
 * </ul>
 */
class TargetPlanImportWriterTest {

    private PerfTargetPlanMapper planMapper;
    private PerfTargetValueMapper valueMapper;
    private TargetPlanImportWriter writer;

    @BeforeEach
    void setUp() {
        planMapper = mock(PerfTargetPlanMapper.class);
        valueMapper = mock(PerfTargetValueMapper.class);
        writer = new TargetPlanImportWriter(planMapper, valueMapper);
    }

    @Test
    @DisplayName("方案不存在 → 新建 1 个方案，upsertBatch 收到 2 条 value，planId 回填")
    void write_newPlan_insertsPlanAndUpserts() {
        when(planMapper.selectByPlanCode("PLAN_A")).thenReturn(null);

        List<PerfTargetValue> values = List.of(value("Q1"), value("Q2"));
        List<String> codes = List.of("PLAN_A", "PLAN_A");
        List<String> names = List.of("方案A", "方案A");

        writer.write(new ArrayList<>(values), codes, names, "admin");

        ArgumentCaptor<PerfTargetPlan> planCap = ArgumentCaptor.forClass(PerfTargetPlan.class);
        verify(planMapper, times(1)).insert(planCap.capture());
        PerfTargetPlan plan = planCap.getValue();
        assertThat(plan.getPlanCode()).isEqualTo("PLAN_A");
        assertThat(plan.getPlanName()).isEqualTo("方案A");
        assertThat(plan.getStatus()).isEqualTo("ACTIVE");
        assertThat(plan.getTargetDim()).isEqualTo("EMP");
        assertThat(plan.getTargetCycle()).isEqualTo("YEAR");
        assertThat(plan.getKpiSchemeId()).isEqualTo("");
        assertThat(plan.getId()).hasSize(32);
        assertThat(plan.getEffectiveDate()).isNotNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfTargetValue>> valCap = ArgumentCaptor.forClass(List.class);
        verify(valueMapper, times(1)).upsertBatch(valCap.capture());
        List<PerfTargetValue> upserted = valCap.getValue();
        assertThat(upserted).hasSize(2);
        for (PerfTargetValue v : upserted) {
            assertThat(v.getId()).hasSize(32);
            assertThat(v.getPlanId()).isEqualTo(plan.getId());
            assertThat(v.getCreatedBy()).isEqualTo("admin");
            assertThat(v.getCreatedTime()).isNotNull();
        }
    }

    @Test
    @DisplayName("方案已存在 → 复用既有 id，never 调 plan insert")
    void write_existingPlan_reusesIdNoInsert() {
        PerfTargetPlan exist = new PerfTargetPlan();
        exist.setId("EXIST_PLAN_ID_0000000000000000");
        exist.setPlanCode("PLAN_A");
        exist.setPlanName("旧名称不改");
        when(planMapper.selectByPlanCode("PLAN_A")).thenReturn(exist);

        List<PerfTargetValue> values = new ArrayList<>(List.of(value("Q1")));
        writer.write(values, List.of("PLAN_A"), List.of("新名称会被忽略"), "admin");

        verify(planMapper, never()).insert(eq(exist));
        verify(planMapper, never()).insert(org.mockito.ArgumentMatchers.any(PerfTargetPlan.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfTargetValue>> valCap = ArgumentCaptor.forClass(List.class);
        verify(valueMapper, times(1)).upsertBatch(valCap.capture());
        assertThat(valCap.getValue().get(0).getPlanId()).isEqualTo("EXIST_PLAN_ID_0000000000000000");
    }

    private static PerfTargetValue value(String stage) {
        PerfTargetValue v = new PerfTargetValue();
        v.setSubjectType("EMP");
        v.setSubjectId("emp001");
        v.setMetricCode("M_0001");
        v.setStageName(stage);
        v.setStartDate(LocalDate.of(2026, 1, 1));
        v.setEndDate(LocalDate.of(2026, 3, 31));
        v.setCycleKey("2026");
        return v;
    }
}
