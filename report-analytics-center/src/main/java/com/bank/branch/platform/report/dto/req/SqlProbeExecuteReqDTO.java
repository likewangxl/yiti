package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SQL 探查执行入参（D.1 POST /sql-probe/execute，Task M4.2.1）.
 *
 * <p>业务规则（plan L2622-L2629 + 03 §D.1 + 07 §1）：
 * <ul>
 *   <li>{@code sql} 必填，长度由 SqlSafeValidator 校验上限 5000，超限抛 RPT-42008</li>
 *   <li>{@code remark} 必填（即 plan 所谓 reason），用于审计追溯，空抛 RPT-42001</li>
 * </ul>
 */
@Data
public class SqlProbeExecuteReqDTO {

    /** SQL 语句（必填） */
    @NotBlank(message = "sql 不能为空")
    private String sql;

    /** 备注 / 原因（必填，用于审计追溯） */
    @NotBlank(message = "remark 不能为空（审计必填）")
    private String remark;
}
