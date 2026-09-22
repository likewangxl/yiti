package com.bank.branch.platform.report.dto.req.presentation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 数值展示格式；不改变来源原始单位。 */
@Data
public class ScreenDisplayFormatDTO {
    private ScreenDisplayUnit displayUnit = ScreenDisplayUnit.AUTO;
    @Min(0)
    @Max(8)
    private Integer decimals;
    private Boolean thousandsSeparator;
    private ScreenNegativeStyle negativeStyle;
    @Size(max = 50)
    private String emptyText;
}
