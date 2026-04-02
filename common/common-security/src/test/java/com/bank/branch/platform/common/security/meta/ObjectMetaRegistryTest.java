package com.bank.branch.platform.common.security.meta;

import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.exception.BizException;
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
}
