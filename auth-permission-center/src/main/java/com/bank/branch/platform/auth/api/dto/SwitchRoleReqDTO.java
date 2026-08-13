package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 旧版角色切换请求 DTO。
 * <p>兼容端点只校验目标角色属于当前用户，不再修改会话或权限。</p>
 *
 * @deprecated 权限已采用全部有效角色并集
 */
@Deprecated(since = "2026-08", forRemoval = true)
@Data
public class SwitchRoleReqDTO {

    @NotBlank
    @Size(max = 64)
    private String roleId;
}
