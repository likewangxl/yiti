package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 支部书记或组织审核员通过任务的请求 DTO。 */
@Data
@Schema(description = "任务通过请求")
public class ReTaskApproveReqDTO {

    /** 可选审核意见。 */
    @Schema(description = "审核意见")
    private String feedback;
}
