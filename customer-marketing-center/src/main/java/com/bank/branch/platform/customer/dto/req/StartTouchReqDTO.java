package com.bank.branch.platform.customer.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 手动发起首次触达请求。 */
@Data
@Schema(description = "手动发起首次触达请求")
public class StartTouchReqDTO {

    @Schema(description = "计划完成时间，格式 yyyy-MM-dd HH:mm:ss；为空时默认七天后")
    private String planFinishTime;
}
