package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

/**
 * 任务签收请求 DTO
 * <p>
 * taskId 由路径参数传入，请求体为空或空 JSON。
 * </p>
 */
@Data
public class ClaimReqDTO {
    // taskId 来自路径参数，请求体无额外字段
}
