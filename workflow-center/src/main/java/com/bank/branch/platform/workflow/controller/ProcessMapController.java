package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.service.ProcessStartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程映射查询控制器
 * <p>
 * 提供根据业务键查询流程映射记录接口。
 * 对齐设计文档 C.4: GET /api/workflow/process-map
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow/process-map")
@Tag(name = "流程映射查询", description = "根据业务键查询流程映射")
public class ProcessMapController {

    private final ProcessStartService processStartService;

    /**
     * 根据业务键查询流程映射记录
     * 设计文档 C.4: GET /api/workflow/process-map
     *
     * @param businessKey 业务键 (可选，与 bizType+bizId 二选一)
     * @param bizType     业务类型 (可选，与 businessKey 二选一)
     * @param bizId       业务ID (可选，与 businessKey 二选一)
     * @return 流程映射 DTO
     */
    @GetMapping
    @Operation(summary = "根据业务键查询流程映射")
    public ResponseWrapper<BizProcessMapDTO> getProcessMap(
            @RequestParam(value = "businessKey", required = false) String businessKey,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "bizId", required = false) String bizId) {
        log.debug("[ProcessMapController.getProcessMap] businessKey={}, bizType={}, bizId={}", businessKey, bizType, bizId);
        BizProcessMapDTO dto;
        if (StringUtils.hasText(businessKey)) {
            dto = processStartService.getProcessByBusinessKey(businessKey);
        } else if (StringUtils.hasText(bizType) && StringUtils.hasText(bizId)) {
            dto = processStartService.getProcessByBizTypeAndBizId(bizType, bizId);
        } else {
            throw new IllegalArgumentException("businessKey 或 bizType+bizId 必须提供其一");
        }
        return ResponseWrapper.success(dto);
    }
}
