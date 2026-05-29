package com.bank.branch.platform.performance.eval.controller;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.dto.EvalUserRoleExportRow;
import com.bank.branch.platform.performance.eval.dto.EvalUserRoleRowDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportResultDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalUserTag;
import com.bank.branch.platform.performance.eval.service.EvalUserTagImportService;
import com.bank.branch.platform.performance.eval.service.EvalUserTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 人员标签关联管理控制器.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/eval/user-tags")
@Tag(name = "Eval User Tag", description = "人员标签关联管理")
@Validated
@RequiredArgsConstructor
public class EvalUserTagController {

    /** 导出最大行数保护。 */
    private static final int EXPORT_ROWS_CAP = 10000;

    private final EvalUserTagService evalUserTagService;
    private final EvalUserTagImportService evalUserTagImportService;

    @GetMapping
    @Operation(summary = "查询人员标签")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<List<EvalUserTag>> list(@RequestParam("userId") String userId) {
        log.debug("[EvalUserTagController.list] userId={}", userId);
        return ResponseWrapper.success(evalUserTagService.getByUserId(userId));
    }

    @PostMapping
    @Operation(summary = "批量绑定人员标签")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<Void> bind(@Validated @RequestBody BindReq req) {
        log.info("[EvalUserTagController.bind] userId={}, tagIds={}", req.getUserId(), req.getTagIds());
        evalUserTagService.batchBind(req.getUserId(), req.getTagIds());
        return ResponseWrapper.success();
    }

    @DeleteMapping
    @Operation(summary = "批量解绑人员标签")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.DELETE)
    public ResponseWrapper<Void> unbind(@Validated @RequestBody BindReq req) {
        log.info("[EvalUserTagController.unbind] userId={}, tagIds={}", req.getUserId(), req.getTagIds());
        evalUserTagService.batchUnbind(req.getUserId(), req.getTagIds());
        return ResponseWrapper.success();
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询人员标签列表（含部门/岗位/角色）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<PageResult<EvalUserRoleRowDTO>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[EvalUserTagController.page] keyword={}, page={}, pageSize={}", keyword, page, pageSize);
        return ResponseWrapper.success(evalUserTagService.pageUserRoles(keyword, page, pageSize));
    }

    @PutMapping("/{userId}/roles")
    @Operation(summary = "覆盖式保存人员评价角色（被评价单选/评价人多选）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<Void> saveRoles(@PathVariable("userId") String userId,
                                           @Validated @RequestBody SaveRolesReq req) {
        log.info("[EvalUserTagController.saveRoles] userId={}, beEvalTagId={}, evalTagIds={}",
                userId, req.getBeEvalTagId(), req.getEvalTagIds());
        evalUserTagService.saveUserRoles(userId, req.getBeEvalTagId(), req.getEvalTagIds());
        return ResponseWrapper.success();
    }

    @PostMapping("/import")
    @Operation(summary = "导入人员评价角色（Excel，同步原子）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.IMPORT)
    public ResponseWrapper<EvalUserTagImportResultDTO> importExcel(
            @RequestPart("file") MultipartFile file) {
        log.info("[EvalUserTagController.importExcel] fileName={}",
                file != null ? file.getOriginalFilename() : null);
        return ResponseWrapper.success(evalUserTagImportService.importExcel(file));
    }

    @GetMapping("/import-template")
    @Operation(summary = "下载人员评价角色导入模板")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.IMPORT)
    public void importTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("人员评价角色导入模板.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        EvalUserTagImportRow sample = new EvalUserTagImportRow();
        sample.setEmpId("100001");
        sample.setBeEvalRoleName("支行行长");
        sample.setEvalRoleNames("副行长,客户经理");
        EasyExcel.write(response.getOutputStream(), EvalUserTagImportRow.class)
                .sheet("人员评价角色")
                .doWrite(List.of(sample));
    }

    @GetMapping("/export")
    @Operation(summary = "导出人员标签列表（Excel，按关键词）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.EXPORT)
    public void export(@RequestParam(value = "keyword", required = false) String keyword,
                       HttpServletResponse response) throws IOException {
        log.info("[EvalUserTagController.export] keyword={}", keyword);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("人员标签列表.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        List<EvalUserRoleRowDTO> rows = evalUserTagService.listForExport(keyword, EXPORT_ROWS_CAP);
        List<EvalUserRoleExportRow> out = new ArrayList<>(rows.size());
        for (EvalUserRoleRowDTO r : rows) {
            EvalUserRoleExportRow e = new EvalUserRoleExportRow();
            e.setUserName(r.getUserName());
            e.setEmpId(r.getUserId());
            e.setOrgName(r.getOrgName());
            e.setPosition(r.getPosition());
            e.setRoleNames(r.getRoleNames() == null ? "" : String.join("，", r.getRoleNames()));
            e.setBeEvalRole(r.getBeEvalTag() == null ? "" : r.getBeEvalTag().getTagName());
            e.setEvalRoles(r.getEvalTags() == null ? "" : r.getEvalTags().stream()
                    .map(t -> t.getTagName()).collect(Collectors.joining("，")));
            out.add(e);
        }
        EasyExcel.write(response.getOutputStream(), EvalUserRoleExportRow.class)
                .sheet("人员标签列表")
                .doWrite(out);
    }

    @Data
    public static class BindReq {
        @NotNull
        private String userId;
        private List<Long> tagIds;
    }

    @Data
    public static class SaveRolesReq {
        /** 被评价人标签ID（null 表示清空被评价人角色）. */
        private Long beEvalTagId;
        /** 评价人标签ID列表（null/空 表示清空评价人角色）. */
        private List<Long> evalTagIds;
    }
}
