package com.bank.branch.platform.portal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作台快捷入口实体，对应 portal_shortcut 表。
 * <p>
 * shortcut_type 字段区分系统级和自定义快捷入口：SYSTEM-系统，CUSTOM-自定义。
 * target_type 字段区分跳转目标：INTERNAL-内部，EXTERNAL-外部。
 * status 字段使用字符串枚举：ACTIVE-启用，DISABLED-禁用。
 * 本表无 deleted 列，删除操作为物理删除。
 * </p>
 */
@Data
@TableName("portal_shortcut")
public class PortalShortcut {

    /** 快捷入口ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 快捷入口名称，对应 shortcut_name */
    private String shortcutName;

    /** 跳转URL，对应 shortcut_url */
    private String shortcutUrl;

    /** 图标，对应 shortcut_icon */
    private String shortcutIcon;

    /** 类型：SYSTEM-系统, CUSTOM-自定义，对应 shortcut_type */
    private String shortcutType;

    /** 目标类型：INTERNAL-内部, EXTERNAL-外部，对应 target_type */
    private String targetType;

    /** 所属用户工号（自定义快捷入口），对应 emp_id */
    private String empId;

    /** 排序号，对应 sort_order */
    private Integer sortOrder;

    /** 状态：ACTIVE-启用, DISABLED-禁用，对应 status */
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
