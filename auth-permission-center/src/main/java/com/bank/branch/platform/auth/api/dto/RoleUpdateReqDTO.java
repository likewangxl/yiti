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

    /** 记录状态：0=启用 1=停用；null 表示不修改 */
    private Integer recordStatus;
}
