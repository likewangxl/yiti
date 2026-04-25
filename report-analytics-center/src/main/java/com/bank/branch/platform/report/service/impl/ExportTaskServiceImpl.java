package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.report.dto.req.CustPoolSummaryReqDTO;
import com.bank.branch.platform.report.dto.req.DynamicQueryReqDTO;
import com.bank.branch.platform.report.dto.req.PerfSummaryReqDTO;
import com.bank.branch.platform.report.dto.req.TouchSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.entity.RptExportTask;
import com.bank.branch.platform.report.mapper.RptExportTaskMapper;
import com.bank.branch.platform.report.service.ExportTaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 异步导出任务实现（Task M1.3.1 占位，M5 全链路落地）.
 *
 * <p>V1.0 M1.3 行为：
 * <ol>
 *   <li>序列化入参为 paramsJson</li>
 *   <li>插入一条 status=PENDING 任务，operatorId=当前 empId</li>
 *   <li>返回 taskId + PENDING（实际执行由 M5 Worker 接管）</li>
 * </ol>
 *
 * <p>异常策略：序列化失败 → 抛运行时异常由 GlobalExceptionHandler 兜底，
 * 不再单独包 RPT-50003（M5 时再细化）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportTaskServiceImpl implements ExportTaskService {

    private static final String EXPORT_TYPE_DYNAMIC_QUERY = "DYNAMIC_QUERY";

    private static final String EXPORT_TYPE_TOUCH_SUMMARY = "TOUCH_SUMMARY";

    private static final String EXPORT_TYPE_PERF_SUMMARY = "PERF_SUMMARY";

    private static final String EXPORT_TYPE_CUST_POOL_SUMMARY = "CUST_POOL_SUMMARY";

    private static final String STATUS_PENDING = "PENDING";

    /** 模块内私有 ObjectMapper（含 LocalDate 序列化能力，独立于全局 ObjectMapper 避免污染）. */
    private final ObjectMapper paramsObjectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final RptExportTaskMapper exportTaskMapper;

    private final CurrentUserApi currentUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExportTaskRespDTO submitDynamicQueryExport(DynamicQueryReqDTO req) {
        String empId = currentUserApi.getCurrentEmpId();
        String paramsJson = serializeParams(req);

        RptExportTask task = new RptExportTask();
        task.setId(UUID.randomUUID().toString().replace("-", ""));
        task.setExportType(EXPORT_TYPE_DYNAMIC_QUERY);
        task.setParamsJson(paramsJson);
        task.setStatus(STATUS_PENDING);
        task.setOperatorId(empId);
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        exportTaskMapper.insert(task);

        log.info("[ExportTask] 已创建动态查询导出任务 taskId={} operatorId={} dim={}",
                task.getId(), empId, req.getDim());

        return ExportTaskRespDTO.builder()
                .taskId(task.getId())
                .status(STATUS_PENDING)
                .build();
    }

    private String serializeParams(DynamicQueryReqDTO req) {
        try {
            return paramsObjectMapper.writeValueAsString(req);
        } catch (JsonProcessingException e) {
            // M1.3 占位阶段不细化错误码，由全局异常处理器兜底；M5 再切到 RPT-50003
            throw new IllegalStateException("Export params 序列化失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExportTaskRespDTO submitTouchSummaryExport(TouchSummaryReqDTO req) {
        String empId = currentUserApi.getCurrentEmpId();
        String paramsJson = serializeTouchSummaryParams(req);

        RptExportTask task = new RptExportTask();
        task.setId(UUID.randomUUID().toString().replace("-", ""));
        task.setExportType(EXPORT_TYPE_TOUCH_SUMMARY);
        task.setParamsJson(paramsJson);
        task.setStatus(STATUS_PENDING);
        task.setOperatorId(empId);
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        exportTaskMapper.insert(task);

        log.info("[ExportTask] 已创建触达汇总导出任务 taskId={} operatorId={} orgId={}",
                task.getId(), empId, req.getOrgId());

        return ExportTaskRespDTO.builder()
                .taskId(task.getId())
                .status(STATUS_PENDING)
                .build();
    }

    private String serializeTouchSummaryParams(TouchSummaryReqDTO req) {
        try {
            return paramsObjectMapper.writeValueAsString(req);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("TouchSummary 导出 params 序列化失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExportTaskRespDTO submitPerfSummaryExport(PerfSummaryReqDTO req) {
        String empId = currentUserApi.getCurrentEmpId();
        String paramsJson = serializePerfSummaryParams(req);

        RptExportTask task = new RptExportTask();
        task.setId(UUID.randomUUID().toString().replace("-", ""));
        task.setExportType(EXPORT_TYPE_PERF_SUMMARY);
        task.setParamsJson(paramsJson);
        task.setStatus(STATUS_PENDING);
        task.setOperatorId(empId);
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        exportTaskMapper.insert(task);

        log.info("[ExportTask] 已创建绩效汇总导出任务 taskId={} operatorId={} dim={} cycleType={}",
                task.getId(), empId, req.getDim(), req.getCycleType());

        return ExportTaskRespDTO.builder()
                .taskId(task.getId())
                .status(STATUS_PENDING)
                .build();
    }

    private String serializePerfSummaryParams(PerfSummaryReqDTO req) {
        try {
            return paramsObjectMapper.writeValueAsString(req);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("PerfSummary 导出 params 序列化失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExportTaskRespDTO submitCustPoolSummaryExport(CustPoolSummaryReqDTO req) {
        String empId = currentUserApi.getCurrentEmpId();
        String paramsJson = serializeCustPoolSummaryParams(req);

        RptExportTask task = new RptExportTask();
        task.setId(UUID.randomUUID().toString().replace("-", ""));
        task.setExportType(EXPORT_TYPE_CUST_POOL_SUMMARY);
        task.setParamsJson(paramsJson);
        task.setStatus(STATUS_PENDING);
        task.setOperatorId(empId);
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        exportTaskMapper.insert(task);

        log.info("[ExportTask] 已创建客户池汇总导出任务 taskId={} operatorId={} orgId={}",
                task.getId(), empId, req.getOrgId());

        return ExportTaskRespDTO.builder()
                .taskId(task.getId())
                .status(STATUS_PENDING)
                .build();
    }

    private String serializeCustPoolSummaryParams(CustPoolSummaryReqDTO req) {
        try {
            return paramsObjectMapper.writeValueAsString(req);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("CustPoolSummary 导出 params 序列化失败", e);
        }
    }
}
