package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 指标引用环路/层级校验纯函数服务.
 *
 * <p>不依赖数据库，调用方负责提供图快照与层级映射，便于单元测试完整覆盖边界。
 */
@Service
public class MetricCycleDetectService {

    /**
     * 校验引用层级是否合法.
     *
     * @param thisLevel       当前指标层级
     * @param refMetricLevels 被引用指标层级映射
     * @throws PerfException 当层级非法时抛出业务异常
     */
    public void checkLevelConstraint(Integer thisLevel, Map<String, Integer> refMetricLevels) {
        if (thisLevel == null || thisLevel < 1 || thisLevel > 3) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, "指标层级仅支持 1/2/3");
        }
        if (refMetricLevels == null || refMetricLevels.isEmpty()) {
            return;
        }
        if (thisLevel == 1) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, "L1 指标不得引用其他指标");
        }

        int expectedRefLevel = thisLevel - 1;
        for (Map.Entry<String, Integer> entry : refMetricLevels.entrySet()) {
            Integer actualLevel = entry.getValue();
            if (actualLevel == null || actualLevel != expectedRefLevel) {
                throw new PerfException(
                        PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "L" + thisLevel + " 只能引用 L" + expectedRefLevel
                                + "，refMetric=" + entry.getKey() + " 实际层级=" + actualLevel);
            }
        }
    }

    /**
     * 校验新增边后是否形成环路.
     *
     * @param existingGraph 当前图快照
     * @param newNode       待新增的上层指标编码
     * @param newRefs       待新增的引用列表
     * @throws PerfException 当形成环路时抛出业务异常
     */
    public void checkNoCycle(Map<String, List<String>> existingGraph,
                             String newNode,
                             List<String> newRefs) {
        if (newRefs == null || newRefs.isEmpty()) {
            return;
        }
        if (newRefs.contains(newNode)) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, "指标 " + newNode + " 自引用");
        }

        Map<String, List<String>> graph = new HashMap<>();
        if (existingGraph != null) {
            existingGraph.forEach((key, value) ->
                    graph.put(key, value == null ? Collections.emptyList() : new ArrayList<>(value)));
        }
        graph.put(newNode, new ArrayList<>(newRefs));

        for (String ref : newRefs) {
            if (reachStart(graph, ref, newNode, new HashSet<>(Set.of(newNode)))) {
                throw new PerfException(
                        PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "指标引用图形成环路，起点=" + newNode);
            }
        }
    }

    private boolean reachStart(Map<String, List<String>> graph,
                               String current,
                               String start,
                               Set<String> path) {
        if (start.equals(current)) {
            return true;
        }
        if (!path.add(current)) {
            return false;
        }
        for (String next : graph.getOrDefault(current, Collections.emptyList())) {
            if (reachStart(graph, next, start, path)) {
                return true;
            }
        }
        path.remove(current);
        return false;
    }
}
