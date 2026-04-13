package com.bank.branch.platform.portal.adapter;

import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeAdapterTest {

    @Test
    void shouldReturnNullWhenInputIsNull() {
        assertThat(DataScopeAdapter.fromAuthRecord(null)).isNull();
    }

    @Test
    void shouldMapAllFieldsFromAuthRecordToCommonPojo() {
        var record = new com.bank.branch.platform.auth.api.dto.DataScopeContext(
            DataScopeType.ORG_SUBTREE,
            "E10001",
            "ORG_SZ_001",
            Set.of("ORG_SZ_001", "ORG_SZ_002"),
            BizType.PRODUCT,
            BizAction.LIST
        );

        var pojo = DataScopeAdapter.fromAuthRecord(record);

        assertThat(pojo).isNotNull();
        assertThat(pojo.getScope()).isEqualTo(DataScopeType.ORG_SUBTREE);
        assertThat(pojo.getEmpId()).isEqualTo("E10001");
        assertThat(pojo.getOrgCode()).isEqualTo("ORG_SZ_001");
        assertThat(pojo.getOrgSubtreeCodes()).containsExactlyInAnyOrder("ORG_SZ_001", "ORG_SZ_002");
        assertThat(pojo.getBizType()).isEqualTo(BizType.PRODUCT);
        assertThat(pojo.getAction()).isEqualTo(BizAction.LIST);
    }
}
