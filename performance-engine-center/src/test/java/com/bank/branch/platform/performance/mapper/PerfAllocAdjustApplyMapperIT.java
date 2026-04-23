package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PerfAllocAdjustApplyMapper 集成测试 (V1.2 Q2.1).
 *
 * <p>对齐生产 DDL：
 * <ul>
 *   <li>字段：id / apply_no(UK) / cust_id / alloc_dim / biz_kind / account_no /
 *       status(DRAFT/IN_APPROVAL/APPROVED/REJECTED) / business_key / process_instance_id /
 *       owner_org_id / remark / created_by/time + updated_by/time</li>
 *   <li>对公/零售通过 biz_kind 判定，不存在 adjust_type 字段</li>
 * </ul>
 *
 * <p>覆盖方法：
 * <ul>
 *   <li>insert / selectById / selectByApplyNo / selectByBusinessKey</li>
 *   <li>updateStatus(id, status, processInstanceId)</li>
 *   <li>selectByConditions(status/bizKind/custId/ownerOrgId/createdBy, page)</li>
 *   <li>countByConditions(同条件)</li>
 * </ul>
 */
class PerfAllocAdjustApplyMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfAllocAdjustApplyMapper mapper;

    /**
     * 构造测试申请对象，使用 TEST_AA_ 前缀避免污染.
     */
    private PerfAllocAdjustApply newApply(String suffix, String status, String bizKind) {
        PerfAllocAdjustApply a = new PerfAllocAdjustApply();
        a.setId("TEST_AA_" + suffix);
        a.setApplyNo("AA_TEST_" + suffix);
        a.setCustId("TEST_AA_CUST_" + suffix);
        a.setAllocDim("RULE");
        a.setBizKind(bizKind);
        a.setAccountNo(null);
        a.setStatus(status);
        a.setBusinessKey("ALLOC_ADJUST:TEST_AA_" + suffix);
        a.setProcessInstanceId(null);
        a.setOwnerOrgId("ORG_TEST");
        a.setRemark("Q2 单元测试 " + suffix);
        a.setCreatedBy("admin");
        a.setCreatedTime(LocalDateTime.now());
        a.setUpdatedBy("admin");
        a.setUpdatedTime(LocalDateTime.now());
        return a;
    }

    @Test
    @DisplayName("insert + selectById 回读一致")
    void insert_and_selectById_ok() {
        PerfAllocAdjustApply apply = newApply("INS_" + UUID.randomUUID().toString().substring(0, 8),
                "IN_APPROVAL", "CORP_LOAN");
        int rows = mapper.insert(apply);
        assertThat(rows).isEqualTo(1);

        PerfAllocAdjustApply loaded = mapper.selectById(apply.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getApplyNo()).isEqualTo(apply.getApplyNo());
        assertThat(loaded.getCustId()).isEqualTo(apply.getCustId());
        assertThat(loaded.getAllocDim()).isEqualTo("RULE");
        assertThat(loaded.getBizKind()).isEqualTo("CORP_LOAN");
        assertThat(loaded.getStatus()).isEqualTo("IN_APPROVAL");
        assertThat(loaded.getOwnerOrgId()).isEqualTo("ORG_TEST");
    }

    @Test
    @DisplayName("selectById 不存在时返回 null")
    void selectById_whenNotFound_returnsNull() {
        assertThat(mapper.selectById("NO_SUCH_APPLY_ID")).isNull();
    }

    @Test
    @DisplayName("selectByApplyNo UK 定位")
    void selectByApplyNo_ok() {
        PerfAllocAdjustApply apply = newApply("NO_" + UUID.randomUUID().toString().substring(0, 8),
                "DRAFT", "RETAIL_CARD");
        mapper.insert(apply);
        PerfAllocAdjustApply loaded = mapper.selectByApplyNo(apply.getApplyNo());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getId()).isEqualTo(apply.getId());
    }

    @Test
    @DisplayName("selectByBusinessKey 回查")
    void selectByBusinessKey_ok() {
        PerfAllocAdjustApply apply = newApply("BK_" + UUID.randomUUID().toString().substring(0, 8),
                "IN_APPROVAL", "CORP_LOAN");
        mapper.insert(apply);
        PerfAllocAdjustApply loaded = mapper.selectByBusinessKey(apply.getBusinessKey());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getId()).isEqualTo(apply.getId());
    }

    @Test
    @DisplayName("updateStatus 更新状态 + processInstanceId")
    void updateStatus_ok() {
        PerfAllocAdjustApply apply = newApply("UPD_" + UUID.randomUUID().toString().substring(0, 8),
                "DRAFT", "CORP_LOAN");
        mapper.insert(apply);

        int rows = mapper.updateStatus(apply.getId(), "IN_APPROVAL", "PI_" + apply.getId());
        assertThat(rows).isEqualTo(1);

        PerfAllocAdjustApply loaded = mapper.selectById(apply.getId());
        assertThat(loaded.getStatus()).isEqualTo("IN_APPROVAL");
        assertThat(loaded.getProcessInstanceId()).isEqualTo("PI_" + apply.getId());
    }

    @Test
    @DisplayName("selectByConditions 支持 status / bizKind / custId / createdBy 过滤 + 分页")
    void selectByConditions_withFilters_ok() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        // CORP_LOAN, IN_APPROVAL
        mapper.insert(newApply("CND_1_" + marker, "IN_APPROVAL", "CORP_LOAN"));
        // RETAIL_CARD, IN_APPROVAL
        mapper.insert(newApply("CND_2_" + marker, "IN_APPROVAL", "RETAIL_CARD"));
        // CORP_LOAN, APPROVED
        PerfAllocAdjustApply a3 = newApply("CND_3_" + marker, "APPROVED", "CORP_LOAN");
        mapper.insert(a3);

        List<PerfAllocAdjustApply> corpInApproval = mapper.selectByConditions(
                "IN_APPROVAL", "CORP_LOAN", null, null, null, 0, 100);
        assertThat(corpInApproval).extracting(PerfAllocAdjustApply::getId)
                .contains("TEST_AA_CND_1_" + marker)
                .doesNotContain("TEST_AA_CND_2_" + marker, "TEST_AA_CND_3_" + marker);

        long count = mapper.countByConditions("IN_APPROVAL", "CORP_LOAN",
                null, null, null);
        assertThat(count).isEqualTo(corpInApproval.size());
    }

    @Test
    @DisplayName("selectByConditions 按 ownerOrgId 过滤")
    void selectByConditions_byOwnerOrg_ok() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        PerfAllocAdjustApply a = newApply("ORG_A_" + marker, "IN_APPROVAL", "CORP_LOAN");
        a.setOwnerOrgId("ORG_A_ONLY");
        PerfAllocAdjustApply b = newApply("ORG_B_" + marker, "IN_APPROVAL", "CORP_LOAN");
        b.setOwnerOrgId("ORG_B_ONLY");
        mapper.insert(a);
        mapper.insert(b);

        List<PerfAllocAdjustApply> orgA = mapper.selectByConditions(
                null, null, null, "ORG_A_ONLY", null, 0, 100);
        assertThat(orgA).extracting(PerfAllocAdjustApply::getId)
                .contains("TEST_AA_ORG_A_" + marker)
                .doesNotContain("TEST_AA_ORG_B_" + marker);
    }
}
