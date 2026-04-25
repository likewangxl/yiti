package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.sql.ReportFlywayTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RptExportTaskMapper 集成测试（Task M5.2.1）.
 *
 * <p>守护 M5 阶段新增的 4 个 Mapper 方法：
 * <ul>
 *   <li>insert + selectById round-trip（基线，M0.5 已守护）</li>
 *   <li>updateStatus（PENDING → RUNNING 过渡）</li>
 *   <li>updateSuccess（fileKey/rowCount/fileSize/expireAt 全填充）</li>
 *   <li>updateFailed（errorMsg 填充 + status=FAILED）</li>
 *   <li>selectByOperator（按 operator_id 过滤 + 按 status 过滤）</li>
 * </ul>
 *
 * <p>使用前缀约定：TEST_RPT_EXP_M5_* 避免与其他测试数据冲突.
 */
@Transactional
@Rollback(true)
class RptExportTaskMapperIT extends ReportFlywayTestBase {

    @Autowired
    private RptExportTaskMapper mapper;

    private static final String TEST_OPERATOR = "TEST_RPT_EXP_M5_OP";

    @Test
    void insertAndSelectById_shouldRoundTrip() {
        RptExportTask task = newPending("TEST_RPT_EXP_M5_001", "DYNAMIC_QUERY");
        int rows = mapper.insert(task);
        assertThat(rows).isEqualTo(1);

        RptExportTask got = mapper.selectById("TEST_RPT_EXP_M5_001");
        assertThat(got).isNotNull();
        assertThat(got.getStatus()).isEqualTo("PENDING");
        assertThat(got.getOperatorId()).isEqualTo(TEST_OPERATOR);
        assertThat(got.getExportType()).isEqualTo("DYNAMIC_QUERY");
    }

    @Test
    void updateStatus_pendingToRunning_shouldPersist() {
        mapper.insert(newPending("TEST_RPT_EXP_M5_002", "DYNAMIC_QUERY"));
        int rows = mapper.updateStatus("TEST_RPT_EXP_M5_002", "RUNNING");
        assertThat(rows).isEqualTo(1);
        assertThat(mapper.selectById("TEST_RPT_EXP_M5_002").getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void updateSuccess_shouldSetFileKeyAndExpireAt() {
        mapper.insert(newPending("TEST_RPT_EXP_M5_003", "TOUCH_SUMMARY"));
        LocalDateTime expireAt = LocalDateTime.now().plusDays(7).withNano(0);
        int rows = mapper.updateSuccess("TEST_RPT_EXP_M5_003",
            "rpt/export/TEST_RPT_EXP_M5_003/touch.xlsx", 100, 102400L, expireAt);
        assertThat(rows).isEqualTo(1);

        RptExportTask got = mapper.selectById("TEST_RPT_EXP_M5_003");
        assertThat(got.getStatus()).isEqualTo("SUCCESS");
        assertThat(got.getFileKey()).isEqualTo("rpt/export/TEST_RPT_EXP_M5_003/touch.xlsx");
        assertThat(got.getRowCount()).isEqualTo(100);
        assertThat(got.getFileSize()).isEqualTo(102400L);
        assertThat(got.getExpireAt()).isNotNull();
    }

    @Test
    void updateFailed_shouldSetErrorMsgAndStatus() {
        mapper.insert(newPending("TEST_RPT_EXP_M5_004", "PERF_SUMMARY"));
        int rows = mapper.updateFailed("TEST_RPT_EXP_M5_004", "MinIO upload failed: timeout");
        assertThat(rows).isEqualTo(1);

        RptExportTask got = mapper.selectById("TEST_RPT_EXP_M5_004");
        assertThat(got.getStatus()).isEqualTo("FAILED");
        assertThat(got.getErrorMsg()).contains("MinIO upload failed");
    }

    @Test
    void selectByOperator_shouldReturnByEmpAndStatusFilter() {
        // 同 operator 3 条不同 status，按 PENDING 过滤应命中 1 条
        mapper.insert(newPending("TEST_RPT_EXP_M5_LIST_1", "DYNAMIC_QUERY"));
        mapper.insert(newWithStatus("TEST_RPT_EXP_M5_LIST_2", "TOUCH_SUMMARY", "SUCCESS"));
        mapper.insert(newWithStatus("TEST_RPT_EXP_M5_LIST_3", "PERF_SUMMARY", "FAILED"));

        List<RptExportTask> pending = mapper.selectByOperator(TEST_OPERATOR, "PENDING");
        assertThat(pending).extracting(RptExportTask::getId)
            .contains("TEST_RPT_EXP_M5_LIST_1");
        assertThat(pending).extracting(RptExportTask::getStatus)
            .allMatch("PENDING"::equals);

        // status=null 时返回所有
        List<RptExportTask> all = mapper.selectByOperator(TEST_OPERATOR, null);
        assertThat(all).hasSizeGreaterThanOrEqualTo(3);
    }

    private RptExportTask newPending(String id, String type) {
        return newWithStatus(id, type, "PENDING");
    }

    private RptExportTask newWithStatus(String id, String type, String status) {
        RptExportTask t = new RptExportTask();
        t.setId(id);
        t.setExportType(type);
        t.setParamsJson("{}");
        t.setStatus(status);
        t.setOperatorId(TEST_OPERATOR);
        t.setCreatedTime(LocalDateTime.now());
        t.setUpdatedTime(LocalDateTime.now());
        return t;
    }
}
