package com.bank.branch.platform.common.security.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class BizTypeTest {
    @Test void shouldHave26Values() { assertEquals(26, BizType.values().length); }
    @Test void crossOrgMarketingShouldHaveCorrectCode() {
        assertEquals("CROSS_ORG_MARKETING", BizType.CROSS_ORG_MARKETING.getCode());
    }
    @Test void navShouldHaveCorrectCode() { assertEquals("NAV", BizType.NAV.getCode()); }
    @Test void sysConfigShouldHaveCorrectCode() { assertEquals("SYS_CONFIG", BizType.SYS_CONFIG.getCode()); }
    @Test void violationShouldHaveCorrectCode() { assertEquals("VIOLATION", BizType.VIOLATION.getCode()); }

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

    @Test void workflowMonitorExists() {
        assertEquals("WORKFLOW_MONITOR", BizType.valueOf("WORKFLOW_MONITOR").getCode());
    }
}
