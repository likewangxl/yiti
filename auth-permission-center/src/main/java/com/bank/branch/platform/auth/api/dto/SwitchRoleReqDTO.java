package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 切换当前角色请求DTO
 * <p>切换本次会话的当前激活角色（仅会话内生效），目标角色必须是当前用户已分配的角色。</p>
 */
@Data
public class SwitchRoleReqDTO {

    @NotBlank
    @Size(max = 64)
    private String roleId;
}
