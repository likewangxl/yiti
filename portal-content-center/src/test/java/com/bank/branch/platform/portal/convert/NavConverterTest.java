package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.api.dto.NavDTO;
import com.bank.branch.platform.portal.entity.PortalNav;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NavConverter 单元测试
 * <p>纯 POJO 转换，不需要 Spring 上下文。</p>
 */
class NavConverterTest {

    @Test
    @DisplayName("toDTO: null 输入 -> 返回 null")
    void toDTOShouldReturnNullOnNullInput() {
        assertNull(NavConverter.toDTO(null));
    }

    @Test
    @DisplayName("toDTO: 7 个字段全量映射验证")
    void toDTOShouldMapAllFields() {
        // given
        PortalNav entity = new PortalNav();
        entity.setId("nav-001");
        entity.setNavName("内部OA系统");
        entity.setNavUrl("https://oa.bank.com");
        entity.setNavIcon("icon-oa");
        entity.setNavCategory("办公系统");
        entity.setSortOrder(1);
        entity.setStatus("ACTIVE");

        // when
        NavDTO dto = NavConverter.toDTO(entity);

        // then
        assertNotNull(dto);
        assertEquals("nav-001", dto.getId());
        assertEquals("内部OA系统", dto.getNavName());
        assertEquals("https://oa.bank.com", dto.getNavUrl());
        assertEquals("icon-oa", dto.getNavIcon());
        assertEquals("办公系统", dto.getNavCategory());
        assertEquals(1, dto.getSortOrder());
        assertEquals("ACTIVE", dto.getStatus());
    }

    @Test
    @DisplayName("toDTO: Entity 额外字段（createdBy 等）不影响转换")
    void toDTOShouldIgnoreExtraEntityFields() {
        // given - 设置 NavDTO 不包含的字段
        PortalNav entity = new PortalNav();
        entity.setId("nav-002");
        entity.setNavName("征信查询");
        entity.setNavUrl("https://credit.bank.com");
        entity.setStatus("DISABLED");
        entity.setCreatedBy("admin");
        entity.setUpdatedBy("admin");

        // when
        NavDTO dto = NavConverter.toDTO(entity);

        // then
        assertNotNull(dto);
        assertEquals("nav-002", dto.getId());
        assertEquals("征信查询", dto.getNavName());
        assertEquals("DISABLED", dto.getStatus());
    }
}
