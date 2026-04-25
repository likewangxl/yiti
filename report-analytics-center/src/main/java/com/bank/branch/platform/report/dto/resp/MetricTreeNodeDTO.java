package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 指标树节点 DTO（M1.1.1 新增）.
 *
 * <p>用于 {@link QueryDimensionRespDTO#getMetrics()} 中表示两级树：
 * <ul>
 *   <li>分组节点：{@code groupCode} + {@code groupName} + {@code children}（指标列表）</li>
 *   <li>叶子节点：{@code metricCode} + {@code metricName} + {@code unit} + {@code description}</li>
 * </ul>
 *
 * <p>约定：分组节点时 metricCode/metricName 为 null；叶子节点时 children 为 null。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MetricTreeNodeDTO {

    /** 分组编码（如 DEPOSIT / LOAN / DEFAULT） */
    private String groupCode;

    /** 分组中文名（通过 METRIC_CATEGORY 字典翻译） */
    private String groupName;

    /** 叶子节点的指标编码 */
    private String metricCode;

    /** 叶子节点的指标中文名 */
    private String metricName;

    /** 叶子节点的单位（如 元 / % / 个） */
    private String unit;

    /** 叶子节点的指标说明 */
    private String description;

    /** 分组节点的子节点列表（叶子节点） */
    private List<MetricTreeNodeDTO> children;
}
