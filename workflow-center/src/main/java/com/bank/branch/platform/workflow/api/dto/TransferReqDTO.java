package com.bank.branch.platform.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务转交请求 DTO
 * <p>
 * 转交接收人工号和转交原因均为必填项。
 * 对齐设计文档 B.4: targetEmpId, reason
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferReqDTO {

    /** 转交接收人工号 */
    @NotBlank(message = "转交接收人不能为空")
    private String targetEmpId;

    /** 转交原因（必填） */
    @NotBlank(message = "转交原因不能为空")
    @Size(max = 500, message = "转交原因长度不能超过500")
    private String reason;
}
