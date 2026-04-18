package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateMetricDefCmd;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricDefService 单元测试.
 */
@ExtendWith(MockitoExtension.class)
class MetricDefServiceTest {

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

    @Test
    @DisplayName("L1 指标创建时写主表且无引用")
    void create_L1_insertsDefAndNoRefs() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_L1")
                .metricName("L1 指标")
                .baseDim("EMP")
                .metricLevel(1)
                .calcFreq("DAY")
                .calcMode("AUTO")
                .calcLogicType("SQL")
                .sqlText("SELECT 1")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_L1")).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot("EMP", 1, null)).thenReturn(1);

        PerfMetricDef created = service.create(cmd);

        assertThat(created.getMetricCode()).isEqualTo("TEST_METRIC_L1");
        assertThat(created.getValSlot()).isEqualTo(1);
        ArgumentCaptor<PerfMetricDef> captor = ArgumentCaptor.forClass(PerfMetricDef.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getRefMetricCodes()).isEqualTo("[]");
        verify(metricRefService).setRefs("TEST_METRIC_L1", List.of());
    }

    @Test
    @DisplayName("L2 指标创建时会写主表并重建引用")
    void create_L2WithRefs_insertsDefAndRefRows() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_L2")
                .metricName("L2 指标")
                .baseDim("EMP")
                .metricLevel(2)
                .calcFreq("DAY")
                .calcMode("AUTO")
                .calcLogicType("EXPR")
                .exprText("#A + #B")
                .refMetricCodes("[\"REF_1\",\"REF_2\"]")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_L2")).thenReturn(null);
        when(mapper.selectByMetricCodes(List.of("REF_1", "REF_2")))
                .thenReturn(List.of(metric("REF_1", 1), metric("REF_2", 1)));
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot("EMP", 2, null)).thenReturn(101);

        PerfMetricDef created = service.create(cmd);

        assertThat(created.getMetricCode()).isEqualTo("TEST_METRIC_L2");
        assertThat(created.getValSlot()).isEqualTo(101);
        verify(metricRefService).setRefs("TEST_METRIC_L2", List.of("REF_1", "REF_2"));
    }

    @Test
    @DisplayName("指标编码重复时抛 40903")
    void create_whenMetricCodeDup_throws40903() {
        when(mapper.selectByMetricCode("TEST_METRIC_DUP")).thenReturn(metric("TEST_METRIC_DUP", 1));

        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_DUP")
                .metricName("dup")
                .baseDim("EMP")
                .metricLevel(1)
                .operator("admin")
                .build();

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_CODE_DUP));
    }

    @Test
    @DisplayName("引用层级错误时抛 40911")
    void create_whenRefLevelWrong_throws40911() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_BAD_LEVEL")
                .metricName("bad")
                .baseDim("EMP")
                .metricLevel(2)
                .refMetricCodes("[\"REF_BAD\"]")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_BAD_LEVEL")).thenReturn(null);
        when(mapper.selectByMetricCodes(List.of("REF_BAD"))).thenReturn(List.of(metric("REF_BAD", 3)));
        doThrow(new PerfException(PerfErrorCode.METRIC_LEVEL_INVALID, "bad"))
                .when(metricCycleDetectService).checkLevelConstraint(eq(2), any(Map.class));

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_LEVEL_INVALID));
    }

    @Test
    @DisplayName("引用成环时抛 40902")
    void create_whenRefFormsCycle_throws40902() {
        CreateMetricDefCmd cmd = CreateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_CYCLE")
                .metricName("cycle")
                .baseDim("EMP")
                .metricLevel(2)
                .refMetricCodes("[\"REF_A\"]")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_CYCLE")).thenReturn(null);
        when(mapper.selectByMetricCodes(List.of("REF_A"))).thenReturn(List.of(metric("REF_A", 1)));
        when(metricRefService.loadFullGraph()).thenReturn(Map.of("REF_A", List.of("TEST_METRIC_CYCLE")));
        doThrow(new PerfException(PerfErrorCode.METRIC_CYCLE_DETECTED, "cycle"))
                .when(metricCycleDetectService).checkNoCycle(any(Map.class), eq("TEST_METRIC_CYCLE"), eq(List.of("REF_A")));

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_CYCLE_DETECTED));
    }

    @Test
    @DisplayName("更新引用时会重建引用关系")
    void update_withRefsChange_callsSetRefsWithNewList() {
        PerfMetricDef existing = metric("TEST_METRIC_UPD", 2);
        existing.setId("ID_UPD");
        existing.setBaseDim("EMP");
        existing.setMetricLevel(2);
        existing.setValSlot(120);

        UpdateMetricDefCmd cmd = UpdateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_UPD")
                .metricName("updated")
                .metricDesc("new desc")
                .refMetricCodes("[\"REF_NEW\"]")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_UPD")).thenReturn(existing);
        when(mapper.selectByMetricCodes(List.of("REF_NEW"))).thenReturn(List.of(metric("REF_NEW", 1)));
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());

        PerfMetricDef updated = service.update(cmd);

        assertThat(updated.getMetricCode()).isEqualTo("TEST_METRIC_UPD");
        verify(metricRefService).setRefs("TEST_METRIC_UPD", List.of("REF_NEW"));
        verify(mapper).updateByIdSelective(any(PerfMetricDef.class));
    }

    @Test
    @DisplayName("更新未显式提供 refMetricCodes 时保留原有依赖")
    void update_whenRefMetricCodesOmitted_keepsExistingRefs() {
        PerfMetricDef existing = metric("TEST_METRIC_KEEP_REFS", 2);
        existing.setId("ID_KEEP_REFS");
        existing.setMetricLevel(2);
        existing.setRefMetricCodes("[\"REF_OLD\"]");

        UpdateMetricDefCmd cmd = UpdateMetricDefCmd.builder()
                .metricCode("TEST_METRIC_KEEP_REFS")
                .metricName("updated-name")
                .operator("admin")
                .build();

        when(mapper.selectByMetricCode("TEST_METRIC_KEEP_REFS")).thenReturn(existing);

        PerfMetricDef updated = service.update(cmd);

        ArgumentCaptor<PerfMetricDef> captor = ArgumentCaptor.forClass(PerfMetricDef.class);
        verify(mapper).updateByIdSelective(captor.capture());
        assertThat(captor.getValue().getRefMetricCodes()).isNull();
        verify(metricRefService, never()).setRefs(any(), any());
        assertThat(updated.getRefMetricCodes()).isEqualTo("[\"REF_OLD\"]");
        assertThat(updated.getMetricName()).isEqualTo("updated-name");
    }

    @Test
    @DisplayName("非 ACTIVE 状态不可停用")
    void disable_whenStatusNotActive_throws40905() {
        PerfMetricDef existing = metric("TEST_METRIC_DISABLED", 1);
        existing.setStatus("DISABLED");
        when(mapper.selectByMetricCode("TEST_METRIC_DISABLED")).thenReturn(existing);

        assertThatThrownBy(() -> service.disable("TEST_METRIC_DISABLED", "停用", "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.INVALID_STATE));
    }

    @Test
    @DisplayName("分页查询返回 PageResult")
    void page_returnsPageResult() {
        when(mapper.countByCondition("EMP", 1, "ACTIVE", "KW")).thenReturn(2L);
        when(mapper.selectByCondition("EMP", 1, "ACTIVE", "KW", 0, 10))
                .thenReturn(List.of(metric("M1", 1), metric("M2", 1)));

        PageResult<PerfMetricDef> result = service.page("EMP", 1, "ACTIVE", "KW", 1, 10);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(2);
    }

    private static PerfMetricDef metric(String metricCode, int level) {
        PerfMetricDef def = new PerfMetricDef();
        def.setId("ID_" + metricCode);
        def.setMetricCode(metricCode);
        def.setMetricName(metricCode);
        def.setBaseDim("EMP");
        def.setMetricLevel(level);
        def.setCalcFreq("DAY");
        def.setCalcMode("AUTO");
        def.setCalcLogicType("SQL");
        def.setStatus("ACTIVE");
        return def;
    }
}
