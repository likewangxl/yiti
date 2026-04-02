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
}
