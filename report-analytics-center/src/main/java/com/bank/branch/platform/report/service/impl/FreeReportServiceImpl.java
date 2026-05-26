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
    @Transactional(rollbackFor = Exception.class)
    public String importExcel(String reportName, MultipartFile file, String empId, String empName) {
        log.info("[FreeReport.import] reportName={}, file={}, empId={}", reportName, file.getOriginalFilename(), empId);

        // 同名覆盖：删旧批次
        List<RptFreeReportBatch> existing = batchMapper.selectByReportName(reportName);
        for (RptFreeReportBatch old : existing) {
            rowMapper.deleteByBatchId(old.getId());
            try { fileApi.deleteFile(old.getFileObjectKey()); } catch (Exception e) { log.warn("删旧文件失败 key={}", old.getFileObjectKey(), e); }
            batchMapper.deleteById(old.getId());
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
            row.setCol1(rowData.getOrDefault("col_1", ""));
            row.setCol2(rowData.getOrDefault("col_2", ""));
            // emp_id / org_code 尝试从前两列推断（工号通常是 col_2）
            row.setEmpId(rowData.getOrDefault("col_2", ""));
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
        return batchId;
    }

    @Override
    public PageResult<Map<String, Object>> queryData(String batchId, String keyword,
                                                      String scopeEmpId, List<String> scopeOrgCodes,
                                                      int pageNo, int pageSize) {
        long total = rowMapper.countByBatch(batchId, keyword, scopeEmpId, scopeOrgCodes);
        if (total == 0) return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());

        int offset = (pageNo - 1) * pageSize;
        List<RptFreeReportRow> rows = rowMapper.selectByBatch(batchId, keyword, scopeEmpId, scopeOrgCodes, offset, pageSize);

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
    public List<RptFreeReportBatch> listBatches() {
        return batchMapper.selectAllOrderByImportTimeDesc();
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
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(String batchId) {
        RptFreeReportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) throw new BizException("RPT-40401", "批次不存在");
        rowMapper.deleteByBatchId(batchId);
        if (batch.getFileObjectKey() != null) {
            try { fileApi.deleteFile(batch.getFileObjectKey()); } catch (Exception e) { log.warn("删文件失败", e); }
        }
        batchMapper.deleteById(batchId);
        log.info("[FreeReport.delete] batchId={}", batchId);
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
