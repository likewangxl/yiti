package com.bank.branch.platform.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务驳回请求 DTO
 * <p>
 * 驳回原因为必填项。
 * 对齐设计文档 B.3: opinion
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectReqDTO {

    /** 驳回原因（必填） */
    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 500, message = "驳回原因长度不能超过500")
    private String opinion;
}
