package com.bank.branch.platform.bizapp.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 转交申请请求 DTO。
 */
@Data
public class TransferReq {

    /** 新承接人工号（必填） */
    @NotBlank(message = "newAssignedEmpId不能为空")
    private String newAssignedEmpId;
}
