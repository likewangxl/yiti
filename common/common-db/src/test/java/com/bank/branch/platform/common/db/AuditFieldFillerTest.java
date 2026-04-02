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
}
