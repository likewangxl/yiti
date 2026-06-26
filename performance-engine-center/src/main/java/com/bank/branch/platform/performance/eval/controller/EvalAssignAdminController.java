package com.bank.branch.platform.performance.eval.controller;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportResultDTO;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportRow;
import com.bank.branch.platform.performance.eval.service.EvalAssignImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * 错误数据超过该阈值时不再返回 JSON，改为生成错误明细 CSV 文件供下载。
     * 默认 100，可经 {@code application.yml} 的 {@code perf.eval.import.error-csv-threshold} 覆盖（不写死）。
     */
    @Value("${perf.eval.import.error-csv-threshold:100}")
    private int errorCsvThreshold = 100;

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
     * 导入评价任务（Excel，同步原子）。
     *
     * @param file     上传的 .xlsx 文件
     * @param taskType 待处理任务类型（EVAL/REWARD，本期主要 EVAL）
     * @param taskName 任务名称（必填）
     * @param deadline 打分截止时间（yyyy-MM-dd HH:mm:ss）
     * @param response HTTP 响应（错误数据超过阈值时直接写出 CSV 文件流）
     * @return 导入结果（成功条数或行级错误明细）；当错误数据超过 {@link #errorCsvThreshold} 条时，
     *         响应被写为错误明细 CSV 文件，方法返回 {@code null}（响应已提交，不再走 JSON 序列化）
     */
    @PostMapping("/import")
    @Operation(summary = "导入待处理任务（评价任务）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.IMPORT)
    public ResponseWrapper<EvalAssignImportResultDTO> importExcel(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "taskType", defaultValue = "EVAL") String taskType,
            @RequestParam("taskName") String taskName,
            @RequestParam("deadline")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime deadline,
            HttpServletResponse response) throws IOException {
        String createBy = currentUserApi.getCurrentEmpId();
        log.info("[EvalAssignAdminController.importExcel] fileName={}, taskType={}, taskName={}, deadline={}, createBy={}",
                file != null ? file.getOriginalFilename() : null, taskType, taskName, deadline, createBy);
        EvalAssignImportResultDTO result = evalAssignImportService.importExcel(file, taskType, taskName, deadline, createBy);
        // 错误数据超过阈值：海量行级错误塞进 JSON 既难传输也不便排查，改为生成错误明细 CSV 供下载
        if (!result.isSuccess() && result.getErrors().size() > errorCsvThreshold) {
            log.info("[EvalAssignAdminController.importExcel] 错误 {} 条 > {}，生成错误明细 CSV",
                    result.getErrors().size(), errorCsvThreshold);
            writeErrorCsv(response, result.getErrors());
            return null;
        }
        return ResponseWrapper.success(result);
    }

    /** 设置错误数据触发 CSV 的阈值（仅供测试覆盖默认配置）。 */
    void setErrorCsvThreshold(int errorCsvThreshold) {
        this.errorCsvThreshold = errorCsvThreshold;
    }

    /**
     * 将行级错误明细写为 CSV 文件流（含行号与具体错误信息）。
     *
     * <p>带 UTF-8 BOM 以便 Excel 直接打开不乱码；字段按 RFC 4180 转义（含逗号/引号/换行的字段
     * 整体加双引号，内部双引号转义为两个双引号）。</p>
     *
     * @param response HTTP 响应
     * @param errors   行级错误明细
     */
    private void writeErrorCsv(HttpServletResponse response,
                               List<EvalAssignImportResultDTO.RowError> errors) throws IOException {
        response.reset();
        response.setContentType("text/csv; charset=UTF-8");
        String fileName = URLEncoder.encode("导入错误明细.csv", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        StringBuilder sb = new StringBuilder();
        sb.append('﻿'); // UTF-8 BOM
        sb.append("行号,错误信息\r\n");
        for (EvalAssignImportResultDTO.RowError e : errors) {
            sb.append(csvCell(String.valueOf(e.getRow())))
                    .append(',')
                    .append(csvCell(e.getMessage()))
                    .append("\r\n");
        }
        response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    /** CSV 单元格转义：含逗号/双引号/换行时整体加双引号，内部双引号翻倍。 */
    private String csvCell(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
