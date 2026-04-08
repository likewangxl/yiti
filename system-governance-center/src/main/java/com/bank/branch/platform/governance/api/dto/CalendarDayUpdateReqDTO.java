package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 日历天更新请求DTO（B.2设置工作/休息日状态）
 */
@Data
public class CalendarDayUpdateReqDTO {
    /** 是否工作日（true=工作日，false=休息日） */
    @NotNull(message = "isWorkday 不能为空")
    private Boolean isWorkday;
    /** 备注 */
    private String remark;
}
