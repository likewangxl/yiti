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
                        // 只要是数值格就存 __raw，**即便它与显示文本相同**（如 General 格式的整数 85）。
                        // 它同时承担「这格在源里是数值」的标记作用：导出时据此按数值写回，
                        // 否则整数会被当文本写死，Excel 里出现"数字以文本形式存储"且不能求和。
                        String raw = getCellRaw(cell);
                        if (!raw.isEmpty()) {
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
                            // 如 -5.00000000069889E-07），也可能是百分比形式（如 91%），
                            // 不能再用只认纯小数的 isDecimal 判定，否则会被当文本写死，
                            // 既不受数字格式控制、点击也看不到原值。
                            num = parseRawValue(raw);
                        } else if (isDecimal(val)) {
                            // 老批次无 __raw：沿用「纯小数才转数值」的保守判定，
                            // 防止工号/卡号类纯数字被转成数值而丢前导零或变科学计数法
                            num = Double.parseDouble(val);
                        }
                    }
                    if (num != null) {
                        cell.setCellValue(num);
                        if (fmt != null && !fmt.isEmpty()) {
                            cell.setCellStyle(styleOf.apply(fmt));       // 复刻源格式
                        } else if (raw == null || raw.isEmpty()) {
                            cell.setCellStyle(styleOf.apply("0.00"));    // 老批次无格式信息，沿用既有行为
                        }
                        // 新批次且源本就是 General（__fmt 为空）：不套任何格式，
                        // 保持通用格式 —— 套 0.00 会把源里显示 85 的整数变成 85.00。
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
            // 先剔除 ?? 占位符渲染出的幻影 0，再 trim（剔除后原地留下空格）
            return stripQuestionMarkPhantomDigits(cell, s).trim();
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
     * 剔除 POI 把 Excel 的 {@code ?} 占位符当成数字位而多渲染出来的「幻影 0」。
     *
     * <p>Excel 三个数字占位符语义不同：{@code 0} 是强制位（没数字也补 0）、
     * {@code #} 是可选位（没数字就不显示）、{@code ?} 是**对齐用的空格位**（没数字显示空格）。
     * 会计格式的零段常写成 {@code _ * "-"??_ }，其中 {@code ??} 只为让 "-" 与上下行小数位对齐，
     * 本身不该显示任何数字——Excel 对 0 显示的就是 "-"。但 POI 把 {@code ?} 当数字位处理，
     * 渲染成 "- 0"，正是现场报表里页面显示 "- 0" 的来源。
     *
     * <p>判据：取该值实际生效的那一段，若它**有 {@code ?} 占位符却没有 {@code 0} 强制位**，
     * 说明这一段压根不打算显示数字，把渲染结果里的数字全部抹掉即可。
     * 正/负数段形如 {@code _ * #,##0.00_ } 含 {@code 0}，不受影响。
     */
    private String stripQuestionMarkPhantomDigits(Cell cell, String shown) {
        if (shown.isEmpty()) return shown;
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type != CellType.NUMERIC) return shown;
        CellStyle style = cell.getCellStyle();
        if (style == null) return shown;
        String fmt = style.getDataFormatString();
        if (fmt == null || fmt.isEmpty()) return shown;
        String section = effectiveSection(fmt, cell.getNumericCellValue());
        if (!hasPlaceholder(section, '?') || hasPlaceholder(section, '0')) return shown;
        return shown.replaceAll("\\d", "");
    }

    /**
     * 取分段格式中对给定值实际生效的那一段。
     *
     * <p>分段规则：1 段全用；2 段是「正/零 | 负」；3 段及以上是「正 | 负 | 零」（第 4 段是文本段，数值不走）。
     * 选段看的是**原始值的符号**，不是按格式舍入后的值——现场报表已证实：四段会计格式下
     * -5.00000000069889E-07 在 Excel 里显示 "-0.00"（负数段），而不是零段的 "-"。
     * 曾经反过来假设并加了「舍入为零就改走零段」的修正，反而把对的改错，已删除。
     */
    private static String effectiveSection(String fmt, double v) {
        List<String> secs = splitFormatSections(fmt);
        if (secs.size() <= 1) return fmt;
        if (v > 0) return secs.get(0);
        if (v < 0) return secs.get(1);
        return secs.size() >= 3 ? secs.get(2) : secs.get(0);
    }

    /** 按分号拆分格式串，跳过引号字面量、方括号段与反斜杠转义里的分号。 */
    private static List<String> splitFormatSections(String fmt) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < fmt.length(); i++) {
            char c = fmt.charAt(i);
            if (c == '\\' && i + 1 < fmt.length()) { cur.append(c).append(fmt.charAt(++i)); continue; }
            if (c == '"') { inQuote = !inQuote; cur.append(c); continue; }
            if (!inQuote && c == '[') {
                int j = fmt.indexOf(']', i);
                if (j > 0) { cur.append(fmt, i, j + 1); i = j; continue; }
            }
            if (!inQuote && c == ';') { out.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    /**
     * 格式段里是否含**生效的**指定占位符。
     *
     * <p>要跳过三类「后一个字符是字面量」的前缀：反斜杠转义、{@code _x}（占位一个 x 的字符宽）、
     * {@code *x}（用 x 填充剩余宽度）——否则 {@code _0} 里的 0 会被误判成强制数字位。
     * 同样跳过引号字面量与 {@code [Red]} 这类方括号段。
     */
    private static boolean hasPlaceholder(String section, char ph) {
        boolean inQuote = false;
        for (int i = 0; i < section.length(); i++) {
            char c = section.charAt(i);
            if (!inQuote && (c == '\\' || c == '_' || c == '*') && i + 1 < section.length()) { i++; continue; }
            if (c == '"') { inQuote = !inQuote; continue; }
            if (inQuote) continue;
            if (c == '[') {
                int j = section.indexOf(']', i);
                if (j > 0) { i = j; continue; }
            }
            if (c == ph) return true;
        }
        return false;
    }

    /** 单元格「完整原值」：与 Excel 编辑栏所见完全一致，供点击查看。 */
    private String getCellRaw(Cell cell) {
        if (cell == null) return "";
        CellType t = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (t != CellType.NUMERIC || DateUtil.isCellDateFormatted(cell)) return "";
        try {
            double v = cell.getNumericCellValue();
            CellStyle style = cell.getCellStyle();
            String fmt = style != null ? style.getDataFormatString() : null;
            if (isPercentFormat(fmt)) {
                // 百分比格式：Excel 编辑栏显示的是「底层值 ×100 加 %」，
                // 例如底层 -16.8324723247232 + 格式 0.0% -> 编辑栏 -1683.24723247232%。
                // 必须用 movePointRight(2) 精确移位，×100 的浮点乘法会引入误差。
                return excelRawText(new java.math.BigDecimal(Double.toString(v)).movePointRight(2)) + "%";
            }
            return excelRawText(v);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 判断数字格式是否为百分比（存在**生效的** {@code %} 记号）。
     *
     * <p>需跳过三类"不算数"的 %：{@code \%} 转义、{@code "…%…"} 引号内的字面量、
     * {@code [Red]}/{@code [$-409]} 这类方括号区段内的内容。
     */
    static boolean isPercentFormat(String fmt) {
        if (fmt == null || fmt.isEmpty()) return false;
        boolean inQuote = false;
        for (int i = 0; i < fmt.length(); i++) {
            char c = fmt.charAt(i);
            if (c == '\\') { i++; continue; }              // 转义：跳过下一个字符
            if (c == '"') { inQuote = !inQuote; continue; }
            if (inQuote) continue;
            if (c == '[') {                                 // 颜色/区域等方括号段整体跳过
                int j = fmt.indexOf(']', i);
                if (j > 0) { i = j; continue; }
            }
            if (c == '%') return true;
        }
        return false;
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
        return excelRawText(new java.math.BigDecimal(Double.toString(v)));
    }

    /** 平铺区间下界：小于它 Excel 编辑栏才切科学计数法。 */
    private static final java.math.BigDecimal PLAIN_MIN = new java.math.BigDecimal("0.0001");
    /** 平铺区间上界（1e15）：大于等于它 Excel 编辑栏才切科学计数法。 */
    private static final java.math.BigDecimal PLAIN_MAX = new java.math.BigDecimal("1000000000000000");

    /**
     * BigDecimal 版本（百分比 movePointRight(2) 后走这条，避免二次转 double 丢精度）。
     */
    static String excelRawText(java.math.BigDecimal bd) {
        if (bd == null) return "";
        if (bd.signum() == 0) return "0";
        java.math.BigDecimal abs = bd.abs();
        if (abs.compareTo(PLAIN_MIN) >= 0 && abs.compareTo(PLAIN_MAX) < 0) {
            // 十进制平铺。stripTrailingZeros 去掉标度带来的尾零——
            // Double.toString(0.0005)="5.0E-4"，直接 toPlainString 会得到 "0.00050"；
            // 同时顺带去掉整数的 ".0"（85.0 -> 85）。
            return bd.stripTrailingZeros().toPlainString();
        }
        return sciText(bd.doubleValue());
    }

    /** 科学计数法：尾数去掉 ".0"，指数补两位并带符号，对齐 Excel 的 E-07 / E+16 写法。 */
    private static String sciText(double v) {
        String s = Double.toString(v);
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

    /**
     * 把 {@code __raw}（Excel 编辑栏原样文本）解析回 Excel 的底层数值，供导出写回单元格。
     *
     * <p>百分比形式（末尾带 %）要 {@code movePointLeft(2)} 还原：编辑栏 {@code 91%}
     * 对应底层 {@code 0.91}，直接 parseDouble 会抛异常导致该格被当文本写死。
     * 解析不了则返回 null，由调用方退回文本写入。
     */
    private static Double parseRawValue(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String s = raw.trim();
        try {
            if (s.endsWith("%")) {
                return new java.math.BigDecimal(s.substring(0, s.length() - 1).trim())
                        .movePointLeft(2).doubleValue();
            }
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
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
