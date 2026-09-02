package com.bank.branch.platform.customer.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 手动发起首次触达请求。 */
@Data
@Schema(description = "手动发起首次触达请求")
public class StartTouchReqDTO {

    /**
     * 仅保留请求反序列化兼容性；服务端不会采信客户端截止时间。
     * 计划完成时间由发起时间加 {@code CUSTOMER_TOUCH_TASK_SLA_DAYS} 计算。
     */
    @Deprecated
    @Schema(hidden = true,
            description = "兼容旧客户端字段，服务端忽略；计划完成时间由发起时间加后台触达时限配置计算")
    private String planFinishTime;
}
