package com.bank.branch.platform.common.security.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataScopeTypeTest {
    @Test void shouldHave7Values() { assertEquals(7, DataScopeType.values().length); }
    @Test void selfCreatedShouldHaveCorrectCode() { assertEquals("SELF_CREATED", DataScopeType.SELF_CREATED.getCode()); }
    @Test void allShouldHaveCorrectCode() { assertEquals("ALL", DataScopeType.ALL.getCode()); }

    @Test void valueOf_shouldRoundTrip() {
        for (DataScopeType type : DataScopeType.values()) {
            assertEquals(type, DataScopeType.valueOf(type.name()));
        }
    }

    @Test void workflowParticipant_shouldHaveCorrectCode() {
        assertEquals("WORKFLOW_PARTICIPANT", DataScopeType.WORKFLOW_PARTICIPANT.getCode());
    }
}
