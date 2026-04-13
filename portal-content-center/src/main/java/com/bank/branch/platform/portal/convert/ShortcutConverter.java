package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.api.dto.ShortcutDTO;
import com.bank.branch.platform.portal.entity.PortalShortcut;

/**
 * PortalShortcut Entity -> DTO 转换器
 * <p>纯静态方法，无业务逻辑。</p>
 */
public final class ShortcutConverter {

    private ShortcutConverter() {}

    /**
     * Entity -> ShortcutDTO
     *
     * @param entity 快捷入口实体
     * @return DTO，entity 为 null 时返回 null
     */
    public static ShortcutDTO toDTO(PortalShortcut entity) {
        if (entity == null) return null;
        ShortcutDTO dto = new ShortcutDTO();
        dto.setId(entity.getId());
        dto.setShortcutName(entity.getShortcutName());
        dto.setShortcutUrl(entity.getShortcutUrl());
        dto.setShortcutIcon(entity.getShortcutIcon());
        dto.setShortcutType(entity.getShortcutType());
        dto.setTargetType(entity.getTargetType());
        dto.setSortOrder(entity.getSortOrder());
        return dto;
    }
}
