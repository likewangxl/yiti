package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 机构下用户简要信息DTO（G.2）
 * 用于 /api/orgs/{orgCode}/users 接口返回值
 */
@Data
public class OrgUserDTO {
    // 原有字段（保留，向后兼容）
    private String userId;
    private String username;
    private String userChnName;

    // 新增字段（与文档 G.2 对齐）
    private String empId;                        // 与 userId 同值
    private String displayName;                  // 与 userChnName 同值
    private String email;                        // 用户邮箱
    private List<RoleSimpleDTO> roles;           // 用户角色列表
}
