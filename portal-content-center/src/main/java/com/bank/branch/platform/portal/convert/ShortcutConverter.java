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
        return ShortcutDTO.builder()
                .id(entity.getId())
                .shortcutName(entity.getShortcutName())
                .shortcutUrl(entity.getShortcutUrl())
                .shortcutIcon(entity.getShortcutIcon())
                .shortcutType(entity.getShortcutType())
                .targetType(entity.getTargetType())
                .sortOrder(entity.getSortOrder())
                .build();
    }
}
