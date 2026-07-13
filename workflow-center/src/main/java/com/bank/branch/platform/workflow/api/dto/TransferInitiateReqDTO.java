package com.bank.branch.platform.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 两阶段转交发起请求 DTO。
 * <p>
 * 与 {@link TransferReqDTO}（一步到位转办）不同：本 DTO 用于发起「待认领」转交
 * （{@code TaskTransferService#initiate}），需接收人主动认领/拒绝才生效，
 * 转交记录先落 {@code WF_TASK_TRANSFER} 表 status=PENDING_ACCEPT，不立即变更任务 assignee。
 * </p>
 */
@Data
public class TransferInitiateReqDTO {

    /** 转交接收人工号 */
    @NotBlank(message = "转交接收人不能为空")
    private String toEmpId;

    /** 转交原因（必填） */
    @NotBlank(message = "转交原因不能为空")
    @Size(max = 500, message = "转交原因长度不能超过500")
    private String reason;
}
