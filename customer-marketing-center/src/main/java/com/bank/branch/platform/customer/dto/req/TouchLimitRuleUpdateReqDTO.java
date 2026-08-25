package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 修改客户标签触达周期规则请求。 */
@Data
public class TouchLimitRuleUpdateReqDTO {

    /** 周期单位。 */
    @NotBlank(message = "cycleUnit 不能为空")
    @Pattern(regexp = "DAY|WEEK|MONTH|QUARTER|YEAR", message = "cycleUnit 不合法")
    private String cycleUnit;

    /** 周期内最多触达次数。 */
    @NotNull(message = "maxTouches 不能为空")
    @Min(value = 1, message = "maxTouches 最小为 1")
    @Max(value = 9999, message = "maxTouches 最大为 9999")
    private Integer maxTouches;
}
