package com.bank.branch.platform.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机构信息实体，对应 EXT_ORG_INFO 表。
 * <p>
 * 该表来自外部系统同步，存储银行行政机构树（总行→分行→支行）。
 * 主键 ID 为数据库自增整型；ORG_CODE 具有唯一约束，业务上通常以 orgCode 作为标识。
 * P_ID 存储上级机构编码（类型 varchar，与 ORG_CODE 关联），不是 ID 主键外键。
 * </p>
 */
@Data
public class ExtOrgInfo {

    /** 自增主键，对应 ID */
    private Integer id;

    /** 机构编号（唯一），对应 ORG_CODE */
    private String orgCode;

    /** 机构名称，对应 ORG_NAME */
    private String orgName;

    /** 机构等级：1-总行，2-分行，3-支行，对应 ORG_LEVEL */
    private Integer orgLevel;

    /** 上级机构编码，对应 P_ID */
    private String pId;

    /** 机构状态：0-启用，1-删除，对应 ORGAN_STATE */
    private Integer organState;

    /** 行政区划代码，对应 ADM_DIVISION_CODE */
    private String admDivisionCode;

    /** 行政区划名称，对应 ADM_DIVISION_NAME */
    private String admDivisionName;

    /** 创建时间，对应 CREATE_TIME */
    private LocalDateTime createTime;

    /** 创建人，对应 CREATE_USER */
    private String createUser;
}
