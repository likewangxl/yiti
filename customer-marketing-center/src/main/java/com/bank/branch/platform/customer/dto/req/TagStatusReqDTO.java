package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 标签启禁用请求 DTO
 */
@Data
public class TagStatusReqDTO {

    /** 目标状态：ACTIVE-启用 / DISABLED-停用 */
    @NotBlank(message = "状态不能为空")
    private String status;
}
