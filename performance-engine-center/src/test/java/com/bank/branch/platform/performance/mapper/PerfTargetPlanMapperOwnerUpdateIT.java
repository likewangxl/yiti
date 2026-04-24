package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.5 P6.1：PerfTargetPlanMapper.updateByIdSelective 补齐 owner 字段 &lt;if&gt; 分支 IT.
 *
 * <p>V1.4 S2 DDL 为 perf_target_plan 新增 owner_emp_id / owner_org_code 字段，
 * 但 updateByIdSelective XML 未补动态分支。此处守护"目标方案转交新 owner"场景
 * 的 selective update 行为：非 null 时更新，null 时保持原值。
 */
class PerfTargetPlanMapperOwnerUpdateIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfTargetPlanMapper mapper;

    @Test
    @DisplayName("updateByIdSelective：ownerEmpId 非 null 时被更新")
    void updateByIdSelective_updatesOwnerEmpIdWhenProvided() {
        PerfTargetPlan seed = buildSeed("TEST_P6_PLAN_001");
        mapper.insert(seed);

        PerfTargetPlan patch = new PerfTargetPlan();
        patch.setId("TEST_P6_PLAN_001");
        patch.setOwnerEmpId("NEW_OWNER_EMP");
        patch.setUpdatedBy("op");
        int affected = mapper.updateByIdSelective(patch);

        assertThat(affected).isGreaterThanOrEqualTo(1);
        PerfTargetPlan after = mapper.selectById("TEST_P6_PLAN_001");
        assertThat(after.getOwnerEmpId()).isEqualTo("NEW_OWNER_EMP");
        // 原字段保持不变
        assertThat(after.getPlanCode()).isEqualTo("TEST_P6_CODE_001");
        assertThat(after.getOwnerOrgCode()).isEqualTo("ORG_ORIGINAL");
    }

    @Test
    @DisplayName("updateByIdSelective：ownerOrgCode 非 null 时被更新")
    void updateByIdSelective_updatesOwnerOrgCodeWhenProvided() {
        PerfTargetPlan seed = buildSeed("TEST_P6_PLAN_002");
        mapper.insert(seed);

        PerfTargetPlan patch = new PerfTargetPlan();
        patch.setId("TEST_P6_PLAN_002");
        patch.setOwnerOrgCode("ORG_NEW");
        patch.setUpdatedBy("op");
        mapper.updateByIdSelective(patch);

        PerfTargetPlan after = mapper.selectById("TEST_P6_PLAN_002");
        assertThat(after.getOwnerOrgCode()).isEqualTo("ORG_NEW");
        assertThat(after.getOwnerEmpId()).isEqualTo("EMP_ORIGINAL");
    }

    @Test
    @DisplayName("updateByIdSelective：owner 字段 null 时保持原值不变")
    void updateByIdSelective_keepsOwnerWhenNullInPatch() {
        PerfTargetPlan seed = buildSeed("TEST_P6_PLAN_003");
        mapper.insert(seed);

        PerfTargetPlan patch = new PerfTargetPlan();
        patch.setId("TEST_P6_PLAN_003");
        patch.setPlanName("NEW_NAME"); // 只改 planName
        patch.setUpdatedBy("op");
        mapper.updateByIdSelective(patch);

        PerfTargetPlan after = mapper.selectById("TEST_P6_PLAN_003");
        assertThat(after.getPlanName()).isEqualTo("NEW_NAME");
        assertThat(after.getOwnerEmpId()).isEqualTo("EMP_ORIGINAL");
        assertThat(after.getOwnerOrgCode()).isEqualTo("ORG_ORIGINAL");
    }

    private PerfTargetPlan buildSeed(String id) {
        PerfTargetPlan p = new PerfTargetPlan();
        p.setId(id);
        p.setPlanCode("TEST_P6_CODE_" + id.substring(id.length() - 3));
        p.setPlanName("ORIGINAL_NAME");
        p.setKpiSchemeId("S_TEST");
        p.setTargetDim("EMP");
        p.setTargetCycle("MONTHLY");
        p.setEffectiveDate(LocalDate.of(2026, 1, 1));
        p.setStatus("DRAFT");
        p.setOwnerEmpId("EMP_ORIGINAL");
        p.setOwnerOrgCode("ORG_ORIGINAL");
        p.setCreatedBy("init");
        p.setCreatedTime(LocalDateTime.now());
        p.setUpdatedBy("init");
        p.setUpdatedTime(LocalDateTime.now());
        return p;
    }
}
