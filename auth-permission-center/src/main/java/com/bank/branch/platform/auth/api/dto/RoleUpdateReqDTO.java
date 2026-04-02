package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色更新请求DTO
 */
@Data
public class RoleUpdateReqDTO {

    @NotBlank
    @Size(max = 100)
    private String roleChName;

    @Size(max = 100)
    private String remark;
}
