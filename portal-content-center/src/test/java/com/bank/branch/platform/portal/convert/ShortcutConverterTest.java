package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.api.dto.ShortcutDTO;
import com.bank.branch.platform.portal.entity.PortalShortcut;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ShortcutConverter 单元测试
 * <p>纯 POJO 转换，不需要 Spring 上下文。</p>
 */
class ShortcutConverterTest {

    @Test
    @DisplayName("toDTO: null 输入 → 返回 null")
    void toDTOShouldReturnNullOnNullInput() {
        assertNull(ShortcutConverter.toDTO(null));
    }

    @Test
    @DisplayName("toDTO: 7 个字段全量映射验证")
    void toDTOShouldMapAllFields() {
        // given
        PortalShortcut entity = new PortalShortcut();
        entity.setId("sc-001");
        entity.setShortcutName("客户管理");
        entity.setShortcutUrl("/customer/list");
        entity.setShortcutIcon("icon-customer");
        entity.setShortcutType("SYSTEM");
        entity.setTargetType("INTERNAL");
        entity.setSortOrder(1);

        // when
        ShortcutDTO dto = ShortcutConverter.toDTO(entity);

        // then
        assertNotNull(dto);
        assertEquals("sc-001", dto.getId());
        assertEquals("客户管理", dto.getShortcutName());
        assertEquals("/customer/list", dto.getShortcutUrl());
        assertEquals("icon-customer", dto.getShortcutIcon());
        assertEquals("SYSTEM", dto.getShortcutType());
        assertEquals("INTERNAL", dto.getTargetType());
        assertEquals(1, dto.getSortOrder());
    }
}
