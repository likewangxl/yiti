package com.bank.branch.platform.common.security.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BizTypeTest {
    @Test void shouldHave17Values() { assertEquals(17, BizType.values().length); }
    @Test void navShouldHaveCorrectCode() { assertEquals("NAV", BizType.NAV.getCode()); }
    @Test void sysConfigShouldHaveCorrectCode() { assertEquals("SYS_CONFIG", BizType.SYS_CONFIG.getCode()); }
}
