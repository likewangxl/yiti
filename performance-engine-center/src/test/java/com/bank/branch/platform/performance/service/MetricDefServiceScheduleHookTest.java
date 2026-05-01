package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateMetricDefCmd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MetricDefService CRUD 调度 Hook 测试（V1.7 P6 Task 18）.
 *
 * <p>使用纯 Mockito UT 策略（无 Spring 事务上下文）。
 * hook 方法在无事务时直接执行（非 afterCommit 延迟），因此可以用 verify 同步断言.
 */
@ExtendWith(MockitoExtension.class)
class MetricDefServiceScheduleHookTest {

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

    @Mock
    private CurrentUserApi currentUserApi;

    @Mock
    private PerfScopeHelper perfScopeHelper;

    @Mock
    private MetricSchedulerService metricSchedulerService;

    @InjectMocks
    private MetricDefService metricDefService;

    @Test
    @DisplayName("create ACTIVE+AUTO 触发 register")
    void create_active_auto_triggers_register() {
        when(mapper.selectByMetricCode("TEST_HOOK_M1")).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot(any(), any(), any())).thenReturn(1);
        when(metricSchedulerService.isSchedulable(any())).thenReturn(true);

        metricDefService.create(buildCreateCmd("TEST_HOOK_M1", "ACTIVE", "AUTO"));

        verify(metricSchedulerService).register(any(PerfMetricDef.class));
    }

    @Test
    @DisplayName("create DISABLED 不触发 register")
    void create_disabled_does_not_trigger_register() {
        when(mapper.selectByMetricCode("TEST_HOOK_M2")).thenReturn(null);
        when(metricRefService.loadFullGraph()).thenReturn(Collections.emptyMap());
        when(metricSlotService.allocSlot(any(), any(), any())).thenReturn(2);
        when(metricSchedulerService.isSchedulable(any())).thenReturn(false);

        metricDefService.create(buildCreateCmd("TEST_HOOK_M2", "DISABLED", "AUTO"));

        verify(metricSchedulerService, never()).register(any());
    }

    @Test
    @DisplayName("update ACTIVE→DISABLED 触发 unregister")
    void update_active_to_disabled_triggers_unregister() {
        PerfMetricDef existing = buildDef("TEST_HOOK_M3", "ACTIVE", "AUTO");
        when(mapper.selectByMetricCode("TEST_HOOK_M3")).thenReturn(existing);
        // update 后 isSchedulable 返回 false（状态已改为 DISABLED）
        when(metricSchedulerService.isSchedulable(any())).thenReturn(false);

        metricDefService.update(UpdateMetricDefCmd.builder()
                .metricCode("TEST_HOOK_M3")
                .calcMode("MANUAL")
                .operator("admin")
                .build());

        verify(metricSchedulerService).unregister("TEST_HOOK_M3");
    }

    @Test
    @DisplayName("deleteMetric 触发 unregister")
    void deleteMetric_triggers_unregister() {
        PerfMetricDef existing = buildDef("TEST_HOOK_M4", "ACTIVE", "AUTO");
        existing.setId("id_m4");
        when(mapper.selectById("id_m4")).thenReturn(existing);

        metricDefService.deleteMetric("id_m4");

        verify(metricSchedulerService).unregister("TEST_HOOK_M4");
    }

    // ---- helpers ----

    private CreateMetricDefCmd buildCreateCmd(String code, String status, String mode) {
        return CreateMetricDefCmd.builder()
                .metricCode(code)
                .metricName(code)
                .baseDim("EMP")
                .metricLevel(1)
                .calcFreq("DAY")
                .calcMode(mode)
                .calcLogicType("SQL")
                .sqlText("SELECT 1")
                .operator("admin")
                .build();
    }

    private PerfMetricDef buildDef(String code, String status, String mode) {
        PerfMetricDef def = new PerfMetricDef();
        def.setId(code);
        def.setMetricCode(code);
        def.setMetricName(code);
        def.setStatus(status);
        def.setCalcMode(mode);
        def.setCalcLogicType("SQL");
        def.setDeleted(0);
        return def;
    }
}
