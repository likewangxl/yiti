package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.api.dto.NavDTO;
import com.bank.branch.platform.portal.entity.PortalNav;

/**
 * PortalNav Entity -> DTO 转换器
 * <p>纯静态方法，无业务逻辑。</p>
 */
public final class NavConverter {

    private NavConverter() {}

    /**
     * Entity -> NavDTO
     *
     * @param entity 导航实体
     * @return NavDTO，entity 为 null 时返回 null
     */
    public static NavDTO toDTO(PortalNav entity) {
        if (entity == null) return null;
        return NavDTO.builder()
                .id(entity.getId())
                .navName(entity.getNavName())
                .navUrl(entity.getNavUrl())
                .navIcon(entity.getNavIcon())
                .navCategory(entity.getNavCategory())
                .sortOrder(entity.getSortOrder())
                .status(entity.getStatus())
                .build();
    }
}
