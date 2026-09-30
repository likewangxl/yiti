package com.bank.branch.platform.report.dto.req.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 信息卡显式比较数据来源配置。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScreenDisplayComparisonDTO {

    /** 显式启用或关闭比较；缺省/空值都不能绕过服务端校验。 */
    @NotNull
    @JsonSetter(nulls = Nulls.FAIL)
    private Boolean enabled;

    /** 当前画布树中受控 LINE_TREND ChartWidget 的正整数 blockId。 */
    @Positive
    @JsonSetter(nulls = Nulls.FAIL)
    private Long historyBlockId;

    /** 历史区块中的一个或多个金额字段；只有金额总额键允许多字段求和。 */
    @Size(min = 1, max = 8)
    @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
    private List<@NotBlank @Size(max = 100) String> valueFields;

    /** 历史区块日期列。 */
    @Size(min = 1, max = 100)
    @JsonSetter(nulls = Nulls.FAIL)
    private String dateField;

    /** 历史区块原始单位；AUTO 不允许成为显式比较来源单位。 */
    @JsonSetter(nulls = Nulls.FAIL)
    private ScreenDisplayUnit sourceUnit;
}
