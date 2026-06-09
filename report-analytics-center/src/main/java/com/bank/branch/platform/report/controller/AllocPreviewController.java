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
    @Operation(summary = "原业绩分配预览（取分配调整申请审批通过的最后一条）")
    public ResponseWrapper<AllocPreviewRespDTO> preview(
            @RequestParam(required = false) String custType,
            @RequestParam String custNo,
            @RequestParam(required = false) String allocDim,
            @RequestParam(required = false) String accountNo,
            @RequestParam(required = false) String statisDt) {
        // 按客户编号取审批通过的最后一条分配；allocDim=ACCOUNT 只查按账号分配，RULE/空查两者。
        // custType/accountNo/statisDt 为兼容旧前端查询串保留入参但不再使用。
        return ResponseWrapper.success(allocPreviewService.preview(custNo, allocDim));
    }
}
