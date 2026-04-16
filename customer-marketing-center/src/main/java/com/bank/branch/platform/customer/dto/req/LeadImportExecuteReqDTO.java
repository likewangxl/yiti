package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 执行导入请求 DTO。
 */
@Data
public class LeadImportExecuteReqDTO {

    /** 批次ID（必填，来自预览接口返回） */
    @NotBlank(message = "批次ID不能为空")
    private String batchId;
}
