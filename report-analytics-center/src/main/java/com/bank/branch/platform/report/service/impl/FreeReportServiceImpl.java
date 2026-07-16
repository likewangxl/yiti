package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.storage.FileCategory;
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

        // 同名 + 同操作人才覆盖：不同操作人传同名文件视为各自独立的批次
        String fileName = file.getOriginalFilename();
        if (fileName != null) {
            List<RptFreeReportBatch> existing = batchMapper.selectByFileName(fileName);
            for (RptFreeReportBatch old : existing) {
                if (!empId.equals(old.getUploaderEmpId())) continue;
                rowMapper.deleteByBatchId(old.getId());
                batchMapper.deleteById(old.getId());
                log.info("[FreeReport.import] 覆盖旧批次 id={}, fileName={}, uploader={}", old.getId(), old.getFileName(), empId);
            }
            // 不清理旧 OBS 文件：MD5 去重下新旧批次可能共享同一 FILE_OBJECT，删旧文件会误删新批次仍在用的对象；
            // 且对已不存在的记录调 deleteFile 会抛异常，把 @Transactional 导入事务标记为 rollback-only 致整单回滚。
            // 旧对象留在 OBS 作为可接受的孤儿（按业务决策不做清理）。
        }

        // 上传文件到 OBS（自由报表用 zybb 类型前缀）
        FileObjectDTO uploaded = fileApi.upload(file, empId, FileCategory.FREE_REPORT);
        String fileObjectKey = uploaded.getId();

        // 解析 Excel
        List<String> headers;
        List<Map<String, String>> dataRows;
        try (InputStream is = file.getInputStream(); Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new BizException("RPT-40010", "Excel 无表头行");

            // 预处理合并单元格：把合并区内每个 (row,col) 映射到主单元格的字符串值，
            // 后续读 cell == null（合并区从属位）时 fallback 拿主单元格的值，避免姓名等列断行
            Map<String, String> mergedAnchorValue = new HashMap<>();
            for (org.apache.poi.ss.util.CellRangeAddress range : sheet.getMergedRegions()) {
                Row anchorRow = sheet.getRow(range.getFirstRow());
                Cell anchorCell = anchorRow != null ? anchorRow.getCell(range.getFirstColumn()) : null;
                String anchorVal = anchorCell != null ? getCellString(anchorCell) : "";
                if (anchorVal.isEmpty()) continue;
                for (int r = range.getFirstRow(); r <= range.getLastRow(); r++) {
                    for (int c = range.getFirstColumn(); c <= range.getLastColumn(); c++) {
                        mergedAnchorValue.put(r + ":" + c, anchorVal);
                    }
                }
            }

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
                // 跳过隐藏行（Excel 模板里隐藏的辅助/计算行，业务用户视图看不到，不应入库）
                if (row.getZeroHeight()) continue;
                Map<String, String> rowData = new LinkedHashMap<>();
                boolean hasData = false;
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    String val = cell != null ? getCellString(cell) : "";
                    // 合并单元格从属位 fallback 到主单元格值
                    if (val.isEmpty()) {
                        val = mergedAnchorValue.getOrDefault(r + ":" + c, "");
                    }
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

        // 保存行数据 —— 分批批量插入（每批 500）。原逐条 insert 对 3000 行要 3000 次 DB 往返，
        // 大文件会超时只写入一部分；批量插入 + 整个方法 @Transactional 保证：要么全部写入、要么回滚，
        // 不再出现「批次记 3000、实际只入 2000」的不一致。
        final int CHUNK = 500;
        LocalDateTime now = LocalDateTime.now();
        List<RptFreeReportRow> buffer = new ArrayList<>(CHUNK);
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
            row.setCreatedTime(now);
            buffer.add(row);
            if (buffer.size() >= CHUNK) {
                rowMapper.insertBatch(buffer);
                buffer.clear();
            }
        }
        if (!buffer.isEmpty()) {
            rowMapper.insertBatch(buffer);
        }

        log.info("[FreeReport.import] 完成 batchId={}, rows={}", batchId, dataRows.size());

        return batchId;
    }

    @Override
    public PageResult<Map<String, Object>> queryData(String batchId, String keyword,
                                                      String empNo, String empName,
                                                      String rowMode, String selfEmpNo, String selfName,
                                                      List<String> scopeOrgCodes,
                                                      int pageNo, int pageSize) {
        long total = rowMapper.countByBatch(batchId, keyword, empNo, empName, rowMode, selfEmpNo, selfName, scopeOrgCodes);
        if (total == 0) return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());

        int offset = (pageNo - 1) * pageSize;
        List<RptFreeReportRow> rows = rowMapper.selectByBatch(batchId, keyword, empNo, empName, rowMode, selfEmpNo, selfName, scopeOrgCodes, offset, pageSize);

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
                                                boolean includeDisabled) {
        java.time.LocalDateTime fromDt = dateFrom != null ? dateFrom.atStartOfDay() : null;
        java.time.LocalDateTime toDt = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;
        return batchMapper.selectBatches(keyword, fromDt, toDt, includeDisabled);
    }

    @Override
    public String getBatchStatus(String batchId) {
        RptFreeReportBatch batch = batchMapper.selectById(batchId);
        return batch != null ? batch.getStatus() : null;
    }

    @Override
    public byte[] exportFilteredExcel(String batchId, String rowMode, String selfEmpNo, String selfName,
                                      List<String> orgCodes) {
        // 列定义（表头）
        List<Map<String, String>> colDefs = getColumns(batchId);
        // 与 /data 同一套行级过滤，但不分页：pageSize 取过滤后总行数
        long total = rowMapper.countByBatch(batchId, null, null, null, rowMode, selfEmpNo, selfName, orgCodes);
        List<RptFreeReportRow> rows = total == 0 ? Collections.emptyList()
                : rowMapper.selectByBatch(batchId, null, null, null, rowMode, selfEmpNo, selfName, orgCodes,
                                          0, (int) Math.min(total, 100000));

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("数据");
            // 复用一个 0.00 数字格式样式（逐格建样式会超 64000 上限）
            CellStyle numStyle = wb.createCellStyle();
            numStyle.setDataFormat(wb.createDataFormat().getFormat("0.00"));
            DataValidationHelper dvHelper = sheet.getDataValidationHelper();
            // 表头
            Row header = sheet.createRow(0);
            for (int c = 0; c < colDefs.size(); c++) {
                header.createCell(c).setCellValue(colDefs.get(c).getOrDefault("label", "col_" + (c + 1)));
            }
            // 数据行：按 col_defs 的 key 顺序取值（col_1/col_2 走专列，其余从 dataJson）
            int rIdx = 1;
            for (RptFreeReportRow r : rows) {
                Map<String, String> extra = Collections.emptyMap();
                if (r.getDataJson() != null && !r.getDataJson().isEmpty()) {
                    try { extra = objectMapper.readValue(r.getDataJson(), new TypeReference<>() {}); }
                    catch (Exception ignore) { /* keep empty */ }
                }
                Row row = sheet.createRow(rIdx);
                for (int c = 0; c < colDefs.size(); c++) {
                    String key = colDefs.get(c).getOrDefault("key", "col_" + (c + 1));
                    String val = switch (key) {
                        case "col_1" -> r.getCol1();
                        case "col_2" -> r.getCol2();
                        default -> extra.get(key);
                    };
                    val = val != null ? val : "";
                    Cell cell = row.createCell(c);
                    // 工号/姓名两列原样文本；其余列若是小数 → 显示截断两位数值(不四舍五入) + 0.00 格式；
                    // 若确有精度被砍(小数位>2) → 挂「数据有效性输入提示」，点击/选中该格弹出完整原值。
                    if (!"col_1".equals(key) && !"col_2".equals(key) && isDecimal(val)) {
                        cell.setCellValue(Double.parseDouble(truncate2(val)));
                        cell.setCellStyle(numStyle);
                        int fracLen = val.length() - val.indexOf('.') - 1;
                        if (fracLen > 2) {
                            DataValidation dv = dvHelper.createValidation(
                                    dvHelper.createCustomConstraint("TRUE()"),
                                    new org.apache.poi.ss.util.CellRangeAddressList(rIdx, rIdx, c, c));
                            dv.createPromptBox("完整值", val);
                            dv.setShowPromptBox(true);
                            dv.setSuppressDropDownArrow(true);
                            sheet.addValidationData(dv);
                        }
                    } else {
                        cell.setCellValue(val);
                    }
                }
                rIdx++;
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException("RPT-50001", "导出 Excel 失败: " + e.getMessage());
        }
    }

    @Override
    public void updateBatchStatus(String batchId, String status) {
        RptFreeReportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) throw new BizException("RPT-40401", "批次不存在");
        batchMapper.updateStatus(batchId, status);
        log.info("[FreeReport.updateStatus] batchId={}, status={}", batchId, status);
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
    public String getBatchFileObjectKey(String batchId) {
        RptFreeReportBatch batch = batchMapper.selectById(batchId);
        if (batch == null || batch.getFileObjectKey() == null) {
            throw new BizException("RPT-40401", "批次不存在或无关联文件");
        }
        return batch.getFileObjectKey();
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

    // 小数截断显示：仅处理形如 -?\d+\.\d+ 的纯小数字符串，砍尾保留两位（不四舍五入，不足补零）；
    // 整数、文本、日期、空一律原样返回（避免把工号/编号误加小数点）。原始数据不变，仅用于下载显示。
    private static final java.util.regex.Pattern DECIMAL = java.util.regex.Pattern.compile("^-?\\d+\\.\\d+$");

    static boolean isDecimal(String s) {
        return s != null && DECIMAL.matcher(s).matches();
    }

    static String truncate2(String s) {
        if (!isDecimal(s)) {
            return s;
        }
        int dot = s.indexOf('.');
        String intPart = s.substring(0, dot);
        String frac2 = (s.substring(dot + 1) + "00").substring(0, 2);
        return intPart + "." + frac2;
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
