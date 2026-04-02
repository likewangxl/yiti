package com.bank.branch.platform.common.security.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BizActionTest {
    @Test void shouldHave15Values() { assertEquals(15, BizAction.values().length); }
    @Test void readShouldHaveCorrectCode() { assertEquals("READ", BizAction.READ.getCode()); }
    @Test void executeSqlShouldHaveCorrectCode() { assertEquals("EXECUTE_SQL", BizAction.EXECUTE_SQL.getCode()); }
}
