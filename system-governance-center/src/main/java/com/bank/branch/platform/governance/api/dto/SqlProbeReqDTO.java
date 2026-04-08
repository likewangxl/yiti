package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SQL探查执行请求DTO
 */
@Data
public class SqlProbeReqDTO {

    /**
     * SQL语句（仅SELECT）
     */
    @NotBlank(message = "SQL语句不能为空")
    @Size(max = 5000, message = "SQL语句长度不能超过5000")
    private String sql;

    /**
     * 执行原因（选填）
     */
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}