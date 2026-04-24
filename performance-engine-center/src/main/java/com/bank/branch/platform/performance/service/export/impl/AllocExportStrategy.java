package com.bank.branch.platform.performance.service.export.impl;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.service.export.ExportStrategy;
import com.bank.branch.platform.performance.service.export.model.AllocExportRow;
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
import java.util.List;

/**
 * 分配关系导出策略（V1.2 Task Q6.3 Green）.
 *
 * <p>params_json 字段：
 * <ul>
 *   <li>{@code effectiveDate}（必填）：时间线基准日</li>
 *   <li>{@code bizKind}（可选）：业务种类</li>
 *   <li>{@code empId}（可选）：员工工号过滤</li>
 * </ul>
 *
 * <p>行数上限 200000（{@link KpiExportStrategy#EXPORT_ROWS_LIMIT}）；超限抛 EXPORT_ROWS_EXCEEDS_LIMIT。
 */
@Slf4j
@Component
public class AllocExportStrategy implements ExportStrategy {

    private final CustAllocRelationMapper allocRelationMapper;
    private final MinioClient minioClient;
    private final String minioBucketName;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AllocExportStrategy(CustAllocRelationMapper allocRelationMapper,
                               MinioClient minioClient,
                               @Qualifier("minioBucketName") String minioBucketName) {
        this.allocRelationMapper = allocRelationMapper;
        this.minioClient = minioClient;
        this.minioBucketName = minioBucketName;
    }

    @Override
    public String exportType() {
        return "ALLOC";
    }

    @Override
    public int execute(PerfExportTask task) {
        Params p = parseParams(task.getParamsJson());

        // 1) 行数预检
        long total = allocRelationMapper.countForExport(p.bizKind, p.empId, p.effectiveDate);
        if (total > KpiExportStrategy.EXPORT_ROWS_LIMIT) {
            throw new PerfException(PerfErrorCode.EXPORT_ROWS_EXCEEDS_LIMIT,
                    total, KpiExportStrategy.EXPORT_ROWS_LIMIT);
        }

        // 2) 批量查询
        List<CustAllocRelation> rels = allocRelationMapper.selectForExport(p.bizKind, p.empId,
                p.effectiveDate, KpiExportStrategy.EXPORT_ROWS_LIMIT);
        List<AllocExportRow> rows = toRows(rels);

        // 3) easyexcel 写 Excel 到字节流
        byte[] bytes;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, AllocExportRow.class)
                    .sheet("分配关系")
                    .doWrite(rows);
            bytes = out.toByteArray();
        } catch (Exception ex) {
            log.warn("[AllocExportStrategy] Excel 生成失败 taskId={}: {}", task.getId(), ex.getMessage());
            throw new PerfException(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED, ex, ex.getMessage());
        }

        // 4) 上传 MinIO
        String fileKey = "perf/export/" + task.getId() + "/alloc_relation_"
                + System.currentTimeMillis() + ".xlsx";
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioBucketName)
                    .object(fileKey)
                    .stream(in, bytes.length, -1)
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .build());
        } catch (Exception ex) {
            log.warn("[AllocExportStrategy] MinIO 上传失败 taskId={}: {}", task.getId(), ex.getMessage());
            throw new PerfException(PerfErrorCode.EXPORT_FILE_GENERATE_FAILED, ex, ex.getMessage());
        }

        task.setFileKey(fileKey);
        task.setFileSize((long) bytes.length);

        log.info("[AllocExportStrategy] 导出完成 taskId={}, rows={}, fileKey={}",
                task.getId(), rows.size(), fileKey);
        return rows.size();
    }

    private Params parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "params_json 必填");
        }
        try {
            JsonNode node = objectMapper.readTree(paramsJson);
            String effectiveDateStr = text(node, "effectiveDate");
            if (effectiveDateStr == null || effectiveDateStr.isBlank()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "effectiveDate 必填");
            }
            Params p = new Params();
            p.effectiveDate = LocalDate.parse(effectiveDateStr);
            p.bizKind = text(node, "bizKind");
            p.empId = text(node, "empId");
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

    private static List<AllocExportRow> toRows(List<CustAllocRelation> rels) {
        List<AllocExportRow> rows = new ArrayList<>(rels.size());
        for (CustAllocRelation r : rels) {
            rows.add(AllocExportRow.builder()
                    .id(r.getId())
                    .custId(r.getCustId())
                    .allocDim(r.getAllocDim())
                    .bizKind(r.getBizKind())
                    .accountNo(r.getAccountNo())
                    .empId(r.getEmpId())
                    .ratio(r.getRatio())
                    .effectiveDate(r.getEffectiveDate())
                    .endDate(r.getEndDate())
                    .build());
        }
        return rows;
    }

    private static class Params {
        LocalDate effectiveDate;
        String bizKind;
        String empId;
    }
}
