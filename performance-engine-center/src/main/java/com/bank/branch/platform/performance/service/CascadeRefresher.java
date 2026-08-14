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
        // 旧签名兼容：业绩分配日期 allocDate 不传 → null（calcMetric 绑定阶段兜底为 dataDate）
        return refreshCascade(rootMetricCode, dataDate, version, null);
    }

    /**
     * 级联刷新（新增业绩分配日期 :allocDate 入参）.
     *
     * <p>同一 allocDate 透传到级联链上每个下游指标的 {@link MetricCalcService#calcMetric}，
     * 保证整条级联使用一致的业绩分配日期；null 时由 calcMetric 绑定阶段兜底为 dataDate。
     *
     * @param allocDate 业绩分配日期（可为 null，绑定阶段兜底为 dataDate）
     * @see #refreshCascade(String, LocalDate, String)
     */
    public String refreshCascade(String rootMetricCode, LocalDate dataDate, String version, LocalDate allocDate) {
        // 无预建根任务行（同步链路）：根指标的 run_task 由 calcMetric 自建
        return refreshCascade(rootMetricCode, dataDate, version, allocDate, null);
    }

    /**
     * 级联刷新，支持<b>复用外部预建的根指标 run_task 行</b>（异步提交链路）.
     *
     * <p>异步提交时根任务行已在请求线程建好并把 taskId 返回给了前端，这里必须复用它，
     * 否则会为同一次执行产生两行 run_task（监控页计算次数虚高、前端轮询的 taskId 永远 PENDING）。
     * 下游指标仍各自新建子任务行，不受影响。
     *
     * @param rootPresetTaskId 根指标预建的 run_task 主键；null 时按原行为自建
     * @see #refreshCascade(String, LocalDate, String, LocalDate)
     */
    public String refreshCascade(String rootMetricCode, LocalDate dataDate, String version,
                                 LocalDate allocDate, String rootPresetTaskId) {
        CascadePlan plan = resolveExecutionPlan(rootMetricCode);

        // 执行前再次全量预检，覆盖绕过 Facade 直接调用级联服务的入口。
        preflight(plan, dataDate);

        // 按拓扑顺序调用 calcMetric；记录根的 taskId 返回
        Set<String> fired = new HashSet<>();
        String rootTaskId = null;
        for (String code : plan.executionOrder()) {
            if (fired.add(code)) {
                log.info("[CascadeRefresher] 刷新指标 {} （date={}, version={}, allocDate={}）",
                        code, dataDate, version, allocDate);
                // triggerType 沿用原 3 参 calcMetric 的默认 MANUAL；allocDate 透传到整条级联
                // 根指标复用预建行（仅异步链路会传），其余一律走原 calcMetric 自建任务行
                boolean reusePreset = rootPresetTaskId != null && rootMetricCode.equals(code);
                String taskId = reusePreset
                        ? metricCalcService.calcMetricWithStats(
                                code, dataDate, version, "MANUAL", allocDate, rootPresetTaskId).runTaskId()
                        : metricCalcService.calcMetric(code, dataDate, version, "MANUAL", allocDate);
                if (rootMetricCode.equals(code) && rootTaskId == null) {
                    rootTaskId = taskId;
                }
            }
        }
        return rootTaskId;
    }

    /**
     * 级联入口的无副作用预检：先解析完整受影响子图，再逐个校验定义与日期，期间不创建任务。
     * Facade 在异步创建根 PENDING 前调用；refreshCascade 执行层也会再次兜底。
     */
    public void preflightCascade(String rootMetricCode, LocalDate dataDate) {
        preflight(resolveExecutionPlan(rootMetricCode), dataDate);
    }

    private void preflight(CascadePlan plan, LocalDate dataDate) {
        for (String code : plan.executionOrder()) {
            metricCalcService.preflightMetric(code, dataDate);
        }
    }

    /** 收集完整下游集合并按先底层后上层生成确定性的执行顺序。 */
    private CascadePlan resolveExecutionPlan(String rootMetricCode) {
        int maxDepth = Math.max(1, perfEngineProperties.getCascadeMaxDepth());

        Set<String> affectedNodes = new LinkedHashSet<>();
        List<PerfMetricRef> allEdges = new ArrayList<>();
        affectedNodes.add(rootMetricCode);

        Deque<String[]> queue = new ArrayDeque<>(); // [metricCode, depth]
        queue.offer(new String[]{rootMetricCode, "0"});
        while (!queue.isEmpty()) {
            String[] node = queue.poll();
            String cur = node[0];
            int depth = Integer.parseInt(node[1]);
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

        List<String> topoOrder = allEdges.isEmpty()
                ? new ArrayList<>(List.of(rootMetricCode))
                : dependencyGraphBuilder.topologicalOrder(allEdges);
        if (!topoOrder.contains(rootMetricCode)) {
            topoOrder.add(0, rootMetricCode);
        }
        topoOrder.retainAll(affectedNodes);

        // 防御拓扑排序遗漏：将受影响节点追加到末尾，保证“全量预检”与实际执行集合一致。
        LinkedHashSet<String> executionOrder = new LinkedHashSet<>(topoOrder);
        executionOrder.addAll(affectedNodes);
        return new CascadePlan(new ArrayList<>(executionOrder));
    }

    private record CascadePlan(List<String> executionOrder) {
    }
}
