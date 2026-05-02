package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.support.AllocTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CustAllocRelationMapper 集成测试（只读）.
 *
 * <p>Mapper 无 insert 方法，测试数据通过 {@link JdbcTemplate} 直接写入，
 * 继承 {@link PerformanceMapperTestBase} 的 {@code @Transactional + @Rollback} 保证自动回滚.
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>时间线过滤：过期 (end_date=昨天)、当前有效 (end_date=null/明天)、未来生效 (effective_date=明天)</li>
 *   <li>数据范围 SQL 片段注入：filter=null 管理员全见 vs filter 指向特定 emp_id 普通用户仅见</li>
 *   <li>基础路径：selectById / selectCurrentByCustAndBiz / selectHistoryByCustAsOf / selectByEmpAndBiz</li>
 *   <li>批量查询：selectCurrentByCustIds / countCustomersByEmps / countDistinctCustomers / summaryByCust</li>
 *   <li>分页查询：selectCurrentByBizPage / countCurrentByBiz</li>
 * </ul>
 */
class CustAllocRelationMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private CustAllocRelationMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 直接走 JDBC 插入（Mapper 只读，V1.0 无 insert 方法）. */
    private void insertRaw(CustAllocRelation r) {
        jdbcTemplate.update(
                "INSERT INTO CUST_ALLOC_RELATION (id, cust_id, alloc_dim, biz_kind, account_no, "
                        + "emp_id, ratio, effective_date, end_date, source_batch_id, source_process_date, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.getId(), r.getCustId(), r.getAllocDim(), r.getBizKind(), r.getAccountNo(),
                r.getEmpId(), r.getRatio(), r.getEffectiveDate(), r.getEndDate(),
                r.getSourceBatchId(), r.getSourceProcessDate(), r.getCreatedBy()
        );
    }

    @Test
    @DisplayName("selectById 不存在时返回 null")
    void selectById_whenNotFound_returnsNull() {
        assertThat(mapper.selectById("NO_SUCH_ID_ALLOC")).isNull();
    }

    @Test
    @DisplayName("selectById 存在时可查回分配关系")
    void selectById_whenExists_ok() {
        CustAllocRelation r = AllocTestDataBuilder.relation("SEL_ID", "EMP_X", "DEPOSIT");
        insertRaw(r);

        CustAllocRelation loaded = mapper.selectById(r.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getCustId()).isEqualTo("TEST_AR_SEL_ID");
        assertThat(loaded.getEmpId()).isEqualTo("EMP_X");
        assertThat(loaded.getBizKind()).isEqualTo("DEPOSIT");
        assertThat(loaded.getAllocDim()).isEqualTo("RULE");
    }

    @Test
    @DisplayName("selectCurrentByCustAndBiz 排除过期记录 (end_date=昨天)")
    void getCurrentAllocations_excludesExpired() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        // 过期：end_date=昨天 (应被排除)
        CustAllocRelation expired = AllocTestDataBuilder.relation(
                "EXP_EXPIRED", "EMP_TL", "DEPOSIT", today.minusDays(10), yesterday);
        // 长期有效：end_date=null (应返回)
        CustAllocRelation active = AllocTestDataBuilder.relation(
                "EXP_ACTIVE", "EMP_TL", "DEPOSIT", today.minusDays(5), null);
        // 未来失效：end_date=明天 (应返回)
        CustAllocRelation futureEnd = AllocTestDataBuilder.relation(
                "EXP_FUTURE", "EMP_TL", "DEPOSIT", today.minusDays(5), tomorrow);
        // 注意：DDL unique key 没有 cust_id+emp_id+biz_kind 唯一约束；但为了避免 "同一客户同期多条" 的混淆，这里用不同 cust_id
        insertRaw(expired);
        insertRaw(active);
        insertRaw(futureEnd);

        List<CustAllocRelation> activeList = mapper.selectCurrentByCustAndBiz(
                "TEST_AR_EXP_ACTIVE", "DEPOSIT", today);
        List<CustAllocRelation> futureList = mapper.selectCurrentByCustAndBiz(
                "TEST_AR_EXP_FUTURE", "DEPOSIT", today);
        List<CustAllocRelation> expiredList = mapper.selectCurrentByCustAndBiz(
                "TEST_AR_EXP_EXPIRED", "DEPOSIT", today);

        assertThat(activeList).hasSize(1);
        assertThat(futureList).hasSize(1);
        assertThat(expiredList).isEmpty();
    }

    @Test
    @DisplayName("selectCurrentByCustAndBiz 排除尚未生效的记录 (effective_date=明天)")
    void selectCurrentByCustAndBiz_excludesFuture() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        CustAllocRelation future = AllocTestDataBuilder.relation(
                "FUT_START", "EMP_F", "LOAN", tomorrow, null);
        insertRaw(future);

        List<CustAllocRelation> list = mapper.selectCurrentByCustAndBiz(
                "TEST_AR_FUT_START", "LOAN", today);

        assertThat(list).isEmpty();
    }

    @Test
    @DisplayName("selectHistoryByCustAsOf 可按 asOfDate 定位到历史有效记录")
    void getAllocationHistory_asOfDate_returnsActiveAtThatTime() {
        LocalDate past = LocalDate.of(2025, 1, 1);
        LocalDate midPast = LocalDate.of(2025, 6, 1);
        LocalDate end = LocalDate.of(2025, 12, 31);

        // 同一 cust 不同历史阶段
        CustAllocRelation r1 = AllocTestDataBuilder.relation(
                "HIST", "EMP_H1", "DEPOSIT", past, midPast.minusDays(1));   // 2025-01-01 ~ 2025-05-31
        CustAllocRelation r2 = AllocTestDataBuilder.relation(
                "HIST", "EMP_H2", "DEPOSIT", midPast, end);                 // 2025-06-01 ~ 2025-12-31
        // 修改主键避免冲突（同一 cust 多条在不同时间段，合法）
        r2.setId(r2.getId().substring(0, 30) + "ZZ");
        CustAllocRelation r3 = AllocTestDataBuilder.relation(
                "HIST", "EMP_H3", "DEPOSIT", LocalDate.of(2026, 1, 1), null); // 2026+ 至今
        r3.setId(r3.getId().substring(0, 30) + "YY");
        insertRaw(r1);
        insertRaw(r2);
        insertRaw(r3);

        // asOfDate = 2025-03-15 -> 只应命中 r1 (EMP_H1)
        List<CustAllocRelation> at202503 = mapper.selectHistoryByCustAsOf(
                "TEST_AR_HIST", LocalDate.of(2025, 3, 15));
        assertThat(at202503).extracting(CustAllocRelation::getEmpId).containsExactly("EMP_H1");

        // asOfDate = 2025-08-01 -> 只应命中 r2 (EMP_H2)
        List<CustAllocRelation> at202508 = mapper.selectHistoryByCustAsOf(
                "TEST_AR_HIST", LocalDate.of(2025, 8, 1));
        assertThat(at202508).extracting(CustAllocRelation::getEmpId).containsExactly("EMP_H2");

        // asOfDate = 2026-02-01 -> 只应命中 r3 (EMP_H3)
        List<CustAllocRelation> at202602 = mapper.selectHistoryByCustAsOf(
                "TEST_AR_HIST", LocalDate.of(2026, 2, 1));
        assertThat(at202602).extracting(CustAllocRelation::getEmpId).containsExactly("EMP_H3");
    }

    @Test
    @DisplayName("selectByEmpAndBiz + 数据范围片段: 普通用户仅见自己 emp_id 的分配")
    void listCustomersByEmp_withDataScopeFilter_onlyOwnEmp() {
        LocalDate today = LocalDate.now();

        // 3 个不同员工的当前有效分配
        CustAllocRelation ra = AllocTestDataBuilder.relation("DS_A", "EMP_A", "DEPOSIT");
        CustAllocRelation rb = AllocTestDataBuilder.relation("DS_B", "EMP_B", "DEPOSIT");
        CustAllocRelation rc = AllocTestDataBuilder.relation("DS_C", "EMP_C", "DEPOSIT");
        insertRaw(ra);
        insertRaw(rb);
        insertRaw(rc);

        // 场景 1: filter = null -> 管理员全见 (查 EMP_A 应只命中 EMP_A 但不受 filter 限制)
        List<CustAllocRelation> adminViewOfA = mapper.selectByEmpAndBiz("EMP_A", "DEPOSIT", today, null);
        assertThat(adminViewOfA).extracting(CustAllocRelation::getCustId).contains("TEST_AR_DS_A");

        // 场景 2: 普通用户视角 - filter="AND emp_id='EMP_A'"，即使 query 传 EMP_B 也只会受 filter 限制
        String filterA = "AND emp_id = 'EMP_A'";
        List<CustAllocRelation> userAList = mapper.selectByEmpAndBiz("EMP_A", "DEPOSIT", today, filterA);
        assertThat(userAList).isNotEmpty();
        assertThat(userAList).extracting(CustAllocRelation::getEmpId).containsOnly("EMP_A");
        assertThat(userAList).extracting(CustAllocRelation::getCustId).contains("TEST_AR_DS_A");
        assertThat(userAList).extracting(CustAllocRelation::getCustId)
                .doesNotContain("TEST_AR_DS_B", "TEST_AR_DS_C");

        // 场景 3: filter 过滤 EMP_A, 即使入参 empId=EMP_B, 也应返回空 (filter 强约束)
        List<CustAllocRelation> crossList = mapper.selectByEmpAndBiz("EMP_B", "DEPOSIT", today, filterA);
        assertThat(crossList).extracting(CustAllocRelation::getEmpId).doesNotContain("EMP_B");
    }

    @Test
    @DisplayName("selectByEmpAndBiz 正常路径 (asOfDate 时间线生效)")
    void selectByEmpAndBiz_ok() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        CustAllocRelation active = AllocTestDataBuilder.relation("EMP_OK_A", "EMP_OK", "LOAN");
        CustAllocRelation expired = AllocTestDataBuilder.relation("EMP_OK_E", "EMP_OK", "LOAN",
                today.minusDays(10), yesterday);
        insertRaw(active);
        insertRaw(expired);

        String filter = "AND emp_id = 'EMP_OK'";
        List<CustAllocRelation> list = mapper.selectByEmpAndBiz("EMP_OK", "LOAN", today, filter);

        assertThat(list).extracting(CustAllocRelation::getCustId).contains("TEST_AR_EMP_OK_A");
        assertThat(list).extracting(CustAllocRelation::getCustId).doesNotContain("TEST_AR_EMP_OK_E");
    }

    @Test
    @DisplayName("selectCurrentByCustIds 批量查询只返回当前有效")
    void selectCurrentByCustIds_batchOk() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        insertRaw(AllocTestDataBuilder.relation("BAT_1", "EMP_BAT", "DEPOSIT"));
        insertRaw(AllocTestDataBuilder.relation("BAT_2", "EMP_BAT", "DEPOSIT"));
        insertRaw(AllocTestDataBuilder.relation("BAT_3", "EMP_BAT", "DEPOSIT",
                today.minusDays(10), yesterday));  // 已过期，应被排除

        Set<String> custIds = new HashSet<>(Arrays.asList(
                "TEST_AR_BAT_1", "TEST_AR_BAT_2", "TEST_AR_BAT_3"));
        List<CustAllocRelation> list = mapper.selectCurrentByCustIds(custIds, "DEPOSIT", today, null);

        assertThat(list).extracting(CustAllocRelation::getCustId)
                .contains("TEST_AR_BAT_1", "TEST_AR_BAT_2")
                .doesNotContain("TEST_AR_BAT_3");
    }

    @Test
    @DisplayName("countDistinctCustomers 去重统计某员工客户数")
    void countDistinctCustomers_ok() {
        LocalDate today = LocalDate.now();

        // EMP_CNT 有 2 个不同客户
        insertRaw(AllocTestDataBuilder.relation("CNT_1", "EMP_CNT", "DEPOSIT"));
        insertRaw(AllocTestDataBuilder.relation("CNT_2", "EMP_CNT", "DEPOSIT"));
        // 同一客户多条分配（不同 biz_kind），应只计 1 次
        insertRaw(AllocTestDataBuilder.relation("CNT_1", "EMP_CNT", "LOAN"));

        long countDeposit = mapper.countDistinctCustomers("EMP_CNT", "DEPOSIT", today);
        long countAllBiz = mapper.countDistinctCustomers("EMP_CNT", null, today);

        assertThat(countDeposit).isEqualTo(2L);
        assertThat(countAllBiz).isEqualTo(2L); // 去重后还是 2 个客户
    }

    @Test
    @DisplayName("countCustomersByEmps 按员工分组统计")
    void countCustomersByEmps_ok() {
        LocalDate today = LocalDate.now();

        insertRaw(AllocTestDataBuilder.relation("GRP_1", "EMP_G1", "DEPOSIT"));
        insertRaw(AllocTestDataBuilder.relation("GRP_2", "EMP_G1", "DEPOSIT"));
        insertRaw(AllocTestDataBuilder.relation("GRP_3", "EMP_G2", "DEPOSIT"));

        Set<String> empIds = new HashSet<>(Arrays.asList("EMP_G1", "EMP_G2", "EMP_G_NONE"));
        List<Map<String, Object>> result = mapper.countCustomersByEmps(empIds, "DEPOSIT", today);

        // 仅有分配的员工会返回，EMP_G_NONE 不在结果中
        assertThat(result).hasSize(2);
        // 校验每组的 emp_id / cust_count
        Map<String, Long> byEmp = new java.util.HashMap<>();
        for (Map<String, Object> row : result) {
            Object empIdVal = row.get("emp_id");
            Object cntVal = row.get("cust_count");
            byEmp.put(String.valueOf(empIdVal), ((Number) cntVal).longValue());
        }
        assertThat(byEmp).containsEntry("EMP_G1", 2L);
        assertThat(byEmp).containsEntry("EMP_G2", 1L);
    }

    @Test
    @DisplayName("summaryByCust 汇总某客户各员工 ratio")
    void summaryByCust_ok() {
        LocalDate today = LocalDate.now();

        CustAllocRelation r1 = AllocTestDataBuilder.relation(
                "SUM", "EMP_S1", "DEPOSIT", today.minusDays(5), null, new BigDecimal("60.00"));
        CustAllocRelation r2 = AllocTestDataBuilder.relation(
                "SUM", "EMP_S2", "DEPOSIT", today.minusDays(5), null, new BigDecimal("40.00"));
        r2.setId(r2.getId().substring(0, 30) + "ZZ");
        insertRaw(r1);
        insertRaw(r2);

        List<Map<String, Object>> rows = mapper.summaryByCust("TEST_AR_SUM", today);

        assertThat(rows).hasSize(2);
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            total = total.add(new BigDecimal(row.get("ratio_sum").toString()));
        }
        assertThat(total).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("selectCurrentByBizPage 分页 + countCurrentByBiz 计数一致")
    void selectCurrentByBizPage_and_countCurrentByBiz_consistent() {
        LocalDate today = LocalDate.now();

        // 插入 3 条 FOREX 业务的当前有效分配
        insertRaw(AllocTestDataBuilder.relation("PAGE_1", "EMP_P", "FOREX"));
        insertRaw(AllocTestDataBuilder.relation("PAGE_2", "EMP_P", "FOREX"));
        insertRaw(AllocTestDataBuilder.relation("PAGE_3", "EMP_P", "FOREX"));

        String filter = "AND emp_id = 'EMP_P'";
        List<CustAllocRelation> page = mapper.selectCurrentByBizPage("FOREX", today, filter, 0, 100);
        long count = mapper.countCurrentByBiz("FOREX", today, filter);

        assertThat(count).isEqualTo(page.size());
        assertThat(page).extracting(CustAllocRelation::getCustId)
                .contains("TEST_AR_PAGE_1", "TEST_AR_PAGE_2", "TEST_AR_PAGE_3");
    }
}
