package com.bank.branch.platform.common.db;

import com.bank.branch.platform.common.security.context.DataScopeContext;
import lombok.Data;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditFieldFillerTest {

    @AfterEach
    void cleanup() { DataScopeContext.clear(); }

    @Data
    static class TestEntity {
        private String createdBy;
        private LocalDateTime createdTime;
        private String updatedBy;
        private LocalDateTime updatedTime;
    }

    @Test
    void shouldFillCreatedFieldsOnInsert() throws Throwable {
        DataScopeContext ctx = new DataScopeContext();
        ctx.setEmpId("E001");
        DataScopeContext.set(ctx);

        TestEntity entity = new TestEntity();
        AuditFieldFiller filler = new AuditFieldFiller();

        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.INSERT);
        Executor executor = mock(Executor.class);

        Invocation invocation = new Invocation(executor,
            Executor.class.getMethod("update", MappedStatement.class, Object.class),
            new Object[]{ms, entity});

        // 由于 plugin wrap 机制，我们直接调用 intercept
        filler.intercept(invocation);

        assertEquals("E001", entity.getCreatedBy());
        assertNotNull(entity.getCreatedTime());
    }

    @Test
    void shouldFillUpdatedFieldsOnUpdate() throws Throwable {
        DataScopeContext ctx = new DataScopeContext();
        ctx.setEmpId("E002");
        DataScopeContext.set(ctx);

        TestEntity entity = new TestEntity();
        AuditFieldFiller filler = new AuditFieldFiller();

        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
        Executor executor = mock(Executor.class);

        Invocation invocation = new Invocation(executor,
            Executor.class.getMethod("update", MappedStatement.class, Object.class),
            new Object[]{ms, entity});

        filler.intercept(invocation);

        assertNull(entity.getCreatedBy(), "UPDATE should not fill createdBy");
        assertNull(entity.getCreatedTime(), "UPDATE should not fill createdTime");
        assertEquals("E002", entity.getUpdatedBy());
        assertNotNull(entity.getUpdatedTime());
    }

    @Test
    void shouldNotOverwriteExistingValues() throws Throwable {
        DataScopeContext ctx = new DataScopeContext();
        ctx.setEmpId("E003");
        DataScopeContext.set(ctx);

        TestEntity entity = new TestEntity();
        entity.setCreatedBy("EXISTING");

        AuditFieldFiller filler = new AuditFieldFiller();
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.INSERT);
        Executor executor = mock(Executor.class);

        Invocation invocation = new Invocation(executor,
            Executor.class.getMethod("update", MappedStatement.class, Object.class),
            new Object[]{ms, entity});

        filler.intercept(invocation);

        assertEquals("EXISTING", entity.getCreatedBy(), "should not overwrite existing createdBy");
        assertNotNull(entity.getCreatedTime());
    }

    @Test
    void shouldHandleNullContextGracefully() throws Throwable {
        // DataScopeContext not set — empId will be null
        TestEntity entity = new TestEntity();
        AuditFieldFiller filler = new AuditFieldFiller();

        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.INSERT);
        Executor executor = mock(Executor.class);

        Invocation invocation = new Invocation(executor,
            Executor.class.getMethod("update", MappedStatement.class, Object.class),
            new Object[]{ms, entity});

        filler.intercept(invocation);

        assertNull(entity.getCreatedBy(), "no context means null empId");
        assertNotNull(entity.getCreatedTime(), "time should still be filled");
    }

    @Test
    void shouldHandleNullParameter() throws Throwable {
        AuditFieldFiller filler = new AuditFieldFiller();
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.INSERT);
        Executor executor = mock(Executor.class);

        Invocation invocation = new Invocation(executor,
            Executor.class.getMethod("update", MappedStatement.class, Object.class),
            new Object[]{ms, null});

        // should not throw
        assertDoesNotThrow(() -> filler.intercept(invocation));
    }
}
