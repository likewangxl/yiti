package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PerfImportBatchMapper 集成测试（Task P1.3 Red）.
 *
 * <p>DDL 对齐 V1_0_0__performance_ddl.sql §8：主键 varchar(32)，
 * UK=batch_no，status 默认 CREATED（业务状态机 CREATED/SUCCESS/FAILED；
 * V1.1 计算期可能新增 RUNNING，数据库不加 CHECK 约束，语义由 Service 层保证）。
 *
 * <p>测试数据前缀 {@code TEST_IMPBATCH_*}。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>insert + selectById 基础回读</li>
 *   <li>updateStatus 状态机流转（CREATED → RUNNING → SUCCESS）</li>
 *   <li>updateCounts 更新 total/success/error 行数（导入结束时汇总）</li>
 *   <li>UK 冲突：同 batch_no 第二次插入抛 DuplicateKeyException</li>
 *   <li>selectByBatchNo 按业务唯一键查询</li>
 * </ul>
 */
class PerfImportBatchMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfImportBatchMapper mapper;

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private PerfImportBatch newBatch(String batchNoSuffix) {
        PerfImportBatch b = new PerfImportBatch();
        b.setId(randomId());
        b.setBatchNo("TEST_IMPBATCH_" + batchNoSuffix);
        b.setImportType("TARGET");
        b.setDim("EMP");
        b.setFileName("targets.xlsx");
        b.setFileMd5("dummy-md5");
        b.setStatus("CREATED");
        b.setTotalRows(0);
        b.setSuccessRows(0);
        b.setErrorRows(0);
        b.setCreatedBy("TEST_OP");
        return b;
    }

    @Test
    @DisplayName("insert 后可按主键 selectById 回读")
    void insert_and_selectById_returnsSaved() {
        PerfImportBatch b = newBatch("B001");
        mapper.insert(b);

        PerfImportBatch got = mapper.selectById(b.getId());

        assertThat(got).isNotNull();
        assertThat(got.getBatchNo()).isEqualTo("TEST_IMPBATCH_B001");
        assertThat(got.getImportType()).isEqualTo("TARGET");
        assertThat(got.getStatus()).isEqualTo("CREATED");
        assertThat(got.getTotalRows()).isZero();
    }

    @Test
    @DisplayName("selectByBatchNo 按业务唯一键查询")
    void selectByBatchNo_returnsRecord() {
        PerfImportBatch b = newBatch("B002");
        mapper.insert(b);

        PerfImportBatch got = mapper.selectByBatchNo("TEST_IMPBATCH_B002");

        assertThat(got).isNotNull();
        assertThat(got.getId()).isEqualTo(b.getId());
    }

    @Test
    @DisplayName("updateStatus 状态机流转：CREATED → RUNNING → SUCCESS")
    void updateStatus_stateMachine_transitions() {
        PerfImportBatch b = newBatch("B003");
        mapper.insert(b);

        mapper.updateStatus(b.getId(), "RUNNING", null);
        assertThat(mapper.selectById(b.getId()).getStatus()).isEqualTo("RUNNING");

        mapper.updateStatus(b.getId(), "SUCCESS", "{\"ok\":true}");
        PerfImportBatch after = mapper.selectById(b.getId());
        assertThat(after.getStatus()).isEqualTo("SUCCESS");
        assertThat(after.getRemark()).contains("ok");
    }

    @Test
    @DisplayName("updateCounts 汇总导入行数")
    void updateCounts_updatesAllThree() {
        PerfImportBatch b = newBatch("B004");
        mapper.insert(b);

        mapper.updateCounts(b.getId(), 100, 95, 5);

        PerfImportBatch after = mapper.selectById(b.getId());
        assertThat(after.getTotalRows()).isEqualTo(100);
        assertThat(after.getSuccessRows()).isEqualTo(95);
        assertThat(after.getErrorRows()).isEqualTo(5);
    }

    @Test
    @DisplayName("UK 冲突：同 batch_no 第二次 insert 抛 DuplicateKeyException")
    void insert_duplicateBatchNo_throwsDuplicateKey() {
        mapper.insert(newBatch("B010"));

        PerfImportBatch dup = newBatch("B010");
        dup.setId(randomId()); // 主键不同，但 batch_no 相同
        assertThatThrownBy(() -> mapper.insert(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("selectById 不存在时返回 null")
    void selectById_whenNotFound_returnsNull() {
        assertThat(mapper.selectById("NOT_EXIST_ID")).isNull();
    }

    @Test
    @DisplayName("V1.9：dim=null 插入成功（METRIC_DEF 等维度无关导入）")
    void insert_dimNull_succeeds() {
        PerfImportBatch b = newBatch("B020");
        b.setImportType("METRIC_DEF");
        b.setDim(null);                // V1.9：dim 列可空
        mapper.insert(b);

        PerfImportBatch got = mapper.selectById(b.getId());
        assertThat(got).isNotNull();
        assertThat(got.getImportType()).isEqualTo("METRIC_DEF");
        assertThat(got.getDim()).isNull();
    }
}
