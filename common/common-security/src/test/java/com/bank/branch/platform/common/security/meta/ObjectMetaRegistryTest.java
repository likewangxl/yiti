package com.bank.branch.platform.common.security.meta;

import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ObjectMetaRegistryTest {
    private ObjectMetaRegistry registry;
    private ObjectMeta leadMeta;

    @BeforeEach void setUp() {
        registry = new ObjectMetaRegistry();
        leadMeta = new ObjectMeta("LEAD", "cust_lead", "owner_org_id", "created_by",
            null, null, null, null, Set.of(DataScopeType.ALL), null);
    }
    @Test void registerAndGetShouldWork() {
        registry.register(leadMeta);
        assertTrue(registry.get("LEAD").isPresent());
    }
    @Test void duplicateRegisterShouldThrow() {
        registry.register(leadMeta);
        assertThrows(IllegalStateException.class, () -> registry.register(leadMeta));
    }
    @Test void getRequiredShouldThrowForMissing() {
        assertThrows(BizException.class, () -> registry.getRequired("NONEXIST"));
    }
    @Test void getMissingShouldReturnEmpty() {
        assertTrue(registry.get("NONEXIST").isEmpty());
    }

    @Test void getRequired_existing_shouldReturnMeta() {
        registry.register(leadMeta);
        ObjectMeta meta = registry.getRequired("LEAD");
        assertEquals("cust_lead", meta.tableName());
    }

    @Test void multipleObjectKeys_shouldCoexist() {
        ObjectMeta custMeta = new ObjectMeta("CUSTOMER", "cust_master", "owner_org_id", "created_by",
            null, null, null, null, Set.of(DataScopeType.ALL, DataScopeType.ORG_SUBTREE), null);
        registry.register(leadMeta);
        registry.register(custMeta);
        assertTrue(registry.get("LEAD").isPresent());
        assertTrue(registry.get("CUSTOMER").isPresent());
    }

    @Test void validateScope_supported_shouldNotThrow() {
        assertDoesNotThrow(() -> leadMeta.validateScope(DataScopeType.ALL));
    }

    @Test void validateScope_unsupported_shouldThrow() {
        // leadMeta only supports ALL
        assertThrows(PermissionDeniedException.class,
            () -> leadMeta.validateScope(DataScopeType.SELF_CREATED));
    }

    @Test void samePhysicalTable_differentLogicalObjects_shouldBothRegister() {
        ObjectMeta supportInitiator = new ObjectMeta("SUPPORT", "support_request", "owner_org_id",
            "created_by", null, null, "business_key", null,
            Set.of(DataScopeType.SELF_CREATED, DataScopeType.ALL), "SUPPORT");
        ObjectMeta supportDept = new ObjectMeta("SUPPORT_DEPT", "support_request", "support_dept_id",
            "created_by", "assigned_emp_id", null, "business_key", null,
            Set.of(DataScopeType.ORG, DataScopeType.SELF_ASSIGNED), "SUPPORT_DEPT");

        registry.register(supportInitiator);
        registry.register(supportDept);

        assertEquals("owner_org_id", registry.getRequired("SUPPORT").ownerOrgCol());
        assertEquals("support_dept_id", registry.getRequired("SUPPORT_DEPT").ownerOrgCol());
    }
}
