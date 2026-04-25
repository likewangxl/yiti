package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.report.dto.req.DynamicQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.mapper.RptExportTaskMapper;
import com.bank.branch.platform.report.service.impl.ExportTaskServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExportTaskService 单元测试（Task M1.3.1，占位）.
 *
 * <p>覆盖：
 * <ul>
 *   <li>submitDynamicQueryExport 应插入 status=PENDING + 返回非空 taskId</li>
 *   <li>operatorId 取 CurrentUserApi.getCurrentEmpId 当前用户</li>
 *   <li>paramsJson 应包含入参字段（dim/subjectIds/metricCodes/dataDate）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ExportTaskServiceTest {

    @Mock
    private RptExportTaskMapper exportTaskMapper;

    @Mock
    private CurrentUserApi currentUserApi;

    @InjectMocks
    private ExportTaskServiceImpl service;

    @Test
    void submitDynamicQueryExport_insertPendingTask_returnsTaskId() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");

        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(List.of("E001", "E002"));
        req.setMetricCodes(List.of("M_DEPOSIT_BAL"));
        req.setDataDate(LocalDate.of(2026, 4, 1));

        ExportTaskRespDTO resp = service.submitDynamicQueryExport(req);

        assertThat(resp.getTaskId()).isNotBlank();
        assertThat(resp.getStatus()).isEqualTo("PENDING");

        ArgumentCaptor<RptExportTask> captor = ArgumentCaptor.forClass(RptExportTask.class);
        verify(exportTaskMapper, times(1)).insert(captor.capture());
        RptExportTask saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getExportType()).isEqualTo("DYNAMIC_QUERY");
        assertThat(saved.getOperatorId()).isEqualTo("E001");
        assertThat(saved.getParamsJson()).contains("EMP").contains("M_DEPOSIT_BAL");
    }
}
