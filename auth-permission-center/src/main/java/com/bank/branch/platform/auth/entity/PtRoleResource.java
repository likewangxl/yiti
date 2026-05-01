package com.bank.branch.platform.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色资源关联实体，对应 PT_ROLE_RESOURCE 表。
 * <p>
 * 记录角色与资源（接口/菜单）的多对多授权关系。
 * 主键为业务生成的 UUID 字符串，不使用自增。
 * </p>
 */
@Data
@TableName("PT_ROLE_RESOURCE")
public class PtRoleResource {

    /** 主键ID，对应 ID；业务赋值（UUID），非自增 */
    @TableId(value = "ID", type = IdType.INPUT)
    private String id;

    /** 角色ID，对应 ROLE_ID */
    private String roleId;

    /** 资源ID，对应 RESOURCE_ID */
    private String resourceId;

    /** 系统编号，对应 SYS_CODE */
    private String sysCode;

    /** 创建时间，对应 CREATE_TIME */
    private LocalDateTime createTime;
}
