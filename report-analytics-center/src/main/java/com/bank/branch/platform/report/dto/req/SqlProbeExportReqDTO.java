package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SQL 探查导出入参（D.5 POST /sql-probe/export）。
 *
 * <p>导出条数由调用方显式控制，缺省为 1000，最大不超过 50000；执行接口不复用此字段，
 * 避免把导出策略泄露到普通 SQL 探查请求中。</p>
 */
@Data
public class SqlProbeExportReqDTO {

    /** SQL 语句（必填）。 */
    @NotBlank(message = "sql 不能为空")
    private String sql;

    /** 备注 / 原因（必填，用于审计追溯）。 */
    @NotBlank(message = "remark 不能为空（审计必填）")
    private String remark;

    /** 本次导出最大行数，缺省 1000，允许范围 1..50000。 */
    @Min(value = 1, message = "导出条数最小为 1")
    @Max(value = 50000, message = "导出条数最大为 50000")
    private Integer exportCount = 1000;
}
