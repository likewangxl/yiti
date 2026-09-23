package com.bank.branch.platform.report.dto.req.presentation;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 中间收入占营业收入的可选展示配置；字段为空时表示数据待接入。 */
@Data
public class ScreenDisplayIncomeRatioDTO {

    @Size(max = 100)
    private String numeratorField;

    @Size(max = 100)
    private String denominatorField;

    private ScreenDisplayUnit unit;
}
