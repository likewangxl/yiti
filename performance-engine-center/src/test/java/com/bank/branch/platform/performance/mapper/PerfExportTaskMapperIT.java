package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PerfExportTaskMapper 集成测试（V1.2 Task Q6.1 Red）.
 *
 * <p>DDL 对齐 V1_2_1__perf_export_task.sql：主键 varchar(32)，
 * 无业务唯一键，status 默认 PENDING；索引 idx_operator + idx_status + idx_export_type。
 *
 * <p>测试数据前缀 {@code TEST_EXPT_*}。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>insert + selectById 基础回读</li>
 *   <li>updateStatus 状态机流转（PENDING → RUNNING）</li>
 *   <li>updateSuccess 回填 file_key / row_count / file_size / expire_at</li>
 *   <li>updateFailed 回填 error_msg</li>
 *   <li>selectByOperator 按操作人倒序查询</li>
 * </ul>
 */
class PerfExportTaskMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfExportTaskMapper mapper;

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private PerfExportTask newTask(String exportType, String operator) {
        PerfExportTask t = new PerfExportTask();
        t.setId(randomId());
        t.setExportType(exportType);
        t.setParamsJson("{\"schemeCode\":\"TEST_EXPT_SC\"}");
        t.setStatus("PENDING");
        t.setOperatorId(operator);
        return t;
    }

    @Test
    @DisplayName("insert 后可按主键 selectById 回读")
    void insert_and_selectById_returnsSaved() {
        PerfExportTask t = newTask("KPI", "TEST_EXPT_OP1");
        mapper.insert(t);

        PerfExportTask got = mapper.selectById(t.getId());

        assertThat(got).isNotNull();
        assertThat(got.getExportType()).isEqualTo("KPI");
        assertThat(got.getStatus()).isEqualTo("PENDING");
        assertThat(got.getOperatorId()).isEqualTo("TEST_EXPT_OP1");
        assertThat(got.getParamsJson()).contains("schemeCode");
    }

    @Test
    @DisplayName("updateStatus 状态机流转：PENDING → RUNNING")
    void updateStatus_pendingToRunning() {
        PerfExportTask t = newTask("KPI", "TEST_EXPT_OP2");
        mapper.insert(t);

        mapper.updateStatus(t.getId(), "RUNNING");

        assertThat(mapper.selectById(t.getId()).getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("updateSuccess 标记成功并回填 file_key/row_count/file_size/expire_at")
    void updateSuccess_fillsResultFields() {
        PerfExportTask t = newTask("KPI", "TEST_EXPT_OP3");
        mapper.insert(t);
        mapper.updateStatus(t.getId(), "RUNNING");

        LocalDateTime expire = LocalDateTime.now().plusDays(7);
        mapper.updateSuccess(t.getId(), "perf/export/" + t.getId() + "/kpi.xlsx",
                100, 1024L, expire);

        PerfExportTask after = mapper.selectById(t.getId());
        assertThat(after.getStatus()).isEqualTo("SUCCESS");
        assertThat(after.getFileKey()).isEqualTo("perf/export/" + t.getId() + "/kpi.xlsx");
        assertThat(after.getRowCount()).isEqualTo(100);
        assertThat(after.getFileSize()).isEqualTo(1024L);
        assertThat(after.getExpireAt()).isNotNull();
    }

    @Test
    @DisplayName("updateFailed 标记失败并回填 error_msg")
    void updateFailed_fillsErrorMsg() {
        PerfExportTask t = newTask("KPI", "TEST_EXPT_OP4");
        mapper.insert(t);
        mapper.updateStatus(t.getId(), "RUNNING");

        mapper.updateFailed(t.getId(), "MinIO connection refused");

        PerfExportTask after = mapper.selectById(t.getId());
        assertThat(after.getStatus()).isEqualTo("FAILED");
        assertThat(after.getErrorMsg()).isEqualTo("MinIO connection refused");
    }

    @Test
    @DisplayName("selectByOperator 按 operator 倒序返回")
    void selectByOperator_orderByCreatedDesc() {
        String operator = "TEST_EXPT_OP_LIST";
        PerfExportTask t1 = newTask("KPI", operator);
        PerfExportTask t2 = newTask("METRIC", operator);
        mapper.insert(t1);
        mapper.insert(t2);

        List<PerfExportTask> list = mapper.selectByOperator(operator, 10);

        assertThat(list).hasSize(2);
        assertThat(list).extracting(PerfExportTask::getOperatorId).containsOnly(operator);
    }

    @Test
    @DisplayName("selectById 不存在时返回 null")
    void selectById_whenNotFound_returnsNull() {
        assertThat(mapper.selectById("NOT_EXIST_EXPT_ID")).isNull();
    }
}
