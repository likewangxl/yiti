package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfTargetAdjustApply;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PerfTargetAdjustApplyMapper 集成测试 (V1.2 Q3.1).
 *
 * <p>对齐生产 DDL §17 perf_target_adjust_apply：
 * <ul>
 *   <li>字段：id / plan_id / subject_type(EMP/ORG) / subject_id / cycle_key /
 *       status(DRAFT/IN_APPROVAL/APPROVED/REJECTED) / business_key / process_instance_id /
 *       owner_org_id / remark(text) / created_by/time + updated_by/time</li>
 *   <li>不存在 metric_code / old_target_value / new_target_value 字段，目标值修改建议
 *       通过 remark JSON 结构承载</li>
 * </ul>
 *
 * <p>覆盖方法：
 * <ul>
 *   <li>insert / selectById / selectByBusinessKey</li>
 *   <li>updateStatus(id, status, processInstanceId)</li>
 *   <li>selectByConditions(status/planId/subjectType/subjectId/ownerOrgId/createdBy, page)</li>
 *   <li>countByConditions(同条件)</li>
 * </ul>
 *
 * <p>测试前缀：{@code TEST_TAA_*}，避免与 Q2 {@code TEST_AA_*} 及 V1.0 {@code TEST_TGT_*} 冲突.
 */
class PerfTargetAdjustApplyMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfTargetAdjustApplyMapper mapper;

    /**
     * 构造测试申请对象（以 TEST_TAA_ 前缀避免污染）.
     */
    private PerfTargetAdjustApply newApply(String suffix, String status,
                                           String subjectType, String subjectId) {
        PerfTargetAdjustApply a = new PerfTargetAdjustApply();
        a.setId("TEST_TAA_" + suffix);
        a.setPlanId("TEST_TAA_PLAN_" + suffix);
        a.setSubjectType(subjectType);
        a.setSubjectId(subjectId);
        a.setCycleKey("2026Q1");
        a.setStatus(status);
        a.setBusinessKey("TARGET_ADJUST:TEST_TAA_" + suffix);
        a.setProcessInstanceId(null);
        a.setOwnerOrgId("ORG_TEST");
        a.setRemark("{\"adjustments\":[{\"metricCode\":\"M_DEP_BAL\",\"oldValue\":100,\"newValue\":120}],\"reason\":\"Q3.1 单元测试\"}");
        a.setCreatedBy("admin");
        a.setCreatedTime(LocalDateTime.now());
        a.setUpdatedBy("admin");
        a.setUpdatedTime(LocalDateTime.now());
        return a;
    }

    @Test
    @DisplayName("insert + selectById 回读一致，含 remark JSON")
    void insert_and_selectById_ok() {
        PerfTargetAdjustApply apply = newApply("INS_" + UUID.randomUUID().toString().substring(0, 8),
                "IN_APPROVAL", "EMP", "EMP_001");
        int rows = mapper.insert(apply);
        assertThat(rows).isEqualTo(1);

        PerfTargetAdjustApply loaded = mapper.selectById(apply.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getPlanId()).isEqualTo(apply.getPlanId());
        assertThat(loaded.getSubjectType()).isEqualTo("EMP");
        assertThat(loaded.getSubjectId()).isEqualTo("EMP_001");
        assertThat(loaded.getCycleKey()).isEqualTo("2026Q1");
        assertThat(loaded.getStatus()).isEqualTo("IN_APPROVAL");
        assertThat(loaded.getOwnerOrgId()).isEqualTo("ORG_TEST");
        assertThat(loaded.getRemark()).contains("M_DEP_BAL");
        assertThat(loaded.getRemark()).contains("adjustments");
    }

    @Test
    @DisplayName("selectById 不存在时返回 null")
    void selectById_whenNotFound_returnsNull() {
        assertThat(mapper.selectById("NO_SUCH_TAA_APPLY_ID")).isNull();
    }

    @Test
    @DisplayName("selectByBusinessKey 回查")
    void selectByBusinessKey_ok() {
        PerfTargetAdjustApply apply = newApply("BK_" + UUID.randomUUID().toString().substring(0, 8),
                "IN_APPROVAL", "ORG", "ORG_101");
        mapper.insert(apply);
        PerfTargetAdjustApply loaded = mapper.selectByBusinessKey(apply.getBusinessKey());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getId()).isEqualTo(apply.getId());
        assertThat(loaded.getSubjectType()).isEqualTo("ORG");
    }

    @Test
    @DisplayName("updateStatus 更新状态 + processInstanceId；null 时不覆写流程 ID")
    void updateStatus_ok() {
        PerfTargetAdjustApply apply = newApply("UPD_" + UUID.randomUUID().toString().substring(0, 8),
                "DRAFT", "EMP", "EMP_003");
        mapper.insert(apply);

        // 第一次：状态 + processInstanceId 同时写入
        int rows = mapper.updateStatus(apply.getId(), "IN_APPROVAL", "PI_" + apply.getId());
        assertThat(rows).isEqualTo(1);
        PerfTargetAdjustApply loaded = mapper.selectById(apply.getId());
        assertThat(loaded.getStatus()).isEqualTo("IN_APPROVAL");
        assertThat(loaded.getProcessInstanceId()).isEqualTo("PI_" + apply.getId());

        // 第二次：仅更新状态，processInstanceId 传 null，不应覆写历史值
        int rows2 = mapper.updateStatus(apply.getId(), "APPROVED", null);
        assertThat(rows2).isEqualTo(1);
        PerfTargetAdjustApply reloaded = mapper.selectById(apply.getId());
        assertThat(reloaded.getStatus()).isEqualTo("APPROVED");
        assertThat(reloaded.getProcessInstanceId()).isEqualTo("PI_" + apply.getId());
    }

    @Test
    @DisplayName("selectByConditions 支持 status / planId / subjectType / subjectId 过滤")
    void selectByConditions_withFilters_ok() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        // EMP_001 IN_APPROVAL
        PerfTargetAdjustApply a1 = newApply("CND_1_" + marker, "IN_APPROVAL", "EMP", "EMP_001");
        a1.setPlanId("PLAN_ALPHA_" + marker);
        mapper.insert(a1);
        // EMP_002 APPROVED
        PerfTargetAdjustApply a2 = newApply("CND_2_" + marker, "APPROVED", "EMP", "EMP_002");
        a2.setPlanId("PLAN_ALPHA_" + marker);
        mapper.insert(a2);
        // ORG_X IN_APPROVAL
        PerfTargetAdjustApply a3 = newApply("CND_3_" + marker, "IN_APPROVAL", "ORG", "ORG_X");
        a3.setPlanId("PLAN_BETA_" + marker);
        mapper.insert(a3);

        // 按 planId + status 过滤
        List<PerfTargetAdjustApply> alphaInApproval = mapper.selectByConditions(
                "IN_APPROVAL", "PLAN_ALPHA_" + marker, null, null, null, null, 0, 100);
        assertThat(alphaInApproval).extracting(PerfTargetAdjustApply::getId)
                .contains("TEST_TAA_CND_1_" + marker)
                .doesNotContain("TEST_TAA_CND_2_" + marker, "TEST_TAA_CND_3_" + marker);

        // 按 subjectType=EMP 过滤
        List<PerfTargetAdjustApply> empOnly = mapper.selectByConditions(
                null, null, "EMP", null, null, null, 0, 100);
        assertThat(empOnly).extracting(PerfTargetAdjustApply::getId)
                .contains("TEST_TAA_CND_1_" + marker, "TEST_TAA_CND_2_" + marker)
                .doesNotContain("TEST_TAA_CND_3_" + marker);

        // count 对齐
        long count = mapper.countByConditions(
                "IN_APPROVAL", "PLAN_ALPHA_" + marker, null, null, null, null);
        assertThat(count).isEqualTo(alphaInApproval.size());
    }

    @Test
    @DisplayName("selectByConditions 按 ownerOrgId 过滤（数据范围维度）")
    void selectByConditions_byOwnerOrg_ok() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        PerfTargetAdjustApply a = newApply("ORG_A_" + marker, "IN_APPROVAL", "EMP", "EMP_A");
        a.setOwnerOrgId("ORG_A_TAA_ONLY");
        PerfTargetAdjustApply b = newApply("ORG_B_" + marker, "IN_APPROVAL", "EMP", "EMP_B");
        b.setOwnerOrgId("ORG_B_TAA_ONLY");
        mapper.insert(a);
        mapper.insert(b);

        List<PerfTargetAdjustApply> orgA = mapper.selectByConditions(
                null, null, null, null, "ORG_A_TAA_ONLY", null, 0, 100);
        assertThat(orgA).extracting(PerfTargetAdjustApply::getId)
                .contains("TEST_TAA_ORG_A_" + marker)
                .doesNotContain("TEST_TAA_ORG_B_" + marker);
    }
}
