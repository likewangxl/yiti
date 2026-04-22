package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricRef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 指标依赖图构建与拓扑排序（V1.1 Task P2.5）.
 *
 * <p>语义约定：
 * <ul>
 *   <li>{@code PerfMetricRef.metricCode} 是<strong>上层（下游）</strong>指标编码。</li>
 *   <li>{@code PerfMetricRef.refMetricCode} 是<strong>下层（上游）</strong>指标编码。</li>
 *   <li>一条 ref {@code (A -> B)} 含义：A 的计算依赖 B 的结果，即计算顺序上 B 必须早于 A。</li>
 * </ul>
 *
 * <p>实现：
 * <ul>
 *   <li>{@link #topologicalOrder(List)}：DFS 三色标记（白/灰/黑）。遇灰节点 → 环路 → 抛
 *       {@link PerfErrorCode#METRIC_CALC_LOGIC_INVALID}（PERF-42201）。排序结果从"被依赖者（叶）"
 *       到"依赖者（根）"，供 {@link CascadeRefresher} 按顺序触发计算。</li>
 *   <li>{@link #downstreamOf(List, String)}：以 refMetricCode 反向索引，BFS 得到根指标的下游闭包
 *       （即所有引用了 root 的指标链）。用于"根指标变更触发所有下游重算"场景。</li>
 * </ul>
 *
 * <p>纯算法组件，无状态，无 Spring Bean 依赖；单元测试可直接 {@code new DependencyGraphBuilder()}。
 */
@Service
public class DependencyGraphBuilder {

    /** 白色：未访问. */
    private static final int WHITE = 0;
    /** 灰色：访问中（栈上）. */
    private static final int GREY = 1;
    /** 黑色：已完成. */
    private static final int BLACK = 2;

    /**
     * 对给定依赖边列表做拓扑排序.
     *
     * <p>返回的列表中，任意一条边 {@code A -> B} 都保证 B 出现在 A 之前（即从叶子到根）。
     *
     * @param refs 依赖边列表（可为 null/空）
     * @return 拓扑有序的指标编码列表（与输入所有节点集合等同）；空输入返回空列表
     * @throws PerfException 若图中存在环路（含自环）
     */
    public List<String> topologicalOrder(List<PerfMetricRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return new ArrayList<>();
        }
        // 邻接表：metricCode -> List(refMetricCode)。即 "A 依赖 B" 方向
        Map<String, List<String>> adj = new HashMap<>();
        Set<String> allNodes = new LinkedHashSet<>();
        for (PerfMetricRef ref : refs) {
            String from = ref.getMetricCode();
            String to = ref.getRefMetricCode();
            allNodes.add(from);
            allNodes.add(to);
            adj.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
            // 自环直接抛（DFS 也能检测但此处提前失败更清晰）
            if (from.equals(to)) {
                throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "指标 " + from + " 自引用");
            }
        }

        Map<String, Integer> color = new HashMap<>();
        List<String> topo = new ArrayList<>();
        for (String node : allNodes) {
            if (color.getOrDefault(node, WHITE) == WHITE) {
                dfs(node, adj, color, topo);
            }
        }
        // 此处 topo 已是"完成顺序"，即先叶子后根，正是期望顺序（计算时按此顺序触发）
        return topo;
    }

    /**
     * 查询指定根指标的全部下游指标（BFS 遍历，沿依赖边正向）.
     *
     * <p>"下游"定义（与 DependencyGraphBuilder 输入边方向一致）：从 root 出发，沿
     * {@code metricCode -> refMetricCode} 正向可达的节点集合，即 root 直接/间接依赖的全部指标。
     * 结果不含 root 自身。
     *
     * <p>注：{@link CascadeRefresher} 的"级联下游"语义则是反向（即 root 变更后被波及的上层指标），
     * 它独立做反向 BFS，本方法不包揽该职责。
     *
     * @param refs 全量依赖边（可为 null/空）
     * @param root 根指标编码
     * @return 下游指标编码列表；不含 root；未命中返回空
     */
    public List<String> downstreamOf(List<PerfMetricRef> refs, String root) {
        if (refs == null || refs.isEmpty() || root == null) {
            return new ArrayList<>();
        }
        // 正向索引：metricCode -> List(refMetricCode 被依赖者)
        Map<String, List<String>> adj = new HashMap<>();
        for (PerfMetricRef ref : refs) {
            adj.computeIfAbsent(ref.getMetricCode(), k -> new ArrayList<>())
                    .add(ref.getRefMetricCode());
        }

        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.offer(root);
        visited.add(root);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String next : adj.getOrDefault(cur, Collections.emptyList())) {
                if (visited.add(next)) {
                    result.add(next);
                    queue.offer(next);
                }
            }
        }
        return result;
    }

    private void dfs(String node, Map<String, List<String>> adj,
                     Map<String, Integer> color, List<String> topo) {
        color.put(node, GREY);
        for (String next : adj.getOrDefault(node, Collections.emptyList())) {
            int c = color.getOrDefault(next, WHITE);
            if (c == GREY) {
                // 回边 = 环
                throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "指标依赖图形成环路，回边 " + node + " -> " + next);
            }
            if (c == WHITE) {
                dfs(next, adj, color, topo);
            }
        }
        color.put(node, BLACK);
        topo.add(node);
    }
}
