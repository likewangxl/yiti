package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.controller.dto.AllocPreviewRespDTO;
import com.bank.branch.platform.report.service.AllocPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业绩调整分配预览接口
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "分配预览", description = "业绩调整申请时的余额和分配关系预览")
public class AllocPreviewController {

    private final AllocPreviewService allocPreviewService;

    @GetMapping("/api/report/alloc-preview")
    @Operation(summary = "分配预览（余额汇总 + 原业绩分配关系）")
    public ResponseWrapper<AllocPreviewRespDTO> preview(
            @RequestParam String custType,
            @RequestParam String custNo,
            @RequestParam String allocDim,
            @RequestParam(required = false) String accountNo,
            @RequestParam(required = false) String statisDt) {
        java.time.LocalDate dt = null;
        if (statisDt != null && !statisDt.isEmpty()) {
            dt = java.time.LocalDate.parse(statisDt);
        }
        return ResponseWrapper.success(allocPreviewService.preview(custType, custNo, allocDim, accountNo, dt));
    }
}
