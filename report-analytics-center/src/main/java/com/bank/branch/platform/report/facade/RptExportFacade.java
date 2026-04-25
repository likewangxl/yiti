package com.bank.branch.platform.report.facade;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.service.export.RptExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 报表异步导出 Facade（Task M5.3.1/2/3，对外编排）.
 *
 * <p>职责：
 * <ul>
 *   <li>从 {@link CurrentUserApi} 取当前 empId 作为 operatorId</li>
 *   <li>调 {@link RptExportService} 完成查询/取消（含归属校验）</li>
 *   <li>下载场景：SUCCESS 校验 + 委托 {@link FileApi#getDownloadUrl} 拿 MinIO 预签名 URL</li>
 *   <li>entity → DTO 装配（Controller 不再接触 entity，对齐 V1.4 S4 NoEntityInControllerLocalsArchTest 规则）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RptExportFacade {

    private final RptExportService exportService;
    private final CurrentUserApi currentUserApi;
    private final FileApi fileApi;

    /**
     * E.1 status 查询：归属校验 + 状态字段返回.
     */
    public ExportTaskRespDTO getStatus(String taskId) {
        String empId = currentUserApi.getCurrentEmpId();
        RptExportTask task = exportService.getTaskForOwner(taskId, empId);
        return toDto(task);
    }

    /**
     * E.2 cancel：归属校验 + 状态机校验（仅 PENDING/RUNNING 可取消）.
     */
    public void cancel(String taskId) {
        String empId = currentUserApi.getCurrentEmpId();
        exportService.cancelTask(taskId, empId);
    }

    /**
     * E.3 download：归属校验 + SUCCESS 状态校验 + 委托 governance.FileApi.getDownloadUrl.
     */
    public String getDownloadUrl(String taskId) {
        String empId = currentUserApi.getCurrentEmpId();
        RptExportTask task = exportService.getTaskForOwner(taskId, empId);
        if (!"SUCCESS".equals(task.getStatus())) {
            // PENDING/RUNNING/FAILED/CANCELLED 均不可下载
            throw new RptException(RptErrorCode.EXPORT_TASK_NOT_READY);
        }
        if (task.getFileKey() == null || task.getFileKey().isBlank()) {
            // SUCCESS 但无 fileKey（异常路径）：归类为 NOT_READY，不暴露内部状态
            log.warn("[RptExportFacade] SUCCESS task fileKey 为空 taskId={}", taskId);
            throw new RptException(RptErrorCode.EXPORT_TASK_NOT_READY);
        }
        return fileApi.getDownloadUrl(task.getFileKey());
    }

    /** entity → DTO 装配：Controller 不接触 entity. */
    private static ExportTaskRespDTO toDto(RptExportTask t) {
        return ExportTaskRespDTO.builder()
            .taskId(t.getId())
            .status(t.getStatus())
            .exportType(t.getExportType())
            .rowCount(t.getRowCount())
            .errorMsg(t.getErrorMsg())
            .createdTime(t.getCreatedTime())
            .build();
    }
}
