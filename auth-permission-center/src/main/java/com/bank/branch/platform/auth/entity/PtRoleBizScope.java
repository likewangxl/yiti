package com.bank.branch.platform.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色业务范围实体，对应 PT_ROLE_BIZ_SCOPE 表。
 * <p>
 * 存储角色在各业务类型下的数据权限范围配置，例如某角色针对客户线索（LEAD）
 * 只能查看"本机构及下属机构（ORG_SUBTREE）"的数据。
 * (ROLE_ID, BIZ_TYPE) 联合唯一，一个角色在一种业务类型下只有一条生效的范围配置。
 * </p>
 */
@Data
public class PtRoleBizScope {

    /** 主键ID，对应 ID */
    private String id;

    /** 角色ID，对应 ROLE_ID */
    private String roleId;

    /**
     * 业务类型，对应 BIZ_TYPE。
     * 取值示例：NAV / PRODUCT / LEAD / CUSTOMER 等
     */
    private String bizType;

    /**
     * 数据范围，对应 DATA_SCOPE。
     * 取值示例：SELF_CREATED / SELF / SELF_ASSIGNED / ORG / ORG_SUBTREE / ALL / WORKFLOW_PARTICIPANT
     */
    private String dataScope;

    /** 记录状态：0-可用，1-不可用，对应 RECORD_STATUS */
    private Integer recordStatus;

    /** 创建时间，对应 CREATE_TIME */
    private LocalDateTime createTime;

    /** 创建人，对应 CREATE_USER */
    private String createUser;

    /** 更新时间，对应 UPDATE_TIME */
    private LocalDateTime updateTime;

    /** 更新人，对应 UPDATE_USER */
    private String updateUser;

    /** 备注，对应 REMARK */
    private String remark;
}
