package com.bank.branch.platform.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体，对应 PT_ROLE 表。
 * <p>
 * 字段命名遵循驼峰，MyBatis 全局 underscore-to-camel 配置负责与数据库大写下划线列名的映射。
 * </p>
 */
@Data
@TableName("PT_ROLE")
public class PtRole {

    /** 角色ID，对应 ROLE_ID；业务赋值，非自增 */
    @TableId(value = "ROLE_ID", type = IdType.INPUT)
    private String roleId;

    /** 角色编码，对应 ROLE_CODE */
    private String roleCode;

    /** 角色中文名，对应 ROLE_CHNAME（DB 列名是 ROLE_CHNAME 单段，不是 ROLE_CH_NAME，需显式映射） */
    @TableField("ROLE_CHNAME")
    private String roleChName;

    /** 记录状态：0-可用，1-不可用，对应 RECORD_STATUS */
    private Integer recordStatus;

    /** 系统编号，对应 SYS_CODE */
    private String sysCode;

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
