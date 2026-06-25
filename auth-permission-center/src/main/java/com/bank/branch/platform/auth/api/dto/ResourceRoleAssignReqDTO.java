package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 资源（菜单）分配角色请求DTO（资源维度全量设置绑定角色）。
 * <p>用于菜单管理页"分配角色"：把某资源的绑定角色整体替换为 {@code roleIds}
 * （传空列表即清空该资源的所有角色绑定）。</p>
 */
@Data
public class ResourceRoleAssignReqDTO {

    /** 替换后的角色ID列表（空=清空该资源所有角色绑定）. */
    @NotNull
    private List<String> roleIds;

    /** 操作原因（审计用，可选）. */
    @Size(max = 200)
    private String reason;
}
