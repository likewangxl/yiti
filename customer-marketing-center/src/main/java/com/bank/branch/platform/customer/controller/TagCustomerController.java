package com.bank.branch.platform.customer.controller;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.req.TagCustomerImportReqDTO;
import com.bank.branch.platform.customer.dto.req.TagCustomerImportRow;
import com.bank.branch.platform.customer.dto.resp.TagCustomerImportResultDTO;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.CustTagRel;
import com.bank.branch.platform.customer.service.TagCustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 标签-客户关联管理 REST 控制器。
 * <p>
 * 提供标签客户的覆盖式导入和标签关联客户列表查询。
 * 导入操作需要 {@link BizAction#IMPORT} 权限，并记录审计日志。
 * 列表查询需要 {@link BizAction#READ} 权限。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tags")
@Validated
@Tag(name = "标签客户管理")
public class TagCustomerController {

    private final TagCustomerService tagCustomerService;
    private final CurrentUserApi currentUserApi;

    /**
     * 覆盖式导入标签客户列表。
     * <p>
     * 将完全替换该标签当前关联的客户列表（先删后批量插入）。
     * </p>
     *
     * @param tagId 标签 ID
     * @param req   包含客户 ID 列表的请求体
     * @return 操作结果
     */
    @PostMapping("/{tagId}/customers/import")
    @BizAuth(bizType = BizType.TAG, action = BizAction.IMPORT)
    @AuditLog(action = "IMPORT", resourceType = "TAG_CUSTOMER")
    @Operation(summary = "标签客户覆盖式导入")
    public ResponseWrapper<Void> importCustomers(@PathVariable String tagId,
                                                  @Valid @RequestBody TagCustomerImportReqDTO req) {
        log.info("[TagCustomerController.importCustomers] tagId={}, custCount={}", tagId, req.getCustIds().size());
        String empId = currentUserApi.getCurrentEmpId();
        tagCustomerService.importCustomers(tagId, req.getCustIds(), req.getMode(), empId);
        return ResponseWrapper.success();
    }

    /**
     * 上传 Excel 完成追加或全量替换导入。
     */
    @PostMapping(value = "/{tagId}/customers/import-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @BizAuth(bizType = BizType.TAG, action = BizAction.IMPORT)
    @AuditLog(action = "IMPORT", resourceType = "TAG_CUSTOMER")
    @Operation(summary = "客户标签 Excel 追加/全量替换导入")
    public ResponseWrapper<TagCustomerImportResultDTO> importCustomersFile(
            @PathVariable String tagId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "APPEND") String mode) {
        String empId = currentUserApi.getCurrentEmpId();
        log.info("[TagCustomerController.importCustomersFile] tagId={}, mode={}, fileName={}",
                tagId, mode, file != null ? file.getOriginalFilename() : null);
        return ResponseWrapper.success(tagCustomerService.importCustomersFile(tagId, file, mode, empId));
    }

    /**
     * 下载当前标签的 Excel 导入模板。模板中预填当前标签名称和说明。
     */
    @GetMapping("/{tagId}/customers/import-template")
    @BizAuth(bizType = BizType.TAG, action = BizAction.IMPORT)
    @Operation(summary = "下载客户标签导入模板")
    public void downloadImportTemplate(@PathVariable String tagId,
                                       HttpServletResponse response) throws IOException {
        TagCustomerImportRow templateRow = tagCustomerService.getImportTemplateRow(tagId);
        String fileName = "客户标签导入模板_" + templateRow.getTagName() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        EasyExcel.write(response.getOutputStream(), TagCustomerImportRow.class)
                .sheet("客户标签导入").doWrite(List.of(templateRow));
    }

    /**
     * 查询标签关联的客户列表。
     *
     * @param tagId 标签 ID
     * @return 关联的客户列表
     */
    @GetMapping("/{tagId}/customers")
    @BizAuth(bizType = BizType.TAG, action = BizAction.READ)
    @Operation(summary = "查询标签关联客户列表")
    public ResponseWrapper<List<CustTagRel>> listCustomers(@PathVariable String tagId) {
        log.debug("[TagCustomerController.listCustomers] tagId={}", tagId);
        return ResponseWrapper.success(tagCustomerService.listCustomersByTag(tagId));
    }

    /**
     * 导出标签关联的客户列表（高危操作，CSV 格式）。
     * <p>
     * 流式写出 CSV 至 HttpServletResponse，包含标签下所有关联客户主档信息。
     * 高危操作，需要 READ 权限，@AuditLog reasonRequired=true 强制留痕。
     * </p>
     *
     * @param tagId    标签 ID（路径参数）
     * @param response HttpServletResponse 用于流式写出
     * @throws IOException IO 异常（写出流时发生）
     */
    @GetMapping("/{tagId}/customers/export")
    @BizAuth(bizType = BizType.TAG, action = BizAction.EXPORT)
    @AuditLog(action = "EXPORT_TAG_CUSTOMERS", resourceType = "TAG", reasonRequired = true)
    @Operation(summary = "导出标签关联客户列表（CSV，高危操作）")
    public void exportTagCustomers(@PathVariable String tagId,
                                   HttpServletResponse response) throws IOException {
        log.info("[TagCustomerController.exportTagCustomers] tagId={}", tagId);

        // 设置 CSV 响应头
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"tag_customers_" + tagId + "_" + System.currentTimeMillis() + ".csv\"");

        List<CustMaster> list = tagCustomerService.listCustomersForExport(tagId);
        log.info("[TagCustomerController.exportTagCustomers] tagId={}, 导出记录数={}", tagId, list.size());

        try (PrintWriter out = response.getWriter()) {
            // 写出 CSV 表头
            out.println("客户编号,客户名称,统一社会信用代码,状态,创建时间");
            // 逐行写出数据（流式，不积累内存）
            for (CustMaster c : list) {
                out.println(String.join(",",
                        quote(c.getCustNo()),
                        quote(c.getCustName()),
                        quote(c.getUnifiedCreditCode()),
                        quote(c.getStatus()),
                        quote(c.getCreatedTime() != null ? c.getCreatedTime().toString() : "")));
            }
        }
    }

    /**
     * CSV 字段安全引用：用双引号包裹，内部双引号转义为两个双引号（RFC 4180 规范）。
     *
     * @param s 原始字符串，null 时返回空字符串
     * @return 安全引用后的 CSV 字段
     */
    private static String quote(String s) {
        if (s == null) {
            return "";
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
