package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.api.dto.AllocSummaryDTO;
import com.bank.branch.platform.performance.api.dto.CustAllocRelationDTO;
import com.bank.branch.platform.performance.service.AllocRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 客户业绩分配关系 REST 控制器 (3 个只读端点, 对齐 PT_RESOURCE P_PERF_ALLOC_*).
 *
 * <p>红步占位: Step 1 仅写骨架, 所有方法暂抛 {@link UnsupportedOperationException},
 * Step 2 接入 {@link AllocRelationService} 后转为真正实现.
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/alloc-relations")
@Tag(name = "Performance Alloc Relation", description = "客户业绩分配关系 (只读)")
@Validated
@RequiredArgsConstructor
public class AllocRelationController {

    private final CurrentUserApi currentUserApi;
    private final AllocRelationService allocRelationService;

    /**
     * 查询客户当前有效的分配关系.
     */
    @GetMapping
    @Operation(summary = "当前分配关系")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<CustAllocRelationDTO>> list(
            @RequestParam(value = "custId") @NotBlank String custId,
            @RequestParam(value = "bizKind", required = false) String bizKind) {
        throw new UnsupportedOperationException("red step placeholder");
    }

    /**
     * 查询客户在指定日期的分配关系快照.
     */
    @GetMapping("/history")
    @Operation(summary = "历史分配关系")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<CustAllocRelationDTO>> history(
            @RequestParam(value = "custId") @NotBlank String custId,
            @RequestParam(value = "asOfDate") @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        throw new UnsupportedOperationException("red step placeholder");
    }

    /**
     * 查询当前登录员工名下的分配关系汇总.
     */
    @GetMapping("/summary")
    @Operation(summary = "分配关系汇总")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<List<AllocSummaryDTO>> summary(
            @RequestParam(value = "bizKind", required = false) String bizKind,
            @RequestParam(value = "asOfDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        throw new UnsupportedOperationException("red step placeholder");
    }
}
