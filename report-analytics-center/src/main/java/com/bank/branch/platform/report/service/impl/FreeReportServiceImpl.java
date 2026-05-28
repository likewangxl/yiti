package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.report.entity.RptFreeReportBatch;
import com.bank.branch.platform.report.entity.RptFreeReportRow;
import com.bank.branch.platform.report.mapper.FreeReportBatchMapper;
import com.bank.branch.platform.report.mapper.FreeReportRowMapper;
import com.bank.branch.platform.report.service.FreeReportService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreeReportServiceImpl implements FreeReportService {

    private final FreeReportBatchMapper batchMapper;
    private final FreeReportRowMapper rowMapper;
    private final FileApi fileApi;
    private final ObjectMapper objectMapper;

    @Override
    public String importExcel(String reportName, MultipartFile file, String empId, String empName) {
        log.info("[FreeReport.import] reportName={}, file={}, empId={}", reportName, file.getOriginalFilename(), empId);

        // 相同文件名覆盖：先删旧批次DB数据（MinIO文件延后删，避免事务内调外部服务导致rollback-only）
        String fileName = file.getOriginalFilename();
        List<String> oldFileKeys = new ArrayList<>();
        if (fileName != null) {
            List<RptFreeReportBatch> existing = batchMapper.selectByFileName(fileName);
            for (RptFreeReportBatch old : existing) {
                rowMapper.deleteByBatchId(old.getId());
                if (old.getFileObjectKey() != null) oldFileKeys.add(old.getFileObjectKey());
                batchMapper.deleteById(old.getId());
                log.info("[FreeReport.import] 覆盖旧批次 id={}, fileName={}", old.getId(), old.getFileName());
            }
        }

        // 上传文件到 MinIO
        FileObjectDTO uploaded = fileApi.upload(file, empId);
        String fileObjectKey = uploaded.getId();

        // 解析 Excel
        List<String> headers;
        List<Map<String, String>> dataRows;
        try (InputStream is = file.getInputStream(); Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new BizException("RPT-40010", "Excel 无表头行");

            headers = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                headers.add(cell != null ? getCellString(cell) : "col_" + (c + 1));
            }
            // 校验前两列必须是工号和姓名
            if (headers.size() < 2) {
                throw new BizException("RPT-40012", "Excel 至少需要 2 列（第1列=工号，第2列=姓名）");
            }
            String h1 = headers.get(0).trim();
            String h2 = headers.get(1).trim();
            if (!h1.contains("工号") && !h1.equalsIgnoreCase("empId") && !h1.equalsIgnoreCase("emp_id")) {
                throw new BizException("RPT-40012", "第1列表头必须包含\"工号\"（当前：" + h1 + "）");
            }
            if (!h2.contains("姓名") && !h2.equalsIgnoreCase("name") && !h2.equalsIgnoreCase("emp_name")) {
                throw new BizException("RPT-40012", "第2列表头必须包含\"姓名\"（当前：" + h2 + "）");
            }

            dataRows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> rowData = new LinkedHashMap<>();
                boolean hasData = false;
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    String val = cell != null ? getCellString(cell) : "";
                    rowData.put("col_" + (c + 1), val);
                    if (!val.isEmpty()) hasData = true;
                }
                if (hasData) dataRows.add(rowData);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("RPT-40011", "Excel 解析失败: " + e.getMessage());
        }

        // 构建列定义
        List<Map<String, String>> colDefs = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            Map<String, String> col = new LinkedHashMap<>();
            col.put("key", "col_" + (i + 1));
            col.put("label", headers.get(i));
            colDefs.add(col);
        }

        // 保存批次
        String batchId = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        RptFreeReportBatch batch = new RptFreeReportBatch();
        batch.setId(batchId);
        batch.setReportName(reportName);
        batch.setFileName(file.getOriginalFilename());
        batch.setFileObjectKey(fileObjectKey);
        batch.setUploaderEmpId(empId);
        batch.setUploaderName(empName);
        batch.setImportTime(LocalDateTime.now());
        batch.setRowCount(dataRows.size());
        batch.setStatus("SUCCESS");
        try {
            batch.setColDefs(objectMapper.writeValueAsString(colDefs));
        } catch (Exception e) {
            batch.setColDefs("[]");
        }
        batchMapper.insert(batch);

        // 保存行数据
        for (Map<String, String> rowData : dataRows) {
            RptFreeReportRow row = new RptFreeReportRow();
            row.setBatchId(batchId);
            row.setCol1(rowData.getOrDefault("col_1", ""));  // 工号
            row.setCol2(rowData.getOrDefault("col_2", ""));  // 姓名
            row.setEmpId(rowData.getOrDefault("col_1", "")); // 工号存 EMP_ID
            row.setOrgCode(null);
            // 除前两列外的数据放 JSON
            Map<String, String> extra = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : rowData.entrySet()) {
                if (!"col_1".equals(e.getKey()) && !"col_2".equals(e.getKey())) {
                    extra.put(e.getKey(), e.getValue());
                }
            }
            try {
                row.setDataJson(objectMapper.writeValueAsString(extra));
            } catch (Exception e) {
                row.setDataJson("{}");
            }
            row.setCreatedTime(LocalDateTime.now());
            rowMapper.insert(row);
        }

        log.info("[FreeReport.import] 完成 batchId={}, rows={}", batchId, dataRows.size());

        // 事务内 DB 操作全部完成后，清理 MinIO 旧文件（不影响事务）
        for (String key : oldFileKeys) {
            try { fileApi.deleteFile(key); } catch (Exception e) { log.warn("清理旧 MinIO 文件失败 key={}", key); }
        }

        return batchId;
    }

    @Override
    public PageResult<Map<String, Object>> queryData(String batchId, String keyword,
                                                      String empNo, String empName,
                                                      String scopeEmpId, List<String> scopeOrgCodes,
                                                      int pageNo, int pageSize) {
        long total = rowMapper.countByBatch(batchId, keyword, empNo, empName, scopeEmpId, scopeOrgCodes);
        if (total == 0) return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());

        int offset = (pageNo - 1) * pageSize;
        List<RptFreeReportRow> rows = rowMapper.selectByBatch(batchId, keyword, empNo, empName, scopeEmpId, scopeOrgCodes, offset, pageSize);

        List<Map<String, Object>> records = rows.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("col_1", r.getCol1());
            map.put("col_2", r.getCol2());
            // 解析 dataJson 展开到 map
            if (r.getDataJson() != null && !r.getDataJson().isEmpty()) {
                try {
                    Map<String, String> extra = objectMapper.readValue(r.getDataJson(), new TypeReference<>() {});
                    map.putAll(extra);
                } catch (Exception e) { /* ignore */ }
            }
            return map;
        }).collect(Collectors.toList());

        return PageResult.of(pageNo, pageSize, total, records);
    }

    @Override
    public List<Map<String, String>> getColumns(String batchId) {
        RptFreeReportBatch batch = batchMapper.selectById(batchId);
        if (batch == null || batch.getColDefs() == null) return Collections.emptyList();
        try {
            return objectMapper.readValue(batch.getColDefs(), new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<RptFreeReportBatch> listBatches(String keyword, java.time.LocalDate dateFrom, java.time.LocalDate dateTo,
                                                String scopeEmpId, java.util.List<String> scopeOrgCodes) {
        java.time.LocalDateTime fromDt = dateFrom != null ? dateFrom.atStartOfDay() : null;
        java.time.LocalDateTime toDt = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;
        return batchMapper.selectBatchesWithScope(keyword, fromDt, toDt, scopeEmpId, scopeOrgCodes);
    }

    @Override
    public String getDownloadUrl(String batchId) {
        RptFreeReportBatch batch = batchMapper.selectById(batchId);
        if (batch == null || batch.getFileObjectKey() == null) {
            throw new BizException("RPT-40401", "批次不存在或无关联文件");
        }
        return fileApi.getDownloadUrl(batch.getFileObjectKey());
    }

    @Override
    public String getBatchFileName(String batchId) {
        RptFreeReportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) return "report.xlsx";
        return batch.getFileName() != null ? batch.getFileName() : batch.getReportName() + ".xlsx";
    }

    @Override
    public void deleteBatch(String batchId) {
        RptFreeReportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) throw new BizException("RPT-40401", "批次不存在");
        String fileKey = batch.getFileObjectKey();
        // DB 删除（无事务注解，每条 DELETE 自动提交，避免 fileApi 嵌套事务污染）
        rowMapper.deleteByBatchId(batchId);
        batchMapper.deleteById(batchId);
        log.info("[FreeReport.delete] batchId={}", batchId);
        // MinIO 文件删除：失败不影响 DB 删除结果
        if (fileKey != null) {
            try { fileApi.deleteFile(fileKey); } catch (Exception e) { log.warn("[FreeReport.delete] 删 MinIO 文件失败 key={}", fileKey, e); }
        }
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double v = cell.getNumericCellValue();
                yield v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }
}
