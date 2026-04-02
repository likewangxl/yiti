package com.bank.branch.platform.common.security.context;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CurrentUserContextTest {
    @Test void hasRoleShouldReturnTrueForExistingRole() {
        var ctx = new CurrentUserContext("E001", "ORG001", Set.of("R1", "R2"), Set.of(), Set.of(), false);
        assertTrue(ctx.hasRole("R1"));
        assertFalse(ctx.hasRole("R3"));
    }
    @Test void hasAnyRoleShouldMatchPartially() {
        var ctx = new CurrentUserContext("E001", "ORG001", Set.of("R1"), Set.of(), Set.of(), false);
        assertTrue(ctx.hasAnyRole(Set.of("R1", "R99")));
        assertFalse(ctx.hasAnyRole(Set.of("R99")));
    }
    @Test void hasRoleShouldHandleNullRoleIds() {
        var ctx = new CurrentUserContext("E001", "ORG001", null, Set.of(), Set.of(), false);
        assertFalse(ctx.hasRole("R1"));
    }
    @Test void hasAnyRoleShouldHandleNullInputs() {
        var ctx = new CurrentUserContext("E001", "ORG001", Set.of("R1"), Set.of(), Set.of(), false);
        assertFalse(ctx.hasAnyRole(null));
    }
}
