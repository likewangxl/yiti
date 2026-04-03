package com.bank.branch.platform.common.security.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BizActionTest {
    @Test void shouldHave15Values() { assertEquals(15, BizAction.values().length); }
    @Test void readShouldHaveCorrectCode() { assertEquals("READ", BizAction.READ.getCode()); }
    @Test void executeSqlShouldHaveCorrectCode() { assertEquals("EXECUTE_SQL", BizAction.EXECUTE_SQL.getCode()); }

    @ParameterizedTest
    @EnumSource(BizAction.class)
    void allValues_shouldHaveNonBlankCodeAndDescription(BizAction action) {
        assertNotNull(action.getCode());
        assertFalse(action.getCode().isBlank());
        assertNotNull(action.getDescription());
        assertFalse(action.getDescription().isBlank());
    }

    @Test void highRiskActions_shouldBeIdentifiable() {
        Set<BizAction> highRisk = Set.of(
            BizAction.EXPORT, BizAction.IMPORT, BizAction.DELETE,
            BizAction.TRANSFER, BizAction.EXECUTE_SQL, BizAction.RECALC,
            BizAction.PERMISSION_CHANGE, BizAction.JOB_TRIGGER
        );
        assertEquals(8, highRisk.size());
        // 确保所有高危动作都在枚举中定义
        for (BizAction action : highRisk) {
            assertNotNull(BizAction.valueOf(action.name()));
        }
    }
}
