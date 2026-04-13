package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.portal.api.dto.ShortcutDTO;
import com.bank.branch.platform.portal.controller.dto.shortcut.ShortcutSaveReqDTO;
import com.bank.branch.platform.portal.convert.ShortcutConverter;
import com.bank.branch.platform.portal.entity.PortalShortcut;
import com.bank.branch.platform.portal.service.ShortcutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作台快捷入口控制器
 * <p>
 * 提供快捷入口列表查询和个性化保存接口。
 * 快捷入口不需要业务权限（@BizAuth），只需要登录态即可访问。
 * </p>
 */
@RestController
@RequestMapping("/api/portal/shortcuts")
@RequiredArgsConstructor
@Tag(name = "快捷入口", description = "工作台快捷入口管理")
public class ShortcutController {

    private final ShortcutService shortcutService;
    private final CurrentUserApi currentUserApi;

    /**
     * A.2 获取快捷入口列表
     * <p>
     * 支持 shortcutType 过滤：SYSTEM 仅系统级，CUSTOM 仅当前用户自定义，
     * ALL 或不传则返回全部（SYSTEM + 当前用户 CUSTOM）。
     * </p>
     *
     * @param shortcutType 类型过滤（可选：SYSTEM/CUSTOM/ALL，默认 ALL）
     * @return 当前用户可见的快捷入口列表
     */
    @GetMapping
    @Operation(summary = "获取快捷入口列表", description = "按类型过滤，支持 SYSTEM/CUSTOM/ALL")
    public ResponseWrapper<List<ShortcutDTO>> listShortcuts(
            @RequestParam(value = "shortcutType", required = false) String shortcutType) {
        String empId = currentUserApi.getCurrentEmpId();
        List<PortalShortcut> entities = shortcutService.listShortcuts(empId, shortcutType);
        List<ShortcutDTO> dtoList = entities.stream()
                .map(ShortcutConverter::toDTO)
                .collect(Collectors.toList());
        return ResponseWrapper.success(dtoList);
    }

    /**
     * A.3 保存个性化快捷入口
     * <p>
     * 全量替换当前用户的 CUSTOM 类型快捷入口：先删除旧记录，再批量插入新记录。
     * </p>
     *
     * @param req 快捷入口保存请求
     * @return 空响应
     */
    @PutMapping
    @Operation(summary = "保存个性化快捷入口", description = "全量替换当前用户的自定义快捷入口")
    public ResponseWrapper<Void> saveShortcuts(@Valid @RequestBody ShortcutSaveReqDTO req) {
        String empId = currentUserApi.getCurrentEmpId();
        shortcutService.saveCustomShortcuts(empId, req.getShortcuts());
        return ResponseWrapper.success();
    }
}
