package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色创建请求DTO
 */
@Data
public class RoleCreateReqDTO {

    // roleCode 可选：不传时后端自动生成 R_XXXXXXXX；老调用方仍可继续传自定义编码
    @Size(max = 10)
    private String roleCode;

    @NotBlank
    @Size(max = 100)
    private String roleChName;

    @Size(max = 100)
    private String remark;
}
