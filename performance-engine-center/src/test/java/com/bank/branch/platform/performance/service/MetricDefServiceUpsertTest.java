package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.result.BatchUpsertMetricDefResult;
import com.bank.branch.platform.performance.service.result.UpsertMetricDefResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V1.11：MetricDefService 按名称 upsert 测试.
 *
 * <p>覆盖：
 * <ul>
 *   <li>upsertByName：不命中 → 走 create 路径，inserted=true</li>
 *   <li>upsertByName：命中 → 走 update 路径，inserted=false，保留 DB 原 metric_code/id</li>
 *   <li>upsertByName：命中 → val_slot 保留 DB 原值</li>
 *   <li>batchUpsertByName：全新增计数正确</li>
 *   <li>batchUpsertByName：全更新计数正确</li>
 *   <li>batchUpsertByName：混合 3 新增 + 2 更新计数正确</li>
 *   <li>batchUpsertByName：任一行抛 → 异常向上传播（整批回滚由 @Transactional 保证）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MetricDefServiceUpsertTest {

    @Mock
    private PerfMetricDefMapper mapper;

    @Mock
    private MetricRefService metricRefService;

    @Mock
    private MetricSlotService metricSlotService;

    @Mock
    private MetricCycleDetectService metricCycleDetectService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MetricDefService service;

    // ===== upsertByName =====

    @Test
    @DisplayName("upsertByName：DB 无命中 → 走 create 路径，inserted=true，metric_code 取 cmd 值")
    void upsertByName_whenNoMatch_insertsNew() {
        CreateMetricDefCmd cmd = baseCmd("M_NEW", "新增指标");

        when(mapper.selectByMetricName("新增指标")).thenReturn(null);
        when(mapper.selectByMetricCode("M_NEW")).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot("EMP", 1, null)).thenReturn(5);

        UpsertMetricDefResult r = service.upsertByName(cmd, "admin");

        assertThat(r.isInserted()).isTrue();
        assertThat(r.getDef().getMetricCode()).isEqualTo("M_NEW");
        assertThat(r.getDef().getMetricName()).isEqualTo("新增指标");
        // 走 create 路径必然 insert 一次
        verify(mapper).insert(any(PerfMetricDef.class));
    }

    @Test
    @DisplayName("upsertByName：DB 命中 → 走 update 路径，inserted=false，metric_code 保留 DB 原值")
    void upsertByName_whenMatch_updatesExisting_preservesCode() {
        // cmd 里 metric_code = "M_FROM_CMD"，但 DB 里同名指标 metric_code = "M_OLD"，应保留 M_OLD
        CreateMetricDefCmd cmd = baseCmd("M_FROM_CMD", "已存在指标");
        cmd.setMetricDesc("Excel 里新的口径说明");

        PerfMetricDef existing = existingDef("ID_OLD", "M_OLD", "已存在指标", 7);
        when(mapper.selectByMetricName("已存在指标")).thenReturn(existing);

        UpsertMetricDefResult r = service.upsertByName(cmd, "admin");

        assertThat(r.isInserted()).isFalse();
        assertThat(r.getDef().getId()).isEqualTo("ID_OLD");
        assertThat(r.getDef().getMetricCode()).isEqualTo("M_OLD");

        // 更新路径：updateByIdSelective 调用一次，patch.metric_code 应为 null（保留 DB 原值）
        ArgumentCaptor<PerfMetricDef> patchCaptor = ArgumentCaptor.forClass(PerfMetricDef.class);
        verify(mapper).updateByIdSelective(patchCaptor.capture());
        PerfMetricDef patch = patchCaptor.getValue();
        assertThat(patch.getId()).isEqualTo("ID_OLD");
        assertThat(patch.getMetricCode()).isNull();    // 不改 metric_code
        assertThat(patch.getValSlot()).isNull();       // 不改 val_slot
        assertThat(patch.getMetricDesc()).isEqualTo("Excel 里新的口径说明");

        // 更新路径不应走 insert / allocSlot
        verify(mapper, never()).insert(any(PerfMetricDef.class));
        verify(metricSlotService, never()).allocSlot(any(), any(), any());
    }

    @Test
    @DisplayName("upsertByName：DB 命中 → val_slot 保留 DB 原值不变（不重新分配）")
    void upsertByName_whenMatch_preservesValSlot() {
        CreateMetricDefCmd cmd = baseCmd("M_X", "已存在2");
        cmd.setPreferredSlot(99);  // 即使 cmd 带 preferredSlot 也应忽略

        PerfMetricDef existing = existingDef("ID_X", "M_X_OLD", "已存在2", 42);
        when(mapper.selectByMetricName("已存在2")).thenReturn(existing);

        UpsertMetricDefResult r = service.upsertByName(cmd, "admin");

        assertThat(r.isInserted()).isFalse();
        assertThat(r.getDef().getValSlot()).isEqualTo(42);   // DB 原值
        verify(metricSlotService, never()).allocSlot(any(), any(), any());
    }

    // ===== batchUpsertByName =====

    @Test
    @DisplayName("batchUpsertByName：全新增 5 行 → insertedRows=5, updatedRows=0")
    void batchUpsertByName_allInsert_countsCorrect() {
        List<CreateMetricDefCmd> cmds = List.of(
                baseCmd("M_NEW1", "新1"),
                baseCmd("M_NEW2", "新2"),
                baseCmd("M_NEW3", "新3"),
                baseCmd("M_NEW4", "新4"),
                baseCmd("M_NEW5", "新5"));

        when(mapper.selectByMetricName(any())).thenReturn(null);
        when(mapper.selectByMetricCode(any())).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot(any(), any(), any())).thenReturn(1);

        BatchUpsertMetricDefResult r = service.batchUpsertByName(cmds, "admin");

        assertThat(r.getInsertedRows()).isEqualTo(5);
        assertThat(r.getUpdatedRows()).isZero();
        assertThat(r.getDefs()).hasSize(5);
        verify(mapper, times(5)).insert(any(PerfMetricDef.class));
        verify(mapper, never()).updateByIdSelective(any());
    }

    @Test
    @DisplayName("batchUpsertByName：全更新 3 行 → insertedRows=0, updatedRows=3")
    void batchUpsertByName_allUpdate_countsCorrect() {
        List<CreateMetricDefCmd> cmds = List.of(
                baseCmd("M_C1", "U1"),
                baseCmd("M_C2", "U2"),
                baseCmd("M_C3", "U3"));

        when(mapper.selectByMetricName("U1")).thenReturn(existingDef("ID1", "M_OLD1", "U1", 1));
        when(mapper.selectByMetricName("U2")).thenReturn(existingDef("ID2", "M_OLD2", "U2", 2));
        when(mapper.selectByMetricName("U3")).thenReturn(existingDef("ID3", "M_OLD3", "U3", 3));

        BatchUpsertMetricDefResult r = service.batchUpsertByName(cmds, "admin");

        assertThat(r.getInsertedRows()).isZero();
        assertThat(r.getUpdatedRows()).isEqualTo(3);
        assertThat(r.getDefs()).hasSize(3);
        verify(mapper, times(3)).updateByIdSelective(any());
        verify(mapper, never()).insert(any(PerfMetricDef.class));
    }

    @Test
    @DisplayName("batchUpsertByName：混合 3 新增 + 2 更新 → insertedRows=3, updatedRows=2")
    void batchUpsertByName_mixed_countsCorrect() {
        List<CreateMetricDefCmd> cmds = List.of(
                baseCmd("M_NEW_A", "新A"),
                baseCmd("M_C_B", "更B"),
                baseCmd("M_NEW_C", "新C"),
                baseCmd("M_C_D", "更D"),
                baseCmd("M_NEW_E", "新E"));

        when(mapper.selectByMetricName("新A")).thenReturn(null);
        when(mapper.selectByMetricName("更B")).thenReturn(existingDef("IB", "M_OB", "更B", 2));
        when(mapper.selectByMetricName("新C")).thenReturn(null);
        when(mapper.selectByMetricName("更D")).thenReturn(existingDef("ID", "M_OD", "更D", 4));
        when(mapper.selectByMetricName("新E")).thenReturn(null);
        when(mapper.selectByMetricCode(any())).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot(any(), any(), any())).thenReturn(1);

        BatchUpsertMetricDefResult r = service.batchUpsertByName(cmds, "admin");

        assertThat(r.getInsertedRows()).isEqualTo(3);
        assertThat(r.getUpdatedRows()).isEqualTo(2);
        assertThat(r.getDefs()).hasSize(5);
    }

    @Test
    @DisplayName("batchUpsertByName：任一行 select 抛 PerfException → 异常向上传播")
    void batchUpsertByName_anyRowThrows_propagates() {
        List<CreateMetricDefCmd> cmds = List.of(
                baseCmd("M_OK1", "OK1"),
                baseCmd("M_BAD", "BAD"),
                baseCmd("M_OK2", "OK2"));

        when(mapper.selectByMetricName("OK1")).thenReturn(null);
        when(mapper.selectByMetricCode("M_OK1")).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot(any(), any(), any())).thenReturn(1);
        when(mapper.selectByMetricName("BAD"))
                .thenThrow(new RuntimeException("DB conn lost"));

        assertThatThrownBy(() -> service.batchUpsertByName(cmds, "admin"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB conn lost");
    }

    // ===== fixture helpers =====

    private static CreateMetricDefCmd baseCmd(String code, String name) {
        return CreateMetricDefCmd.builder()
                .metricCode(code)
                .metricName(name)
                .baseDim("EMP")
                .metricLevel(1)
                .calcFreq("DAY")
                .calcMode("AUTO")
                .calcLogicType("SQL")
                .sqlText("SELECT 1")
                .summaryRule("SUM")
                .operator("admin")
                .build();
    }

    private static PerfMetricDef existingDef(String id, String code, String name, Integer slot) {
        PerfMetricDef def = new PerfMetricDef();
        def.setId(id);
        def.setMetricCode(code);
        def.setMetricName(name);
        def.setBaseDim("EMP");
        def.setMetricLevel(1);
        def.setCalcFreq("DAY");
        def.setCalcMode("AUTO");
        def.setCalcLogicType("SQL");
        def.setSqlText("SELECT 0");
        def.setSummaryRule("SUM");
        def.setValSlot(slot);
        def.setStatus("ACTIVE");
        def.setDeleted(0);
        def.setCreatedBy("old");
        def.setCreatedTime(LocalDateTime.now().minusDays(7));
        def.setUpdatedBy("old");
        def.setUpdatedTime(LocalDateTime.now().minusDays(7));
        return def;
    }
}
