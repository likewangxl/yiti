package com.bank.branch.platform.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户角色关联实体，对应 PT_USER_ROLE 表。
 * <p>
 * 联合主键为 (USER_ID, ROLE_ID)，不设置单独的自增 ID。
 * groupAssing 对应数据库列 GROUP_ASSING（注意原表名存在拼写不规范，保持与 DDL 一致）。
 * </p>
 */
@Data
public class PtUserRole {

    /** 用户ID，对应 USER_ID */
    private String userId;

    /** 角色ID，对应 ROLE_ID */
    private String roleId;

    /** 默认分配标志：0-否，1-是，对应 DEFAULT_ASSIGN */
    private Integer defaultAssign;

    /** 用户组角色继承标志：0-否，1-是，对应 INHERIT_ASSIGN */
    private Integer inheritAssign;

    /** 角色组分配标志：0-否，1-是，对应 GROUP_ASSING（DDL 原名保留） */
    private Integer groupAssing;

    /** 创建时间，对应 CREATE_TIME */
    private LocalDateTime createTime;
}
