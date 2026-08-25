package com.bank.branch.platform.yundun.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 违规记录批量删除请求。 */
@Data
public class ViolationBatchDeleteReq {
    @NotEmpty(message = "请选择要删除的记录")
    @Size(max = 100, message = "单次最多删除 100 条记录")
    private List<Long> ids;
    @NotBlank(message = "删除原因不能为空")
    @Size(max = 500, message = "删除原因不能超过 500 个字符")
    private String reason;
}
