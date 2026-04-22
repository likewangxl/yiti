package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.entity.PerfMetricRef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 级联刷新服务（V1.1 Task P2.5）.
 *
 * <p>职责：给定根指标变更（例如 L1 底层数据重算），遍历其全部下游依赖链，按
 * 拓扑序依次调用 {@link MetricCalcService#calcMetric(String, LocalDate, String)}
 * 重算。
 *
 * <p>算法步骤：
 * <ol>
 *   <li>从 {@code root} 开始，BFS 遍历反向引用（refMetricCode -> metricCode 的反向边），
 *       得到"需要重算的指标集合"。</li>
 *   <li>BFS 过程中记录深度，超过 {@link PerfEngineProperties#getCascadeMaxDepth()}
 *       即刻抛 {@link PerfErrorCode#METRIC_CALC_LOGIC_INVALID}（PERF-42201）。</li>
 *   <li>对该集合内部的依赖子图做拓扑排序（{@link DependencyGraphBuilder#topologicalOrder}），
 *       按拓扑顺序（先叶子后根）逐个触发 {@link MetricCalcService#calcMetric}。</li>
 *   <li>环路场景在步骤 3 自然被 topo 检测捕获。</li>
 * </ol>
 *
 * <p>V1.1 P2 简化：<strong>不</strong>在 run_task 建立父子关系 {@code parent_id}；
 * 各层 metricCalc 各自生成独立 run_task。V1.2 会扩展一个"级联任务头"记录。
 */
@Slf4j
@Service
public class CascadeRefresher {

    private final MetricRefService metricRefService;
    private final MetricCalcService metricCalcService;
    private final DependencyGraphBuilder dependencyGraphBuilder;
    private final PerfEngineProperties perfEngineProperties;

    @Autowired
    public CascadeRefresher(MetricRefService metricRefService,
                            MetricCalcService metricCalcService,
                            DependencyGraphBuilder dependencyGraphBuilder,
                            PerfEngineProperties perfEngineProperties) {
        this.metricRefService = metricRefService;
        this.metricCalcService = metricCalcService;
        this.dependencyGraphBuilder = dependencyGraphBuilder;
        this.perfEngineProperties = perfEngineProperties;
    }

    /**
     * 级联刷新：从根指标开始遍历所有下游，按拓扑序调用 {@link MetricCalcService#calcMetric}.
     *
     * <p>V1.1 Task P3.2 起返回根指标计算的 run_task ID（原 {@code void} 签名）。
     * 调用方通过根 taskId 轮询 {@code perf_run_task} 获取状态；下游每层计算
     * 生成独立子任务（V1.2 会建立 parent_id 关联，当前 P3 仅记录根）。
     *
     * @param rootMetricCode 根指标编码
     * @param dataDate       数据日期
     * @param version        数据版本
     * @return 根指标计算的 run_task ID；上游下游子任务的 taskId 通过查询 run_task 表获得
     * @throws PerfException 深度越界 / 环路 / 计算失败透传
     */
    public String refreshCascade(String rootMetricCode, LocalDate dataDate, String version) {
        int maxDepth = Math.max(1, perfEngineProperties.getCascadeMaxDepth());

        // 1. BFS 收集下游集合（含根）+ 深度检查
        Set<String> affectedNodes = new LinkedHashSet<>();
        List<PerfMetricRef> allEdges = new ArrayList<>();
        affectedNodes.add(rootMetricCode);

        Deque<String[]> queue = new ArrayDeque<>(); // [metricCode, depth]
        queue.offer(new String[]{rootMetricCode, "0"});

        while (!queue.isEmpty()) {
            String[] node = queue.poll();
            String cur = node[0];
            int depth = Integer.parseInt(node[1]);
            // 根（depth=0）不参与深度判定；depth>maxDepth 才算越界
            if (depth > maxDepth) {
                throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "级联深度超过 " + maxDepth + "：当前指标 " + cur + " 深度=" + depth);
            }
            List<PerfMetricRef> whoRef = metricRefService.listWhoRef(cur);
            if (whoRef == null || whoRef.isEmpty()) {
                continue;
            }
            for (PerfMetricRef edge : whoRef) {
                allEdges.add(edge);
                String parent = edge.getMetricCode();
                if (affectedNodes.add(parent)) {
                    queue.offer(new String[]{parent, String.valueOf(depth + 1)});
                }
            }
        }

        // 2. 对受影响子图做拓扑排序
        List<String> order;
        if (allEdges.isEmpty()) {
            // 只有根节点，没有下游
            order = new ArrayList<>(List.of(rootMetricCode));
        } else {
            order = dependencyGraphBuilder.topologicalOrder(allEdges);
            // 根可能不在 edges 中（无上游），手工加入
            if (!order.contains(rootMetricCode)) {
                order.add(0, rootMetricCode);
            }
            // 过滤：仅计算在 affectedNodes 内的节点（其他节点与根无关）
            order.retainAll(affectedNodes);
        }

        // 3. 按拓扑顺序调用 calcMetric；记录根的 taskId 返回
        Set<String> fired = new HashSet<>();
        String rootTaskId = null;
        for (String code : order) {
            if (fired.add(code)) {
                log.info("[CascadeRefresher] 刷新指标 {} （date={}, version={}）", code, dataDate, version);
                String taskId = metricCalcService.calcMetric(code, dataDate, version);
                if (rootMetricCode.equals(code) && rootTaskId == null) {
                    rootTaskId = taskId;
                }
            }
        }

        // 未被 topo 覆盖但确实需要刷新的节点（理论上不应出现；防御式）
        for (String code : affectedNodes) {
            if (fired.add(code)) {
                log.warn("[CascadeRefresher] 拓扑遗漏节点补算 {}", code);
                String taskId = metricCalcService.calcMetric(code, dataDate, version);
                if (rootMetricCode.equals(code) && rootTaskId == null) {
                    rootTaskId = taskId;
                }
            }
        }
        Collections.emptyList();
        return rootTaskId;
    }
}
