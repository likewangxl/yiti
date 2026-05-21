package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色菜单分配请求 DTO。
 * <p>用于全量替换某角色的菜单绑定（PT_RESOURCE.IS_MENU=1 部分）。
 * 接口资源绑定（IS_MENU=0）不受此操作影响。传空列表清空该角色全部菜单绑定。</p>
 */
@Data
public class RoleMenuReplaceReqDTO {

    @NotNull
    private List<String> menuIds;

    // 改为可选：分配菜单不再强制变更原因（产品决定，前端去掉了输入框）
    @Size(max = 200)
    private String reason;
}
