package com.bank.branch.platform.performance.service.importer.impl;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.SysControlService;
import com.bank.branch.platform.performance.service.importer.ImportContext;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import com.bank.branch.platform.performance.service.importer.model.MetricResultImportRow;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 指标结果导入策略（V1.12，importType=METRIC_RESULT）.
 *
 * <p>对齐 {@code docs/指标结果模板.xlsx}（长格式 5 列固定）：
 * <pre>
 *   数据日期：由前端日期选择器经 HTTP 参数 dataDate (yyyy-MM-dd) 传入，
 *            整文件（含多 Sheet）共用同一 dataDate；
 *            缺失/格式错由 Controller/Service 层 fail-fast，不进入本策略。
 *   Sheet 名：纯展示用（业务方任意命名）
 *   列 1：序号（透传，不参与业务）
 *   列 2：基础维度（EMP / ORG / CUST / 空）
 *   列 3：维度对象（员工号 / 机构号 / 客户编号）
 *   列 4：指标名称（中文，必须存在于 PERF_METRIC_DEF）
 *   列 5：指标数值
 * </pre>
 *
 * <p>历史：V1.12 初版用 Sheet 名携带 dataDate（每 Sheet 一个日期），2026-05-19 改为
 * 前端日期选择器经 HTTP 参数传入，行为简化为整文件统一 dataDate。
 *
 * <p>校验项（行级最大努力，单行失败累计到 errorSummary 不抛异常）：
 * <ol type="a">
 *   <li>基础维度 ∈ {EMP, ORG, CUST, null}</li>
 *   <li>指标名称必须在 PERF_METRIC_DEF（{@code deleted=0}）中存在</li>
 *   <li>基础维度=EMP → 维度对象必须在 ADDRBOOK_EMPLOYEE 中存在（{@link AddressBookApi#getEmployee}）</li>
 *   <li>基础维度=ORG → 维度对象必须在 EXT_ORG_INFO 中存在（{@link OrgApi#getOrg}）</li>
 *   <li>基础维度=CUST/null 时跳过维度对象存在性校验</li>
 * </ol>
 *
 * <p>入库路由：
 * <ul>
 *   <li>baseDim=EMP → EMP_INDEX_RESULT.val_${valSlot}</li>
 *   <li>baseDim=ORG → ORG_INDEX_RESULT.val_${valSlot}</li>
 *   <li>baseDim=CUST → CUST_INDEX_RESULT.val_${valSlot}</li>
 *   <li>baseDim=null → 跳过入库（无主体维度，不入三大宽表，记 successRows）</li>
 * </ul>
 *
 * <p>UPSERT：依赖宽表 {@code uk_subject_date_ver}，由 mapper XML 的
 * {@code INSERT ... ON DUPLICATE KEY UPDATE val_${slot}=VALUES(...), updated_time=NOW()}
 * 完成"指标值刷新 + 时间戳更新"。
 *
 * <p>version 取值：调 {@link SysControlService#getCurrentVersion}(baseDim)；
 * 维度无 sys_control 记录时降级为 {@code "V1"}（{@code SYS_CONTROL_VERSION_NOT_FOUND} 捕获）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricResultImportStrategy implements ImportStrategy {

    /** Excel 表头行（1-based 第 1 行）. */
    private static final int HEADER_ROW_INDEX = 0;

    /** 单元格分隔符（错误消息行内分隔）. */
    private static final String ERROR_DELIMITER = "; ";

    /** sys_control 缺失时的默认版本. */
    private static final String DEFAULT_VERSION = "V1";

    /** 允许的基础维度值. */
    private static final Set<String> ALLOWED_BASE_DIMS = new HashSet<>(Arrays.asList("EMP", "ORG", "CUST"));

    private final PerfMetricDefMapper metricDefMapper;
    private final EmpIndexResultMapper empIndexResultMapper;
    private final OrgIndexResultMapper orgIndexResultMapper;
    private final CustIndexResultMapper custIndexResultMapper;
    private final UserApi userApi;
    private final OrgApi orgApi;
    private final SysControlService sysControlService;

    @Override
    public String importType() {
        return "METRIC_RESULT";
    }

    @Override
    public ImportResult execute(PerfImportBatch batch, MultipartFile file, ImportContext ctx) {
        // dataDate 由 Controller/Service 层 fail-fast 校验，进入策略时必非空
        LocalDate dataDate = ctx == null ? null : ctx.dataDate();
        if (dataDate == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "dataDate 必填（METRIC_RESULT）");
        }

        List<MetricResultImportRow> rows = parseAllSheets(file);
        log.info("[MetricResultImportStrategy] 解析完成 batchId={}, rows={}, dataDate={}",
                batch.getId(), rows.size(), dataDate);

        if (rows.isEmpty()) {
            return new ImportResult(0, 0, 0, null);
        }

        // 批量预取所有 metricName → PerfMetricDef，规避逐行 DB 往返
        Map<String, PerfMetricDef> defByDimAndName = loadMetricDefMap(rows);

        // version 缓存（每个 baseDim 一次 sys_control 查询）
        Map<String, String> versionByDim = new HashMap<>();

        List<String> errors = new ArrayList<>();
        int successRows = 0;

        for (MetricResultImportRow row : rows) {
            try {
                validateBaseDim(row);

                if (row.getBaseDim() != null) {
                    validateSubjectExists(row);
                }
                if (row.getSubjectKey() == null || row.getSubjectKey().isBlank()) {
                    // baseDim=null 时维度对象通常也应为空；填了也无处可去
                    if (row.getBaseDim() != null) {
                        throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "维度对象必填");
                    }
                }
                if (row.getMetricName() == null || row.getMetricName().isBlank()) {
                    throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "指标名称必填");
                }
                if (row.getValue() == null) {
                    throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "指标数值必填");
                }

                // 按 (维度 + 指标名) 查指标定义：同名指标可跨维度并存，行的维度决定取哪一条。
                // 该名在本行维度下不存在 → METRIC_NOT_FOUND（区别于"换个维度才有"）。
                PerfMetricDef def = defByDimAndName.get(dimKey(row.getBaseDim(), row.getMetricName()));
                if (def == null) {
                    throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND,
                            "指标在该维度下不存在（维度=" + row.getBaseDim()
                                    + "，指标名=" + row.getMetricName() + "）");
                }
                if (def.getValSlot() == null) {
                    throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                            "指标未分配 val_slot: " + row.getMetricName());
                }
                // 按 (维度+名) 命中后 def.baseDim == row.baseDim 恒成立，无需再单独做一致性校验。

                if (row.getBaseDim() == null) {
                    // 维度无关型：def.baseDim 也为 null 才会到此分支，仍不入三大宽表
                    successRows++;
                    continue;
                }

                String version = versionByDim.computeIfAbsent(row.getBaseDim(), this::resolveVersion);
                dispatchUpsert(row.getBaseDim(), row.getSubjectKey(), dataDate,
                        version, def.getValSlot(), row.getValue());
                successRows++;
            } catch (RuntimeException ex) {
                errors.add(formatErrorPrefix(row) + safeMessage(ex));
            }
        }

        String summary = errors.isEmpty() ? null : String.join(ERROR_DELIMITER, errors);
        return new ImportResult(rows.size(), successRows, rows.size() - successRows, summary);
    }

    /** 用 POI 解析多 Sheet，所有 Sheet 行汇总（Sheet 名仅用于错误定位）. */
    List<MetricResultImportRow> parseAllSheets(MultipartFile file) {
        List<MetricResultImportRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (InputStream in = file.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                int lastRow = sheet.getLastRowNum();
                for (int r = HEADER_ROW_INDEX + 1; r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null || isRowBlank(row)) {
                        continue;
                    }
                    MetricResultImportRow ir = readRow(row, formatter, sheetName);
                    rows.add(ir);
                }
            }
        } catch (IOException ex) {
            log.warn("[MetricResultImportStrategy] 读取文件失败: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID, ex, ex.getMessage());
        }
        return rows;
    }

    /** 读一行 5 列：序号 / baseDim / subject / metricName / value. */
    private static MetricResultImportRow readRow(Row row, DataFormatter formatter,
                                                 String sheetName) {
        return MetricResultImportRow.builder()
                .sheetName(sheetName)
                .excelRowNum(row.getRowNum() + 1)
                .indexNo(getString(row.getCell(0), formatter))
                .baseDim(normalizeBaseDim(getString(row.getCell(1), formatter)))
                .subjectKey(getString(row.getCell(2), formatter))
                .metricName(getString(row.getCell(3), formatter))
                .value(getBigDecimal(row.getCell(4), formatter))
                .build();
    }

    /** 规范基础维度：空串视为 null，其余大写化保留. */
    private static String normalizeBaseDim(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty() || "NULL".equalsIgnoreCase(s)) {
            return null;
        }
        return s.toUpperCase(Locale.ROOT);
    }

    /** Cell 取字符串：数字类型用 DataFormatter（避免 1.23E5 科学计数法）；空 cell 返回 null. */
    private static String getString(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            // 数字单元格按整数优先（员工号/机构号场景不带小数）
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

    /** Cell 取 BigDecimal：数字→直接 valueOf；字符串→trim 后 new BigDecimal；不可解析返回 null. */
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
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "指标数值无法解析为数字: " + formatter.formatCellValue(cell));
        }
    }

    /** 一行所有列空白视作空行，跳过. */
    private static boolean isRowBlank(Row row) {
        for (int c = 0; c <= row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !(cell.getCellType() == CellType.STRING
                            && (cell.getStringCellValue() == null || cell.getStringCellValue().isBlank()))) {
                return false;
            }
        }
        return true;
    }

    /** 1) baseDim ∈ {EMP, ORG, CUST, null}. */
    private static void validateBaseDim(MetricResultImportRow row) {
        if (row.getBaseDim() != null && !ALLOWED_BASE_DIMS.contains(row.getBaseDim())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "基础维度非法（期望 EMP/ORG/CUST 或空）: " + row.getBaseDim());
        }
    }

    /** 3/4) 主体存在性校验（EMP→按工号查 PT_USER.username；ORG→EXT_ORG_INFO；CUST/null 跳过）. */
    private void validateSubjectExists(MetricResultImportRow row) {
        String dim = row.getBaseDim();
        String subject = row.getSubjectKey();
        if (subject == null || subject.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "维度对象必填");
        }
        if ("EMP".equals(dim)) {
            // 维度对象=员工工号(PT_USER.username)，与宽表 emp_id 存储口径一致；按工号校验存在性
            // （不能按 USER_ID 查，否则工号查不到被误判"不存在"）
            List<UserDTO> users = userApi.getUsersByUsernames(List.of(subject));
            if (users == null || users.isEmpty()) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                        "员工不存在（工号）: " + subject);
            }
        } else if ("ORG".equals(dim)) {
            // 维度对象=机构编号(EXT_ORG_INFO.DEPT_NO)，按 DEPT_NO 校验存在性（不能按 ORG_CODE 查）
            if (orgApi.getOrgByDeptNo(subject) == null) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                        "机构不存在（EXT_ORG_INFO 部门编号 DEPT_NO）: " + subject);
            }
        }
        // CUST 由产品后续接入客户主数据校验，本期跳过（与用户需求一致）
    }

    /** baseDim → 对应宽表 mapper 路由 + UPSERT 写值（含 updated_time）. */
    private void dispatchUpsert(String baseDim, String subjectKey, LocalDate dataDate,
                                String version, int slot, BigDecimal value) {
        switch (baseDim) {
            case "EMP" -> empIndexResultMapper.insertSlotValue(subjectKey, dataDate, version, slot, value);
            case "ORG" -> orgIndexResultMapper.insertSlotValue(subjectKey, dataDate, version, slot, value);
            case "CUST" -> custIndexResultMapper.insertSlotValue(subjectKey, dataDate, version, slot, value);
            default -> throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "未知 baseDim: " + baseDim);
        }
    }

    /** 取该维度生效版本，缺失降级 V1（V1.12 简化策略）. */
    private String resolveVersion(String scopeDim) {
        try {
            SysControl sc = sysControlService.getCurrentVersion(scopeDim);
            return sc.getCurrentVersion();
        } catch (PerfException ex) {
            // sys_control 未初始化该维度时降级，不阻断导入
            log.warn("[MetricResultImportStrategy] sys_control 无 {} 维度生效版本，降级到 {}",
                    scopeDim, DEFAULT_VERSION);
            return DEFAULT_VERSION;
        }
    }

    /** 批量预取 metric_def，构建 metric_name → def 映射. */
    private Map<String, PerfMetricDef> loadMetricDefMap(List<MetricResultImportRow> rows) {
        Set<String> names = new LinkedHashSet<>();
        for (MetricResultImportRow r : rows) {
            if (r.getMetricName() != null && !r.getMetricName().isBlank()) {
                names.add(r.getMetricName().trim());
            }
        }
        if (names.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PerfMetricDef> defs = metricDefMapper.selectByMetricNames(new ArrayList<>(names));
        // 按 (维度 + 指标名) 建键：同名指标可跨维度并存（如 EMP 与 ORG 各一条），
        // 仅按名会取错维度的定义导致"基础维度不匹配"误判。首命中优先。
        Map<String, PerfMetricDef> map = new HashMap<>(names.size());
        if (defs != null) {
            for (PerfMetricDef d : defs) {
                map.putIfAbsent(dimKey(d.getBaseDim(), d.getMetricName()), d);
            }
        }
        return map;
    }

    /** (维度 + 指标名) 组合键；维度 null（维度无关型）归一为空串前缀. */
    private static String dimKey(String baseDim, String metricName) {
        return (baseDim == null ? "" : baseDim.trim().toUpperCase()) + "|"
                + (metricName == null ? "" : metricName.trim());
    }

    private static String formatErrorPrefix(MetricResultImportRow row) {
        return "Sheet[" + (row.getSheetName() == null ? "" : row.getSheetName())
                + "] 第" + row.getExcelRowNum() + "行: ";
    }

    private static String safeMessage(Throwable ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
