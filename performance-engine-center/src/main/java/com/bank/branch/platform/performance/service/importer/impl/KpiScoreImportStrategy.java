package com.bank.branch.platform.performance.service.importer.impl;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.entity.PerfKpiScore;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfKpiScoreMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.importer.ImportContext;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * KPI 结果导入策略（importType={@code KPI_SCORE}），落 {@code PERF_KPI_SCORE}.
 *
 * <p>模板列（长格式 8 列）：维度 / 指标名称 / 维度对象 / 得分 / 实际值 / 权重(%) / 目标值 / 基础值。
 *
 * <p>整文件级参数（页面输入项，经 {@link ImportContext} 注入）：
 * <ul>
 *   <li>{@code data_date} —— 数据日期；缺失 fail-fast（Service 层已校验，这里再防御）</li>
 *   <li>{@code scheme_code} —— KPI 方案编码（= "KPI 编号"）；缺失 fail-fast</li>
 * </ul>
 *
 * <p><b>整批 all-or-none 语义</b>（与 TARGET/BASE_DATA/ALLOC 行级最大努力不同）：
 * 任一行校验失败 → 抛 {@link PerfErrorCode#IMPORT_BATCH_ALL_OR_NONE_FAILED}（detail 含所有失败行），
 * 整批不落库；全部通过 → 在同一事务内 upsert，任一 DB 异常自动回滚。
 *
 * <p>逐行校验与落库口径：
 * <ul>
 *   <li>维度 ∈ {EMP, ORG, CUST}（兼容中文 员工/机构/客户）</li>
 *   <li>指标名称 → 按 <b>维度 + 指标名</b> 在 {@code PERF_METRIC_DEF} 查指标编码（base_dim==维度 且 metric_name==指标名，
 *       deleted=0）；匹配不上 → 报错整批失败。落库用查到的 metric_code</li>
 *   <li>维度=ORG → 维度对象须在 {@code EXT_ORG_INFO} 机构编号存在（{@link OrgApi#getOrg}），落库用机构编号</li>
 *   <li>维度=EMP → 维度对象须在 {@code PT_USER.username}(工号) 存在（{@link UserApi#getUsersByUsernames}），落库用工号</li>
 *   <li>维度=CUST → 不校验维度对象，原样落 subject_id</li>
 * </ul>
 *
 * <p>upsert 依赖唯一键 {@code uk_date_scheme_metric_subject}
 * {@code (data_date, scheme_code, metric_code, subject_type, subject_id)}：已存在则更新数值。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KpiScoreImportStrategy implements ImportStrategy {

    /** errorSummary 行之间的分隔符. */
    private static final String SEP = "; ";

    private final PerfKpiScoreMapper kpiScoreMapper;
    private final PerfMetricDefMapper metricDefMapper;
    private final OrgApi orgApi;
    private final UserApi userApi;

    @Override
    public String importType() {
        return "KPI_SCORE";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult execute(PerfImportBatch batch, MultipartFile file, ImportContext ctx) {
        // 整文件级 fail-fast：数据日期 + KPI 方案编码（页面输入项）
        LocalDate dataDate = ctx == null ? null : ctx.dataDate();
        String schemeCode = ctx == null ? null : ctx.schemeCode();
        if (dataDate == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "数据日期必填（KPI_SCORE）");
        }
        if (schemeCode == null || schemeCode.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "KPI 方案编码必填（KPI_SCORE）");
        }

        // 1) 解析所有行
        List<Raw> rows = parse(file);
        if (rows.isEmpty()) {
            ImportResult empty = new ImportResult();
            empty.setTotalRows(0);
            return empty;
        }

        // 2) 批量按指标名查指标定义，构建 (维度|指标名) → metric_code 映射（首命中优先，避免逐行 DB 往返）
        Set<String> names = new LinkedHashSet<>();
        for (Raw r : rows) {
            if (r.metricName != null && !r.metricName.isBlank()) {
                names.add(r.metricName.trim());
            }
        }
        Map<String, String> dimNameToCode = new HashMap<>();
        if (!names.isEmpty()) {
            List<PerfMetricDef> defs = metricDefMapper.selectByMetricNames(new ArrayList<>(names));
            if (defs != null) {
                for (PerfMetricDef d : defs) {
                    if (d == null || d.getMetricName() == null) {
                        continue;
                    }
                    String key = dimKey(d.getBaseDim(), d.getMetricName());
                    dimNameToCode.putIfAbsent(key, d.getMetricCode());
                }
            }
        }

        // 3) all-or-none 校验：逐行收集错误 + 组装待 upsert 行
        List<String> errors = new ArrayList<>();
        List<PerfKpiScore> toUpsert = new ArrayList<>(rows.size());
        for (Raw r : rows) {
            String loc = r.locate();
            String dim = normalizeDim(r.dim);
            if (dim == null) {
                errors.add(loc + "维度非法（期望 EMP/ORG/CUST 或 员工/机构/客户）: " + r.dim);
                continue;
            }
            if (r.metricName == null || r.metricName.isBlank()) {
                errors.add(loc + "指标名称必填");
                continue;
            }
            if (r.subject == null || r.subject.isBlank()) {
                errors.add(loc + "维度对象必填");
                continue;
            }
            String metricCode = dimNameToCode.get(dimKey(dim, r.metricName.trim()));
            if (metricCode == null) {
                errors.add(loc + "指标名称匹配不上（维度=" + dim + "，指标名=" + r.metricName.trim() + "）");
                continue;
            }
            String subject = r.subject.trim();
            if ("ORG".equals(dim)) {
                // 维度对象=机构编号(EXT_ORG_INFO.DEPT_NO)，按 DEPT_NO 校验存在性
                if (orgApi.getOrgByDeptNo(subject) == null) {
                    errors.add(loc + "机构不存在（EXT_ORG_INFO 部门编号 DEPT_NO）: " + subject);
                    continue;
                }
            } else if ("EMP".equals(dim)) {
                // 轻量 filterExistingUsernames（单次 IN、仅存在性）规避 getUsersByUsernames 的逐人 N+1
                if (userApi.filterExistingUsernames(List.of(subject)).isEmpty()) {
                    errors.add(loc + "员工不存在（PT_USER 工号）: " + subject);
                    continue;
                }
            }
            // CUST 不校验维度对象

            PerfKpiScore s = new PerfKpiScore();
            s.setDataDate(dataDate);
            s.setSchemeCode(schemeCode);
            s.setMetricCode(metricCode);
            s.setSubjectType(dim);
            s.setSubjectId(subject);
            s.setScore(r.score);
            s.setActualValue(r.actual);
            s.setWeight(r.weight);
            s.setTargetValue(r.target);
            s.setBaseValue(r.base);
            toUpsert.add(s);
        }

        if (!errors.isEmpty()) {
            // 整批失败：抛异常 → Service 批次置 FAILED + remark，并向页面返回错误
            String detail = "KPI 结果导入校验失败（整批未落库）：" + String.join(SEP, errors);
            throw new PerfException(PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED, detail);
        }

        // 4) 全部通过 → 事务内 upsert（重复行按唯一键更新数值）
        int updated = 0;
        for (PerfKpiScore s : toUpsert) {
            int r = kpiScoreMapper.upsert(s);
            if (r == 2) { // MySQL：插入返回 1，更新返回 2
                updated++;
            }
        }

        ImportResult result = new ImportResult();
        result.setTotalRows(rows.size());
        result.setSuccessRows(toUpsert.size());
        result.setErrorRows(0);
        result.setUpdatedRows(updated);
        log.info("[KpiScoreImportStrategy] 导入成功 batchId={}, scheme={}, dataDate={}, total={}, updated={}",
                batch == null ? null : batch.getId(), schemeCode, dataDate, rows.size(), updated);
        return result;
    }

    /** 解析所有 Sheet 的数据行（跳过每个 Sheet 的表头行 0）. */
    private List<Raw> parse(MultipartFile file) {
        List<Raw> list = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (InputStream in = file.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {
            int sheetCnt = wb.getNumberOfSheets();
            for (int si = 0; si < sheetCnt; si++) {
                Sheet sheet = wb.getSheetAt(si);
                if (sheet == null) {
                    continue;
                }
                String sheetName = sheet.getSheetName();
                int last = sheet.getLastRowNum();
                for (int ri = 1; ri <= last; ri++) { // 行 0 为表头
                    Row row = sheet.getRow(ri);
                    if (row == null) {
                        continue;
                    }
                    Raw raw = new Raw();
                    raw.sheet = sheetName;
                    raw.rowNo = ri + 1; // Excel 行号（1-based）
                    raw.dim = getString(row.getCell(0), fmt);
                    raw.metricName = getString(row.getCell(1), fmt);
                    raw.subject = getString(row.getCell(2), fmt);
                    raw.score = getDecimal(row.getCell(3), fmt);
                    raw.actual = getDecimal(row.getCell(4), fmt);
                    raw.weight = getDecimal(row.getCell(5), fmt);
                    raw.target = getDecimal(row.getCell(6), fmt);
                    raw.base = getDecimal(row.getCell(7), fmt);
                    // 整行空白跳过
                    if (raw.isBlank()) {
                        continue;
                    }
                    list.add(raw);
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "KPI 结果文件解析失败: " + e.getMessage());
        }
        return list;
    }

    private static String dimKey(String baseDim, String metricName) {
        return (baseDim == null ? "" : baseDim.trim().toUpperCase()) + "|"
                + (metricName == null ? "" : metricName.trim());
    }

    /** 维度归一：EMP/ORG/CUST（兼容中文 员工/机构/客户）；无法识别返回 null. */
    private static String normalizeDim(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toUpperCase();
        switch (v) {
            case "EMP": case "员工": return "EMP";
            case "ORG": case "机构": return "ORG";
            case "CUST": case "客户": return "CUST";
            default: return null;
        }
    }

    private static String getString(Cell cell, DataFormatter fmt) {
        if (cell == null) {
            return null;
        }
        String s = fmt.formatCellValue(cell);
        return s == null ? null : s.trim();
    }

    /** 读数值列：空 → null；非空不可解析 → 抛（属文件格式问题，整批失败）. */
    private static BigDecimal getDecimal(Cell cell, DataFormatter fmt) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String s = fmt.formatCellValue(cell);
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "数值列格式非法: " + s);
        }
    }

    /** 一行原始数据（解析后未校验）. */
    private static final class Raw {
        private String sheet;
        private int rowNo;
        private String dim;
        private String metricName;
        private String subject;
        private BigDecimal score;
        private BigDecimal actual;
        private BigDecimal weight;
        private BigDecimal target;
        private BigDecimal base;

        private boolean isBlank() {
            return (dim == null || dim.isBlank())
                    && (metricName == null || metricName.isBlank())
                    && (subject == null || subject.isBlank())
                    && score == null && actual == null && weight == null && target == null && base == null;
        }

        private String locate() {
            return "[" + sheet + " 第" + rowNo + "行] ";
        }
    }
}
