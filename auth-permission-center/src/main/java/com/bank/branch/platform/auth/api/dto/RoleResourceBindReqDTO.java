package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色资源绑定请求DTO
 * 用于为角色追加绑定资源（增量操作）
 */
@Data
public class RoleResourceBindReqDTO {

    @NotEmpty
    private List<String> resourceIds;

    @NotBlank
    @Size(max = 200)
    private String reason;
}
