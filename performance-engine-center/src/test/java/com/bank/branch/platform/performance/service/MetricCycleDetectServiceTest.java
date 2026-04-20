package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MetricCycleDetectService 纯函数单元测试.
 */
class MetricCycleDetectServiceTest {

    private final MetricCycleDetectService service = new MetricCycleDetectService();

    @Test
    @DisplayName("L2 引用 L1 合法")
    void checkLevelConstraint_L2RefL1_ok() {
        assertThatCode(() -> service.checkLevelConstraint(2, Map.of("A", 1, "B", 1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("L2 引用 L3 非法")
    void checkLevelConstraint_L2RefL3_throws40911() {
        assertThatThrownBy(() -> service.checkLevelConstraint(2, Map.of("A", 3)))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_LEVEL_INVALID));
    }

    @Test
    @DisplayName("L3 引用 L1 非法")
    void checkLevelConstraint_L3RefL1_throws40911() {
        assertThatThrownBy(() -> service.checkLevelConstraint(3, Map.of("A", 1)))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_LEVEL_INVALID));
    }

    @Test
    @DisplayName("L1 不能引用任何指标")
    void checkLevelConstraint_L1HasAnyRef_throws40911() {
        assertThatThrownBy(() -> service.checkLevelConstraint(1, Map.of("A", 1)))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_LEVEL_INVALID));
    }

    @Test
    @DisplayName("自引用会形成环路")
    void checkNoCycle_selfRef_throws40902() {
        assertThatThrownBy(() -> service.checkNoCycle(Map.of(), "A", List.of("A")))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_CYCLE_DETECTED));
    }

    @Test
    @DisplayName("A->B 且已有 B->A 形成二元环")
    void checkNoCycle_twoNodeLoop_A_B_A_throws40902() {
        Map<String, List<String>> graph = Map.of("B", List.of("A"));
        assertThatThrownBy(() -> service.checkNoCycle(graph, "A", List.of("B")))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_CYCLE_DETECTED));
    }

    @Test
    @DisplayName("A->B 且已有 B->C->A 形成三元环")
    void checkNoCycle_threeNodeLoop_A_B_C_A_throws40902() {
        Map<String, List<String>> graph = Map.of(
                "B", List.of("C"),
                "C", List.of("A"));
        assertThatThrownBy(() -> service.checkNoCycle(graph, "A", List.of("B")))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_CYCLE_DETECTED));
    }

    @Test
    @DisplayName("菱形引用但不回到起点时合法")
    void checkNoCycle_diamondNoCycle_ok() {
        Map<String, List<String>> graph = Map.of(
                "B", List.of("D"),
                "C", List.of("D"));
        assertThatCode(() -> service.checkNoCycle(graph, "A", List.of("B", "C")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("深链但不回到起点时合法")
    void checkNoCycle_deepChain_noLoop_ok() {
        Map<String, List<String>> graph = Map.of(
                "B", List.of("C"),
                "C", List.of("D"),
                "D", List.of("E"));
        assertThatCode(() -> service.checkNoCycle(graph, "A", List.of("B")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("空引用列表合法")
    void checkNoCycle_emptyRefs_ok() {
        assertThatCode(() -> service.checkNoCycle(Map.of(), "A", List.of()))
                .doesNotThrowAnyException();
    }
}
