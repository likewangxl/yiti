package com.bank.branch.platform.common.security.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataScopeContextTest {
    @AfterEach void cleanup() { DataScopeContext.clear(); }

    @Test void setAndCurrentShouldWork() {
        DataScopeContext ctx = new DataScopeContext();
        ctx.setEmpId("E001");
        DataScopeContext.set(ctx);
        assertNotNull(DataScopeContext.current());
        assertEquals("E001", DataScopeContext.current().getEmpId());
    }
    @Test void clearShouldRemoveContext() {
        DataScopeContext.set(new DataScopeContext());
        DataScopeContext.clear();
        assertNull(DataScopeContext.current());
    }
    @Test void currentShouldReturnNullWhenNotSet() {
        assertNull(DataScopeContext.current());
    }
}
