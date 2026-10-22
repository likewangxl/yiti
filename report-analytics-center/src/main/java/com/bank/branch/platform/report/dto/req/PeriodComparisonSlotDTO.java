package com.bank.branch.platform.report.dto.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Data;

/**
 * 单个较昨日/较上月/较上年期间比较槽位。
 *
 * <p>{@code mode=UNSET} 时其余字段必须是空字符串；{@code METRIC} 和
 * {@code OFFSET} 使用结果表指标列，单位和结果表由保存校验器严格约束。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PeriodComparisonSlotDTO {

    /** UNSET、METRIC 或 OFFSET。 */
    @JsonSetter(nulls = Nulls.FAIL)
    private String mode;

    /** 绩效指标目录编码。 */
    @JsonSetter(nulls = Nulls.FAIL)
    private String metricCode;

    /** 结果行中的指标输出列名。 */
    @JsonSetter(nulls = Nulls.FAIL)
    private String outputColumn;

    /** 原始金额单位；历史缺失值允许保留为空并在运行时失败关闭。 */
    @JsonSetter(nulls = Nulls.FAIL)
    private String unit;

    /** 指标所属结果表。 */
    @JsonSetter(nulls = Nulls.FAIL)
    private String tableName;
}
