package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.portal.api.dto.ShortcutDTO;
import com.bank.branch.platform.portal.controller.dto.shortcut.ShortcutItemDTO;
import com.bank.branch.platform.portal.controller.dto.shortcut.ShortcutSaveReqDTO;
import com.bank.branch.platform.portal.convert.ShortcutConverter;
import com.bank.branch.platform.portal.entity.PortalShortcut;
import com.bank.branch.platform.portal.mapper.PortalShortcutMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 工作台快捷入口业务服务
 * <p>
 * 提供快捷入口的查询和个性化保存能力。
 * SYSTEM 类型快捷入口全员可见，CUSTOM 类型快捷入口仅所属员工可见。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortcutService {

    private final PortalShortcutMapper shortcutMapper;
    private final CurrentUserApi currentUserApi;

    /**
     * 按 shortcutType 查询快捷入口列表。
     * <ul>
     *   <li>shortcutType=null 或 "ALL"：返回 SYSTEM（全员可见）+ empId 的 CUSTOM</li>
     *   <li>shortcutType="SYSTEM"：仅返回系统级快捷入口</li>
     *   <li>shortcutType="CUSTOM"：仅返回该员工的自定义快捷入口</li>
     * </ul>
     *
     * @param empId        员工工号
     * @param shortcutType 类型过滤（null/ALL/SYSTEM/CUSTOM）
     * @return 快捷入口实体列表（按 sort_order 排序）
     */
    public List<PortalShortcut> listShortcuts(String empId, String shortcutType) {
        // null 或空串视同 ALL
        if (shortcutType == null || shortcutType.isBlank() || "ALL".equalsIgnoreCase(shortcutType)) {
            return shortcutMapper.listByEmpIdOrSystem(empId);
        }
        if ("SYSTEM".equalsIgnoreCase(shortcutType)) {
            return shortcutMapper.listSystemOnly();
        }
        if ("CUSTOM".equalsIgnoreCase(shortcutType)) {
            return shortcutMapper.listByEmpId(empId);
        }
        // 未知类型，返回空列表
        log.warn("[ShortcutService.listShortcuts] 未知 shortcutType={}, 返回空列表", shortcutType);
        return Collections.emptyList();
    }

    /**
     * A.2 查询当前用户可见的快捷入口（SYSTEM + 自己的 CUSTOM），快捷方法。
     *
     * @return 当前用户可见的快捷入口 DTO 列表
     */
    public List<ShortcutDTO> listMyShortcuts() {
        String empId = currentUserApi.getCurrentEmpId();
        List<PortalShortcut> entities = listShortcuts(empId, null);
        return entities.stream().map(ShortcutConverter::toDTO).collect(Collectors.toList());
    }

    /**
     * 保存指定员工的自定义快捷入口（delete-then-batch-insert）。
     * <p>
     * 先删除该员工所有 CUSTOM 快捷入口，再批量插入新列表。
     * 使用事务保证原子性。
     * </p>
     *
     * @param empId 员工工号
     * @param items 快捷入口项列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveCustomShortcuts(String empId, List<ShortcutItemDTO> items) {
        // 1. 删除该用户所有 CUSTOM 快捷入口
        shortcutMapper.deleteCustomByEmpId(empId);
        // 2. 批量插入新的
        List<PortalShortcut> entities = items.stream().map(item -> {
            PortalShortcut s = new PortalShortcut();
            s.setId(UUID.randomUUID().toString().replace("-", ""));
            s.setShortcutName(item.getShortcutName());
            s.setShortcutUrl(item.getShortcutUrl());
            s.setShortcutIcon(item.getShortcutIcon());
            s.setShortcutType("CUSTOM");
            s.setTargetType(item.getTargetType());
            s.setEmpId(empId);
            s.setSortOrder(item.getSortOrder());
            s.setStatus("ACTIVE");
            s.setCreatedBy(empId);
            s.setUpdatedBy(empId);
            return s;
        }).collect(Collectors.toList());
        if (!entities.isEmpty()) {
            shortcutMapper.insertBatch(entities);
        }
        log.info("[ShortcutService.saveCustomShortcuts] 保存自定义快捷入口成功, empId={}, count={}", empId, items.size());
    }

    /**
     * A.3 保存当前用户的自定义快捷入口，快捷方法。
     *
     * @param req 快捷入口保存请求，包含快捷入口列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMyCustomShortcuts(ShortcutSaveReqDTO req) {
        String empId = currentUserApi.getCurrentEmpId();
        saveCustomShortcuts(empId, req.getShortcuts());
    }
}
