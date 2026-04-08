package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.service.ProcessQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程查询控制器
 * <p>
 * 提供流程实例详情、流程图、历史查询及流程映射记录查询接口。
 * 无 @BizAuth 注解，通过 empId 或 processInstanceId 自然过滤数据。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow/processes")
@Tag(name = "流程查询", description = "流程实例/进度图/历史/映射查询")
public class ProcessController {

    private final ProcessQueryService processQueryService;

    // ==================== C.1 / C.2 / C.3 流程实例查询 ====================

    /**
     * 查询流程实例详情（C.1）
     *
     * @param processInstanceId 流程实例ID
     * @return 流程实例信息
     */
    @GetMapping("/{processInstanceId}")
    @Operation(summary = "查询流程实例详情")
    public ResponseWrapper<ProcessQueryService.ProcessInstanceInfo> getProcessInstanceInfo(
            @PathVariable(value = "processInstanceId") String processInstanceId) {
        log.debug("[ProcessController.getProcessInstanceInfo] processInstanceId={}", processInstanceId);
        ProcessQueryService.ProcessInstanceInfo info = processQueryService.getProcessInstanceInfo(processInstanceId);
        return ResponseWrapper.success(info);
    }

    /**
     * 获取流程进度图 PNG（C.2）
     *
     * @param processInstanceId 流程实例ID
     * @return PNG 图像字节数组
     */
    @GetMapping(value = "/{processInstanceId}/diagram", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "获取流程进度图")
    public byte[] getProcessDiagram(
            @PathVariable(value = "processInstanceId") String processInstanceId) {
        log.debug("[ProcessController.getProcessDiagram] processInstanceId={}", processInstanceId);
        return processQueryService.generateProcessDiagram(processInstanceId);
    }

    /**
     * 查询流程历史节点列表（C.3）
     *
     * @param processInstanceId 流程实例ID
     * @return 历史活动节点列表
     */
    @GetMapping("/{processInstanceId}/history")
    @Operation(summary = "查询流程历史节点")
    public ResponseWrapper<List<ProcessQueryService.ProcessHistoryDTO>> getProcessHistory(
            @PathVariable(value = "processInstanceId") String processInstanceId) {
        log.debug("[ProcessController.getProcessHistory] processInstanceId={}", processInstanceId);
        List<ProcessQueryService.ProcessHistoryDTO> history = processQueryService.getProcessHistory(processInstanceId);
        return ResponseWrapper.success(history);
    }

    // C.4 流程映射查询已移至 ProcessMapController
}
