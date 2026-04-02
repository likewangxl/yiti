package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 业务范围保存请求DTO
 * 用于新增或更新角色的业务数据范围配置（存在则更新，不存在则新增）
 */
@Data
public class BizScopeSaveReqDTO {

    @NotBlank
    @Size(max = 50)
    private String roleId;

    @NotBlank
    private String bizType;

    @NotBlank
    private String dataScope;

    @NotBlank
    @Size(max = 200)
    private String reason;
}
