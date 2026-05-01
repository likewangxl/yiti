package com.bank.branch.platform.portal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网址导航实体，对应 portal_nav 表。
 * <p>
 * 用于门户页面的网址导航管理，支持按分类和排序展示。
 * status 字段使用字符串枚举：ACTIVE-启用，DISABLED-禁用。
 * </p>
 */
@Data
@TableName("portal_nav")
public class PortalNav {

    /** 导航ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 导航名称，对应 nav_name */
    private String navName;

    /** 导航URL，对应 nav_url */
    private String navUrl;

    /** 图标，对应 nav_icon */
    private String navIcon;

    /** 导航分类，对应 nav_category */
    private String navCategory;

    /** 排序号，对应 sort_order */
    private Integer sortOrder;

    /** 状态：ACTIVE-启用，DISABLED-禁用，对应 status */
    private String status;

    /** 创建人，对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新人，对应 updated_by */
    private String updatedBy;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
