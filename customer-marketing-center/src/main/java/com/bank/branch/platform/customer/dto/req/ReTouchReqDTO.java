package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重新发起触达请求 DTO（POST /api/claims/{claimId}/re-touch）
 */
@Data
public class ReTouchReqDTO {

    @NotBlank(message = "重新触达原因不能为空")
    @Size(min = 10, max = 500, message = "原因长度须在 10~500 字符")
    private String reason;

    /** 计划完成时间（可选，缺省按默认 SLA 7 天计算）。格式 yyyy-MM-dd HH:mm:ss */
    private String planFinishTime;
}
