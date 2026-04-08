package com.bank.branch.platform.common.security.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class BizTypeTest {
    @Test void shouldHave18Values() { assertEquals(18, BizType.values().length); }
    @Test void navShouldHaveCorrectCode() { assertEquals("NAV", BizType.NAV.getCode()); }
    @Test void sysConfigShouldHaveCorrectCode() { assertEquals("SYS_CONFIG", BizType.SYS_CONFIG.getCode()); }

    @ParameterizedTest
    @EnumSource(BizType.class)
    void allValues_shouldHaveNonBlankCodeAndDescription(BizType type) {
        assertNotNull(type.getCode());
        assertFalse(type.getCode().isBlank());
        assertNotNull(type.getDescription());
        assertFalse(type.getDescription().isBlank());
    }

    @Test void valueOf_invalidName_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> BizType.valueOf("INVALID"));
    }
}
