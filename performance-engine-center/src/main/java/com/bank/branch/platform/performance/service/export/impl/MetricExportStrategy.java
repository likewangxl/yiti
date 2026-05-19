package com.bank.branch.platform.performance.service.export.impl;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpMetricValueRow;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.export.ExportStrategy;
import com.bank.branch.platform.performance.service.export.model.MetricExportRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标宽表结果导出策略（V1.2 Task Q6.3 Green）.
 *
 * <p>params_json 字段：
 * <ul>
 *   <li>{@code metricCodes[]}（必填）：要导出的指标编码数组</li>
 *   <li>{@code baseDim}（必填）：EMP / ORG（宽表选择依据）</li>
 *   <li>{@code dataDate}（必填）：数据日期</li>
 *   <li>{@code version}（必填）：数据版本</li>
 * </ul>
 *
 * <p>实现逻辑：
 * <ol>
 *   <li>解析 metricCodes，批量查 {@link PerfMetricDef} 获取每个指标的 val_slot</li>
 *   <li>从宽表取 distinct 对象 ID 列表（查询效率考虑，全量 EMP/ORG）</li>
 *   <li>对每个 metricCode/slot 调 selectSlotValuesByEmps（或 Org 对称方法）查值</li>
 *   <li>组装为 MetricExportRow 扁平行 → easyexcel 写 → MinIO 上传</li>
 * </ol>
 *
 * <p>因 400 slot 宽表每行维度固定（emp_id / data_date / version），多指标需分别查询再 join；
 * 本策略简化实现：一个 metricCode 查一批行，按 (subjectId, metricCode) 扁平化输出。
 */
@Slf4j
@Component
public class MetricExportStrategy implements ExportStrategy {

    private final PerfMetricDefMapper metricDefMapper;
    private final EmpIndexResultMapper empIndexResultMapper;
    private final OrgIndexResultMapper orgIndexResultMapper;
    private final MinioClient minioClient;
    private final String minioBucketName;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MetricExportStrategy(PerfMetricDefMapper metricDefMapper,
                                EmpIndexResultMapper empIndexResultMapper,
                                OrgIndexResultMapper orgIndexResultMapper,
                                MinioClient minioClient,
                                @Qualifier("minioBucketName") String minioBucketName) {
        this.metricDefMapper = metricDefMapper;
        this.empIndexResultMapper = empIndexResultMapper;
        this.orgIndexResultMapper = orgIndexResultMapper;
        this.minioClient = minioClient;
        this.minioBucketName = minioBucketName;
    }

    @Override
    public String exportType() {
        return "METRIC";
    }

    @Override
    public int execute(PerfExportTask task) {
        Params p = parseParams(task.getParamsJson());

        // 1) 批量取指标定义 → metricCode → (slot, name) 映射
        List<PerfMetricDef> defs = metricDefMapper.selectByMetricCodes(p.metricCodes);
        if (defs == null || defs.isEmpty()) {
            throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND,
                    String.join(",", p.metricCodes));
        }
        Map<String, PerfMetricDef> defMap = new HashMap<>();
        for (PerfMetricDef def : defs) {
            defMap.put(def.getMetricCode(), def);
        }
        // 传入的 metricCode 必须全部存在；否则抛 METRIC_NOT_FOUND
        for (String code : p.metricCodes) {
            if (!defMap.containsKey(code)) {
                throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, code);
            }
        }

        // 2) 根据 baseDim 分派查询
        List<MetricExportRow> rows = new ArrayList<>();
        if ("EMP".equalsIgnoreCase(p.baseDim)) {
            List<String> empIds = empIndexResultMapper.selectDistinctEmpIds(p.dataDate, p.version);
            if (empIds == null) {
                empIds = Collections.emptyList();
            }
            for (PerfMetricDef def : defs) {
                if (def.getValSlot() == null) {
                    continue;
                }
                if (empIds.isEmpty()) {
                    continue;
                }
                List<EmpMetricValueRow> slotRows = empIndexResultMapper.selectSlotValuesByEmps(
                        empIds, p.dataDate, p.version, def.getValSlot());
                if (slotRows == null) {
                    continue;
                }
                for (EmpMetricValueRow r : slotRows) {
                    rows.add(MetricExportRow.builder()
                            .baseDim("EMP")
                            .subjectId(r.getEmpId())
                            .metricCode(def.getMetricCode())
                            .metricName(def.getMetricName())
                            .dataDate(p.dataDate)
                            .dataVersion(p.version)
                            .metricValue(r.getMetricValue())
                            .build());
                }
            }
        } else if ("ORG".equalsIgnoreCase(p.baseDim)) {
            // ORG 宽表导出：因为 OrgIndexResultMapper 暂无 selectDistinctOrgCodes/selectForExport，
            // 本期简化为"依 orgCodes 必填"，若 params 未传 orgCodes 直接抛 VALIDATION_FAILED。
            if (p.orgCodes == null || p.orgCodes.isEmpty()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                        "ORG 维度导出必须传 orgCodes");
            }
            for (PerfMetricDef def : defs) {
                if (def.getValSlot() == null) {
                    continue;
                }
                List<com.bank.branch.platform.performance.mapper.OrgMetricValueRow> slotRows =
                        orgIndexResultMapper.selectSlotValuesByOrgs(p.orgCodes, p.dataDate, p.version, def.getValSlot());
                if (slotRows == null) {
                    continue;
                }
                for (com.bank.branch.platform.performance.mapper.OrgMetricValueRow r : slotRows) {
                    rows.add(MetricExportRow.builder()
                            .baseDim("ORG")
                            .subjectId(r.getOrgCode())
                            .metricCode(def.getMetricCode())
                            .metricName(def.getMetricName())
                            .dataDate(p.dataDate)
                            .dataVersion(p.version)
                            .metricValue(r.getMetricValue())
                            .build());
                }
            }
        } else {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "baseDim 必须是 EMP 或 ORG: " + p.baseDim);
        }

        // 3) 行数上限校验（事后）
        if (rows.size() > KpiExportStrategy.EXPORT_ROWS_LIMIT) {
            throw new PerfException(PerfErrorCode.EXPORT_ROWS_EXCEEDS_LIMIT,
                    rows.size(), KpiExportStrategy.EXPORT_ROWS_LIMIT);
        }

        // 4) easyexcel 写 Excel
        byte[] bytes;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, MetricExportRow.class)
                    .sheet("指标结果")
                    .doWrite(rows);
            bytes = out.toByteArray();
        } catch (Exception ex) {
            log.warn("[MetricExportStrategy] Excel 生成失败 taskId={}: {}", task.getId(), ex.getMessage());
            throw new PerfException(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED, ex, ex.getMessage());
        }

        // 5) 上传 MinIO
        String fileKey = "perf/export/" + task.getId() + "/metric_result_"
                + System.currentTimeMillis() + ".xlsx";
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioBucketName)
                    .object(fileKey)
                    .stream(in, bytes.length, -1)
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .build());
        } catch (Exception ex) {
            log.warn("[MetricExportStrategy] MinIO 上传失败 taskId={}: {}", task.getId(), ex.getMessage());
            throw new PerfException(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED, ex, ex.getMessage());
        }

        task.setFileKey(fileKey);
        task.setFileSize((long) bytes.length);

        log.info("[MetricExportStrategy] 导出完成 taskId={}, rows={}, fileKey={}",
                task.getId(), rows.size(), fileKey);
        return rows.size();
    }

    private Params parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "params_json 必填");
        }
        try {
            JsonNode node = objectMapper.readTree(paramsJson);
            JsonNode codesNode = node.get("metricCodes");
            if (codesNode == null || !codesNode.isArray() || codesNode.isEmpty()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "metricCodes 必填且非空");
            }
            List<String> metricCodes = objectMapper.convertValue(codesNode,
                    new TypeReference<List<String>>() { });

            String baseDim = text(node, "baseDim");
            String dataDateStr = text(node, "dataDate");
            String version = text(node, "version");
            if (baseDim == null || baseDim.isBlank()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "baseDim 必填");
            }
            if (dataDateStr == null || dataDateStr.isBlank()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "dataDate 必填");
            }
            if (version == null || version.isBlank()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "version 必填");
            }

            Params p = new Params();
            p.metricCodes = metricCodes;
            p.baseDim = baseDim;
            p.dataDate = LocalDate.parse(dataDateStr);
            p.version = version;
            JsonNode orgsNode = node.get("orgCodes");
            if (orgsNode != null && orgsNode.isArray() && !orgsNode.isEmpty()) {
                p.orgCodes = objectMapper.convertValue(orgsNode,
                        new TypeReference<List<String>>() { });
            }
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

    @SuppressWarnings("unused")
    private static List<String> defaultEmpty(List<String> list) {
        return list == null ? Collections.emptyList() : list;
    }

    /** Java 11 兼容的 Array.asList 常量. */
    @SuppressWarnings("unused")
    private static final List<String> EMPTY_LIST = Arrays.asList();

    private static class Params {
        List<String> metricCodes;
        String baseDim;
        LocalDate dataDate;
        String version;
        List<String> orgCodes;
    }
}
