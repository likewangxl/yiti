package com.bank.branch.platform.performance.service.importer.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.alibaba.excel.exception.ExcelCommonException;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import com.bank.branch.platform.performance.service.importer.model.AllocRelationImportRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 分配关系导入策略（Task P5.4 Green）.
 *
 * <p>职责：
 * <ol>
 *   <li>easyexcel 同步解析 Excel → List<AllocRelationImportRow></li>
 *   <li>行级校验：custId / empId / effectiveDate 必填，ACCOUNT 类型必须 accountNo，
 *       effectiveDate 可解析为 LocalDate</li>
 *   <li>逐行调 {@link CustAllocRelationMapper#insert}；DuplicateKeyException / DataAccessException
 *       → errorSummary 不整批回滚</li>
 *   <li>生成 id 为 UUID 去掉 dash，sourceBatchId 写入 batch.id，createdBy 取自 batch.createdBy</li>
 * </ol>
 *
 * <p>ratio 为空默认 100.00（100% 分配）；allocType 为空默认 RULE.
 *
 * <p>整文件级错误（列头全部不匹配 / 解析失败）→ 抛 {@link PerfErrorCode#IMPORT_COLUMN_MAPPING_INVALID}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllocRelationImportStrategy implements ImportStrategy {

    private static final int DATA_ROW_EXCEL_OFFSET = 2;
    private static final String ERROR_DELIMITER = "; ";
    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
    /** ratio 默认值（独占客户，100.00%）. */
    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("100.00");
    /** allocType 默认值（规则分配）. */
    private static final String DEFAULT_ALLOC_DIM = "RULE";

    private final CustAllocRelationMapper custAllocRelationMapper;

    @Override
    public String importType() {
        return "ALLOC";
    }

    @Override
    public ImportResult execute(PerfImportBatch batch, MultipartFile file) {
        List<AllocRelationImportRow> rows = parseRows(file);
        log.info("[AllocRelationImportStrategy] 解析完成 batchId={}, rows={}",
                batch.getId(), rows == null ? 0 : rows.size());

        if (rows == null || rows.isEmpty()) {
            return new ImportResult(0, 0, 0, null);
        }

        List<String> errors = new ArrayList<>();
        int successRows = 0;

        for (int i = 0; i < rows.size(); i++) {
            AllocRelationImportRow row = rows.get(i);
            int excelRowNum = i + DATA_ROW_EXCEL_OFFSET;
            try {
                validateRequired(row);
                CustAllocRelation entity = toEntity(row, batch);
                custAllocRelationMapper.insert(entity);
                successRows++;
            } catch (DataAccessException dae) {
                // 主键冲突 / 唯一约束冲突等 DB 层异常：记录到 errorSummary
                errors.add("第" + excelRowNum + "行: " + safeMessage(dae));
            } catch (RuntimeException ex) {
                errors.add("第" + excelRowNum + "行: " + safeMessage(ex));
            }
        }

        String summary = errors.isEmpty() ? null : String.join(ERROR_DELIMITER, errors);
        return new ImportResult(rows.size(), successRows, rows.size() - successRows, summary);
    }

    private List<AllocRelationImportRow> parseRows(MultipartFile file) {
        try {
            List<AllocRelationImportRow> rows = EasyExcel.read(file.getInputStream())
                    .head(AllocRelationImportRow.class)
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
            log.warn("[AllocRelationImportStrategy] Excel 解析失败: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID, ex, ex.getMessage());
        } catch (IOException ex) {
            log.warn("[AllocRelationImportStrategy] 读取 MultipartFile 失败: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID, ex, ex.getMessage());
        }
    }

    private static boolean isAllKeyFieldsNull(List<AllocRelationImportRow> rows) {
        for (AllocRelationImportRow r : rows) {
            if (r.getCustId() != null || r.getEmpId() != null
                    || r.getEffectiveDate() != null || r.getAllocType() != null
                    || r.getBizKind() != null || r.getAccountNo() != null
                    || r.getRatio() != null) {
                return false;
            }
        }
        return true;
    }

    private static void validateRequired(AllocRelationImportRow row) {
        if (row.getCustId() == null || row.getCustId().isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "custId 必填");
        }
        if (row.getEmpId() == null || row.getEmpId().isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "empId 必填");
        }
        if (row.getEffectiveDate() == null || row.getEffectiveDate().isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "effectiveDate 必填");
        }
        String dim = row.getAllocType() == null || row.getAllocType().isBlank()
                ? DEFAULT_ALLOC_DIM : row.getAllocType();
        if (!"RULE".equals(dim) && !"ACCOUNT".equals(dim)) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "allocType 必须为 RULE/ACCOUNT，实际: " + dim);
        }
        if ("ACCOUNT".equals(dim) && (row.getAccountNo() == null || row.getAccountNo().isBlank())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "ACCOUNT 类型必须填 accountNo");
        }
    }

    private static CustAllocRelation toEntity(AllocRelationImportRow row, PerfImportBatch batch) {
        CustAllocRelation e = new CustAllocRelation();
        e.setId(generateId());
        e.setCustId(row.getCustId());
        e.setEmpId(row.getEmpId());
        String dim = row.getAllocType() == null || row.getAllocType().isBlank()
                ? DEFAULT_ALLOC_DIM : row.getAllocType();
        e.setAllocDim(dim);
        e.setBizKind(row.getBizKind());
        e.setAccountNo(row.getAccountNo());
        e.setRatio(row.getRatio() == null ? DEFAULT_RATIO : row.getRatio());
        e.setEffectiveDate(parseDate(row.getEffectiveDate()));
        e.setEndDate(null);
        e.setSourceBatchId(batch.getId());
        e.setSourceProcessDate(LocalDate.now());
        e.setCreatedBy(batch.getCreatedBy());
        e.setUpdatedBy(batch.getCreatedBy());
        return e;
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s, ISO_DATE);
        } catch (DateTimeParseException ex) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "effectiveDate 格式非法（期望 yyyy-MM-dd）: " + s);
        }
    }

    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String safeMessage(Throwable ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
