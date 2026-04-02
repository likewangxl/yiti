package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/**
 * 权限检查响应DTO
 * 返回详细的权限检查结论，便于调用方定位拒绝原因
 */
@Data
public class CheckPermissionRespDTO {

    private Boolean allowed;
    /** RBAC资源权限是否通过 */
    private Boolean rbacPassed;
    /** 数据范围权限是否通过 */
    private Boolean scopePassed;
    /** 实际数据范围值 */
    private String scope;
    /** 拒绝原因描述 */
    private String denyReason;
}
