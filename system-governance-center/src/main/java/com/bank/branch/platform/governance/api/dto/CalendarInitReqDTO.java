package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 日历初始化请求DTO（B.3）
 */
@Data
public class CalendarInitReqDTO {

    /** 年份 */
    @NotNull(message = "年份不能为空")
    @Min(value = 2026, message = "年份不能早于2026")
    @Max(value = 2100, message = "年份不能超过2100")
    private Integer year;
}