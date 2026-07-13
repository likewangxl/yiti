package com.bank.branch.platform.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 转交拒绝决策请求 DTO：接收人拒绝待认领转交（{@code TaskTransferService#decline}）时的必填理由。
 * <p>
 * 理由为强制字段——Controller 层（Task 12）用 {@code @Valid} 挡在最外层，
 * {@code TaskTransferService#decline} 内部再做一次非空兜底（防御性双重校验，
 * 防止未来出现绕过 Controller 直接调用 Service 的调用路径）。
 * </p>
 */
@Data
public class TransferDecisionReqDTO {

    /** 拒绝理由（必填） */
    @NotBlank(message = "拒绝理由不能为空")
    @Size(max = 500, message = "拒绝理由长度不能超过500")
    private String reason;
}
