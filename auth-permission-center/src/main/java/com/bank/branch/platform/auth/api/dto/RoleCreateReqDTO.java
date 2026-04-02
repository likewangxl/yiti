package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色创建请求DTO
 */
@Data
public class RoleCreateReqDTO {

    @NotBlank
    @Size(max = 10)
    @Pattern(regexp = "^[A-Z_]+$")
    private String roleCode;

    @NotBlank
    @Size(max = 100)
    private String roleChName;

    @Size(max = 100)
    private String remark;
}
