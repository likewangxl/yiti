package com.bank.branch.platform.performance.service.export.impl;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.performance.entity.KpiResult;
import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import com.bank.branch.platform.performance.service.export.ExportStrategy;
import com.bank.branch.platform.performance.service.export.model.KpiExportRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.storage.FileCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * KPI 结果导出策略（V1.2 Task Q6.2 Green）.
 *
 * <p>职责：
 * <ol>
 *   <li>从 {@code task.paramsJson} 解析 cycleType / cycleDate / asOfDate / dataVersion（可选）</li>
 *   <li>调 {@link KpiResultMapper#countForExport} 预检行数；超过 {@link #EXPORT_ROWS_LIMIT}（200000）
 *       抛 {@code EXPORT_ROWS_EXCEEDS_LIMIT (PERF-42207)}</li>
 *   <li>调 {@link KpiResultMapper#selectForExport} 查询数据，映射为 {@link KpiExportRow}</li>
 *   <li>用 easyexcel 将行写入 {@link ByteArrayOutputStream}</li>
 *   <li>构造 fileKey = {@code perf/export/{taskId}/kpi_result_{timestamp}.xlsx}，上传 MinIO</li>
 *   <li>回写 {@code task.fileKey} + {@code task.fileSize}，返回实际行数</li>
 * </ol>
 *
 * <p>错误码映射：
 * <ul>
 *   <li>params 缺 cycleType/cycleDate/asOfDate → {@code VALIDATION_FAILED (PERF-42200)}</li>
 *   <li>行数超限 → {@code EXPORT_ROWS_EXCEEDS_LIMIT (PERF-42207)}</li>
 *   <li>MinIO / easyexcel 异常 → {@code EXPORT_FILE_GENERATE_FAILED (PERF-50002)}</li>
 * </ul>
 */
@Slf4j
@Component
public class KpiExportStrategy implements ExportStrategy {

    /** 单次导出行数上限（03 §K.3 PERF-42207）. */
    public static final int EXPORT_ROWS_LIMIT = 200000;

    private final KpiResultMapper kpiResultMapper;
    private final FileApi fileApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KpiExportStrategy(KpiResultMapper kpiResultMapper, FileApi fileApi) {
        this.kpiResultMapper = kpiResultMapper;
        this.fileApi = fileApi;
    }

    @Override
    public String exportType() {
        return "KPI";
    }

    @Override
    public int execute(PerfExportTask task) {
        KpiExportParams params = parseParams(task.getParamsJson());

        // 1) 行数预检
        long total = kpiResultMapper.countForExport(params.cycleType, params.cycleDate,
                params.asOfDate, params.dataVersion);
        if (total > EXPORT_ROWS_LIMIT) {
            throw new PerfException(PerfErrorCode.EXPORT_ROWS_EXCEEDS_LIMIT, total, EXPORT_ROWS_LIMIT);
        }

        // 2) 批量查询（上限 == EXPORT_ROWS_LIMIT，预检已保证不超）
        List<KpiResult> results = kpiResultMapper.selectForExport(params.cycleType,
                params.cycleDate, params.asOfDate, params.dataVersion, EXPORT_ROWS_LIMIT);
        List<KpiExportRow> rows = toRows(results);

        // 3) easyexcel 写 Excel 到字节流
        byte[] bytes;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, KpiExportRow.class)
                    .sheet("KPI 结果")
                    .doWrite(rows);
            bytes = out.toByteArray();
        } catch (Exception ex) {
            log.warn("[KpiExportStrategy] Excel 生成失败 taskId={}: {}", task.getId(), ex.getMessage());
            throw new PerfException(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED, ex, ex.getMessage());
        }

        // 4) 上传 OBS（统一走 FileApi），fileKey 存 file_object.id 供下载预签名
        String fileName = "kpi_result_" + System.currentTimeMillis() + ".xlsx";
        FileObjectDTO dto;
        try {
            dto = fileApi.upload(bytes, fileName,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    task.getOperatorId(), FileCategory.EXPORT_KPI);
        } catch (Exception ex) {
            log.warn("[KpiExportStrategy] OBS 上传失败 taskId={}: {}", task.getId(), ex.getMessage());
            throw new PerfException(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED, ex, ex.getMessage());
        }

        // 5) 回写 task 结果字段
        task.setFileKey(dto.getId());
        task.setFileSize((long) bytes.length);

        log.info("[KpiExportStrategy] 导出完成 taskId={}, rows={}, size={}, fileKey={}",
                task.getId(), rows.size(), bytes.length, dto.getId());

        return rows.size();
    }

    private KpiExportParams parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "params_json 必填");
        }
        try {
            JsonNode node = objectMapper.readTree(paramsJson);
            String cycleType = textOrNull(node, "cycleType");
            String cycleDateStr = textOrNull(node, "cycleDate");
            String asOfDateStr = textOrNull(node, "asOfDate");
            if (cycleType == null || cycleType.isBlank()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "cycleType 必填");
            }
            if (cycleDateStr == null || cycleDateStr.isBlank()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "cycleDate 必填");
            }
            if (asOfDateStr == null || asOfDateStr.isBlank()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "asOfDate 必填");
            }
            KpiExportParams p = new KpiExportParams();
            p.cycleType = cycleType;
            p.cycleDate = LocalDate.parse(cycleDateStr);
            p.asOfDate = LocalDate.parse(asOfDateStr);
            p.dataVersion = textOrNull(node, "dataVersion");
            return p;
        } catch (PerfException pex) {
            throw pex;
        } catch (Exception ex) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, ex, "params_json 解析失败: " + ex.getMessage());
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static List<KpiExportRow> toRows(List<KpiResult> results) {
        List<KpiExportRow> rows = new ArrayList<>(results.size());
        for (KpiResult r : results) {
            rows.add(KpiExportRow.builder()
                    .empId(r.getEmpId())
                    .cycleType(r.getCycleType())
                    .cycleDate(r.getCycleDate())
                    .asOfDate(r.getAsOfDate())
                    .dataVersion(r.getDataVersion())
                    .kpiTotalScore(r.getKpiTotalScore())
                    .calculatedTime(r.getCalculatedTime())
                    .build());
        }
        return rows;
    }

    /** 解析后参数结构（内部 POJO，无需公开）. */
    private static class KpiExportParams {
        String cycleType;
        LocalDate cycleDate;
        LocalDate asOfDate;
        String dataVersion;
    }
}
