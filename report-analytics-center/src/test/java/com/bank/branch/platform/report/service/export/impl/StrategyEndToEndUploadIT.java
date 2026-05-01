package com.bank.branch.platform.report.service.export.impl;

import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.mapper.RptExportTaskMapper;
import com.bank.branch.platform.report.service.export.RptExportService;
import com.bank.branch.platform.report.sql.ReportFlywayTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4 strategy 端到端 governance.FileApi.upload 集成测试（Task M6.0.2）.
 *
 * <p>覆盖：
 * <ul>
 *   <li>RptExportService.createTask 同步链路真实跑：
 *       PENDING → RUNNING → strategy.execute → updateSuccess</li>
 *   <li>strategy.execute 真实调 fileApi.upload(MultipartFile, operatorId)</li>
 *   <li>fileApi.upload 返回的 FileObjectDTO.id 持久化到 DB rpt_export_task.file_key 列</li>
 *   <li>RptExportFacade 后续 fileApi.getDownloadUrl(fileKey) 调用语义对齐</li>
 * </ul>
 *
 * <p>前置：本地 MySQL onepl_test_bootstrap + Redis 6379 + Flyway V1_0_0 ~ V1_0_6 已迁移.
 * 不依赖真实 MinIO（FileApi 用 @MockBean 替换）.
 *
 * <p>差异 vs ExportStrategiesTest：后者纯 mock 单元，本 IT 覆盖 Service 调度状态机
 * + Mapper 真实写库 + fileKey 持久化形式（governance.FileApi.upload 返回 fileId 而非 MinIO key）.
 *
 * <p>测试数据前缀：{@code TEST_RPT_E2E_*} 不与 M5 RptExportTaskMapperIT (TEST_RPT_EXP_M5_*) 冲突.
 */
@Transactional
@Rollback(true)
class StrategyEndToEndUploadIT extends ReportFlywayTestBase {

    @Autowired
    private RptExportService exportService;

    @Autowired
    private RptExportTaskMapper taskMapper;

    @MockBean
    private FileApi fileApi;

    private static final String OPERATOR = "TEST_RPT_E2E_OP";

    @Test
    void dynamicQueryStrategy_e2e_shouldPersistFileIdAsFileKey() {
        FileObjectDTO uploaded = stub("FID-DQ-E2E-001", 2048L);
        when(fileApi.upload(any(MultipartFile.class), eq(OPERATOR))).thenReturn(uploaded);

        Map<String, Object> params = new HashMap<>();
        params.put("dim", "EMP");
        String taskId = exportService.createTask("DYNAMIC_QUERY", params, OPERATOR);

        RptExportTask t = taskMapper.selectById(taskId);
        assertThat(t).isNotNull();
        assertThat(t.getStatus()).isEqualTo("SUCCESS");
        assertThat(t.getFileKey()).isEqualTo("FID-DQ-E2E-001");
        assertThat(t.getFileSize()).isEqualTo(2048L);
        verify(fileApi, times(1)).upload(any(MultipartFile.class), eq(OPERATOR));
    }

    @Test
    void touchSummaryStrategy_e2e_shouldPersistFileIdAsFileKey() {
        FileObjectDTO uploaded = stub("FID-TS-E2E-001", 1500L);
        when(fileApi.upload(any(MultipartFile.class), eq(OPERATOR))).thenReturn(uploaded);

        String taskId = exportService.createTask("TOUCH_SUMMARY", Map.of(), OPERATOR);
        RptExportTask t = taskMapper.selectById(taskId);
        assertThat(t.getStatus()).isEqualTo("SUCCESS");
        assertThat(t.getFileKey()).isEqualTo("FID-TS-E2E-001");
        assertThat(t.getFileSize()).isEqualTo(1500L);
    }

    @Test
    void perfSummaryStrategy_e2e_shouldPersistFileIdAsFileKey() {
        FileObjectDTO uploaded = stub("FID-PS-E2E-001", 1800L);
        when(fileApi.upload(any(MultipartFile.class), eq(OPERATOR))).thenReturn(uploaded);

        String taskId = exportService.createTask("PERF_SUMMARY", Map.of(), OPERATOR);
        RptExportTask t = taskMapper.selectById(taskId);
        assertThat(t.getStatus()).isEqualTo("SUCCESS");
        assertThat(t.getFileKey()).isEqualTo("FID-PS-E2E-001");
        assertThat(t.getFileSize()).isEqualTo(1800L);
    }

    @Test
    void custPoolSummaryStrategy_e2e_shouldPersistFileIdAsFileKey() {
        FileObjectDTO uploaded = stub("FID-CP-E2E-001", 1000L);
        when(fileApi.upload(any(MultipartFile.class), eq(OPERATOR))).thenReturn(uploaded);

        String taskId = exportService.createTask("CUSTPOOL_SUMMARY", Map.of(), OPERATOR);
        RptExportTask t = taskMapper.selectById(taskId);
        assertThat(t.getStatus()).isEqualTo("SUCCESS");
        assertThat(t.getFileKey()).isEqualTo("FID-CP-E2E-001");
        assertThat(t.getFileSize()).isEqualTo(1000L);
    }

    private FileObjectDTO stub(String fileId, Long size) {
        FileObjectDTO dto = new FileObjectDTO();
        dto.setId(fileId);
        dto.setFileSize(size);
        dto.setFileName("rpt-export.xlsx");
        return dto;
    }
}
