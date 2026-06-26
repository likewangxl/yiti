package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.DataImportQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.DataImportBatchVO;
import com.bank.branch.platform.report.dto.resp.DataImportDataVO;
import com.bank.branch.platform.report.service.DataImportQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据导入查询 REST 控制器（只读，历史数据查询 - 数据导入查询，归属报表分析中心）.
 *
 * <p>资源登记见 2026-06-25-history-data-query-resources.sql：
 * <ul>
 *   <li>GET /api/reports/data-imports             → R_RPT_DTIMP_LIST（批次列表，创建时间倒序）</li>
 *   <li>GET /api/reports/data-imports/{batchNum}  → R_RPT_DTIMP_DATA（批次透视数据）</li>
 * </ul></p>
 */
@Slf4j
@RestController
@RequestMapping("/api/reports/data-imports")
@Tag(name = "历史数据查询-数据导入查询", description = "amas_dt_import 批次列表 + 透视数据")
@Validated
@RequiredArgsConstructor
public class DataImportQueryController {

    private final DataImportQueryService dataImportQueryService;

    @GetMapping
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "数据导入批次列表")
    public ResponseWrapper<DataImportBatchVO> list(
            @Valid @ModelAttribute DataImportQueryReqDTO req,
            @Valid @ModelAttribute PageRequest page) {
        PageResult<DataImportBatchVO> result = dataImportQueryService.pageBatches(req, page);
        return ResponseWrapper.page(result);
    }

    @GetMapping("/{batchNum}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "数据导入批次透视数据（动态表头 + 当前页行，按逻辑行分页，支持前两列模糊过滤）")
    public ResponseWrapper<DataImportDataVO> data(
            @PathVariable("batchNum") String batchNum,
            @Valid @ModelAttribute PageRequest page,
            @RequestParam(value = "col1Key", required = false) String col1Key,
            @RequestParam(value = "col1Kw", required = false) String col1Kw,
            @RequestParam(value = "col2Key", required = false) String col2Key,
            @RequestParam(value = "col2Kw", required = false) String col2Kw) {
        // 前两列模糊过滤：{DT_TITLE_NO -> 关键字}，service 内再按可见列与空值清洗
        Map<String, String> filters = new LinkedHashMap<>();
        putFilter(filters, col1Key, col1Kw);
        putFilter(filters, col2Key, col2Kw);
        return ResponseWrapper.success(dataImportQueryService.batchData(batchNum, page, filters));
    }

    private void putFilter(Map<String, String> filters, String key, String kw) {
        if (key != null && !key.isBlank() && kw != null && !kw.isBlank()) {
            filters.put(key.trim(), kw.trim());
        }
    }

    @GetMapping("/{batchNum}/export")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "导出整个批次为 Excel（全部行）")
    public void export(@PathVariable("batchNum") String batchNum, HttpServletResponse resp) throws IOException {
        byte[] bytes = dataImportQueryService.exportExcel(batchNum);
        String fileName = "数据导入_" + batchNum + ".xlsx";
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
        resp.getOutputStream().write(bytes);
        resp.flushBuffer();
    }
}
