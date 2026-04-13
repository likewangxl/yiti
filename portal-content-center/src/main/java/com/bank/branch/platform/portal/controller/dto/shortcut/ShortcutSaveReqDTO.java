package com.bank.branch.platform.portal.controller.dto.shortcut;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 快捷入口保存请求 DTO（A.2 个性化配置）
 */
@Data
public class ShortcutSaveReqDTO {

    /** 快捷入口列表（至少一项） */
    @NotEmpty(message = "快捷入口列表不能为空")
    @Valid
    private List<ShortcutItemDTO> shortcuts;
}
