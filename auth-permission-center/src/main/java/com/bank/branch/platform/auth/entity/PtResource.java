package com.bank.branch.platform.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资源实体，对应 PT_RESOURCE 表。
 * <p>
 * 该表存储所有 REST 接口及菜单资源，是 RBAC 权限模型的核心表。
 * 全局 underscore-to-camel 负责 RESOURCE_URL → resourceUrl 等映射。
 * 注意数据库中字段名为 ISMENU（无下划线），MyBatis 仍能正确映射到 isMenu。
 * </p>
 */
@Data
@TableName("PT_RESOURCE")
public class PtResource {

    /** 资源ID，对应 RESOURCE_ID；业务赋值，非自增 */
    @TableId(value = "RESOURCE_ID", type = IdType.INPUT)
    private String resourceId;

    /** 资源URL（支持 Ant 通配符），对应 RESOURCE_URL */
    private String resourceUrl;

    /** 请求方法：GET/POST/PUT/DELETE/*，对应 RESOURCE_METHOD */
    private String resourceMethod;

    /** 菜单名称，对应 MENU_NAME */
    private String menuName;

    /** 菜单图标路径，对应 MENU_ICON_URL */
    private String menuIconUrl;

    /** 菜单排序号，对应 MENU_RANK_NO */
    private Integer menuRankNo;

    /** 是否为菜单：0-是，1-否，对应 ISMENU（无下划线，MyBatis-Plus 约定式 SQL 会自动转 is_menu 撞墙，显式标注） */
    @com.baomidou.mybatisplus.annotation.TableField("ISMENU")
    private Integer isMenu;

    /** 是否为叶子节点菜单：1-是，0-否，对应 MENU_ENDFLAG */
    private String menuEndflag;

    /** 上级资源ID，对应 PARENT_RESOURCE_ID */
    private String parentResourceId;

    /** 状态：0-启用，1-不启用，对应 STATUS */
    private Integer status;

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
