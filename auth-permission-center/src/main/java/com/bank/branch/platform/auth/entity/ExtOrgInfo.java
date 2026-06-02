package com.bank.branch.platform.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机构信息实体，对应 EXT_ORG_INFO 表。
 * <p>
 * 该表来自外部系统同步，存储银行行政机构树（总行→分行→支行）。
 * 数据库自增主键为 ID（整型），但业务上以 ORG_CODE（唯一约束）作为标识。
 * MyBatis-Plus @TableId 指向业务唯一键 ORG_CODE，自增 ID 改用 @TableField 映射。
 * P_ID 存储上级机构编码（类型 varchar，与 ORG_CODE 关联），不是 ID 主键外键。
 * OrgMapper 仅做查询，BaseMapper 的 insert/updateById 对本表不使用。
 * </p>
 */
@Data
@TableName("EXT_ORG_INFO")
public class ExtOrgInfo {

    /** 数据库自增整型主键，对应 ID；非 BaseMapper 主键，用 @TableField 映射 */
    @TableField("ID")
    private Integer id;

    /** 机构编号（唯一业务键），对应 ORG_CODE；作为 MyBatis-Plus 逻辑主键 */
    @TableId(value = "ORG_CODE", type = IdType.INPUT)
    private String orgCode;

    /** 机构名称，对应 ORG_NAME */
    private String orgName;

    /** 机构等级：1-总行，2-分行，3-支行，对应 ORG_LEVEL */
    private Integer orgLevel;

    /** 上级机构编码，对应 P_ID */
    private String pId;

    /** 机构编号（来自 xanpd sys_dept.DEPT_NO），对应 DEPT_NO */
    private String deptNo;

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
