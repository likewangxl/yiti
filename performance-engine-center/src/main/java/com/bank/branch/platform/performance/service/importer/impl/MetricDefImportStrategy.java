package com.bank.branch.platform.performance.service.importer.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.alibaba.excel.exception.ExcelCommonException;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import com.bank.branch.platform.performance.service.importer.model.MetricDefImportRow;
import com.bank.branch.platform.performance.service.result.BatchUpsertMetricDefResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 指标定义 Excel 导入策略（V1.9 Task M2）.
 *
 * <p>importType = {@code "METRIC_DEF"}.
 *
 * <p><b>整批 all-or-none 语义</b>（与 TARGET/BASE_DATA/ALLOC 三个策略的"行级最大努力"不同）：
 * <ol>
 *   <li>EasyExcel 同步解析得到 {@link MetricDefImportRow} 列表</li>
 *   <li>遍历所有行做<b>预校验</b>，收集全部错误（必填、枚举值、文件内 metric_code 重复、
 *       来源=2/3 但 sql_text 为空 等）</li>
 *   <li>任一错误 → 抛 {@link PerfErrorCode#IMPORT_BATCH_ALL_OR_NONE_FAILED}，
 *       message 携全部行号 + 原因，外层 {@code PerfImportServiceImpl} catch 后批次置 FAILED，
 *       remark 写入明细，{@code errorRows = 错误明细行数}（在 message 中体现）</li>
 *   <li>校验全通过 → 调 {@link MetricDefService#batchCreateMetricDefs}（{@code @Transactional}），
 *       任一行 DB 异常自动回滚整批</li>
 * </ol>
 *
 * <p>Excel 行号约定：数据第 N 行对应 Excel 第 N+1 行（第 1 行为列头）。
 *
 * <p>列值翻译规则（参考 {@code docs/superpowers/specs/2026-05-17-metric-def-import-design.md} §3）：
 * <ul>
 *   <li>metric_code 空 → {@code M_{indexNo:04d}}</li>
 *   <li>sourceType=1 → calc_mode=MANUAL, calc_logic_type=EXPR；2/3 → AUTO/SQL</li>
 *   <li>scheduleType 1/2/3/4 → calc_freq DAY/MONTH/QUARTER/YEAR</li>
 *   <li>statusFlag 1/0 → ACTIVE/DISABLED；其他视为非法</li>
 *   <li>base_dim 默认 EMP；summary_rule 默认 SUM</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricDefImportStrategy implements ImportStrategy {

    /** Excel 数据行（跳过列头）相对物理行号的偏移：第 1 条数据在 Excel 第 2 行. */
    private static final int DATA_ROW_EXCEL_OFFSET = 2;

    /** errorSummary 行之间的分隔符. */
    private static final String ERROR_DELIMITER = "; ";

    /** scheduleType → calc_freq 映射. */
    private static final Map<Integer, String> SCHEDULE_TO_FREQ;

    static {
        Map<Integer, String> m = new HashMap<>();
        m.put(1, "DAY");
        m.put(2, "MONTH");
        m.put(3, "QUARTER");
        m.put(4, "YEAR");
        SCHEDULE_TO_FREQ = m;
    }

    /**
     * 缺省 base_dim. V1.9 改造：导入模板无 base_dim 列，默认 null 表示
     * "维度无关型指标"（不占 slot、不入三大宽表、不进入自动调度）。
     * 业务方如需指定维度，需在指标管理 UI 编辑后启用。
     */
    static final String DEFAULT_BASE_DIM = null;

    /** 缺省 summary_rule. */
    static final String DEFAULT_SUMMARY_RULE = "SUM";

    private final MetricDefService metricDefService;

    @Override
    public String importType() {
        return "METRIC_DEF";
    }

    @Override
    public ImportResult execute(PerfImportBatch batch, MultipartFile file) {
        List<MetricDefImportRow> rows = parseRows(file);
        log.info("[MetricDefImportStrategy] 解析完成 batchId={}, rows={}",
                batch.getId(), rows == null ? 0 : rows.size());

        if (rows == null || rows.isEmpty()) {
            return new ImportResult(0, 0, 0, 0, null);
        }

        // 第一遍：列值翻译 + 收集每行错误
        List<String> errors = new ArrayList<>();
        List<CreateMetricDefCmd> cmds = new ArrayList<>(rows.size());
        Map<String, Integer> codeFirstSeenLine = new LinkedHashMap<>();
        Map<String, Integer> nameFirstSeenLine = new LinkedHashMap<>();   // V1.11

        for (int i = 0; i < rows.size(); i++) {
            MetricDefImportRow row = rows.get(i);
            int excelRow = i + DATA_ROW_EXCEL_OFFSET;
            try {
                CreateMetricDefCmd cmd = translateRow(row, excelRow, batch.getCreatedBy());

                // V1.11：文件内 metric_name 重复检测（重名行作为 upsert 命中键会触发"末位覆盖"，必须显式拒绝）
                Integer nameSeen = nameFirstSeenLine.putIfAbsent(cmd.getMetricName(), excelRow);
                if (nameSeen != null) {
                    errors.add("第" + excelRow + "行: metric_name 与第" + nameSeen
                            + "行重复(文件内): " + cmd.getMetricName());
                    continue;
                }

                // 文件内 metric_code 重复检测（保留首次出现行号，后续行号都报）
                Integer firstSeen = codeFirstSeenLine.putIfAbsent(cmd.getMetricCode(), excelRow);
                if (firstSeen != null) {
                    errors.add("第" + excelRow + "行: metric_code 与第" + firstSeen
                            + "行重复(文件内): " + cmd.getMetricCode());
                    continue;
                }
                cmds.add(cmd);
            } catch (PerfException pex) {
                errors.add("第" + excelRow + "行: " + safeMessage(pex));
            }
        }

        // V1.11：基础格式校验失败仍走整批 all-or-none（数据质量问题，而非冲突问题）
        if (!errors.isEmpty()) {
            String detail = "共" + errors.size() + "行失败; " + String.join(ERROR_DELIMITER, errors);
            throw new PerfException(PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED, detail);
        }

        // V1.11：校验全通过 → 整批 upsert（命中 metric_name → update，否则 → insert）
        BatchUpsertMetricDefResult r =
                metricDefService.batchUpsertByName(cmds, batch.getCreatedBy());
        int total = rows.size();
        int success = r.getInsertedRows() + r.getUpdatedRows();
        return new ImportResult(total, success, 0, r.getUpdatedRows(), null);
    }

    /**
     * 将一行 Excel 数据翻译为 {@link CreateMetricDefCmd}.
     *
     * @param row       Excel 行
     * @param excelRow  Excel 物理行号（仅用于错误消息）
     * @param operator  操作人（写入 createdBy）
     * @return 翻译后的命令
     * @throws PerfException 必填缺失或枚举值非法
     */
    CreateMetricDefCmd translateRow(MetricDefImportRow row, int excelRow, String operator) {
        if (row.getMetricName() == null || row.getMetricName().isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "metricName 必填");
        }
        if (row.getMetricLevel() == null
                || row.getMetricLevel() < 1 || row.getMetricLevel() > 3) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "metricLevel 必须在 1~3: " + row.getMetricLevel());
        }
        if (row.getIndexNo() == null || row.getIndexNo() <= 0) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "indexNo 必填且大于 0");
        }

        String calcFreq = SCHEDULE_TO_FREQ.get(row.getScheduleType());
        if (calcFreq == null) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_FREQ_INVALID,
                    "scheduleType 非法（期望 1=每日/2=每月/3=每季/4=每年）: " + row.getScheduleType());
        }

        // 状态：1=ACTIVE 0=DISABLED
        String status;
        if (Objects.equals(row.getStatusFlag(), 1)) {
            status = "ACTIVE";
        } else if (Objects.equals(row.getStatusFlag(), 0)) {
            status = "DISABLED";
        } else {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "statusFlag 非法（期望 0 或 1）: " + row.getStatusFlag());
        }

        // 来源 → calc_mode + calc_logic_type
        String calcMode;
        String calcLogicType;
        String sqlText = null;
        String exprText = null;
        switch (row.getSourceType() == null ? -1 : row.getSourceType()) {
            case 1 -> {
                calcMode = "MANUAL";
                calcLogicType = "EXPR";
                exprText = row.getCalcRule();
            }
            case 2, 3 -> {
                calcMode = "AUTO";
                calcLogicType = "SQL";
                sqlText = row.getCalcRule();
                if (sqlText == null || sqlText.isBlank()) {
                    throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                            "sourceType=" + row.getSourceType() + " 时计算规则必填");
                }
            }
            default -> throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "sourceType 非法（期望 1/2/3）: " + row.getSourceType());
        }

        String metricCode = row.getMetricCode() == null || row.getMetricCode().isBlank()
                ? String.format("M_%04d", row.getIndexNo())
                : row.getMetricCode().trim();

        CreateMetricDefCmd.CreateMetricDefCmdBuilder b = CreateMetricDefCmd.builder()
                .metricCode(metricCode)
                .metricName(row.getMetricName().trim())
                .metricCategory(row.getMetricCategory())
                .baseDim(DEFAULT_BASE_DIM)
                .metricLevel(row.getMetricLevel())
                .calcFreq(calcFreq)
                .calcMode(calcMode)
                .calcLogicType(calcLogicType)
                .sqlText(sqlText)
                .exprText(exprText)
                .summaryRule(DEFAULT_SUMMARY_RULE)
                .preferredSlot(row.getIndexNo())
                .status(status)         // V1.9：statusFlag 翻译后的 ACTIVE/DISABLED 透传，落库尊重 Excel 意图
                .operator(operator);
        return b.build();
    }

    /** EasyExcel 解析，异常统一转 IMPORT_COLUMN_MAPPING_INVALID. */
    private List<MetricDefImportRow> parseRows(MultipartFile file) {
        try {
            List<MetricDefImportRow> rows = EasyExcel.read(file.getInputStream())
                    .head(MetricDefImportRow.class)
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
            log.warn("[MetricDefImportStrategy] Excel 解析失败: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID, ex, ex.getMessage());
        } catch (IOException ex) {
            log.warn("[MetricDefImportStrategy] 读取 MultipartFile 失败: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID, ex, ex.getMessage());
        }
    }

    /** 列头全部不匹配识别：所有行的关键字段都为 null. */
    private static boolean isAllKeyFieldsNull(List<MetricDefImportRow> rows) {
        for (MetricDefImportRow r : rows) {
            if (r.getIndexNo() != null || r.getMetricLevel() != null
                    || r.getMetricName() != null || r.getMetricCode() != null
                    || r.getMetricCategory() != null || r.getSourceType() != null
                    || r.getCalcRule() != null || r.getScheduleType() != null
                    || r.getStatusFlag() != null) {
                return false;
            }
        }
        return true;
    }

    private static String safeMessage(Throwable ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
