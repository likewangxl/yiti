package com.bank.branch.platform.yundun.controller;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationDTO;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationQuery;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationSaveReq;
import com.bank.branch.platform.yundun.dto.ViolationBatchDeleteReq;
import com.bank.branch.platform.yundun.dto.ViolationImportReq;
import com.bank.branch.platform.yundun.excel.AccountabilityViolationExcelWriteHandler;
import com.bank.branch.platform.yundun.excel.TextLocalDateTimeConverter;
import com.bank.branch.platform.yundun.enums.YundunErrorCode;
import com.bank.branch.platform.yundun.service.AccountabilityViolationService;
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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/** 人员违规问责信息 REST 接口。 */
@Slf4j
@Validated
@Tag(name = "浦爱云盾-人员违规信息")
@RestController
@RequestMapping("/api/yundun/accountability-violations")
@RequiredArgsConstructor
public class AccountabilityViolationController {

    private final AccountabilityViolationService service;

    /** 分页查询人员违规信息。 */
    @Operation(summary = "分页查询人员违规信息")
    @GetMapping
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.LIST)
    public ResponseWrapper<AccountabilityViolationDTO> list(@Valid AccountabilityViolationQuery query) {
        IPage<AccountabilityViolationDTO> page = service.page(query);
        log.info("[AccountabilityViolationController.list] pageNo={}, pageSize={}, total={}",
                query.normalizedPageNo(), query.normalizedPageSize(), page.getTotal());
        return ResponseWrapper.page(PageResult.of(
                (int) page.getCurrent(), (int) page.getSize(), page.getTotal(), page.getRecords()));
    }

    /** 查询人员违规详情。 */
    @Operation(summary = "查询人员违规详情")
    @GetMapping("/{id:[0-9]{1,19}}")
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.READ)
    public ResponseWrapper<AccountabilityViolationDTO> get(@PathVariable Long id) {
        AccountabilityViolationDTO result = service.get(id);
        log.info("[AccountabilityViolationController.get] id={}", id);
        return ResponseWrapper.success(result);
    }

    /** 新增人员违规信息。 */
    @Operation(summary = "新增人员违规信息")
    @PostMapping
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.WRITE)
    @AuditLog(action = "YD_ACCOUNTABILITY_ADD", resourceType = "T_ACCOUNTABILITY_FOR_VIOLATIONS")
    public ResponseWrapper<AccountabilityViolationDTO> create(
            @Valid @RequestBody AccountabilityViolationSaveReq req) {
        AccountabilityViolationDTO result = service.create(req);
        log.info("[AccountabilityViolationController.create] id={}", result.getId());
        return ResponseWrapper.success(result);
    }

    /** 修改人员违规信息。 */
    @Operation(summary = "修改人员违规信息")
    @PutMapping("/{id:[0-9]{1,19}}")
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.WRITE)
    @AuditLog(action = "YD_ACCOUNTABILITY_UPDATE", resourceType = "T_ACCOUNTABILITY_FOR_VIOLATIONS")
    public ResponseWrapper<AccountabilityViolationDTO> update(
            @PathVariable Long id, @Valid @RequestBody AccountabilityViolationSaveReq req) {
        AccountabilityViolationDTO result = service.update(id, req);
        log.info("[AccountabilityViolationController.update] id={}", id);
        return ResponseWrapper.success(result);
    }

    /** 批量删除人员违规信息。 */
    @Operation(summary = "批量删除人员违规信息")
    @PostMapping("/batch-delete")
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.DELETE)
    @AuditLog(action = "YD_ACCOUNTABILITY_DELETE", resourceType = "T_ACCOUNTABILITY_FOR_VIOLATIONS",
            reasonRequired = true)
    public ResponseWrapper<Integer> delete(@Valid @RequestBody ViolationBatchDeleteReq req) {
        int count = service.delete(req.getIds());
        log.info("[AccountabilityViolationController.delete] count={}, reason={}", count, req.getReason());
        return ResponseWrapper.success(count);
    }

    /** 按当前条件或选中记录导出 Excel。 */
    @Operation(summary = "导出人员违规信息")
    @GetMapping("/export")
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.EXPORT)
    @AuditLog(action = "YD_ACCOUNTABILITY_EXPORT", resourceType = "T_ACCOUNTABILITY_FOR_VIOLATIONS")
    public void export(@Valid AccountabilityViolationQuery query,
                       @RequestParam(required = false) List<Long> ids,
                       HttpServletResponse response) throws IOException {
        List<AccountabilityViolationSaveReq> rows = service.exportRows(query, ids);
        prepareExcelResponse(response, "人员违规信息");
        EasyExcel.write(response.getOutputStream(), AccountabilityViolationSaveReq.class)
                .registerWriteHandler(new AccountabilityViolationExcelWriteHandler())
                .sheet("人员违规信息").doWrite(rows);
        log.info("[AccountabilityViolationController.export] count={}", rows.size());
    }

    /** 下载人员违规信息导入模板，仅保留当前导入字段。 */
    @Operation(summary = "下载人员违规信息导入模板")
    @GetMapping("/import-template")
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.IMPORT)
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        prepareExcelResponse(response, "人员违规信息导入模板");
        EasyExcel.write(response.getOutputStream(), AccountabilityViolationSaveReq.class)
                .registerWriteHandler(new AccountabilityViolationExcelWriteHandler())
                .excludeColumnFieldNames(Set.of(
                        "deptName", "roleClassification", "businessArea", "primaryAndSecondaryResponsibility",
                        "circumstancesOfTheViolation", "rankAtTheTimeOfTheViolation", "accountabilityRanks",
                        "otherAreas", "partyAttitude"))
                .sheet("人员违规信息")
                .doWrite(List.of());
    }

    /** 从 Excel 导入人员违规信息。 */
    @Operation(summary = "导入人员违规信息")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @BizAuth(bizType = BizType.VIOLATION, action = BizAction.IMPORT)
    @AuditLog(action = "YD_ACCOUNTABILITY_IMPORT", resourceType = "T_ACCOUNTABILITY_FOR_VIOLATIONS",
            reasonRequired = true)
    public ResponseWrapper<Integer> importExcel(@Valid ViolationImportReq req) {
        validateFile(req.getFile());
        try {
            List<AccountabilityViolationSaveReq> rows = EasyExcel
                    .read(req.getFile().getInputStream(), AccountabilityViolationSaveReq.class, null)
                    .registerConverter(new TextLocalDateTimeConverter())
                    .headRowNumber(1).sheet().doReadSync();
            int count = service.importRows(rows);
            log.info("[AccountabilityViolationController.importExcel] filename={}, count={}, reason={}",
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
