package com.bank.branch.platform.redengine.mapper;

import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.support.RedEngineMapperTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** RE_PARTY_ORG 基础 CRUD 与软删链路（真库 onepl_test_bootstrap） */
class RePartyOrgMapperIT extends RedEngineMapperTestBase {

    @Autowired
    private RePartyOrgMapper mapper;

    @Test
    void insertSelectAndLogicDelete() {
        RePartyOrg org = new RePartyOrg();
        org.setOrgName("TEST_RE_测试支部");
        org.setOrgLevel(2);
        org.setOrgCode("TEST_RE_001");
        assertEquals(1, mapper.insert(org));
        assertNotNull(org.getId());

        RePartyOrg loaded = mapper.selectById(org.getId());
        assertEquals("TEST_RE_测试支部", loaded.getOrgName());

        assertEquals(1, mapper.deleteById(org.getId()));   // @TableLogic → UPDATE deleted=1
        assertNull(mapper.selectById(org.getId()));         // 软删后查不到
    }
}
