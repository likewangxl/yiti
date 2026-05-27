package com.bank.branch.platform.performance.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指标分类下拉项 DTO（V1.10）.
 *
 * <p>对应 PERF_METRIC_DEF.metric_category 列的 DISTINCT 聚合结果。
 * V1.9 列存储的就是中文名（规模类/效益类/质量类/合规类等），sys_dict 未引入翻译表，
 * 因此 {@code value} 与 {@code label} 在当前版本相同；保留双字段以便未来引入字典
 * 翻译时仅扩展 {@code label}，前端契约稳定。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricCategoryDTO {

    /** 分类值（与数据库 metric_category 列一致）. */
    private String value;

    /** 中文展示名称（V1.10 暂等于 value）. */
    private String label;
}
