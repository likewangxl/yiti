package com.bank.branch.platform.performance.service.importer.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.alibaba.excel.exception.ExcelCommonException;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.TargetPlanService;
import com.bank.branch.platform.performance.service.TargetValueService;
import com.bank.branch.platform.performance.service.cmd.UpsertTargetValueCmd;
import com.bank.branch.platform.performance.service.importer.ImportContext;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import com.bank.branch.platform.performance.service.importer.model.TargetImportRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 目标值导入策略（Task P5.2 Green）.
 *
 * <p>职责：
 * <ol>
 *   <li>使用 easyexcel 同步读取 Excel（{@code doReadSync}），得到 {@link TargetImportRow} 列表</li>
 *   <li>按行校验：targetPlanCode / empId / metricCode / targetValue 必填，targetPlanCode 必须
 *       存在于 perf_target_plan（用 {@link TargetPlanService#getByCodeOrNull} 小缓存避免多次查）</li>
 *   <li>合法行调 {@link TargetValueService#upsertOne} 单值 upsert（subjectType 固定 EMP,
 *       cycleKey 使用 target_plan.target_cycle）</li>
 *   <li>单行失败累计到 {@code errorSummary}（格式：{@code 第{excelRowNum}行: {reason}}，分号分隔），不抛异常</li>
 *   <li>整文件级错误（列头不匹配 / 解析异常）→ 抛 {@link PerfErrorCode#IMPORT_COLUMN_MAPPING_INVALID}</li>
 * </ol>
 *
 * <p>Excel 行号约定：数据第 1 行对应 Excel 第 2 行（第 1 行为列头）。
 *
 * <p>并发/事务：{@code upsertOne} 已在 Service 层 {@code @Transactional}，
 * 本策略不再包裹事务；单行失败互不回滚（行级幂等由 UK 保证）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TargetImportStrategy implements ImportStrategy {

    /** Excel 数据行（跳过列头）相对物理行号的偏移：第 1 条数据在 Excel 第 2 行. */
    private static final int DATA_ROW_EXCEL_OFFSET = 2;

    /** errorSummary 行之间的分隔符（与 PerfImportServiceImpl.truncate 截断语义一致）. */
    private static final String ERROR_DELIMITER = "; ";

    private final TargetValueService targetValueService;
    private final TargetPlanService targetPlanService;

    @Override
    public String importType() {
        return "TARGET";
    }

    @Override
    public ImportResult execute(PerfImportBatch batch, MultipartFile file, ImportContext ctx) {
        List<TargetImportRow> rows = parseRows(file);
        log.info("[TargetImportStrategy] 解析完成 batchId={}, rows={}",
                batch.getId(), rows == null ? 0 : rows.size());

        if (rows == null || rows.isEmpty()) {
            return new ImportResult(0, 0, 0, null);
        }

        List<String> errors = new ArrayList<>();
        int successRows = 0;
        // planCode 查询缓存：一次导入内多次复用减少 DB 压力
        Map<String, Optional<PerfTargetPlan>> planCache = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            TargetImportRow row = rows.get(i);
            int excelRowNum = i + DATA_ROW_EXCEL_OFFSET;
            try {
                validateRequired(row);
                PerfTargetPlan plan = resolvePlan(planCache, row.getTargetPlanCode());
                UpsertTargetValueCmd cmd = UpsertTargetValueCmd.builder()
                        .planId(plan.getId())
                        .subjectType("EMP")
                        .subjectId(row.getEmpId())
                        .cycleKey(plan.getTargetCycle())
                        .metricCode(row.getMetricCode())
                        .targetValue(row.getTargetValue())
                        .operator(batch.getCreatedBy())
                        .build();
                targetValueService.upsertOne(cmd);
                successRows++;
            } catch (RuntimeException ex) {
                errors.add("第" + excelRowNum + "行: " + safeMessage(ex));
            }
        }

        String summary = errors.isEmpty() ? null : String.join(ERROR_DELIMITER, errors);
        return new ImportResult(rows.size(), successRows, rows.size() - successRows, summary);
    }

    /**
     * 调用 easyexcel 同步解析，失败转 IMPORT_COLUMN_MAPPING_INVALID.
     *
     * <p>注：easyexcel 对列头不匹配不主动抛异常（按名字匹配匹配不到就赋 null），
     * 列头完全不匹配会导致所有字段为 null，由后续 validateRequired 统一拦到 errorSummary。
     * 真正抛 ExcelAnalysisException 的场景是文件格式损坏 / 不是合法 xlsx 等。
     */
    private List<TargetImportRow> parseRows(MultipartFile file) {
        try {
            List<TargetImportRow> rows = EasyExcel.read(file.getInputStream())
                    .head(TargetImportRow.class)
                    .sheet()
                    .doReadSync();
            // 进一步判定：若所有行的"任一关键字段"都为 null，视为列头全部不匹配
            if (rows != null && !rows.isEmpty() && isAllKeyFieldsNull(rows)) {
                throw new PerfException(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID,
                        "Excel 列头与模板不匹配");
            }
            return rows;
        } catch (PerfException pex) {
            throw pex;
        } catch (ExcelAnalysisException | ExcelCommonException ex) {
            log.warn("[TargetImportStrategy] Excel 解析失败: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID, ex, ex.getMessage());
        } catch (IOException ex) {
            log.warn("[TargetImportStrategy] 读取 MultipartFile 失败: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID, ex, ex.getMessage());
        }
    }

    /** 判定"所有行"的 targetPlanCode / empId / metricCode / targetValue 是否全为 null. */
    private static boolean isAllKeyFieldsNull(List<TargetImportRow> rows) {
        for (TargetImportRow r : rows) {
            if (r.getTargetPlanCode() != null || r.getEmpId() != null
                    || r.getMetricCode() != null || r.getTargetValue() != null) {
                return false;
            }
        }
        return true;
    }

    /** 必填校验：空字符串按空处理. */
    private static void validateRequired(TargetImportRow row) {
        if (row.getTargetPlanCode() == null || row.getTargetPlanCode().isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "targetPlanCode 必填");
        }
        if (row.getEmpId() == null || row.getEmpId().isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "empId 必填");
        }
        if (row.getMetricCode() == null || row.getMetricCode().isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "metricCode 必填");
        }
        if (row.getTargetValue() == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "targetValue 必填");
        }
        // decimal(20,4) 理论上允许负数，业务允许差异与下调，此处不再校验范围
    }

    /** 带缓存解析 targetPlanCode；不存在抛 TARGET_PLAN_NOT_FOUND，被调用方 catch 到 errorSummary. */
    private PerfTargetPlan resolvePlan(Map<String, Optional<PerfTargetPlan>> cache, String code) {
        Optional<PerfTargetPlan> opt = cache.computeIfAbsent(code, targetPlanService::getByCodeOrNull);
        if (opt == null || !opt.isPresent()) {
            throw new PerfException(PerfErrorCode.TARGET_PLAN_NOT_FOUND, code);
        }
        return opt.get();
    }

    /** 防御：异常 message 为 null 时使用简单类名. */
    private static String safeMessage(Throwable ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    /** 保留兼容：允许外部复用 BigDecimal 格式化（预留给 V1.2 的数值范围校验）. */
    @SuppressWarnings("unused")
    private static BigDecimal normalizeValue(BigDecimal v) {
        return v == null ? null : v;
    }
}
