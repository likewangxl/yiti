package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricRef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DependencyGraphBuilder} 单元测试（Task P2.5 Red）.
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>简单链式依赖 A -> B -> C 的拓扑排序</li>
 *   <li>菱形依赖 A -> B/C, B/C -> D 的合法排序</li>
 *   <li>直接环 A -> B -> A 必须抛 METRIC_CALC_LOGIC_INVALID（PERF-42201）</li>
 *   <li>自环 A -> A 必须抛 METRIC_CALC_LOGIC_INVALID</li>
 *   <li>空图 / 单节点</li>
 *   <li>下游检索：{@code downstreamOf(root)} 必须返回 BFS 可达的全部下游（不含 root）</li>
 * </ul>
 */
class DependencyGraphBuilderTest {

    private final DependencyGraphBuilder builder = new DependencyGraphBuilder();

    @Test
    @DisplayName("空图：topologicalOrder 返回空列表")
    void emptyGraph_returnsEmpty() {
        List<String> order = builder.topologicalOrder(Collections.emptyList());
        assertThat(order).isEmpty();
    }

    @Test
    @DisplayName("链式 A -> B -> C：拓扑顺序 C, B, A（先叶子后根）")
    void chain_topologicalOrder() {
        // A ref B, B ref C ⇒ 计算顺序：先 C（无依赖），再 B，最后 A
        List<PerfMetricRef> refs = Arrays.asList(
                buildRef("A", "B"),
                buildRef("B", "C"));

        List<String> order = builder.topologicalOrder(refs);

        // 合法拓扑序列：C 必在 B 之前，B 必在 A 之前
        assertThat(order).hasSize(3);
        assertThat(order.indexOf("C")).isLessThan(order.indexOf("B"));
        assertThat(order.indexOf("B")).isLessThan(order.indexOf("A"));
    }

    @Test
    @DisplayName("菱形 A ->{B,C}, {B,C} -> D：四节点合法拓扑")
    void diamond_topologicalOrder() {
        // A ref B, A ref C, B ref D, C ref D
        List<PerfMetricRef> refs = Arrays.asList(
                buildRef("A", "B"),
                buildRef("A", "C"),
                buildRef("B", "D"),
                buildRef("C", "D"));

        List<String> order = builder.topologicalOrder(refs);

        assertThat(order).containsExactlyInAnyOrder("A", "B", "C", "D");
        // D 必是第一个（无任何依赖）；A 必是最后一个
        assertThat(order.indexOf("D")).isLessThan(order.indexOf("B"));
        assertThat(order.indexOf("D")).isLessThan(order.indexOf("C"));
        assertThat(order.indexOf("B")).isLessThan(order.indexOf("A"));
        assertThat(order.indexOf("C")).isLessThan(order.indexOf("A"));
    }

    @Test
    @DisplayName("直接环 A <-> B：抛 METRIC_CALC_LOGIC_INVALID")
    void directCycle_throws() {
        List<PerfMetricRef> refs = Arrays.asList(
                buildRef("A", "B"),
                buildRef("B", "A"));

        assertThatThrownBy(() -> builder.topologicalOrder(refs))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @Test
    @DisplayName("间接环 A -> B -> C -> A：抛 METRIC_CALC_LOGIC_INVALID")
    void indirectCycle_throws() {
        List<PerfMetricRef> refs = Arrays.asList(
                buildRef("A", "B"),
                buildRef("B", "C"),
                buildRef("C", "A"));

        assertThatThrownBy(() -> builder.topologicalOrder(refs))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @Test
    @DisplayName("自环 A -> A：抛 METRIC_CALC_LOGIC_INVALID")
    void selfLoop_throws() {
        List<PerfMetricRef> refs = Collections.singletonList(buildRef("A", "A"));

        assertThatThrownBy(() -> builder.topologicalOrder(refs))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @Test
    @DisplayName("downstreamOf 根指标：返回 BFS 可达下游集合（不含根自身）")
    void downstreamOf_returnsReachableExcludingRoot() {
        // 依赖边：A -> B, A -> C, B -> D ⇒ downstream of A 是 {B, C, D}
        List<PerfMetricRef> refs = Arrays.asList(
                buildRef("A", "B"),
                buildRef("A", "C"),
                buildRef("B", "D"));

        List<String> downstream = builder.downstreamOf(refs, "A");

        assertThat(downstream).containsExactlyInAnyOrder("B", "C", "D");
        assertThat(downstream).doesNotContain("A");
    }

    @Test
    @DisplayName("downstreamOf 不存在节点：返回空列表")
    void downstreamOf_unknownNode_returnsEmpty() {
        List<PerfMetricRef> refs = Collections.singletonList(buildRef("A", "B"));
        assertThat(builder.downstreamOf(refs, "ZZZ")).isEmpty();
    }

    @Test
    @DisplayName("null/空引用列表：downstreamOf 返回空")
    void downstreamOf_empty_returnsEmpty() {
        assertThat(builder.downstreamOf(null, "X")).isEmpty();
        assertThat(builder.downstreamOf(new ArrayList<>(), "X")).isEmpty();
    }

    private PerfMetricRef buildRef(String metricCode, String refMetricCode) {
        PerfMetricRef ref = new PerfMetricRef();
        ref.setMetricCode(metricCode);
        ref.setRefMetricCode(refMetricCode);
        return ref;
    }
}
