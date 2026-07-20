package com.bank.branch.platform.performance.service.importer.impl;

import com.bank.branch.platform.governance.api.PersonTagApi;
import com.bank.branch.platform.governance.api.dto.PersonTagDTO;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.importer.ImportContext;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import com.bank.branch.platform.performance.service.importer.KpiSchemeImportWriter;
import com.bank.branch.platform.performance.service.importer.model.KpiSchemeImportRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * KPI 方案 Excel 导入策略（2026-06-17，importType=KPI_SCHEME）.
 *
 * <p>平行于「目标方案导入」(TARGET_PLAN) 的新通道，复用现有
 * {@code POST /api/perf/import/upload?importType=KPI_SCHEME} 端点，无需新增资源/Controller。
 *
 * <p>模板列（sheet1，第 0 行表头，从第 1 行起数据），11 列：
 * <pre>
 *   序号 | 方案编号 | 方案名称 | 员工标签范围 | 维度 | 指标名称
 *        | 表达式类型 | 表达式 | 权重 | 计分上线 | 计分下限
 * </pre>
 *
 * <p><b>整批 all-or-none 语义</b>（与 TARGET/BASE_DATA/ALLOC 的"行级最大努力"不同）：
 * 先解析全部行 → 全量校验，任一行任一错误就抛 {@link PerfErrorCode#IMPORT_BATCH_ALL_OR_NONE_FAILED}
 * （message 含行号与原因），不写任何库；全部通过后在 {@link KpiSchemeImportWriter#write} 的
 * {@code @Transactional} 方法里落库，DB 异常自动回滚。
 *
 * <p>每行校验/转换：
 * <ol>
 *   <li>方案编号、方案名称非空</li>
 *   <li>指标名称非空且必须存在于 PERF_METRIC_DEF（批量预取 name→def）；方案项 base_dim 取该指标的
 *       {@code def.getBaseDim()}（忽略模板「维度」列）</li>
 *   <li>表达式类型：「计算表达式」=FORMULA / 「SQL表达式」=SQL（兼容大小写 FORMULA/SQL）；表达式内容可空，
 *       不做语法校验。FORMULA → formula=内容、sqlExpr=null；SQL → sqlExpr=内容、formula=null；内容空 → 皆空</li>
 *   <li>权重 / 计分上线(maxScore) / 计分下限(minScore)：可解析 BigDecimal（空则 null，由 writer 兜底）</li>
 *   <li>员工标签范围（方案级，按方案分组取首次出现行的值）：非空时按英文逗号分割、trim、去空，
 *       逐个用 {@link PersonTagApi#getTagsByNames(List)} 的 tagName→tagId 映射校验；任一查不到则报错；
 *       全部命中则 tagId 用逗号连接存入 scheme.empTagScope；空 → null</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KpiSchemeImportStrategy implements ImportStrategy {

    /** Excel 表头行（0-based 第 0 行）. */
    private static final int HEADER_ROW_INDEX = 0;

    /** errorSummary 行之间的分隔符. */
    private static final String ERROR_DELIMITER = "; ";

    /** 默认操作人（PerfImportBatch.createdBy 缺失时兜底）. */
    private static final String DEFAULT_OPERATOR = "import";

    // 列下标（与模板列序对齐；2026-06-17 模板取消「维度」列，维度改取自指标定义）
    private static final int COL_INDEX_NO = 0;
    private static final int COL_SCHEME_CODE = 1;
    private static final int COL_SCHEME_NAME = 2;
    private static final int COL_EMP_TAG_SCOPE = 3;
    private static final int COL_METRIC_NAME = 4;
    private static final int COL_EXPR_TYPE = 5;
    private static final int COL_EXPR_CONTENT = 6;
    private static final int COL_WEIGHT = 7;
    private static final int COL_MAX_SCORE = 8;
    private static final int COL_MIN_SCORE = 9;

    private final PerfMetricDefMapper metricDefMapper;
    private final PerfKpiSchemeMapper schemeMapper;
    /** 「员工标签范围」按标签名称解析 ID（governance 人员标签，2026-07-20 取代原角色范围）. */
    private final PersonTagApi personTagApi;
    private final KpiSchemeImportWriter kpiSchemeImportWriter;

    @Override
    public String importType() {
        return "KPI_SCHEME";
    }

    @Override
    public ImportResult execute(PerfImportBatch batch, MultipartFile file, ImportContext ctx) {
        // 本策略忽略 ctx.dataDate
        List<KpiSchemeImportRow> rows = parseRows(file);
        log.info("[KpiSchemeImportStrategy] 解析完成 batchId={}, rows={}",
                batch == null ? null : batch.getId(), rows.size());

        if (rows.isEmpty()) {
            return new ImportResult(0, 0, 0, null);
        }

        // 批量预取：指标名→def、人员标签名→标签ID，规避逐行 DB 往返
        Map<String, PerfMetricDef> defByName = loadMetricDefMap(rows);
        Map<String, Long> tagIdByName = loadTagIdMap(rows);

        // 第一遍：逐行校验 + 转换为 PerfKpiItem，收集错误（all-or-none：任一错误整批失败）
        List<String> errors = new ArrayList<>();
        List<PerfKpiItem> items = new ArrayList<>(rows.size());
        List<String> schemeCodes = new ArrayList<>(rows.size());

        for (KpiSchemeImportRow row : rows) {
            try {
                PerfKpiItem item = translateRow(row, defByName);
                items.add(item);
                schemeCodes.add(row.getSchemeCode().trim());
            } catch (PerfException pex) {
                errors.add("第" + row.getExcelRowNum() + "行: " + safeMessage(pex));
            }
        }

        // 第二遍：按方案分组解析「员工标签范围」（取每个方案首次出现行的值）
        Map<String, SchemeInfo> schemeInfos = new LinkedHashMap<>();
        if (errors.isEmpty()) {
            schemeInfos.putAll(resolveSchemeInfos(rows, tagIdByName, errors));
        }

        if (!errors.isEmpty()) {
            String detail = "共" + errors.size() + "行失败; " + String.join(ERROR_DELIMITER, errors);
            throw new PerfException(PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED, detail);
        }

        // 全部通过 → 整批落库（@Transactional，DB 异常自动回滚）
        String operator = resolveOperator(batch);
        kpiSchemeImportWriter.write(items, schemeCodes, schemeInfos, operator);

        // all-or-none 成功即全部成功
        return new ImportResult(rows.size(), rows.size(), 0, null);
    }

    /**
     * 将一行校验并转换为 PerfKpiItem（id/schemeId/createdTime 及兜底默认由 Writer 回填）.
     *
     * @throws PerfException 任一校验失败
     */
    PerfKpiItem translateRow(KpiSchemeImportRow row, Map<String, PerfMetricDef> defByName) {
        // 1) 方案编号 / 名称非空
        if (isBlank(row.getSchemeCode())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "方案编号必填");
        }
        if (isBlank(row.getSchemeName())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "方案名称必填");
        }

        // 2) 指标名称 → def（base_dim 取自指标定义，忽略模板「维度」列）
        if (isBlank(row.getMetricName())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "指标名称必填");
        }
        PerfMetricDef def = defByName.get(row.getMetricName().trim());
        if (def == null) {
            throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, row.getMetricName().trim());
        }

        // 3) 表达式类型路由（内容可空，不做语法校验）
        ExprType exprType = parseExprType(row.getExprTypeRaw());
        String content = isBlank(row.getExprContent()) ? null : row.getExprContent().trim();

        PerfKpiItem item = new PerfKpiItem();
        item.setMetricCode(def.getMetricCode());
        item.setBaseDim(def.getBaseDim());
        if (exprType == ExprType.SQL) {
            item.setSqlExpr(content);
            item.setFormula(null);
        } else {
            item.setFormula(content);
            item.setSqlExpr(null);
        }
        // 4) 权重 / 计分上线(maxScore) / 计分下限(minScore)，空则留 null 由 writer 兜底
        item.setWeight(row.getWeight());
        item.setMaxScore(row.getMaxScore());
        item.setMinScore(row.getMinScore());
        return item;
    }

    /**
     * 按方案分组解析「员工标签范围」（取每个方案首次出现行的值），校验标签名命中.
     *
     * @return schemeCode → SchemeInfo（schemeName + empTagScope，empTagScope 空则 null）
     */
    private Map<String, SchemeInfo> resolveSchemeInfos(List<KpiSchemeImportRow> rows,
                                                       Map<String, Long> tagIdByName,
                                                       List<String> errors) {
        Map<String, SchemeInfo> result = new LinkedHashMap<>();
        for (KpiSchemeImportRow row : rows) {
            String code = row.getSchemeCode().trim();
            if (result.containsKey(code)) {
                // 已取首次出现行的方案信息，后续行不再覆盖
                continue;
            }
            String empTagScope;
            try {
                empTagScope = resolveEmpTagScope(row.getEmpTagScopeRaw(), tagIdByName);
            } catch (PerfException pex) {
                errors.add("第" + row.getExcelRowNum() + "行: " + safeMessage(pex));
                continue;
            }
            result.put(code, new SchemeInfo(row.getSchemeName().trim(), empTagScope));
        }
        return result;
    }

    /** 标签范围原文（标签名称 CSV） → 标签 ID CSV（空 → null；任一标签名查不到 → 报错）. */
    private String resolveEmpTagScope(String raw, Map<String, Long> tagIdByName) {
        if (isBlank(raw)) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (String part : raw.split(",")) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            Long tagId = tagIdByName.get(name);
            if (tagId == null) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "人员标签不存在: " + name);
            }
            String idStr = String.valueOf(tagId);
            if (!ids.contains(idStr)) {
                ids.add(idStr);
            }
        }
        return ids.isEmpty() ? null : String.join(",", ids);
    }

    /** 表达式类型识别：计算表达式/FORMULA → FORMULA，SQL表达式/SQL → SQL，其它报错. */
    private static ExprType parseExprType(String raw) {
        if (raw == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "表达式类型非法");
        }
        String s = raw.trim();
        if ("计算表达式".equals(s) || "FORMULA".equalsIgnoreCase(s)) {
            return ExprType.FORMULA;
        }
        if ("SQL表达式".equals(s) || "SQL".equalsIgnoreCase(s)) {
            return ExprType.SQL;
        }
        throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "表达式类型非法: " + s);
    }

    /** 用 POI 解析 sheet1，第 0 行表头，从第 1 行起数据. */
    List<KpiSchemeImportRow> parseRows(MultipartFile file) {
        List<KpiSchemeImportRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (InputStream in = file.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {
            if (wb.getNumberOfSheets() == 0) {
                return rows;
            }
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            for (int r = HEADER_ROW_INDEX + 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowBlank(row)) {
                    continue;
                }
                rows.add(readRow(row, formatter));
            }
        } catch (IOException ex) {
            log.warn("[KpiSchemeImportStrategy] 读取文件失败: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID, ex, ex.getMessage());
        }
        return rows;
    }

    /** 读一行 11 列. */
    private static KpiSchemeImportRow readRow(Row row, DataFormatter formatter) {
        return KpiSchemeImportRow.builder()
                .excelRowNum(row.getRowNum() + 1)
                .indexNo(getString(row.getCell(COL_INDEX_NO), formatter))
                .schemeCode(getString(row.getCell(COL_SCHEME_CODE), formatter))
                .schemeName(getString(row.getCell(COL_SCHEME_NAME), formatter))
                .empTagScopeRaw(getString(row.getCell(COL_EMP_TAG_SCOPE), formatter))
                .metricName(getString(row.getCell(COL_METRIC_NAME), formatter))
                .exprTypeRaw(getString(row.getCell(COL_EXPR_TYPE), formatter))
                .exprContent(getString(row.getCell(COL_EXPR_CONTENT), formatter))
                .weight(getBigDecimal(row.getCell(COL_WEIGHT), formatter))
                .maxScore(getBigDecimal(row.getCell(COL_MAX_SCORE), formatter))
                .minScore(getBigDecimal(row.getCell(COL_MIN_SCORE), formatter))
                .build();
    }

    /** Cell 取字符串：数字类型整数优先，空 cell 返回 null. */
    private static String getString(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate().toString();
            }
            if (Math.floor(d) == d && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return BigDecimal.valueOf(d).toPlainString();
        }
        String s = formatter.formatCellValue(cell);
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** Cell 取 BigDecimal：不可解析返回 null（空 → 由 writer 兜底默认）. */
    private static BigDecimal getBigDecimal(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            String s = formatter.formatCellValue(cell);
            if (s == null || s.isBlank()) {
                return null;
            }
            return new BigDecimal(s.trim());
        } catch (NumberFormatException | IllegalStateException ex) {
            return null;
        }
    }

    /** 一行所有列空白视作空行，跳过. */
    private static boolean isRowBlank(Row row) {
        short last = row.getLastCellNum();
        for (int c = 0; c < last; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !(cell.getCellType() == CellType.STRING
                            && (cell.getStringCellValue() == null || cell.getStringCellValue().isBlank()))) {
                return false;
            }
        }
        return true;
    }

    /** 批量预取 metric_def，构建 metric_name → def 映射（首命中优先）. */
    private Map<String, PerfMetricDef> loadMetricDefMap(List<KpiSchemeImportRow> rows) {
        Set<String> names = new LinkedHashSet<>();
        for (KpiSchemeImportRow r : rows) {
            if (!isBlank(r.getMetricName())) {
                names.add(r.getMetricName().trim());
            }
        }
        if (names.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PerfMetricDef> defs = metricDefMapper.selectByMetricNames(new ArrayList<>(names));
        Map<String, PerfMetricDef> map = new HashMap<>(names.size());
        if (defs != null) {
            for (PerfMetricDef d : defs) {
                if (d.getMetricName() != null) {
                    map.putIfAbsent(d.getMetricName().trim(), d);
                }
            }
        }
        return map;
    }

    /** 批量预取人员标签：构建 tagName → tagId 映射（一次 IN 查询，首命中优先）. */
    private Map<String, Long> loadTagIdMap(List<KpiSchemeImportRow> rows) {
        List<String> names = new ArrayList<>();
        for (KpiSchemeImportRow row : rows) {
            String raw = row.getEmpTagScopeRaw();
            if (isBlank(raw)) {
                continue;
            }
            for (String part : raw.split(",")) {
                String name = part.trim();
                if (!name.isEmpty() && !names.contains(name)) {
                    names.add(name);
                }
            }
        }
        if (names.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PersonTagDTO> tags = personTagApi.getTagsByNames(names);
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Long> map = new HashMap<>(tags.size());
        for (PersonTagDTO t : tags) {
            if (t.getTagName() != null && t.getTagId() != null) {
                map.putIfAbsent(t.getTagName().trim(), t.getTagId());
            }
        }
        return map;
    }

    /** 操作人：取 PerfImportBatch.createdBy，缺失则 DEFAULT_OPERATOR. */
    private static String resolveOperator(PerfImportBatch batch) {
        if (batch != null && !isBlank(batch.getCreatedBy())) {
            return batch.getCreatedBy().trim();
        }
        return DEFAULT_OPERATOR;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safeMessage(Throwable ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    /** 表达式类型枚举. */
    private enum ExprType {
        /** 计算表达式 → formula 列. */
        FORMULA,
        /** SQL表达式 → sql_expr 列. */
        SQL
    }

    /**
     * 方案级信息（按 schemeCode 去重，仅新建方案时取用）.
     *
     * @param schemeName    方案名称
     * @param empTagScope  员工标签范围（人员标签 ID CSV，空 → null）
     */
    public record SchemeInfo(String schemeName, String empTagScope) {
    }
}
