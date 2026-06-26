package com.bank.branch.platform.performance.eval.controller;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportAcceptedDTO;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportRow;
import com.bank.branch.platform.performance.eval.service.EvalAssignImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 待处理任务（导入式评价任务）管理端控制器.
 * <p>提供导入模板下载与 Excel 导入。导入即生成一批显式配对明细，分发到各打分人的待处理任务。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/eval/assign")
@Tag(name = "Eval Assign", description = "待处理任务导入（管理端）")
@RequiredArgsConstructor
public class EvalAssignAdminController {

    private final EvalAssignImportService evalAssignImportService;
    private final CurrentUserApi currentUserApi;

    /**
     * 下载评价任务导入模板（10 列）。
     */
    @GetMapping("/import-template")
    @Operation(summary = "下载待处理任务导入模板")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.IMPORT)
    public void importTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("评价任务导入模板.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        EvalAssignImportRow sample = new EvalAssignImportRow();
        sample.setBeEvalUserId("100002");
        sample.setBeEvalUserName("张三");
        sample.setBeEvalDept("信贷部");
        sample.setBeEvalTag("客户经理");
        sample.setEvalUserId("100001");
        sample.setEvalUserName("李四");
        sample.setEvalUserTag("支行长");
        sample.setEvalUserDept("管理部");
        sample.setWeightTag("主要");
        sample.setScoreTypeText("数值打分");
        EasyExcel.write(response.getOutputStream(), EvalAssignImportRow.class)
                .sheet("评价任务")
                .doWrite(List.of(sample));
    }

    /**
     * 导入评价任务（Excel，异步受理）。
     *
     * <p>接口线程仅做「快、会立即失败」的事：解析文件成内存行集（文件空/格式错在此同步快速反馈，
     * 走原错误码）→ 建 IMPORTING(3) 批次并独立事务提交 → 触发后台 {@code @Async} 逐行校验入库，
     * 立即返回 {@code {batchId, status:3}}。前端据 batchId 轮询批次详情获取最终结果（草稿/失败）。</p>
     *
     * @param file     上传的 .xlsx 文件
     * @param taskType 待处理任务类型（EVAL/REWARD，本期主要 EVAL）
     * @param taskName 任务名称（必填）
     * @param deadline 打分截止时间（yyyy-MM-dd HH:mm:ss）
     * @return 受理结果 {@link EvalAssignImportAcceptedDTO}（batchId + status=3 处理中）
     */
    @PostMapping("/import")
    @Operation(summary = "导入待处理任务（评价任务，异步受理）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.IMPORT)
    public ResponseWrapper<EvalAssignImportAcceptedDTO> importExcel(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "taskType", defaultValue = "EVAL") String taskType,
            @RequestParam("taskName") String taskName,
            @RequestParam("deadline")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime deadline) {
        String createBy = currentUserApi.getCurrentEmpId();
        log.info("[EvalAssignAdminController.importExcel] fileName={}, taskType={}, taskName={}, deadline={}, createBy={}",
                file != null ? file.getOriginalFilename() : null, taskType, taskName, deadline, createBy);
        // 1. 同步解析（空文件/格式错快速反馈，走原错误码，不建批次）
        List<EvalAssignImportRow> rows = evalAssignImportService.parseRows(file);
        // 2. 建 IMPORTING(3) 批次并独立提交，使 batchId 立即对轮询可见
        Long batchId = evalAssignImportService.createImportingBatch(taskType, taskName, deadline, createBy);
        // 3. 交给异步线程逐行校验入库，立即返回受理结果
        evalAssignImportService.processImport(batchId, rows, taskType, taskName, deadline, createBy);
        return ResponseWrapper.success(new EvalAssignImportAcceptedDTO(batchId, 3));
    }
}
