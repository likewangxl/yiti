package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 支部书记或组织审核员驳回任务的请求 DTO。 */
@Data
@Schema(description = "任务驳回请求")
public class ReTaskRejectReqDTO {

    /** 驳回意见，业务上必须填写。 */
    @NotBlank(message = "驳回意见不能为空")
    @Schema(description = "驳回意见", requiredMode = Schema.RequiredMode.REQUIRED)
    private String feedback;
}
