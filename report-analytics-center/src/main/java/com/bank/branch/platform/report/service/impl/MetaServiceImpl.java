package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.performance.api.MetricApi;
import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import com.bank.branch.platform.report.dto.resp.MetricTreeNodeDTO;
import com.bank.branch.platform.report.dto.resp.QueryDimensionRespDTO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.service.MetaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MetaService 实现（Task M1.1.1，Green）.
 *
 * <p>实现 A.1 GET /api/reports/query-dimensions，按入参 dim 读取
 * {@link MetricApi#listMetrics(String, Integer)} 的 PUBLISHED 指标定义，
 * 通过 {@code metricLevel} 做分组 key（V1.0 MetricDefDTO 尚无 category 字段，
 * 以 metricLevel 作为分组维度，未来若 DTO 扩展 category 再切换）。
 *
 * <p>分组 key 规则：
 * <ul>
 *   <li>有 metricLevel → 分组编码形如 {@code LEVEL_1} / {@code LEVEL_2} / {@code LEVEL_3}</li>
 *   <li>无 metricLevel → 落入 {@code DEFAULT} 分组</li>
 * </ul>
 *
 * <p>维度名/分组名通过 {@link DictApi#getDictLabel} 翻译（字典类型 REPORT_DIM / METRIC_CATEGORY）.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaServiceImpl implements MetaService {

    private static final Set<String> VALID_DIMS = Set.of("EMP", "ORG", "CUST");

    private static final String GROUP_CODE_DEFAULT = "DEFAULT";

    private static final String DICT_TYPE_REPORT_DIM = "REPORT_DIM";

    private static final String DICT_TYPE_METRIC_CATEGORY = "METRIC_CATEGORY";

    private final MetricApi metricApi;

    private final DictApi dictApi;

    @Override
    public QueryDimensionRespDTO getQueryDimensions(String dim) {
        if (dim == null || !VALID_DIMS.contains(dim)) {
            throw new RptException(RptErrorCode.METRIC_DIM_MISMATCH);
        }

        List<MetricDefDTO> metrics = metricApi.listMetrics(dim, null);
        // metricLevel 为 null 时归入 DEFAULT，其他按 LEVEL_{n} 分组
        Map<String, List<MetricTreeNodeDTO>> grouped = metrics.stream()
                .collect(Collectors.groupingBy(
                        this::resolveGroupCode,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toLeafNode, Collectors.toList())));

        List<MetricTreeNodeDTO> tree = grouped.entrySet().stream()
                .map(e -> MetricTreeNodeDTO.builder()
                        .groupCode(e.getKey())
                        .groupName(dictApi.getDictLabel(DICT_TYPE_METRIC_CATEGORY, e.getKey()))
                        .children(e.getValue())
                        .build())
                .sorted(Comparator.comparing(MetricTreeNodeDTO::getGroupCode))
                .collect(Collectors.toList());

        return QueryDimensionRespDTO.builder()
                .dim(dim)
                .dimName(dictApi.getDictLabel(DICT_TYPE_REPORT_DIM, dim))
                .metrics(tree)
                .build();
    }

    /**
     * 将 MetricDefDTO 转成叶子节点。
     * V1.0 MetricDefDTO 尚未暴露 unit 字段，先置 null，description 透传。
     */
    private MetricTreeNodeDTO toLeafNode(MetricDefDTO m) {
        return MetricTreeNodeDTO.builder()
                .metricCode(m.getMetricCode())
                .metricName(m.getMetricName())
                .unit(null)
                .description(m.getDescription())
                .build();
    }

    /** metricLevel 映射 group code（V1.0 MetricDefDTO 无 category 字段时的降级策略）. */
    private String resolveGroupCode(MetricDefDTO m) {
        Integer level = m.getMetricLevel();
        if (level == null) {
            return GROUP_CODE_DEFAULT;
        }
        return "LEVEL_" + level;
    }
}
