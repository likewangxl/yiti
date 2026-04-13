package com.bank.branch.platform.portal.api.dto;

import lombok.Data;

/**
 * 工作台快捷入口 DTO（A.1/A.2 用）
 *
 * <p>用于工作台快捷入口列表展示和个性化配置场景。</p>
 */
@Data
public class ShortcutDTO {

    /** 快捷入口ID */
    private String id;

    /** 快捷入口名称 */
    private String shortcutName;

    /** 跳转URL */
    private String shortcutUrl;

    /** 图标 */
    private String shortcutIcon;

    /** 类型：SYSTEM-系统, CUSTOM-自定义 */
    private String shortcutType;

    /** 目标类型：INTERNAL-内部, EXTERNAL-外部 */
    private String targetType;

    /** 排序号 */
    private Integer sortOrder;
}
