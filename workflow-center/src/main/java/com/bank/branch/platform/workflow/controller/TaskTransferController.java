package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.api.dto.TransferCandidateDTO;
import com.bank.branch.platform.workflow.api.dto.TransferDecisionReqDTO;
import com.bank.branch.platform.workflow.api.dto.TransferInitiateReqDTO;
import com.bank.branch.platform.workflow.api.dto.TransferItemDTO;
import com.bank.branch.platform.workflow.service.TaskTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务转交待认领控制器：发起（秘书岗/行长代发起）+ 接收人收件箱/认领/拒绝 + 发起人发件箱/撤回。
 * <p>
 * 旧单阶段「一步到位直接改 assignee」的转交（{@code TaskController#transferTask}）已下线；
 * 本控制器对应 {@link com.bank.branch.platform.workflow.service.TaskTransferService} 的两阶段转交
 * （发起后先落 {@code WF_TASK_TRANSFER} 待认领记录，须接收人主动认领/拒绝才真正转移办理权）。
 * </p>
 * <p>
 * 发起端点挂在监控域下（{@code /monitor/tasks/{taskId}/transfer}），鉴权对齐审批流监控
 * （{@code @BizAuth(WORKFLOW_MONITOR, TRANSFER)}，秘书岗/行长同一批数据范围）；
 * 收件箱/认领/拒绝/发件箱/撤回挂在 {@code /transfers/*} 下，接收人可以是任意具备任务办理
 * 角色的人，不限秘书岗/行长，仅需登录（PT_RESOURCE 仍必须登记，否则 ResourceMatcher 403）。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow")
@Tag(name = "任务转交待认领", description = "两阶段转交：发起/收件箱/认领/拒绝/发件箱/撤回")
public class TaskTransferController {

    private final TaskTransferService taskTransferService;

    /**
     * 发起两阶段转交（待认领）。
     *
     * @param taskId 任务ID
     * @param req    转交发起请求（接收人工号 + 原因）
     * @return 转交记录ID
     */
    @PostMapping("/monitor/tasks/{taskId}/transfer")
    @Operation(summary = "发起转交（待认领）")
    @BizAuth(bizType = BizType.WORKFLOW_MONITOR, action = BizAction.TRANSFER)
    @AuditLog(action = "TRANSFER", resourceType = "WORKFLOW_TASK", reasonRequired = true)
    public ResponseWrapper<String> initiate(@PathVariable String taskId,
            @Valid @RequestBody TransferInitiateReqDTO req) {
        log.info("[TaskTransferController.initiate] taskId={}, toEmpId={}", taskId, req.getToEmpId());
        return ResponseWrapper.success(taskTransferService.initiate(taskId, req));
    }

    /**
     * 列出该任务可选的转交接收人（发起弹窗用）。
     * <p>
     * 与 {@link #initiate} 同域、同鉴权（{@code WORKFLOW_MONITOR/TRANSFER}）——候选人名单
     * 等于「谁能办理这个节点」，属于与发起同级的敏感信息，不能比发起动作更松。
     * </p>
     * <p>
     * 返回列表与 initiate 的资格校验同源：列表里的人提交必定通过，不会再出现
     * 「弹窗能选、提交被 WF-40912 打回」。
     * </p>
     *
     * @param taskId 任务ID
     * @return 可选接收人列表
     */
    @GetMapping("/monitor/tasks/{taskId}/transfer-candidates")
    @Operation(summary = "查询可转交接收人")
    @BizAuth(bizType = BizType.WORKFLOW_MONITOR, action = BizAction.TRANSFER)
    public ResponseWrapper<List<TransferCandidateDTO>> transferCandidates(@PathVariable String taskId) {
        return ResponseWrapper.success(taskTransferService.listCandidates(taskId));
    }

    /**
     * 转交收件箱：当前登录用户待认领的转交任务列表。
     *
     * @return 展示项列表
     */
    @GetMapping("/transfers/inbox")
    @Operation(summary = "转交收件箱")
    public ResponseWrapper<List<TransferItemDTO>> inbox() {
        return ResponseWrapper.success(taskTransferService.listInbox());
    }

    /**
     * 接收人认领转交。
     *
     * @param id 转交记录ID
     * @return 成功响应
     */
    @PostMapping("/transfers/{id}/accept")
    @Operation(summary = "认领转交")
    @AuditLog(action = "TRANSFER_ACCEPT", resourceType = "WORKFLOW_TASK")
    public ResponseWrapper<Void> accept(@PathVariable String id) {
        log.info("[TaskTransferController.accept] id={}", id);
        taskTransferService.accept(id);
        return ResponseWrapper.success();
    }

    /**
     * 接收人拒绝转交（理由必填）。
     *
     * @param id  转交记录ID
     * @param req 拒绝请求（理由）
     * @return 成功响应
     */
    @PostMapping("/transfers/{id}/decline")
    @Operation(summary = "拒绝转交")
    @AuditLog(action = "TRANSFER_DECLINE", resourceType = "WORKFLOW_TASK", reasonRequired = true)
    public ResponseWrapper<Void> decline(@PathVariable String id,
            @Valid @RequestBody TransferDecisionReqDTO req) {
        log.info("[TaskTransferController.decline] id={}", id);
        taskTransferService.decline(id, req.getReason());
        return ResponseWrapper.success();
    }

    /**
     * 转交发件箱：当前登录用户发起的转交任务列表（待认领+已认领）。
     *
     * @return 展示项列表
     */
    @GetMapping("/transfers/outbox")
    @Operation(summary = "转交发件箱")
    public ResponseWrapper<List<TransferItemDTO>> outbox() {
        return ResponseWrapper.success(taskTransferService.listOutbox());
    }

    /**
     * 发起人撤回转交。
     *
     * @param id 转交记录ID
     * @return 成功响应
     */
    @PostMapping("/transfers/{id}/cancel")
    @Operation(summary = "撤回转交")
    @AuditLog(action = "TRANSFER_CANCEL", resourceType = "WORKFLOW_TASK")
    public ResponseWrapper<Void> cancel(@PathVariable String id) {
        log.info("[TaskTransferController.cancel] id={}", id);
        taskTransferService.cancel(id);
        return ResponseWrapper.success();
    }
}
