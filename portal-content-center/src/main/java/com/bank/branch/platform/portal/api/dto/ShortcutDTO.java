package com.bank.branch.platform.portal.api.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 工作台快捷入口 DTO（不可变）
 *
 * <p>用于工作台快捷入口列表展示和个性化配置场景。</p>
 */
@Value
@Builder
public class ShortcutDTO {

    /** 快捷入口ID */
    String id;

    /** 快捷入口名称 */
    String shortcutName;

    /** 跳转URL */
    String shortcutUrl;

    /** 图标 */
    String shortcutIcon;

    /** 类型：SYSTEM-系统, CUSTOM-自定义 */
    String shortcutType;

    /** 目标类型：INTERNAL-内部, EXTERNAL-外部 */
    String targetType;

    /** 排序号 */
    Integer sortOrder;
}
