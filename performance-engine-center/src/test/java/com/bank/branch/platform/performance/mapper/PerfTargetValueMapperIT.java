package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import com.bank.branch.platform.performance.support.TargetTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PerfTargetValueMapper 集成测试.
 *
 * <p>核心覆盖 upsertBatch 三个场景（DoD 硬性）：
 * <ul>
 *   <li>upsert_newRow_inserts：全新 UK → 插入, selectByUniqueKey 拿到值</li>
 *   <li>upsert_existingRow_updates：同 UK 再次 upsert → 仅更新字段, 行数仍 1</li>
 *   <li>upsertBatch_mix_newAndExisting_ok：批内混合新旧 UK → 各自处理正确</li>
 * </ul>
 * 以及基础路径：selectByUniqueKey / selectById / listByPlan / countByPlan / deleteByPlanId。
 */
class PerfTargetValueMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfTargetValueMapper mapper;

    private String randomPlanId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    @DisplayName("upsertBatch 全新 UK 时插入单行, 可按 UK 查回")
    void upsert_newRow_inserts() {
        String planId = randomPlanId();
        PerfTargetValue v = TargetTestDataBuilder.value(
                planId, "EMP", "E001", "2026", "TEST_TGT_METRIC_A", new BigDecimal("100.0000"));

        int rows = mapper.upsertBatch(Collections.singletonList(v));

        // MySQL upsert: 新插入记 1
        assertThat(rows).isEqualTo(1);
        PerfTargetValue loaded = mapper.selectByUniqueKey(
                planId, "EMP", "E001", "2026", "TEST_TGT_METRIC_A");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getTargetValue()).isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(loaded.getCreatedBy()).isEqualTo("test");
    }

    @Test
    @DisplayName("upsertBatch 同 UK 再次写入时更新 target_value, 仅保留 1 行")
    void upsert_existingRow_updates() {
        String planId = randomPlanId();
        // 首次 upsert
        PerfTargetValue a = TargetTestDataBuilder.value(
                planId, "EMP", "E002", "2026", "TEST_TGT_METRIC_B", new BigDecimal("100.0000"));
        mapper.upsertBatch(Collections.singletonList(a));

        // 构造同 UK 但不同 id / 不同 target 的第二条
        PerfTargetValue b = TargetTestDataBuilder.value(
                planId, "EMP", "E002", "2026", "TEST_TGT_METRIC_B", new BigDecimal("200.0000"));
        mapper.upsertBatch(Collections.singletonList(b));

        // 查回验证: target_value 已更新为 200, 总数仍 1
        PerfTargetValue loaded = mapper.selectByUniqueKey(
                planId, "EMP", "E002", "2026", "TEST_TGT_METRIC_B");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getTargetValue()).isEqualByComparingTo(new BigDecimal("200.0000"));

        long count = mapper.countByPlan(planId, null, null, null);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("upsertBatch 混合新旧 UK 时各自处理正确")
    void upsertBatch_mix_newAndExisting_ok() {
        String planId = randomPlanId();
        // 预插 1 条
        PerfTargetValue pre = TargetTestDataBuilder.value(
                planId, "EMP", "E003", "2026", "TEST_TGT_METRIC_C", new BigDecimal("100.0000"));
        mapper.upsertBatch(Collections.singletonList(pre));

        // 批内: 1 条同 UK 更新 (target=300), 1 条新 UK 插入
        PerfTargetValue updateHit = TargetTestDataBuilder.value(
                planId, "EMP", "E003", "2026", "TEST_TGT_METRIC_C", new BigDecimal("300.0000"));
        PerfTargetValue newRow = TargetTestDataBuilder.value(
                planId, "EMP", "E004", "2026", "TEST_TGT_METRIC_D", new BigDecimal("500.0000"));
        mapper.upsertBatch(Arrays.asList(updateHit, newRow));

        // 最终 2 行, 各自值对
        long count = mapper.countByPlan(planId, null, null, null);
        assertThat(count).isEqualTo(2);

        PerfTargetValue updated = mapper.selectByUniqueKey(
                planId, "EMP", "E003", "2026", "TEST_TGT_METRIC_C");
        assertThat(updated.getTargetValue()).isEqualByComparingTo(new BigDecimal("300.0000"));

        PerfTargetValue inserted = mapper.selectByUniqueKey(
                planId, "EMP", "E004", "2026", "TEST_TGT_METRIC_D");
        assertThat(inserted.getTargetValue()).isEqualByComparingTo(new BigDecimal("500.0000"));
    }

    @Test
    @DisplayName("selectByUniqueKey 不存在时返回 null")
    void selectByUniqueKey_whenNotExists_returnsNull() {
        assertThat(mapper.selectByUniqueKey(
                randomPlanId(), "EMP", "NO_SUCH", "2026", "TEST_TGT_NO_METRIC")).isNull();
    }

    @Test
    @DisplayName("listByPlan 可按 subjectType/subjectId/cycleKey 过滤")
    void listByPlan_withFilters_returnsFiltered() {
        String planId = randomPlanId();
        mapper.upsertBatch(Arrays.asList(
                TargetTestDataBuilder.value(planId, "EMP", "E100", "2026", "TEST_TGT_M1", new BigDecimal("10")),
                TargetTestDataBuilder.value(planId, "EMP", "E100", "2026Q1", "TEST_TGT_M1", new BigDecimal("20")),
                TargetTestDataBuilder.value(planId, "EMP", "E101", "2026", "TEST_TGT_M1", new BigDecimal("30")),
                TargetTestDataBuilder.value(planId, "ORG", "O200", "2026", "TEST_TGT_M1", new BigDecimal("40"))
        ));

        // 无过滤: 4 条
        List<PerfTargetValue> all = mapper.listByPlan(planId, null, null, null, 0, 10);
        assertThat(all).hasSize(4);

        // 只看 EMP
        List<PerfTargetValue> emp = mapper.listByPlan(planId, "EMP", null, null, 0, 10);
        assertThat(emp).hasSize(3);

        // EMP + E100 (两个 cycle)
        List<PerfTargetValue> e100 = mapper.listByPlan(planId, "EMP", "E100", null, 0, 10);
        assertThat(e100).hasSize(2);

        // EMP + E100 + 2026
        List<PerfTargetValue> e100Year = mapper.listByPlan(planId, "EMP", "E100", "2026", 0, 10);
        assertThat(e100Year).hasSize(1);
        assertThat(e100Year.get(0).getTargetValue()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    @DisplayName("countByPlan 与 listByPlan 过滤条件一致")
    void countByPlan_filtersSameAsList() {
        String planId = randomPlanId();
        mapper.upsertBatch(Arrays.asList(
                TargetTestDataBuilder.value(planId, "EMP", "E200", "2026", "TEST_TGT_MX", new BigDecimal("1")),
                TargetTestDataBuilder.value(planId, "EMP", "E201", "2026", "TEST_TGT_MX", new BigDecimal("2")),
                TargetTestDataBuilder.value(planId, "ORG", "O300", "2026", "TEST_TGT_MX", new BigDecimal("3"))
        ));

        assertThat(mapper.countByPlan(planId, null, null, null)).isEqualTo(3);
        assertThat(mapper.countByPlan(planId, "EMP", null, null)).isEqualTo(2);
        assertThat(mapper.countByPlan(planId, "ORG", null, null)).isEqualTo(1);
    }

    @Test
    @DisplayName("selectById 可按主键查回")
    void selectById_ok() {
        String planId = randomPlanId();
        PerfTargetValue v = TargetTestDataBuilder.value(
                planId, "EMP", "E999", "2026", "TEST_TGT_BYID", new BigDecimal("42"));
        mapper.upsertBatch(Collections.singletonList(v));

        PerfTargetValue loaded = mapper.selectById(v.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getTargetValue()).isEqualByComparingTo(new BigDecimal("42"));
    }

    @Test
    @DisplayName("deleteByPlanId 级联删除该方案下所有目标值")
    void deleteByPlanId_cascadesAllValues() {
        String planId = randomPlanId();
        mapper.upsertBatch(Arrays.asList(
                TargetTestDataBuilder.value(planId, "EMP", "E1", "2026", "TEST_TGT_DEL_1", new BigDecimal("1")),
                TargetTestDataBuilder.value(planId, "EMP", "E2", "2026", "TEST_TGT_DEL_2", new BigDecimal("2")),
                TargetTestDataBuilder.value(planId, "ORG", "O1", "2026", "TEST_TGT_DEL_3", new BigDecimal("3"))
        ));
        assertThat(mapper.countByPlan(planId, null, null, null)).isEqualTo(3);

        int rows = mapper.deleteByPlanId(planId);

        assertThat(rows).isEqualTo(3);
        assertThat(mapper.countByPlan(planId, null, null, null)).isEqualTo(0);
    }

    // =====================================================================
    // V1.4 S2.2: owner_emp_id / owner_org_code 字段 CRUD 守护
    // =====================================================================

    @Test
    @DisplayName("V1.4 S2.2: upsertBatch 带 owner 字段 + selectByUniqueKey 往返校验")
    void upsert_andSelectByUk_roundtrips_ownerFields() {
        String planId = randomPlanId();
        PerfTargetValue v = TargetTestDataBuilder.value(
                planId, "EMP", "E_OWN_RT", "2026", "TEST_TGT_METRIC_OWN", new BigDecimal("100.0000"));
        v.setOwnerEmpId("USER_VAL_OWN_A");
        v.setOwnerOrgCode("BRANCH_VAL_OWN_01");

        mapper.upsertBatch(Collections.singletonList(v));

        PerfTargetValue loaded = mapper.selectByUniqueKey(
                planId, "EMP", "E_OWN_RT", "2026", "TEST_TGT_METRIC_OWN");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getOwnerEmpId())
            .as("owner_emp_id 应 roundtrip")
            .isEqualTo("USER_VAL_OWN_A");
        assertThat(loaded.getOwnerOrgCode())
            .as("owner_org_code 应 roundtrip")
            .isEqualTo("BRANCH_VAL_OWN_01");
    }

    @Test
    @DisplayName("V1.4 S2.2: selectByConditionWithScope 基于 owner_emp_id 片段过滤返回匹配目标值")
    void selectByConditionWithScope_filtersByOwnerEmpId() {
        String planId = randomPlanId();

        PerfTargetValue hit = TargetTestDataBuilder.value(
                planId, "EMP", "E_SCP_HIT", "2026", "TEST_TGT_SCP_A", new BigDecimal("100"));
        hit.setOwnerEmpId("USER_SCP_HIT");
        PerfTargetValue miss = TargetTestDataBuilder.value(
                planId, "EMP", "E_SCP_MISS", "2026", "TEST_TGT_SCP_B", new BigDecimal("200"));
        miss.setOwnerEmpId("USER_SCP_MISS");
        mapper.upsertBatch(Arrays.asList(hit, miss));

        java.util.Map<String, Object> scopeParams = new java.util.HashMap<>();
        scopeParams.put("ownerEmpId", "USER_SCP_HIT");
        List<PerfTargetValue> list = mapper.selectByConditionWithScope(
                planId, "EMP", null, "2026",
                0, 10,
                "owner_emp_id = #{scopeParams.ownerEmpId}", scopeParams);

        assertThat(list).extracting(PerfTargetValue::getSubjectId)
                .containsExactly("E_SCP_HIT");
    }
}
