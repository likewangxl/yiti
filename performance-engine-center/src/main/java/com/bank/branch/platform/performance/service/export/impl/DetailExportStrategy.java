package com.bank.branch.platform.performance.service.export.impl;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.performance.entity.KpiResult;
import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import com.bank.branch.platform.performance.service.export.ExportStrategy;
import com.bank.branch.platform.performance.service.export.model.KpiDetailExportRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.storage.FileCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * KPI 明细导出策略（V1.2 Task Q6.3 Green）.
 *
 * <p>params_json 字段（与 {@link KpiExportStrategy} 一致）：
 * <ul>
 *   <li>{@code cycleType}（必填）</li>
 *   <li>{@code cycleDate}（必填）</li>
 *   <li>{@code asOfDate}（必填）</li>
 *   <li>{@code dataVersion}（可选）</li>
 * </ul>
 *
 * <p>与 {@link KpiExportStrategy} 的区别：每条 KPI 主记录的 detail_json 包含 items 数组，
 * 每个 item 拆一行 Excel（空 items 不产行）。导出结果更细粒度。
 */
@Slf4j
@Component
public class DetailExportStrategy implements ExportStrategy {

    private final KpiResultMapper kpiResultMapper;
    private final FileApi fileApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DetailExportStrategy(KpiResultMapper kpiResultMapper, FileApi fileApi) {
        this.kpiResultMapper = kpiResultMapper;
        this.fileApi = fileApi;
    }

    @Override
    public String exportType() {
        return "DETAIL";
    }

    @Override
    public int execute(PerfExportTask task) {
        Params p = parseParams(task.getParamsJson());

        // 1) 预检主记录数（KPI detail 每行 KPI 可能展开 N items，此处按主记录数做保守估算）
        long total = kpiResultMapper.countForExport(p.cycleType, p.cycleDate, p.asOfDate, p.dataVersion);
        if (total > KpiExportStrategy.EXPORT_ROWS_LIMIT) {
            throw new PerfException(PerfErrorCode.EXPORT_ROWS_EXCEEDS_LIMIT,
                    total, KpiExportStrategy.EXPORT_ROWS_LIMIT);
        }

        // 2) 查 KPI 主记录
        List<KpiResult> kpis = kpiResultMapper.selectForExport(p.cycleType, p.cycleDate,
                p.asOfDate, p.dataVersion, KpiExportStrategy.EXPORT_ROWS_LIMIT);

        // 3) 拆 detail_json.items → 每个 item 一行
        List<KpiDetailExportRow> rows = new ArrayList<>();
        for (KpiResult kpi : kpis) {
            List<Item> items = parseItems(kpi.getDetailJson());
            for (Item it : items) {
                rows.add(KpiDetailExportRow.builder()
                        .empId(kpi.getEmpId())
                        .cycleType(kpi.getCycleType())
                        .cycleDate(kpi.getCycleDate())
                        .asOfDate(kpi.getAsOfDate())
                        .kpiTotalScore(kpi.getKpiTotalScore())
                        .itemCode(it.itemCode)
                        .metricValue(it.metricValue)
                        .targetValue(it.targetValue)
                        .weight(it.weight)
                        .itemScore(it.score)
                        .build());
            }
        }

        // 再次校验拆行后是否超限
        if (rows.size() > KpiExportStrategy.EXPORT_ROWS_LIMIT) {
            throw new PerfException(PerfErrorCode.EXPORT_ROWS_EXCEEDS_LIMIT,
                    rows.size(), KpiExportStrategy.EXPORT_ROWS_LIMIT);
        }

        // 4) easyexcel 写 Excel
        byte[] bytes;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, KpiDetailExportRow.class)
                    .sheet("KPI 明细")
                    .doWrite(rows);
            bytes = out.toByteArray();
        } catch (Exception ex) {
            log.warn("[DetailExportStrategy] Excel 生成失败 taskId={}: {}", task.getId(), ex.getMessage());
            throw new PerfException(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED, ex, ex.getMessage());
        }

        // 5) 上传 OBS（统一走 FileApi），fileKey 存 file_object.id 供下载预签名
        String fileName = "kpi_detail_" + System.currentTimeMillis() + ".xlsx";
        FileObjectDTO dto;
        try {
            dto = fileApi.upload(bytes, fileName,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    task.getOperatorId(), FileCategory.EXPORT_DETAIL);
        } catch (Exception ex) {
            log.warn("[DetailExportStrategy] OBS 上传失败 taskId={}: {}", task.getId(), ex.getMessage());
            throw new PerfException(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED, ex, ex.getMessage());
        }

        task.setFileKey(dto.getId());
        task.setFileSize((long) bytes.length);

        log.info("[DetailExportStrategy] 导出完成 taskId={}, kpis={}, rows={}, fileKey={}",
                task.getId(), kpis.size(), rows.size(), dto.getId());
        return rows.size();
    }

    private List<Item> parseItems(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(detailJson);
            JsonNode arr = root.get("items");
            if (arr == null || !arr.isArray()) {
                return List.of();
            }
            List<Item> list = new ArrayList<>(arr.size());
            for (JsonNode node : arr) {
                Item it = new Item();
                it.itemCode = text(node, "itemCode");
                it.metricValue = decimal(node, "metricValue");
                it.targetValue = decimal(node, "targetValue");
                it.weight = decimal(node, "weight");
                it.score = decimal(node, "score");
                list.add(it);
            }
            return list;
        } catch (Exception ex) {
            log.warn("[DetailExportStrategy] detail_json 解析失败, 跳过: {}", ex.getMessage());
            return List.of();
        }
    }

    private Params parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "params_json 必填");
        }
        try {
            JsonNode node = objectMapper.readTree(paramsJson);
            String cycleType = text(node, "cycleType");
            String cycleDateStr = text(node, "cycleDate");
            String asOfDateStr = text(node, "asOfDate");
            if (cycleType == null || cycleType.isBlank()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "cycleType 必填");
            }
            if (cycleDateStr == null || cycleDateStr.isBlank()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "cycleDate 必填");
            }
            if (asOfDateStr == null || asOfDateStr.isBlank()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "asOfDate 必填");
            }
            Params p = new Params();
            p.cycleType = cycleType;
            p.cycleDate = LocalDate.parse(cycleDateStr);
            p.asOfDate = LocalDate.parse(asOfDateStr);
            p.dataVersion = text(node, "dataVersion");
            return p;
        } catch (PerfException pex) {
            throw pex;
        } catch (Exception ex) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, ex, "params_json 解析失败: " + ex.getMessage());
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        try {
            return v.decimalValue();
        } catch (Exception ex) {
            return new BigDecimal(v.asText());
        }
    }

    private static class Params {
        String cycleType;
        LocalDate cycleDate;
        LocalDate asOfDate;
        String dataVersion;
    }

    private static class Item {
        String itemCode;
        BigDecimal metricValue;
        BigDecimal targetValue;
        BigDecimal weight;
        BigDecimal score;
    }
}
