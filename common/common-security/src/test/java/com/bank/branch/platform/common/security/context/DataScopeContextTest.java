package com.bank.branch.platform.common.security.context;

import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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

    @Test void overwriteShouldReplaceContext() {
        DataScopeContext ctx1 = new DataScopeContext();
        ctx1.setEmpId("E001");
        DataScopeContext.set(ctx1);

        DataScopeContext ctx2 = new DataScopeContext();
        ctx2.setEmpId("E002");
        DataScopeContext.set(ctx2);

        assertEquals("E002", DataScopeContext.current().getEmpId());
    }

    @Test void allFieldsShouldBeAccessible() {
        DataScopeContext ctx = new DataScopeContext();
        ctx.setBizType(BizType.LEAD);
        ctx.setAction(BizAction.WRITE);
        ctx.setScope(DataScopeType.ORG_SUBTREE);
        ctx.setEmpId("E001");
        ctx.setOrgCode("ORG001");
        ctx.setOrgSubtreeCodes(Set.of("ORG001", "ORG002"));
        ctx.setCandidateGroupKeys(Set.of("GRP1"));
        DataScopeContext.set(ctx);

        DataScopeContext current = DataScopeContext.current();
        assertEquals(BizType.LEAD, current.getBizType());
        assertEquals(BizAction.WRITE, current.getAction());
        assertEquals(DataScopeType.ORG_SUBTREE, current.getScope());
        assertEquals("E001", current.getEmpId());
        assertEquals("ORG001", current.getOrgCode());
        assertEquals(2, current.getOrgSubtreeCodes().size());
        assertTrue(current.getCandidateGroupKeys().contains("GRP1"));
    }

    @Test void threadIsolation_shouldNotLeakBetweenThreads() throws Exception {
        DataScopeContext ctx = new DataScopeContext();
        ctx.setEmpId("MAIN_THREAD");
        DataScopeContext.set(ctx);

        AtomicReference<DataScopeContext> otherThreadCtx = new AtomicReference<>();
        Thread t = new Thread(() -> otherThreadCtx.set(DataScopeContext.current()));
        t.start();
        t.join();

        assertNull(otherThreadCtx.get(), "other thread should not see main thread context");
        assertEquals("MAIN_THREAD", DataScopeContext.current().getEmpId());
    }
}
