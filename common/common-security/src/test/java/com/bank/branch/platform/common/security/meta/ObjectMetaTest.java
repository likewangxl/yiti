package com.bank.branch.platform.common.security.meta;

import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ObjectMetaTest {
    @Test void validateScopeShouldPassForSupportedScope() {
        ObjectMeta meta = new ObjectMeta("LEAD", "cust_lead", "owner_org_id", "created_by",
            null, null, null, null, Set.of(DataScopeType.SELF_CREATED, DataScopeType.ALL), null);
        assertDoesNotThrow(() -> meta.validateScope(DataScopeType.SELF_CREATED));
    }
    @Test void validateScopeShouldThrowForUnsupportedScope() {
        ObjectMeta meta = new ObjectMeta("LEAD", "cust_lead", "owner_org_id", "created_by",
            null, null, null, null, Set.of(DataScopeType.SELF_CREATED), null);
        PermissionDeniedException ex = assertThrows(PermissionDeniedException.class,
            () -> meta.validateScope(DataScopeType.ALL));
        assertEquals("SCOPE_001", ex.getCode());
    }
}
