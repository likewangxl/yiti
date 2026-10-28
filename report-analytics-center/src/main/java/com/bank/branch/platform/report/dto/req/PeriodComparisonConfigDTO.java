package com.bank.branch.platform.report.dto.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.Valid;
import lombok.Data;

/**
 * 一个画布展示键的期间比较配置。
 *
 * <p>配置键由画布展示目录约束，值固定包含较昨日、较上月和较上年三个槽位。
 * 槽位字段保留字符串以兼容前端对 {@code UNSET} 槽位使用空字符串；保存校验器
 * 负责枚举、长度和跨字段约束。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PeriodComparisonConfigDTO {

    @Valid
    @JsonSetter(nulls = Nulls.FAIL)
    private PeriodComparisonSlotDTO day;

    @Valid
    @JsonSetter(nulls = Nulls.FAIL)
    private PeriodComparisonSlotDTO month;

    @Valid
    @JsonSetter(nulls = Nulls.FAIL)
    private PeriodComparisonSlotDTO year;
}
