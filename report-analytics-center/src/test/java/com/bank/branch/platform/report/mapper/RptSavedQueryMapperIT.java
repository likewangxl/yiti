package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.RptSavedQuery;
import com.bank.branch.platform.report.sql.ReportFlywayTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RptSavedQueryMapper 集成测试（Task M0.5.1）.
 *
 * <p>守护 3 个行为：
 * <ul>
 *   <li>insert + selectById 完整 round-trip</li>
 *   <li>countByEmpId 无数据时返回 0（M1.4 "每用户最多 10 个方案"校验的前置能力）</li>
 *   <li>countByEmpId 插入 1 条后返回 1（计数准确性）</li>
 * </ul>
 *
 * <p>继承 {@link ReportFlywayTestBase} 在测试启动时自动执行 V1_0_0__rpt_init.sql
 * 创建/校验 4 张 rpt_* 表，测试方法使用 {@code @Transactional + @Rollback(true)}
 * 保证测试数据不会污染库.
 *
 * <p>使用前缀约定：TEST_SQ_M0_5_* 避免与 M1+ 阶段测试数据冲突.
 */
@Transactional
@Rollback(true)
class RptSavedQueryMapperIT extends ReportFlywayTestBase {

    @Autowired
    private RptSavedQueryMapper mapper;

    private static final String TEST_ID_1 = "TEST_SQ_M0_5_001";
    private static final String TEST_ID_2 = "TEST_SQ_M0_5_002";
    private static final String TEST_EMP_ID = "TEST_SQ_M0_5_EMP";

    @Test
    void insertAndSelectById_shouldRoundTrip() {
        RptSavedQuery e = new RptSavedQuery();
        e.setId(TEST_ID_1);
        e.setEmpId(TEST_EMP_ID);
        e.setName("我的方案 1");
        e.setDim("EMP");
        e.setSubjectIds("[\"E10001\"]");
        e.setMetricCodes("[\"M_DEPOSIT_BAL\"]");
        e.setVersion(0);
        e.setCreatedTime(LocalDateTime.now());
        e.setUpdatedTime(LocalDateTime.now());

        int rows = mapper.insert(e);
        assertThat(rows).isEqualTo(1);

        RptSavedQuery loaded = mapper.selectById(TEST_ID_1);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getEmpId()).isEqualTo(TEST_EMP_ID);
        assertThat(loaded.getName()).isEqualTo("我的方案 1");
        assertThat(loaded.getDim()).isEqualTo("EMP");
        assertThat(loaded.getSubjectIds()).isEqualTo("[\"E10001\"]");
        assertThat(loaded.getMetricCodes()).isEqualTo("[\"M_DEPOSIT_BAL\"]");
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void countByEmpId_shouldReturnZero_whenNoData() {
        assertThat(mapper.countByEmpId("TEST_SQ_M0_5_EMP_NOT_EXIST")).isZero();
    }

    @Test
    void countByEmpId_shouldReturnOne_afterInsert() {
        RptSavedQuery e = new RptSavedQuery();
        e.setId(TEST_ID_2);
        e.setEmpId(TEST_EMP_ID);
        e.setName("计数测试方案");
        e.setDim("ORG");
        e.setSubjectIds("[\"O10001\"]");
        e.setMetricCodes("[\"M_CUSTOMER_CNT\"]");
        e.setVersion(0);
        e.setCreatedTime(LocalDateTime.now());
        e.setUpdatedTime(LocalDateTime.now());

        mapper.insert(e);
        assertThat(mapper.countByEmpId(TEST_EMP_ID)).isEqualTo(1);
    }

    // ===== M1.4 / M1.5 增量 Mapper 方法守护 =====

    @Test
    void listByEmpAndDim_filterByDim_returnsOnlyMatched() {
        // 插两条 EMP + 一条 ORG，按 dim=EMP 过滤应只命中 2 条
        insertEntity("TEST_SQ_M1_LIST_1", TEST_EMP_ID, "EMP", "EMP-A");
        insertEntity("TEST_SQ_M1_LIST_2", TEST_EMP_ID, "EMP", "EMP-B");
        insertEntity("TEST_SQ_M1_LIST_3", TEST_EMP_ID, "ORG", "ORG-A");

        var list = mapper.listByEmpAndDim(TEST_EMP_ID, "EMP");
        assertThat(list).hasSize(2);
        assertThat(list).extracting(RptSavedQuery::getDim)
                .allMatch(d -> "EMP".equals(d));
    }

    @Test
    void listByEmpAndDim_nullDim_returnsAll() {
        insertEntity("TEST_SQ_M1_LIST_4", TEST_EMP_ID, "EMP", "any-A");
        insertEntity("TEST_SQ_M1_LIST_5", TEST_EMP_ID, "ORG", "any-B");
        var list = mapper.listByEmpAndDim(TEST_EMP_ID, null);
        assertThat(list).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void findOldestId_returnsFirstCreated() throws InterruptedException {
        insertEntity("TEST_SQ_M1_OLD_1", TEST_EMP_ID, "EMP", "first");
        // 略微等待保证 created_time 有差异（datetime 精度到秒）
        Thread.sleep(1100);
        insertEntity("TEST_SQ_M1_OLD_2", TEST_EMP_ID, "EMP", "second");
        String oldest = mapper.findOldestId(TEST_EMP_ID);
        assertThat(oldest).isEqualTo("TEST_SQ_M1_OLD_1");
    }

    @Test
    void updateWithOptimisticLock_versionMatch_succeeds() {
        insertEntityWithVersion("TEST_SQ_M1_OL_1", TEST_EMP_ID, "EMP", "orig", 1);

        RptSavedQuery patch = new RptSavedQuery();
        patch.setId("TEST_SQ_M1_OL_1");
        patch.setName("renamed");
        int rows = mapper.updateWithOptimisticLock(patch, 1);
        assertThat(rows).isEqualTo(1);

        RptSavedQuery loaded = mapper.selectById("TEST_SQ_M1_OL_1");
        assertThat(loaded.getName()).isEqualTo("renamed");
        assertThat(loaded.getVersion()).isEqualTo(2);
    }

    @Test
    void updateWithOptimisticLock_versionMismatch_returnsZero() {
        insertEntityWithVersion("TEST_SQ_M1_OL_2", TEST_EMP_ID, "EMP", "orig", 5);

        RptSavedQuery patch = new RptSavedQuery();
        patch.setId("TEST_SQ_M1_OL_2");
        patch.setName("blocked");
        int rows = mapper.updateWithOptimisticLock(patch, 1);
        assertThat(rows).isZero();

        RptSavedQuery loaded = mapper.selectById("TEST_SQ_M1_OL_2");
        assertThat(loaded.getName()).isEqualTo("orig");
        assertThat(loaded.getVersion()).isEqualTo(5);
    }

    private void insertEntity(String id, String empId, String dim, String name) {
        insertEntityWithVersion(id, empId, dim, name, 0);
    }

    private void insertEntityWithVersion(String id, String empId, String dim, String name, int version) {
        RptSavedQuery e = new RptSavedQuery();
        e.setId(id);
        e.setEmpId(empId);
        e.setName(name);
        e.setDim(dim);
        e.setSubjectIds("[]");
        e.setMetricCodes("[]");
        e.setVersion(version);
        e.setCreatedTime(LocalDateTime.now());
        e.setUpdatedTime(LocalDateTime.now());
        mapper.insert(e);
    }
}
