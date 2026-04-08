package com.bank.branch.platform.common.security.context;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CurrentUserContextTest {
    @Test void hasRoleShouldReturnTrueForExistingRole() {
        var ctx = new CurrentUserContext("E001", "E001", "测试用户", "ORG001", "总行", 1,
            Set.of("R1", "R2"), Set.of(), Set.of(), false);
        assertTrue(ctx.hasRole("R1"));
        assertFalse(ctx.hasRole("R3"));
    }
    @Test void hasAnyRoleShouldMatchPartially() {
        var ctx = new CurrentUserContext("E001", "E001", "测试用户", "ORG001", "总行", 1,
            Set.of("R1"), Set.of(), Set.of(), false);
        assertTrue(ctx.hasAnyRole(Set.of("R1", "R99")));
        assertFalse(ctx.hasAnyRole(Set.of("R99")));
    }
    @Test void hasRoleShouldHandleNullRoleIds() {
        var ctx = new CurrentUserContext("E001", "E001", "测试用户", "ORG001", "总行", 1,
            null, Set.of(), Set.of(), false);
        assertFalse(ctx.hasRole("R1"));
    }
    @Test void hasAnyRoleShouldHandleNullInputs() {
        var ctx = new CurrentUserContext("E001", "E001", "测试用户", "ORG001", "总行", 1,
            Set.of("R1"), Set.of(), Set.of(), false);
        assertFalse(ctx.hasAnyRole(null));
    }

    @Test void hasAnyRole_bothNull_shouldReturnFalse() {
        var ctx = new CurrentUserContext("E001", "E001", "测试用户", "ORG001", "总行", 1,
            null, Set.of(), Set.of(), false);
        assertFalse(ctx.hasAnyRole(Set.of("R1")));
    }

    @Test void hasAnyRole_emptyRoleIds_shouldReturnFalse() {
        var ctx = new CurrentUserContext("E001", "E001", "测试用户", "ORG001", "总行", 1,
            Collections.emptySet(), Set.of(), Set.of(), false);
        assertFalse(ctx.hasAnyRole(Set.of("R1")));
    }

    @Test void recordAccessors_shouldReturnCorrectValues() {
        var ctx = new CurrentUserContext("E001", "E001", "测试用户", "ORG001", "总行", 1,
            Set.of("R1"), Set.of("ADMIN"), Set.of("GRP1"), true);
        assertEquals("E001", ctx.empId());
        assertEquals("ORG001", ctx.mainOrgCode());
        assertTrue(ctx.systemAdmin());
        assertTrue(ctx.roleCodes().contains("ADMIN"));
        assertTrue(ctx.candidateGroupKeys().contains("GRP1"));
    }
}
