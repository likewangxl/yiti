package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 大屏可绑定的指标目录项。
 *
 * <p>来自 PERF_METRIC_DEF 的只读快照。outputColumn 是宽表查询别名（指标名称），
 * 绑定 fields 必须使用它，而不是 val_N 或 semantic。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScreenMetricCatalogItemDTO {

    /** 指标编码 */
    private String metricCode;

    /** 指标名称（库中原文） */
    private String metricName;

    /** EMP / ORG / CUST */
    private String baseDim;

    /** 宽表槽位；未分配时不可绑定 */
    private Integer valSlot;

    /** 库中原始单位文本；无法识别时保留，供人工选择 */
    private String unitRaw;

    /** 绑定白名单单位；无法识别时为 null，调用方不得猜测 */
    private String unit;

    /** COLUMN / DESCRIPTION / CONFIRMED_CODE / NONE */
    private String unitSource;

    /** 指标分类。列为空时取口径 JSON 的一级分类 */
    private String metricCategory;

    /** 机构汇总规则 SUM/AVG/MAX/MIN/COUNT，仅作聚合默认值 */
    private String summaryRule;

    /** 指标层级 */
    private Integer metricLevel;

    /** 计算频率 */
    private String calcFreq;

    /** 对应宽表；维度未知时为 null */
    private String tableName;

    /** 宽表主体列 */
    private String subjectColumn;

    /** 查询输出列名，已去掉会破坏反引号别名的字符 */
    private String outputColumn;

    /** 能否直接生成宽表数据源 */
    private boolean bindable;

    /** 不可绑定原因；可绑定时为 null */
    private String unavailableReason;
}
