package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色资源全量替换请求DTO
 * 用于替换角色的全部资源绑定（覆盖操作，传空列表即清空所有资源）
 */
@Data
public class RoleResourceReplaceReqDTO {

    @NotNull
    private List<String> resourceIds;

    @NotBlank
    @Size(max = 200)
    private String reason;
}
