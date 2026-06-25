package com.bank.branch.platform.performance.service.importer.impl;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.service.importer.ImportContext;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import com.bank.branch.platform.performance.service.importer.TargetPlanImportWriter;
import com.bank.branch.platform.performance.service.importer.model.TargetPlanImportRow;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 目标方案 Excel 导入策略（2026-06-17，importType=TARGET_PLAN）.
 *
 * <p>复用现有 {@code POST /api/perf/import/upload?importType=TARGET_PLAN} 端点，无需新增资源/Controller。
 *
 * <p>模板列（sheet1，第 0 行表头，从第 1 行起数据）：
 * <pre>
 *   序号 | 目标方案编号 | 目标方案名称 | 阶段名称 | 阶段起始日期 | 阶段截止日期
 *        | 维度 | 工号/部门编号 | 指标名称 | 目标值 | 基础值
 * </pre>
 *
 * <p><b>整批 all-or-none 语义</b>（与 TARGET/BASE_DATA/ALLOC 的"行级最大努力"不同）：
 * 先解析全部行 → 全量校验，任一行错误就抛 {@link PerfErrorCode#IMPORT_BATCH_ALL_OR_NONE_FAILED}
 * （message 含行号与原因），不写任何库；全部通过后在 {@link TargetPlanImportWriter#write} 的
 * {@code @Transactional} 方法里落库，DB 异常自动回滚。
 *
 * <p>每行校验/转换：
 * <ol>
 *   <li>目标方案编号、名称非空</li>
 *   <li>维度：员工→EMP，机构→ORG（兼容 EMP/ORG 大小写），其它报错</li>
 *   <li>指标名称必须在 PERF_METRIC_DEF 存在（批量预取 name→def），取 metricCode；
 *       且指标 base_dim 必须与行维度一致（维度正确性）</li>
 *   <li>对象存在性：EMP → 工号须在 PT_USER（UserApi.filterExistingUsernames，批量分片 IN）；
 *       ORG → 部门编号须在机构表（OrgApi.getOrgByDeptNo），并把 subjectId 归一为内部机构编码</li>
 *   <li>阶段起止日期可解析为 LocalDate；阶段名称非空（uk 的一部分）</li>
 *   <li>目标值非空且可解析 BigDecimal；基础值可空</li>
 *   <li>日期不重叠：同方案下同对象同指标，不同阶段的 [start,end] 闭区间不可重叠
 *       （文件内 + 既有 DB 行联合判定）</li>
 * </ol>
 *
 * <p>cycle_key 列 NOT NULL 但不在 uk 内：派生为 {@code String.valueOf(startDate.getYear())}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TargetPlanImportStrategy implements ImportStrategy {

    /** Excel 表头行（0-based 第 0 行）. */
    private static final int HEADER_ROW_INDEX = 0;

    /** errorSummary 行之间的分隔符. */
    private static final String ERROR_DELIMITER = "; ";

    /** 默认操作人（PerfImportBatch.createdBy 缺失时兜底）. */
    private static final String DEFAULT_OPERATOR = "import";

    /** yyyyMMdd 整数日期解析格式. */
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 列下标（与模板列序对齐）
    private static final int COL_INDEX_NO = 0;
    private static final int COL_PLAN_CODE = 1;
    private static final int COL_PLAN_NAME = 2;
    private static final int COL_STAGE_NAME = 3;
    private static final int COL_START_DATE = 4;
    private static final int COL_END_DATE = 5;
    private static final int COL_DIM = 6;
    private static final int COL_SUBJECT_KEY = 7;
    private static final int COL_METRIC_NAME = 8;
    private static final int COL_TARGET_VALUE = 9;
    private static final int COL_BASE_VALUE = 10;

    private final PerfMetricDefMapper metricDefMapper;
    private final OrgApi orgApi;
    private final UserApi userApi;
    private final PerfTargetPlanMapper targetPlanMapper;
    private final PerfTargetValueMapper targetValueMapper;
    private final TargetPlanImportWriter targetPlanImportWriter;

    @Override
    public String importType() {
        return "TARGET_PLAN";
    }

    @Override
    public ImportResult execute(PerfImportBatch batch, MultipartFile file, ImportContext ctx) {
        // 本策略忽略 ctx.dataDate
        List<TargetPlanImportRow> rows = parseRows(file);
        log.info("[TargetPlanImportStrategy] 解析完成 batchId={}, rows={}",
                batch == null ? null : batch.getId(), rows.size());

        if (rows.isEmpty()) {
            return new ImportResult(0, 0, 0, null);
        }

        // 批量预取：指标名→def、有效 EMP 工号集、ORG 部门编号→机构，规避逐行 DB 往返
        Map<String, PerfMetricDef> defByName = loadMetricDefMap(rows);
        Set<String> validEmpKeys = loadValidEmpKeys(rows);
        Map<String, OrgDTO> orgByDeptNo = loadOrgMap(rows);

        // 第一遍：全量校验 + 转换，收集错误（all-or-none：任一错误整批失败）
        List<String> errors = new ArrayList<>();
        List<PerfTargetValue> values = new ArrayList<>(rows.size());
        List<String> planCodes = new ArrayList<>(rows.size());
        List<String> planNames = new ArrayList<>(rows.size());

        for (TargetPlanImportRow row : rows) {
            try {
                PerfTargetValue v = translateRow(row, defByName, validEmpKeys, orgByDeptNo);
                values.add(v);
                planCodes.add(row.getPlanCode().trim());
                planNames.add(row.getPlanName().trim());
            } catch (PerfException pex) {
                errors.add("第" + row.getExcelRowNum() + "行: " + safeMessage(pex));
            }
        }

        // 第二遍：跨行日期重叠校验（仅在逐行均通过时执行，避免基于半成品数据误判）
        if (errors.isEmpty()) {
            errors.addAll(checkDateOverlaps(values, planCodes));
        }

        if (!errors.isEmpty()) {
            String detail = "共" + errors.size() + "行失败; " + String.join(ERROR_DELIMITER, errors);
            throw new PerfException(PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED, detail);
        }

        // 全部通过 → 整批落库（@Transactional，DB 异常自动回滚）
        String operator = resolveOperator(batch);
        targetPlanImportWriter.write(values, planCodes, planNames, operator);

        // all-or-none 成功即全部成功
        return new ImportResult(rows.size(), rows.size(), 0, null);
    }

    /**
     * 将一行校验并转换为 PerfTargetValue（id/planId/createdBy/createdTime 留空，由 Writer 回填）.
     *
     * @throws PerfException 任一校验失败
     */
    PerfTargetValue translateRow(TargetPlanImportRow row, Map<String, PerfMetricDef> defByName,
                                 Set<String> validEmpKeys, Map<String, OrgDTO> orgByDeptNo) {
        // 1) 方案编号 / 名称非空
        if (isBlank(row.getPlanCode())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "目标方案编号必填");
        }
        if (isBlank(row.getPlanName())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "目标方案名称必填");
        }

        // 2) 维度归一
        String subjectType = normalizeDim(row.getDimRaw());

        // 3) subjectKey 非空
        if (isBlank(row.getSubjectKey())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "工号/部门编号必填");
        }
        String subjectKey = row.getSubjectKey().trim();

        // 4) 指标名称 → def + 维度正确性（指标 base_dim 必须等于行维度）
        if (isBlank(row.getMetricName())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "指标名称必填");
        }
        PerfMetricDef def = defByName.get(row.getMetricName().trim());
        if (def == null) {
            throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, row.getMetricName().trim());
        }
        if (!subjectType.equals(def.getBaseDim())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "维度与指标不匹配（指标 " + row.getMetricName().trim()
                            + " 期望 " + def.getBaseDim() + "，行维度 " + subjectType + "）");
        }

        // 5) 对象存在性 + ORG 归一
        String subjectId;
        if ("ORG".equals(subjectType)) {
            OrgDTO org = orgByDeptNo.get(subjectKey);
            if (org == null || isBlank(org.getOrgCode())) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                        "机构不存在（部门编号）: " + subjectKey);
            }
            subjectId = org.getOrgCode();
        } else {
            if (!validEmpKeys.contains(subjectKey)) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "员工不存在（工号）: " + subjectKey);
            }
            subjectId = subjectKey;
        }

        // 6) 阶段名称 + 起止日期
        if (isBlank(row.getStageName())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "阶段名称必填");
        }
        if (row.getStartDate() == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "阶段起始日期非法或为空");
        }
        if (row.getEndDate() == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "阶段截止日期非法或为空");
        }
        if (row.getStartDate().isAfter(row.getEndDate())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "阶段起始日期不能晚于截止日期");
        }

        // 7) 目标值非空可解析；基础值可空
        if (row.getTargetValue() == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "目标值必填且必须为数字");
        }

        PerfTargetValue v = new PerfTargetValue();
        v.setSubjectType(subjectType);
        v.setSubjectId(subjectId);
        v.setMetricCode(def.getMetricCode());
        v.setStageName(row.getStageName().trim());
        v.setStartDate(row.getStartDate());
        v.setEndDate(row.getEndDate());
        v.setTargetValue(row.getTargetValue());
        v.setBaseValue(row.getBaseValue());
        // cycle_key 列 NOT NULL 但不在 uk 内，按起始日期年份派生
        v.setCycleKey(String.valueOf(row.getStartDate().getYear()));
        return v;
    }

    /**
     * 日期不重叠校验：在同一目标方案(planCode)下、同一对象(subjectType+subjectId)、同一指标(metricCode)，
     * 不同阶段(stageName)的 [start,end] 闭区间不可重叠。
     *
     * <p>判定集合 = 文件内同组各阶段区间 ∪ 既有 DB 行中阶段名不在文件里的区间（导入后仍保留的）；
     * 文件里同阶段的会以新区间覆盖旧 DB 行，旧区间不参与判定。
     *
     * @return 重叠错误明细（空表示通过）
     */
    private List<String> checkDateOverlaps(List<PerfTargetValue> values, List<String> planCodes) {
        List<String> errors = new ArrayList<>();
        // 按 (planCode|subjectType|subjectId|metricCode) 分组
        Map<String, List<PerfTargetValue>> groups = new LinkedHashMap<>();
        Map<String, String> groupPlanCode = new HashMap<>();
        for (int i = 0; i < values.size(); i++) {
            PerfTargetValue v = values.get(i);
            String planCode = planCodes.get(i);
            String key = planCode + "|" + v.getSubjectType() + "|" + v.getSubjectId() + "|" + v.getMetricCode();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
            groupPlanCode.put(key, planCode);
        }

        // 缓存 planCode → planId（不存在为 null，按"无既有行"处理）
        Map<String, String> planIdCache = new HashMap<>();
        // 缓存 planId → (对象+指标分组键 → 既有 DB 行)：每个方案只 selectAllByPlanId 一次（避免逐组 N+1）
        Map<String, Map<String, List<PerfTargetValue>>> dbGroupsByPlanId = new HashMap<>();

        for (Map.Entry<String, List<PerfTargetValue>> e : groups.entrySet()) {
            List<PerfTargetValue> grp = e.getValue();
            PerfTargetValue head = grp.get(0);
            String planCode = groupPlanCode.get(e.getKey());

            // 文件内：阶段名 → 区间；同组同阶段名重复 → 报错
            Map<String, long[]> stageRange = new LinkedHashMap<>();
            boolean dupStage = false;
            for (PerfTargetValue v : grp) {
                if (stageRange.containsKey(v.getStageName())) {
                    errors.add("方案[" + planCode + "] 对象[" + v.getSubjectId() + "] 指标["
                            + v.getMetricCode() + "] 阶段[" + v.getStageName() + "] 文件内重复");
                    dupStage = true;
                    break;
                }
                stageRange.put(v.getStageName(),
                        new long[]{v.getStartDate().toEpochDay(), v.getEndDate().toEpochDay()});
            }
            if (dupStage) {
                continue;
            }

            // 并入既有 DB 行（阶段名不在文件里的）
            String planId = planIdCache.computeIfAbsent(planCode, pc -> {
                PerfTargetPlan p = targetPlanMapper.selectByPlanCode(pc);
                return p == null ? null : p.getId();
            });
            if (planId != null) {
                // 该方案的既有目标值一次性拉取并按 (对象+指标) 预分组，本组直接查表
                Map<String, List<PerfTargetValue>> dbGroups =
                        dbGroupsByPlanId.computeIfAbsent(planId, this::loadDbGroups);
                List<PerfTargetValue> dbRows = dbGroups.get(subjectMetricKey(head));
                if (dbRows != null) {
                    for (PerfTargetValue db : dbRows) {
                        if (db.getStartDate() == null || db.getEndDate() == null
                                || stageRange.containsKey(db.getStageName())) {
                            continue;
                        }
                        stageRange.put(db.getStageName(),
                                new long[]{db.getStartDate().toEpochDay(), db.getEndDate().toEpochDay()});
                    }
                }
            }

            // 两两判重叠（闭区间：s1<=e2 && s2<=e1）
            List<long[]> ranges = new ArrayList<>(stageRange.values());
            boolean overlapped = false;
            for (int a = 0; a < ranges.size() && !overlapped; a++) {
                for (int b = a + 1; b < ranges.size(); b++) {
                    long[] r1 = ranges.get(a);
                    long[] r2 = ranges.get(b);
                    if (r1[0] <= r2[1] && r2[0] <= r1[1]) {
                        errors.add("方案[" + planCode + "] 对象[" + head.getSubjectId() + "] 指标["
                                + head.getMetricCode() + "] 同对象同指标阶段日期重叠");
                        overlapped = true;
                        break;
                    }
                }
            }
        }
        return errors;
    }

    /** 一次性拉取某方案全部目标值，按 (对象类型|对象|指标) 分组（供重叠校验内存查表）. */
    private Map<String, List<PerfTargetValue>> loadDbGroups(String planId) {
        List<PerfTargetValue> all = targetValueMapper.selectAllByPlanId(planId);
        Map<String, List<PerfTargetValue>> map = new HashMap<>();
        if (all != null) {
            for (PerfTargetValue r : all) {
                map.computeIfAbsent(subjectMetricKey(r), k -> new ArrayList<>()).add(r);
            }
        }
        return map;
    }

    /** (对象类型|对象|指标) 分组键. */
    private static String subjectMetricKey(PerfTargetValue v) {
        return v.getSubjectType() + "|" + v.getSubjectId() + "|" + v.getMetricCode();
    }

    /** 维度归一：员工/EMP → EMP，机构/ORG → ORG，其它报错. */
    private static String normalizeDim(String raw) {
        String s = tryNormalizeDim(raw);
        if (s == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "维度非法（期望 员工/机构 或 EMP/ORG）: " + raw);
        }
        return s;
    }

    /** 维度归一（不抛异常版，用于预取分类）：非法返回 null. */
    private static String tryNormalizeDim(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if ("员工".equals(s) || "EMP".equalsIgnoreCase(s)) {
            return "EMP";
        }
        if ("机构".equals(s) || "ORG".equalsIgnoreCase(s)) {
            return "ORG";
        }
        return null;
    }

    /** 用 POI 解析 sheet1，第 0 行表头，从第 1 行起数据. */
    List<TargetPlanImportRow> parseRows(MultipartFile file) {
        List<TargetPlanImportRow> rows = new ArrayList<>();
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
            log.warn("[TargetPlanImportStrategy] 读取文件失败: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID, ex, ex.getMessage());
        }
        return rows;
    }

    /** 读一行 11 列. */
    private static TargetPlanImportRow readRow(Row row, DataFormatter formatter) {
        return TargetPlanImportRow.builder()
                .excelRowNum(row.getRowNum() + 1)
                .indexNo(getString(row.getCell(COL_INDEX_NO), formatter))
                .planCode(getString(row.getCell(COL_PLAN_CODE), formatter))
                .planName(getString(row.getCell(COL_PLAN_NAME), formatter))
                .stageName(getString(row.getCell(COL_STAGE_NAME), formatter))
                .startDate(getLocalDate(row.getCell(COL_START_DATE), formatter))
                .endDate(getLocalDate(row.getCell(COL_END_DATE), formatter))
                .dimRaw(getString(row.getCell(COL_DIM), formatter))
                .subjectKey(getString(row.getCell(COL_SUBJECT_KEY), formatter))
                .metricName(getString(row.getCell(COL_METRIC_NAME), formatter))
                .targetValue(getBigDecimal(row.getCell(COL_TARGET_VALUE), formatter))
                .baseValue(getBigDecimal(row.getCell(COL_BASE_VALUE), formatter))
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

    /**
     * Cell 取日期：既支持数值 20260101（yyyyMMdd）也支持真实日期格式 cell.
     * 不可解析返回 null（由 translateRow 报错）.
     */
    private static LocalDate getLocalDate(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                // 真实日期格式 cell → 直接取日期；否则按整数 yyyyMMdd 解析
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate();
                }
                long n = (long) cell.getNumericCellValue();
                return LocalDate.parse(String.valueOf(n), YYYYMMDD);
            }
            String s = formatter.formatCellValue(cell);
            if (s == null || s.isBlank()) {
                return null;
            }
            return parseDateString(s.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** 字符串日期：优先 yyyyMMdd，其次 yyyy-MM-dd（ISO）. */
    private static LocalDate parseDateString(String s) {
        try {
            return LocalDate.parse(s, YYYYMMDD);
        } catch (RuntimeException ignore) {
            return LocalDate.parse(s); // ISO yyyy-MM-dd
        }
    }

    /** Cell 取 BigDecimal：不可解析返回 null（由 translateRow 报错）. */
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
    private Map<String, PerfMetricDef> loadMetricDefMap(List<TargetPlanImportRow> rows) {
        Set<String> names = new LinkedHashSet<>();
        for (TargetPlanImportRow r : rows) {
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

    /** 批量预取有效 EMP 工号集（维度=员工的行的工号 → UserApi 校验存在性）. */
    private Set<String> loadValidEmpKeys(List<TargetPlanImportRow> rows) {
        Set<String> empKeys = new LinkedHashSet<>();
        for (TargetPlanImportRow r : rows) {
            if ("EMP".equals(tryNormalizeDim(r.getDimRaw())) && !isBlank(r.getSubjectKey())) {
                empKeys.add(r.getSubjectKey().trim());
            }
        }
        if (empKeys.isEmpty()) {
            return Collections.emptySet();
        }
        // 以 PT_USER 为准的批量存在性校验（单次/分片 IN，避免 getUsersByUsernames 的逐人 N+1）
        List<String> existing = userApi.filterExistingUsernames(new ArrayList<>(empKeys));
        return existing == null ? Collections.emptySet() : new HashSet<>(existing);
    }

    /** 批量预取 ORG 部门编号 → 机构（去重逐个 getOrgByDeptNo，缺失放 null）. */
    private Map<String, OrgDTO> loadOrgMap(List<TargetPlanImportRow> rows) {
        Set<String> deptNos = new LinkedHashSet<>();
        for (TargetPlanImportRow r : rows) {
            if ("ORG".equals(tryNormalizeDim(r.getDimRaw())) && !isBlank(r.getSubjectKey())) {
                deptNos.add(r.getSubjectKey().trim());
            }
        }
        if (deptNos.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, OrgDTO> map = new HashMap<>(deptNos.size());
        for (String deptNo : deptNos) {
            map.put(deptNo, orgApi.getOrgByDeptNo(deptNo));
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
}
