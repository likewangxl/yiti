package com.bank.branch.platform.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户机构关联实体，对应 EXT_USER_ORG 表。
 * <p>
 * 记录用户与所属机构的归属关系。V1 阶段一个用户只归属一个主机构，
 * 联合主键为 (USER_ID, ORG_CODE)，预留多机构扩展能力。
 * </p>
 */
@Data
public class ExtUserOrg {

    /** 用户ID，对应 USER_ID */
    private String userId;

    /** 机构编码，对应 ORG_CODE */
    private String orgCode;

    /** 创建时间，对应 CREATE_TIME */
    private LocalDateTime createTime;
}
