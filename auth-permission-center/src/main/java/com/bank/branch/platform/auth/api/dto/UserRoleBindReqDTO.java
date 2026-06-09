package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户角色绑定请求DTO
 * 用于为指定用户批量绑定角色
 */
@Data
public class UserRoleBindReqDTO {

    @NotEmpty
    @Size(max = 20)
    private List<String> roleIds;

    @NotBlank
    @Size(max = 200)
    private String reason;

    /**
     * 主角色ID（可选）。传入时将其设为该用户主角色；必须是本次绑定后用户已拥有的角色。
     * 不传时由服务端保证用户至少有一个主角色（无主角色则取第一个已分配角色）。
     */
    @Size(max = 64)
    private String primaryRoleId;
}
