package com.bank.branch.platform.customer.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
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

    /**
     * 仅保留请求反序列化兼容性；服务端不会采信客户端截止时间。
     * 计划完成时间由发起时间加 CUSTOMER_TOUCH_TASK_SLA_DAYS 计算。
     */
    @Deprecated
    @Schema(hidden = true,
            description = "兼容旧客户端字段，服务端忽略；计划完成时间由发起时间加后台触达时限配置计算")
    private String planFinishTime;
}
