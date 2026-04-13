package com.bank.branch.platform.portal.controller.dto.shortcut;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 快捷入口单项 DTO（ShortcutSaveReqDTO 的子结构）
 */
@Data
public class ShortcutItemDTO {

    /** 快捷入口名称 */
    @NotBlank(message = "快捷入口名称不能为空")
    @Size(max = 100, message = "快捷入口名称最长100字符")
    private String shortcutName;

    /** 跳转URL */
    @NotBlank(message = "跳转URL不能为空")
    @Size(max = 500, message = "跳转URL最长500字符")
    private String shortcutUrl;

    /** 图标 */
    @Size(max = 100, message = "图标标识最长100字符")
    private String shortcutIcon;

    /** 目标类型 INTERNAL/EXTERNAL */
    @NotBlank(message = "目标类型不能为空")
    @Pattern(regexp = "INTERNAL|EXTERNAL", message = "目标类型只能为 INTERNAL 或 EXTERNAL")
    private String targetType;

    /** 排序号 */
    @NotNull(message = "排序号不能为空")
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sortOrder;
}
