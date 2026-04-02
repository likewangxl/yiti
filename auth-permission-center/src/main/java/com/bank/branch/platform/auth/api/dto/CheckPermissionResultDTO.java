package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/**
 * 权限检查结果DTO
 * 用于跨模块返回详细的权限校验结论
 */
@Data
public class CheckPermissionResultDTO {
    private Boolean allowed;
    private Boolean rbacPassed;
    private Boolean scopePassed;
    private String scope;
    private String denyReason;
}
