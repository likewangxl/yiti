package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.service.ProcessStartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程查询控制器
 * <p>
 * 提供按业务键和业务类型+业务ID查询流程映射记录的接口。
 * 无 @BizAuth 注解。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow/processes")
@Tag(name = "流程查询", description = "流程映射信息查询")
public class ProcessController {

    private final ProcessStartService processStartService;

    /**
     * 根据业务键查询流程映射记录
     *
     * @param businessKey 业务键
     * @return 流程映射 DTO
     */
    @GetMapping("/{businessKey}")
    @Operation(summary = "根据业务键查询流程")
    public ResponseWrapper<BizProcessMapDTO> getProcessByBusinessKey(
            @PathVariable(value = "businessKey") String businessKey) {
        log.debug("[ProcessController.getProcessByBusinessKey] businessKey={}", businessKey);
        BizProcessMapDTO dto = processStartService.getProcessByBusinessKey(businessKey);
        return ResponseWrapper.success(dto);
    }

    /**
     * 根据业务类型和业务ID查询流程映射记录
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @return 流程映射 DTO
     */
    @GetMapping("/biz/{bizType}/{bizId}")
    @Operation(summary = "根据业务类型和业务ID查询流程")
    public ResponseWrapper<BizProcessMapDTO> getProcessByBizTypeAndBizId(
            @PathVariable(value = "bizType") String bizType,
            @PathVariable(value = "bizId") String bizId) {
        log.debug("[ProcessController.getProcessByBizTypeAndBizId] bizType={}, bizId={}", bizType, bizId);
        BizProcessMapDTO dto = processStartService.getProcessByBizTypeAndBizId(bizType, bizId);
        return ResponseWrapper.success(dto);
    }
}
