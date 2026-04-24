package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricRef;
import com.bank.branch.platform.performance.mapper.PerfMetricRefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 指标引用关系服务.
 */
@Service
@RequiredArgsConstructor
public class MetricRefService {

    private final PerfMetricRefMapper mapper;

    /**
     * 重建某个指标的引用关系.
     *
     * @param metricCode     上层指标编码
     * @param refMetricCodes 新的被引用指标编码列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void setRefs(String metricCode, List<String> refMetricCodes) {
        mapper.deleteByMetricCode(metricCode);
        if (refMetricCodes == null || refMetricCodes.isEmpty()) {
            return;
        }
        List<PerfMetricRef> refs = new ArrayList<>(refMetricCodes.size());
        LocalDateTime now = LocalDateTime.now();
        for (String refMetricCode : refMetricCodes) {
            PerfMetricRef ref = new PerfMetricRef();
            ref.setId(UUID.randomUUID().toString().replace("-", ""));
            ref.setMetricCode(metricCode);
            ref.setRefMetricCode(refMetricCode);
            ref.setCreatedTime(now);
            refs.add(ref);
        }
        mapper.insertBatch(refs);
    }

    /**
     * 查询某指标引用了哪些指标.
     *
     * @param metricCode 上层指标编码
     * @return 引用关系列表
     */
    @Transactional(readOnly = true)
    public List<PerfMetricRef> listRefsOf(String metricCode) {
        return mapper.selectByMetricCode(metricCode);
    }

    /**
     * V1.3 R4.1：返回 metric 所引用的 metricCode 列表（字符串视图，Controller 专用）.
     */
    @Transactional(readOnly = true)
    public List<String> listRefCodesOf(String metricCode) {
        return listRefsOf(metricCode).stream().map(PerfMetricRef::getRefMetricCode).toList();
    }

    /**
     * 查询哪些指标引用了指定指标.
     *
     * @param refMetricCode 被引用指标编码
     * @return 引用关系列表
     */
    @Transactional(readOnly = true)
    public List<PerfMetricRef> listWhoRef(String refMetricCode) {
        return mapper.selectByRefMetricCode(refMetricCode);
    }

    /**
     * V1.3 R4.1：返回"引用本指标"的上游 metricCode 列表（字符串视图，Controller 专用）.
     */
    @Transactional(readOnly = true)
    public List<String> listCodesWhoRef(String refMetricCode) {
        return listWhoRef(refMetricCode).stream().map(PerfMetricRef::getMetricCode).toList();
    }

    /**
     * 加载完整引用图.
     *
     * @return metricCode -> refMetricCodes
     */
    @Transactional(readOnly = true)
    public Map<String, List<String>> loadFullGraph() {
        List<PerfMetricRef> refs = mapper.selectAll();
        if (refs == null || refs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (PerfMetricRef ref : refs) {
            graph.computeIfAbsent(ref.getMetricCode(), key -> new ArrayList<>())
                    .add(ref.getRefMetricCode());
        }
        return graph;
    }
}
