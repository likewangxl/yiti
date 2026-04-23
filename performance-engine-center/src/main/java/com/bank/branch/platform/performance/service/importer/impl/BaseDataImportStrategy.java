package com.bank.branch.platform.performance.service.importer.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.alibaba.excel.exception.ExcelCommonException;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import com.bank.branch.platform.performance.service.importer.model.BaseDataImportRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 基础数据导入策略（Task P5.3 Green）.
 *
 * <p>职责：
 * <ol>
 *   <li>easyexcel 同步读 Excel → List<BaseDataImportRow></li>
 *   <li>批量用 metricCode 调 {@link PerfMetricDefMapper#selectByMetricCodes} 建立
 *       metricCode → (baseDim, valSlot) 映射（减少多次 DB 查询）</li>
 *   <li>按行校验必填 / metricCode 存在 / dataDate 可解析 / baseDim 已知</li>
 *   <li>按 baseDim 路由到 {@link EmpIndexResultMapper#insertSlotValue} /
 *       {@link OrgIndexResultMapper#insertSlotValue} / {@link CustIndexResultMapper#insertSlotValue}</li>
 *   <li>单行失败累计 errorSummary，不抛异常</li>
 * </ol>
 *
 * <p>整文件级错误（列头全部不匹配 / 解析失败）→ 抛
 * {@link PerfErrorCode#IMPORT_COLUMN_MAPPING_INVALID}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaseDataImportStrategy implements ImportStrategy {

    private static final int DATA_ROW_EXCEL_OFFSET = 2;
    private static final String ERROR_DELIMITER = "; ";
    /** ISO 标准日期格式 yyyy-MM-dd. */
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd", Locale.ROOT);

    private final PerfMetricDefMapper metricDefMapper;
    private final EmpIndexResultMapper empIndexResultMapper;
    private final OrgIndexResultMapper orgIndexResultMapper;
    private final CustIndexResultMapper custIndexResultMapper;

    @Override
    public String importType() {
        return "BASE_DATA";
    }

    @Override
    public ImportResult execute(PerfImportBatch batch, MultipartFile file) {
        List<BaseDataImportRow> rows = parseRows(file);
        log.info("[BaseDataImportStrategy] 解析完成 batchId={}, rows={}",
                batch.getId(), rows == null ? 0 : rows.size());

        if (rows == null || rows.isEmpty()) {
            return new ImportResult(0, 0, 0, null);
        }

        // 批量查 metricDef 构建 (metricCode → def) 映射
        Map<String, PerfMetricDef> metricMap = loadMetricMap(rows);

        List<String> errors = new ArrayList<>();
        int successRows = 0;

        for (int i = 0; i < rows.size(); i++) {
            BaseDataImportRow row = rows.get(i);
            int excelRowNum = i + DATA_ROW_EXCEL_OFFSET;
            try {
                validateRequired(row);
                PerfMetricDef def = metricMap.get(row.getMetricCode());
                if (def == null) {
                    throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, row.getMetricCode());
                }
                if (def.getValSlot() == null) {
                    throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                            "metric 未分配 val_slot: " + row.getMetricCode());
                }
                LocalDate dataDate = parseDate(row.getDataDate());
                dispatchInsert(def, row.getSubjectKey(), dataDate, row.getVersion(), def.getValSlot(),
                        row.getValue());
                successRows++;
            } catch (RuntimeException ex) {
                errors.add("第" + excelRowNum + "行: " + safeMessage(ex));
            }
        }

        String summary = errors.isEmpty() ? null : String.join(ERROR_DELIMITER, errors);
        return new ImportResult(rows.size(), successRows, rows.size() - successRows, summary);
    }

    /** easyexcel 解析，异常统一转 IMPORT_COLUMN_MAPPING_INVALID. */
    private List<BaseDataImportRow> parseRows(MultipartFile file) {
        try {
            List<BaseDataImportRow> rows = EasyExcel.read(file.getInputStream())
                    .head(BaseDataImportRow.class)
                    .sheet()
                    .doReadSync();
            if (rows != null && !rows.isEmpty() && isAllKeyFieldsNull(rows)) {
                throw new PerfException(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID,
                        "Excel 列头与模板不匹配");
            }
            return rows;
        } catch (PerfException pex) {
            throw pex;
        } catch (ExcelAnalysisException | ExcelCommonException ex) {
            log.warn("[BaseDataImportStrategy] Excel 解析失败: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID, ex, ex.getMessage());
        } catch (IOException ex) {
            log.warn("[BaseDataImportStrategy] 读取 MultipartFile 失败: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID, ex, ex.getMessage());
        }
    }

    /** "所有行"的 subjectKey / metricCode / dataDate / value 是否全为 null（列头完全不匹配识别）. */
    private static boolean isAllKeyFieldsNull(List<BaseDataImportRow> rows) {
        for (BaseDataImportRow r : rows) {
            if (r.getSubjectKey() != null || r.getMetricCode() != null
                    || r.getDataDate() != null || r.getValue() != null) {
                return false;
            }
        }
        return true;
    }

    /** 汇总 rows 的 distinct metricCode，批量查 metric_def. */
    private Map<String, PerfMetricDef> loadMetricMap(List<BaseDataImportRow> rows) {
        Set<String> codes = new HashSet<>();
        for (BaseDataImportRow r : rows) {
            if (r.getMetricCode() != null && !r.getMetricCode().isBlank()) {
                codes.add(r.getMetricCode());
            }
        }
        if (codes.isEmpty()) {
            return new HashMap<>();
        }
        List<PerfMetricDef> defs = metricDefMapper.selectByMetricCodes(new ArrayList<>(codes));
        Map<String, PerfMetricDef> map = new HashMap<>(codes.size());
        if (defs != null) {
            for (PerfMetricDef d : defs) {
                map.put(d.getMetricCode(), d);
            }
        }
        return map;
    }

    /** 按 baseDim 路由到对应 Mapper. */
    private void dispatchInsert(PerfMetricDef def, String subjectKey, LocalDate dataDate,
                                String version, int slot, java.math.BigDecimal value) {
        String baseDim = def.getBaseDim();
        if ("EMP".equals(baseDim)) {
            empIndexResultMapper.insertSlotValue(subjectKey, dataDate, version, slot, value);
        } else if ("ORG".equals(baseDim)) {
            orgIndexResultMapper.insertSlotValue(subjectKey, dataDate, version, slot, value);
        } else if ("CUST".equals(baseDim)) {
            custIndexResultMapper.insertSlotValue(subjectKey, dataDate, version, slot, value);
        } else {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "未知 baseDim: " + baseDim);
        }
    }

    private static void validateRequired(BaseDataImportRow row) {
        if (row.getSubjectKey() == null || row.getSubjectKey().isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "subjectKey 必填");
        }
        if (row.getMetricCode() == null || row.getMetricCode().isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "metricCode 必填");
        }
        if (row.getDataDate() == null || row.getDataDate().isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "dataDate 必填");
        }
        if (row.getVersion() == null || row.getVersion().isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "version 必填");
        }
        if (row.getValue() == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "value 必填");
        }
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s, ISO_DATE);
        } catch (DateTimeParseException ex) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "dataDate 格式非法（期望 yyyy-MM-dd）: " + s);
        }
    }

    private static String safeMessage(Throwable ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
