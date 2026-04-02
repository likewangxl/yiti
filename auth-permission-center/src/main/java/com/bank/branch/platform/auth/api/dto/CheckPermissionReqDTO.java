package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 权限检查请求DTO
 * 用于实时校验当前用户是否有权访问指定资源及执行指定业务操作
 */
@Data
public class CheckPermissionReqDTO {

    @NotBlank
    @Size(max = 256)
    private String resourceUrl;

    @NotBlank
    @Pattern(regexp = "^(GET|POST|PUT|DELETE)$")
    private String resourceMethod;

    /** 业务类型（可选，填写后同时检查数据范围权限） */
    private String bizType;

    /** 业务操作（可选，与 bizType 配合使用） */
    private String action;
}
