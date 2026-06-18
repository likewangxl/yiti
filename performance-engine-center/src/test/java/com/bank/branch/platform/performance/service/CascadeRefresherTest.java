package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.entity.PerfMetricRef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;

/**
 * {@link CascadeRefresher} 单元测试（Task P2.5 Red）.
 *
 * <p>覆盖：
 * <ul>
 *   <li>链式依赖 A -> B -> C：按拓扑序从 C 到 A 刷新，调用次数 3 次</li>
 *   <li>根指标无下游：仅刷新根自身 1 次</li>
 *   <li>级联深度超过 cascadeMaxDepth：抛 METRIC_CALC_LOGIC_INVALID</li>
 *   <li>环路：抛 METRIC_CALC_LOGIC_INVALID</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CascadeRefresherTest {

    @Mock
    private MetricRefService metricRefService;

    @Mock
    private MetricCalcService metricCalcService;

    @Mock
    private PerfEngineProperties perfEngineProperties;

    private DependencyGraphBuilder dependencyGraphBuilder;

    @InjectMocks
    private CascadeRefresher cascadeRefresher;

    @BeforeEach
    void initRealGraphBuilder() {
        // DependencyGraphBuilder 是纯算法组件，实际类注入
        dependencyGraphBuilder = new DependencyGraphBuilder();
        cascadeRefresher = new CascadeRefresher(metricRefService, metricCalcService,
                dependencyGraphBuilder, perfEngineProperties);
    }

    @Test
    @DisplayName("链式 A -> B -> C：C/B/A 各刷新一次，顺序由拓扑决定")
    void chain_refreshesInTopologicalOrder() {
        PerfMetricRef ab = buildRef("A", "B");
        PerfMetricRef bc = buildRef("B", "C");
        when(metricRefService.listWhoRef("A")).thenReturn(Collections.emptyList());
        when(metricRefService.listWhoRef("B")).thenReturn(Collections.singletonList(ab));
        when(metricRefService.listWhoRef("C")).thenReturn(Collections.singletonList(bc));
        when(perfEngineProperties.getCascadeMaxDepth()).thenReturn(5);

        cascadeRefresher.refreshCascade("C", LocalDate.of(2026, 4, 22), "20260422");

        InOrder order = inOrder(metricCalcService);
        order.verify(metricCalcService).calcMetric(eq("C"), any(LocalDate.class), eq("20260422"), eq("MANUAL"), any());
        order.verify(metricCalcService).calcMetric(eq("B"), any(LocalDate.class), eq("20260422"), eq("MANUAL"), any());
        order.verify(metricCalcService).calcMetric(eq("A"), any(LocalDate.class), eq("20260422"), eq("MANUAL"), any());
        verify(metricCalcService, times(3)).calcMetric(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("根指标无下游：仅刷新根自身")
    void noDownstream_refreshesOnlyRoot() {
        when(metricRefService.listWhoRef("ROOT")).thenReturn(Collections.emptyList());
        when(perfEngineProperties.getCascadeMaxDepth()).thenReturn(5);

        cascadeRefresher.refreshCascade("ROOT", LocalDate.of(2026, 4, 22), "v1");

        verify(metricCalcService, times(1)).calcMetric(eq("ROOT"), any(), eq("v1"), eq("MANUAL"), any());
    }

    @Test
    @DisplayName("级联深度超限：抛 METRIC_CALC_LOGIC_INVALID，不触发计算")
    void depthExceeded_throws() {
        // 构造 7 层链：R <- L1 <- L2 <- ... <- L6（刷新从 R 开始，BFS 深度 7 > cascadeMaxDepth=5）
        when(perfEngineProperties.getCascadeMaxDepth()).thenReturn(5);
        when(metricRefService.listWhoRef("R")).thenReturn(Collections.singletonList(buildRef("L1", "R")));
        when(metricRefService.listWhoRef("L1")).thenReturn(Collections.singletonList(buildRef("L2", "L1")));
        when(metricRefService.listWhoRef("L2")).thenReturn(Collections.singletonList(buildRef("L3", "L2")));
        when(metricRefService.listWhoRef("L3")).thenReturn(Collections.singletonList(buildRef("L4", "L3")));
        when(metricRefService.listWhoRef("L4")).thenReturn(Collections.singletonList(buildRef("L5", "L4")));
        when(metricRefService.listWhoRef("L5")).thenReturn(Collections.singletonList(buildRef("L6", "L5")));
        // L6 是深度 6 超限层级，CascadeRefresher 根本不会探到（poll L6 时先抛异常），无需 stub

        assertThatThrownBy(() -> cascadeRefresher.refreshCascade("R", LocalDate.now(), "v1"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);

        // 超深时必须在开始计算前就拒绝
        verify(metricCalcService, never()).calcMetric(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("环路：拓扑排序阶段抛 METRIC_CALC_LOGIC_INVALID")
    void cyclic_throws() {
        PerfMetricRef ab = buildRef("A", "B");
        PerfMetricRef ba = buildRef("B", "A");
        when(metricRefService.listWhoRef("A")).thenReturn(Collections.singletonList(ba));
        when(metricRefService.listWhoRef("B")).thenReturn(Arrays.asList(ab));
        when(perfEngineProperties.getCascadeMaxDepth()).thenReturn(5);

        assertThatThrownBy(() -> cascadeRefresher.refreshCascade("A", LocalDate.now(), "v1"))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    private PerfMetricRef buildRef(String metricCode, String refMetricCode) {
        PerfMetricRef ref = new PerfMetricRef();
        ref.setMetricCode(metricCode);
        ref.setRefMetricCode(refMetricCode);
        return ref;
    }
}
