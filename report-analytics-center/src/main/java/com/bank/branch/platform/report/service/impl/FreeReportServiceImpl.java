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
            // POI 的 DataFormatter 按单元格自身的数字格式渲染，结果与 Excel 里看到的一字不差
            // （-5.00000000069889E-7 配格式 0.0 -> "-0.0"；0.545175438596492 配 0.0% -> "54.5%"）
            DataFormatter formatter = new DataFormatter();

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
                    String key = "col_" + (c + 1);
                    // 显示文本：与 Excel 里看到的完全一致（-0.0 / 54.5%），不是 String.valueOf 的科学计数法
                    String val = cell != null ? getCellDisplay(cell, formatter) : "";
                    // 合并单元格从属位 fallback 到主单元格值
                    if (val.isEmpty()) {
                        val = mergedAnchorValue.getOrDefault(r + ":" + c, "");
                    }
                    rowData.put(key, val);
                    // 前两列是工号/姓名（纯文本），不需要原值/格式；其余数值列另存两份：
                    //   __raw 完整原值（点击查看用）、__fmt 原数字格式（导出复刻 Excel 显示用）
                    if (c >= 2 && cell != null) {
                        String raw = getCellRaw(cell);
                        if (!raw.isEmpty() && !raw.equals(val)) {
                            rowData.put(key + "__raw", raw);
                        }
                        String fmt = getCellFormat(cell);
                        if (!fmt.isEmpty()) {
                            rowData.put(key + "__fmt", fmt);
                        }
                    }
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
            // 按「数字格式串」缓存样式：同一格式只建一个 CellStyle。
            // 逐格新建会撞 Excel 的 64000 样式上限，而格式种类通常只有个位数。
            Map<String, CellStyle> styleCache = new HashMap<>();
            java.util.function.Function<String, CellStyle> styleOf = fmt ->
                    styleCache.computeIfAbsent(fmt, f -> {
                        CellStyle st = wb.createCellStyle();
                        st.setDataFormat(wb.createDataFormat().getFormat(f));
                        return st;
                    });
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
                    // 工号/姓名两列原样文本。其余数值列写「完整原值 + 原 Excel 数字格式」，
                    // 完整复刻源文件行为：格内按原格式渲染（-0.0 / 54.5%），点击后编辑栏是完整值。
                    // 这正是源 Excel 自己的做法，故不存在「显示」与「完整值」二选一的取舍。
                    String raw = extra.get(key + "__raw");   // 导入时存的完整原值（老批次无此键）
                    String fmt = extra.get(key + "__fmt");   // 导入时存的原数字格式（老批次无此键）
                    boolean isDataCol = !"col_1".equals(key) && !"col_2".equals(key);
                    Double num = null;
                    if (isDataCol) {
                        if (raw != null && !raw.isEmpty()) {
                            // 有 __raw 说明导入时该格确为数值格，直接按数值解析——
                            // 注意 __raw 可能是科学计数法（Excel 编辑栏对极小值的原样写法，
                            // 如 -5.00000000069889E-07），不能再用只认纯小数的 isDecimal 判定，
                            // 否则会被当文本写死，既不受数字格式控制、点击也看不到原值。
                            try { num = Double.parseDouble(raw); } catch (NumberFormatException ignore) { /* 退回文本 */ }
                        } else if (isDecimal(val)) {
                            // 老批次无 __raw：沿用「纯小数才转数值」的保守判定，
                            // 防止工号/卡号类纯数字被转成数值而丢前导零或变科学计数法
                            num = Double.parseDouble(val);
                        }
                    }
                    if (num != null) {
                        cell.setCellValue(num);
                        // 有原格式就用原格式；老批次没有则退回 0.00（保持既有行为）
                        cell.setCellStyle(styleOf.apply(fmt != null && !fmt.isEmpty() ? fmt : "0.00"));
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

    // 导出时判定「该值是否按数值格写入 Excel」：仅认形如 -?\d+\.\d+ 的纯小数字符串。
    // 整数、文本、日期、空一律走文本原样写（避免把工号/编号类纯数字转成数值而丢前导零或变科学计数法）。
    private static final java.util.regex.Pattern DECIMAL = java.util.regex.Pattern.compile("^-?\\d+\\.\\d+$");

    static boolean isDecimal(String s) {
        return s != null && DECIMAL.matcher(s).matches();
    }

    /**
     * 单元格「显示文本」：与 Excel 里肉眼所见完全一致。
     *
     * <p>关键在于用 {@link DataFormatter} 按单元格自身的数字格式渲染，而不是取原始 double 再
     * {@code String.valueOf}——后者对极小值会输出科学计数法（-5.00000000069889E-7），
     * 对百分比会丢掉 % 号（0.545175438596492）。原始值另由 {@link #getCellRaw} 保留。
     */
    private String getCellDisplay(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toString();
        }
        try {
            String s = renderByFormat(cell, formatter);
            if (s == null) return "";
            s = s.trim();
            return fixNegativeZeroByZeroSection(cell, formatter, s);
        } catch (Exception e) {
            // 格式串异常时退回原始取值，保证导入不中断
            return getCellString(cell);
        }
    }

    /**
     * 按单元格自身的数字格式渲染成文本。
     *
     * <p>数值格**必须**走 {@code formatRawCellContents(值, formatIndex, 格式串)}，
     * 不能用 {@code formatCellValue(cell)}：后者对**从文件读入**的自定义格式
     * （如现场报表里 {@code numFmtId=177} 的 {@code 0.00_ ;[Red]\-0.00\ }）会退化，
     * 把 -5e-7 渲染成科学计数法 {@code -5.00000000069889E-07}；而前者渲染为 {@code -0.00}，
     * 与 Excel/WPS 所见一致。（代码新建的自定义格式 POI 分配 numFmtId=164 时两者表现相同，
     * 所以只有用真实文件才能复现，见 importExcel_realFile_* 回归用例。）
     */
    private String renderByFormat(Cell cell, DataFormatter formatter) {
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        CellStyle style = cell.getCellStyle();
        if (type == CellType.NUMERIC && style != null) {
            String fmt = style.getDataFormatString();
            if (fmt != null && !fmt.isEmpty()) {
                return formatter.formatRawCellContents(
                        cell.getNumericCellValue(), style.getDataFormat(), fmt);
            }
        }
        return formatter.formatCellValue(cell);
    }

    /**
     * 修正 POI 与 Excel 在「分段格式 + 舍入后为零」上的渲染差异。
     *
     * <p>Excel 决定用格式的哪一段（正;负;零）看的是**按该格式舍入后**的值：会计格式
     * {@code _ * #,##0_ ;_ * \-#,##0_ ;_ * "-"_ } 把 -5e-7 舍成 0，于是走第三段显示 "-"。
     * 而 POI 的 DataFormatter 只看原始值的符号，直接走负数段渲染成 "-0"。
     *
     * <p>故这里检测「渲染结果的数字部分全为 0（即舍入后为零）」且「格式确有零段（≥3 段）」，
     * 改用零值重新渲染，与 Excel 对齐。不满足条件时原样返回，不影响 -0.00 这类
     * 只有两段（正;负）的自定义格式——它们本就该显示 -0.00。
     */
    private String fixNegativeZeroByZeroSection(Cell cell, DataFormatter formatter, String shown) {
        if (shown.isEmpty() || shown.indexOf('-') < 0) return shown;
        // 数字部分是否全为 0（去掉负号/千分位/小数点/空白后只剩 0）
        String digits = shown.replaceAll("[-,.\\s ]", "");
        if (digits.isEmpty() || digits.chars().anyMatch(c -> c != '0')) return shown;
        // 格式必须确有「零段」：Excel 分段格式为 正;负;零[;文本]
        CellStyle style = cell.getCellStyle();
        if (style == null) return shown;
        String fmt = style.getDataFormatString();
        if (fmt == null || fmt.split(";", -1).length < 3) return shown;
        try {
            String zeroShown = formatter.formatRawCellContents(0d, style.getDataFormat(), fmt);
            return zeroShown != null ? zeroShown.trim() : shown;
        } catch (Exception e) {
            return shown;
        }
    }

    /** 单元格「完整原值」：与 Excel 编辑栏所见完全一致，供点击查看。 */
    private String getCellRaw(Cell cell) {
        if (cell == null) return "";
        CellType t = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (t != CellType.NUMERIC || DateUtil.isCellDateFormatted(cell)) return "";
        try {
            return excelRawText(cell.getNumericCellValue());
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 把 double 渲染成「Excel 编辑栏原样」的文本。
     *
     * <p>要求是与源报表点开单元格时看到的一字不差，而不是"消除科学计数法"——
     * Excel 编辑栏对极小值**本来就用科学计数法**（如 {@code -5.00000000069889E-07}）。
     * 与 Java 默认表示的差异有两处，都在这里抹平：
     * <ul>
     *   <li>整数：{@code Double.toString(0.0)}="0.0"，Excel 编辑栏是 "0" —— 去掉尾部 ".0"</li>
     *   <li>指数：{@code Double.toString(-5e-7)}="-5.00000000069889E-7"，
     *       Excel 是 "E-07" —— 指数补足两位并显式带符号</li>
     * </ul>
     *
     * <p>不走 POI 的 {@code General} 渲染：它会把 0.498888897666 截成 0.4988888977（丢精度）。
     * 阈值上，绝对值落在 [1e-4, 1e15) 用十进制平铺（Excel 编辑栏同样如此），
     * 超出该区间才用科学计数法——避免 Java 在 1e-3/1e7 就切科学计数法而与 Excel 不符。
     */
    static String excelRawText(double v) {
        if (v == 0) return "0";                       // 含 -0.0
        if (Double.isNaN(v) || Double.isInfinite(v)) return String.valueOf(v);
        double abs = Math.abs(v);
        String s = Double.toString(v);
        if (abs >= 1e-4 && abs < 1e15) {
            // 十进制平铺。先 stripTrailingZeros 去掉标度带来的尾零——
            // Double.toString(0.0005)="5.0E-4"，直接 toPlainString 会得到 "0.00050"；
            // 同时它也顺带去掉整数的 ".0"（85.0 -> 85），无需另行截尾。
            return new java.math.BigDecimal(s).stripTrailingZeros().toPlainString();
        }
        // 科学计数法：尾数去掉 ".0"，指数补两位并带符号，对齐 Excel 的 E-07 / E+16 写法
        int e = s.indexOf('E');
        if (e < 0) return s;
        String mant = s.substring(0, e);
        String exp = s.substring(e + 1);
        if (mant.endsWith(".0")) mant = mant.substring(0, mant.length() - 2);
        char sign = '+';
        if (exp.startsWith("-")) { sign = '-'; exp = exp.substring(1); }
        else if (exp.startsWith("+")) { exp = exp.substring(1); }
        if (exp.length() < 2) exp = "0" + exp;
        return mant + "E" + sign + exp;
    }

    /** 单元格的数字格式串（如 0.0 / 0.0% / #,##0.00）；General、文本格式返回空。 */
    private String getCellFormat(Cell cell) {
        if (cell == null || cell.getCellStyle() == null) return "";
        String fmt = cell.getCellStyle().getDataFormatString();
        if (fmt == null) return "";
        // 判空/判 General 用去空白后的副本，但**返回原串**——绝不能 trim 后返回。
        // Excel 格式里 `_`(占位一个字符宽) 与 `\`(转义) 后面都必须再跟一个字符，
        // 而会计格式常以 `_ @_ `、自定义格式常以 `\-0.00\ ` 结尾（末位是空格）。
        // 一旦 trim 掉尾空格，就会留下孤立的 `_` 或 `\`，Excel/WPS 判定整串非法后
        // 退回常规格式 —— 表现为 0 显示成 "0"、极小值显示成科学计数法，格式全失效。
        String probe = fmt.trim();
        if (probe.isEmpty() || "General".equalsIgnoreCase(probe) || "@".equals(probe)) {
            return "";
        }
        return fmt;
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
