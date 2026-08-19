package com.bank.branch.platform.yundun.controller;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.yundun.dto.CreditViolationDTO;
import com.bank.branch.platform.yundun.dto.CreditViolationQuery;
import com.bank.branch.platform.yundun.dto.CreditViolationSaveReq;
import com.bank.branch.platform.yundun.dto.ViolationBatchDeleteReq;
import com.bank.branch.platform.yundun.dto.ViolationImportReq;
import com.bank.branch.platform.yundun.excel.CreditViolationExcelWriteHandler;
import com.bank.branch.platform.yundun.excel.TextLocalDateTimeConverter;
import com.bank.branch.platform.yundun.enums.YundunErrorCode;
import com.bank.branch.platform.yundun.service.CreditViolationService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/** 信贷风险责任认定信息 REST 接口。 */
@Slf4j
@Validated
@Tag(name = "浦爱云盾-信贷风险信息")
@RestController
@RequestMapping("/api/yundun/credit-violations")
@RequiredArgsConstructor
public class CreditViolationController {

    private static final int CREDIT_DATA_START_ROW = 4;
    private static final int CREDIT_DOCUMENT_COLUMN = 30;

    private final CreditViolationService service;

    /** 分页查询信贷风险信息。 */
    @Operation(summary = "分页查询信贷风险信息")
    @GetMapping
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.LIST)
    public ResponseWrapper<CreditViolationDTO> list(@Valid CreditViolationQuery query) {
        IPage<CreditViolationDTO> page = service.page(query);
        log.info("[CreditViolationController.list] pageNo={}, pageSize={}, total={}",
                query.normalizedPageNo(), query.normalizedPageSize(), page.getTotal());
        return ResponseWrapper.page(PageResult.of(
                (int) page.getCurrent(), (int) page.getSize(), page.getTotal(), page.getRecords()));
    }

    /** 查询信贷风险详情。 */
    @Operation(summary = "查询信贷风险详情")
    @GetMapping("/{id:[0-9]{1,19}}")
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.READ)
    public ResponseWrapper<CreditViolationDTO> get(@PathVariable Long id) {
        CreditViolationDTO result = service.get(id);
        log.info("[CreditViolationController.get] id={}", id);
        return ResponseWrapper.success(result);
    }

    /** 新增信贷风险信息。 */
    @Operation(summary = "新增信贷风险信息")
    @PostMapping
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.WRITE)
    @AuditLog(action = "YD_CREDIT_ADD", resourceType = "T_CREDIT_VIOLATION")
    public ResponseWrapper<CreditViolationDTO> create(@Valid @RequestBody CreditViolationSaveReq req) {
        CreditViolationDTO result = service.create(req);
        log.info("[CreditViolationController.create] id={}", result.getId());
        return ResponseWrapper.success(result);
    }

    /** 修改信贷风险信息。 */
    @Operation(summary = "修改信贷风险信息")
    @PutMapping("/{id:[0-9]{1,19}}")
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.WRITE)
    @AuditLog(action = "YD_CREDIT_UPDATE", resourceType = "T_CREDIT_VIOLATION")
    public ResponseWrapper<CreditViolationDTO> update(
            @PathVariable Long id, @Valid @RequestBody CreditViolationSaveReq req) {
        CreditViolationDTO result = service.update(id, req);
        log.info("[CreditViolationController.update] id={}", id);
        return ResponseWrapper.success(result);
    }

    /** 批量删除信贷风险信息。 */
    @Operation(summary = "批量删除信贷风险信息")
    @PostMapping("/batch-delete")
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.DELETE)
    @AuditLog(action = "YD_CREDIT_DELETE", resourceType = "T_CREDIT_VIOLATION", reasonRequired = true)
    public ResponseWrapper<Integer> delete(@Valid @RequestBody ViolationBatchDeleteReq req) {
        int count = service.delete(req.getIds());
        log.info("[CreditViolationController.delete] count={}, reason={}", count, req.getReason());
        return ResponseWrapper.success(count);
    }

    /** 按当前条件或选中记录导出 Excel。 */
    @Operation(summary = "导出信贷风险信息")
    @GetMapping("/export")
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.EXPORT)
    @AuditLog(action = "YD_CREDIT_EXPORT", resourceType = "T_CREDIT_VIOLATION")
    public void export(@Valid CreditViolationQuery query,
                       @RequestParam(required = false) List<Long> ids,
                       HttpServletResponse response) throws IOException {
        List<CreditViolationSaveReq> rows = service.exportRows(query, ids);
        prepareExcelResponse(response, "信贷风险信息");
        EasyExcel.write(response.getOutputStream(), CreditViolationSaveReq.class)
                // 多级表头的合并范围由写处理器精确控制，避免框架将末级空标题横向合并。
                .automaticMergeHead(false)
                .registerWriteHandler(new CreditViolationExcelWriteHandler(true))
                .sheet("信贷风险信息").doWrite(rows);
        log.info("[CreditViolationController.export] count={}", rows.size());
    }

    /** 下载信贷风险信息导入模板，仅保留当前导入字段。 */
    @Operation(summary = "下载信贷风险信息导入模板")
    @GetMapping("/import-template")
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.IMPORT)
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        prepareExcelResponse(response, "信贷风险信息导入模板");
        EasyExcel.write(response.getOutputStream(), CreditViolationSaveReq.class)
                // 与导出保持一致：仅使用写处理器声明的合并范围。
                .automaticMergeHead(false)
                .registerWriteHandler(new CreditViolationExcelWriteHandler(false))
                .excludeColumnFieldNames(Set.of(
                        "responsibilityDetermination", "isEconomicDeduction", "recycleAndReturn", "isFalseBuckle",
                        "otherProcessing", "responsibilityDeterminationTime", "isReportToTheBankingRegulatoryCommission",
                        "submissionTime", "remark"))
                .sheet("信贷风险信息")
                .doWrite(List.of());
    }

    /** 从 Excel 导入信贷风险信息。 */
    @Operation(summary = "导入信贷风险信息")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.IMPORT)
    @AuditLog(action = "YD_CREDIT_IMPORT", resourceType = "T_CREDIT_VIOLATION", reasonRequired = true)
    public ResponseWrapper<Integer> importExcel(@Valid ViolationImportReq req) {
        validateFile(req.getFile());
        try (InputStream input = req.getFile().getInputStream()) {
            List<CreditViolationSaveReq> rows = EasyExcel
                    .read(new ByteArrayInputStream(expandMergedCustomerCells(input)), CreditViolationSaveReq.class, null)
                    .registerConverter(new TextLocalDateTimeConverter())
                    .headRowNumber(4).sheet().doReadSync();
            int count = service.importRows(rows);
            log.info("[CreditViolationController.importExcel] filename={}, count={}, reason={}",
                    req.getFile().getOriginalFilename(), count, req.getReason());
            return ResponseWrapper.success(count);
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BizException bizException) {
                throw bizException;
            }
            throw error(YundunErrorCode.IMPORT_PARSE_FAILED);
        }
    }

    private void validateFile(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw error(YundunErrorCode.IMPORT_INVALID);
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw error(YundunErrorCode.IMPORT_FILE_TOO_LARGE);
        }
    }

    /**
     * 将用户上传文件中同一客户的纵向合并单元格还原为每行都有值的普通单元格后再解析。
     *
     * <p>EasyExcel 只读取合并区左上角，若不展开，第二行起的 A-H、AE 会被导入为空值。
     * 仅处理数据区的 A-H、AE 单列合并，不影响四层表头或其他责任明细字段。</p>
     */
    private byte[] expandMergedCustomerCells(InputStream input) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                expandMergedCustomerCells(workbook.getSheetAt(sheetIndex));
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void expandMergedCustomerCells(Sheet sheet) {
        for (int index = sheet.getNumMergedRegions() - 1; index >= 0; index--) {
            CellRangeAddress range = sheet.getMergedRegion(index);
            if (range.getFirstRow() < CREDIT_DATA_START_ROW
                    || range.getFirstColumn() != range.getLastColumn()
                    || !isCustomerColumn(range.getFirstColumn())) {
                continue;
            }
            Row sourceRow = sheet.getRow(range.getFirstRow());
            Cell source = sourceRow == null ? null : sourceRow.getCell(range.getFirstColumn());
            for (int rowIndex = range.getFirstRow() + 1; rowIndex <= range.getLastRow(); rowIndex++) {
                Row targetRow = sheet.getRow(rowIndex);
                if (targetRow == null) {
                    targetRow = sheet.createRow(rowIndex);
                }
                copyCellValue(source, targetRow.getCell(range.getFirstColumn(), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
            }
            sheet.removeMergedRegion(index);
        }
    }

    private boolean isCustomerColumn(int column) {
        return (column >= 0 && column <= 7) || column == CREDIT_DOCUMENT_COLUMN;
    }

    private void copyCellValue(Cell source, Cell target) {
        if (source == null) {
            target.setBlank();
            return;
        }
        CellType type = source.getCellType();
        switch (type) {
            case STRING -> target.setCellValue(source.getStringCellValue());
            case NUMERIC -> target.setCellValue(source.getNumericCellValue());
            case BOOLEAN -> target.setCellValue(source.getBooleanCellValue());
            case FORMULA -> target.setCellFormula(source.getCellFormula());
            case ERROR -> target.setCellErrorValue(source.getErrorCellValue());
            case BLANK, _NONE -> target.setBlank();
        }
    }

    private BizException error(YundunErrorCode code) {
        return new BizException(code.getCode(), code.getMessage());
    }

    private void prepareExcelResponse(HttpServletResponse response, String prefix) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String filename = URLEncoder.encode(prefix + "_" + timestamp + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
    }
}
