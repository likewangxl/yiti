package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import com.bank.branch.platform.performance.support.TargetTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PerfTargetPlanMapper 集成测试.
 *
 * <p>覆盖 DoD 硬性场景：
 * <ul>
 *   <li>insert_whenPlanCodeDup_throwsDuplicateKey：uk_plan_code 唯一键冲突</li>
 *   <li>updateByIdSelective_whenPatchCreatedFields_ignored：created_by/created_time 不可变</li>
 * </ul>
 * 以及基础路径：insert/selectById / selectByPlanCode / selectByCondition / countByCondition /
 * updateByIdSelective / updateStatusById / deleteById。
 */
class PerfTargetPlanMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfTargetPlanMapper mapper;

    private String fakeKpiSchemeId() {
        // IT 不做 FK, 用随机 ID 即可接近真实路径; 真实 FK 由 Service 层校验 (Task 3.2).
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    @DisplayName("insert 后可按 id 查回目标方案")
    void insertAndSelectById_ok() {
        PerfTargetPlan plan = TargetTestDataBuilder.plan("SEL_001", fakeKpiSchemeId());

        mapper.insert(plan);
        PerfTargetPlan loaded = mapper.selectById(plan.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getPlanCode()).isEqualTo("TEST_TGT_SEL_001");
        assertThat(loaded.getPlanName()).isEqualTo("测试目标方案-SEL_001");
        assertThat(loaded.getTargetDim()).isEqualTo("EMP");
        assertThat(loaded.getTargetCycle()).isEqualTo("YEAR");
        assertThat(loaded.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("DoD: 重复 plan_code 触发唯一键冲突")
    void insert_whenPlanCodeDup_throwsDuplicateKey() {
        PerfTargetPlan first = TargetTestDataBuilder.plan("DUP_CODE", fakeKpiSchemeId());
        mapper.insert(first);
        // 同 plan_code 不同 id, 触发 uk_plan_code
        PerfTargetPlan dup = TargetTestDataBuilder.plan("DUP_CODE", fakeKpiSchemeId());

        assertThatThrownBy(() -> mapper.insert(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("selectByPlanCode 不存在时返回 null")
    void selectByPlanCode_whenNotExists_returnsNull() {
        assertThat(mapper.selectByPlanCode("TEST_TGT_NO_SUCH")).isNull();
    }

    @Test
    @DisplayName("selectByPlanCode 存在时可查回对应方案")
    void selectByPlanCode_whenExists_ok() {
        PerfTargetPlan plan = TargetTestDataBuilder.plan("BY_CODE", fakeKpiSchemeId());
        mapper.insert(plan);

        PerfTargetPlan loaded = mapper.selectByPlanCode("TEST_TGT_BY_CODE");

        assertThat(loaded).isNotNull();
        assertThat(loaded.getId()).isEqualTo(plan.getId());
    }

    @Test
    @DisplayName("selectByCondition 关键字可匹配编码或名称")
    void selectByCondition_withKeyword_matchesCodeOrName() {
        String kpiSchemeId = fakeKpiSchemeId();
        mapper.insert(TargetTestDataBuilder.plan("KW_A", kpiSchemeId));
        mapper.insert(TargetTestDataBuilder.plan("KW_B", kpiSchemeId));

        List<PerfTargetPlan> list = mapper.selectByCondition(kpiSchemeId, "ACTIVE", "KW_", 0, 10);

        assertThat(list).extracting(PerfTargetPlan::getPlanCode)
                .contains("TEST_TGT_KW_A", "TEST_TGT_KW_B");
    }

    @Test
    @DisplayName("countByCondition 与条件查询保持一致")
    void countByCondition_filtersSameAsSelect() {
        String kpiSchemeId = fakeKpiSchemeId();
        mapper.insert(TargetTestDataBuilder.plan("CNT_A", kpiSchemeId));
        mapper.insert(TargetTestDataBuilder.plan("CNT_B", kpiSchemeId));
        PerfTargetPlan disabled = TargetTestDataBuilder.plan("CNT_C", kpiSchemeId);
        disabled.setStatus("DISABLED");
        mapper.insert(disabled);

        long count = mapper.countByCondition(kpiSchemeId, "ACTIVE", "CNT_");

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("updateStatusById 会更新状态和更新人")
    void updateStatusById_changesStatus() {
        PerfTargetPlan plan = TargetTestDataBuilder.plan("ST_T", fakeKpiSchemeId());
        mapper.insert(plan);

        int rows = mapper.updateStatusById(plan.getId(), "DISABLED", "test-admin");

        assertThat(rows).isEqualTo(1);
        PerfTargetPlan loaded = mapper.selectById(plan.getId());
        assertThat(loaded.getStatus()).isEqualTo("DISABLED");
        assertThat(loaded.getUpdatedBy()).isEqualTo("test-admin");
    }

    @Test
    @DisplayName("updateByIdSelective 只更新非空字段")
    void updateByIdSelective_onlyUpdatesNonNullFields() {
        PerfTargetPlan plan = TargetTestDataBuilder.plan("UPD_T", fakeKpiSchemeId());
        mapper.insert(plan);

        PerfTargetPlan patch = new PerfTargetPlan();
        patch.setId(plan.getId());
        patch.setPlanName("新名称");
        patch.setUpdatedBy("patcher");
        int rows = mapper.updateByIdSelective(patch);

        assertThat(rows).isEqualTo(1);
        PerfTargetPlan loaded = mapper.selectById(plan.getId());
        assertThat(loaded.getPlanName()).isEqualTo("新名称");
        assertThat(loaded.getPlanCode()).isEqualTo("TEST_TGT_UPD_T");
        assertThat(loaded.getUpdatedBy()).isEqualTo("patcher");
    }

    @Test
    @DisplayName("updateByIdSelective 忽略 patch 的 created_by/created_time (创建字段不可变)")
    void updateByIdSelective_whenPatchCreatedFields_ignored() {
        // 创建字段必须不可变, XML 刻意不为 created_by/created_time 提供 <if> 分支.
        // 即便调用方误传, 这里也应保持原始值不变.
        PerfTargetPlan plan = TargetTestDataBuilder.plan("CF_IGN", fakeKpiSchemeId());
        mapper.insert(plan);
        // 以数据库侧视角取回 created_by/created_time (datetime 列无纳秒精度, 经过一次往返后才能稳定比较)
        PerfTargetPlan beforePatch = mapper.selectById(plan.getId());
        String origCreatedBy = beforePatch.getCreatedBy();
        LocalDateTime origCreatedTime = beforePatch.getCreatedTime();

        PerfTargetPlan patch = new PerfTargetPlan();
        patch.setId(plan.getId());
        patch.setPlanName("新名称"); // 非 id 字段至少一项, 避免空 <set>
        patch.setCreatedBy("hacker");
        patch.setCreatedTime(LocalDateTime.now().plusDays(1));
        int rows = mapper.updateByIdSelective(patch);

        assertThat(rows).isEqualTo(1);
        PerfTargetPlan loaded = mapper.selectById(plan.getId());
        assertThat(loaded.getPlanName()).isEqualTo("新名称");
        // created_by/created_time 应保持 insert 时的值, 不被 patch 覆盖
        assertThat(loaded.getCreatedBy()).isEqualTo(origCreatedBy);
        assertThat(loaded.getCreatedBy()).isNotEqualTo("hacker");
        assertThat(loaded.getCreatedTime()).isEqualTo(origCreatedTime);
    }

    @Test
    @DisplayName("deleteById 可删除方案")
    void deleteById_ok() {
        PerfTargetPlan plan = TargetTestDataBuilder.plan("DEL_T", fakeKpiSchemeId());
        mapper.insert(plan);

        int rows = mapper.deleteById(plan.getId());

        assertThat(rows).isEqualTo(1);
        assertThat(mapper.selectById(plan.getId())).isNull();
    }

    // =====================================================================
    // V1.4 S2.2: owner_emp_id / owner_org_code 字段 CRUD 守护
    // =====================================================================

    @Test
    @DisplayName("V1.4 S2.2: insert 带 owner 字段 + selectById 往返校验")
    void insert_andSelectById_roundtrips_ownerFields() {
        PerfTargetPlan plan = TargetTestDataBuilder.plan("OWN_RT", fakeKpiSchemeId());
        plan.setOwnerEmpId("USER_OWN_A");
        plan.setOwnerOrgCode("BRANCH_OWN_01");
        mapper.insert(plan);

        PerfTargetPlan loaded = mapper.selectById(plan.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getOwnerEmpId())
            .as("owner_emp_id 应 roundtrip")
            .isEqualTo("USER_OWN_A");
        assertThat(loaded.getOwnerOrgCode())
            .as("owner_org_code 应 roundtrip")
            .isEqualTo("BRANCH_OWN_01");
    }

    @Test
    @DisplayName("V1.4 S2.2: selectByConditionWithScope 基于 owner_emp_id 片段过滤返回匹配方案")
    void selectByConditionWithScope_filtersByOwnerEmpId() {
        String kpiSchemeId = fakeKpiSchemeId();
        PerfTargetPlan matched = TargetTestDataBuilder.plan("OWN_SCP_A", kpiSchemeId);
        matched.setOwnerEmpId("USER_SCP_HIT");
        matched.setOwnerOrgCode("BRANCH_SCP_01");
        mapper.insert(matched);

        PerfTargetPlan notMatched = TargetTestDataBuilder.plan("OWN_SCP_B", kpiSchemeId);
        notMatched.setOwnerEmpId("USER_SCP_MISS");
        notMatched.setOwnerOrgCode("BRANCH_SCP_02");
        mapper.insert(notMatched);

        java.util.Map<String, Object> scopeParams = new java.util.HashMap<>();
        scopeParams.put("ownerEmpId", "USER_SCP_HIT");
        List<PerfTargetPlan> list = mapper.selectByConditionWithScope(
                kpiSchemeId, null, "OWN_SCP_",
                0, 10,
                "owner_emp_id = #{scopeParams.ownerEmpId}", scopeParams);

        assertThat(list).extracting(PerfTargetPlan::getPlanCode)
                .containsExactly("TEST_TGT_OWN_SCP_A");
    }
}
