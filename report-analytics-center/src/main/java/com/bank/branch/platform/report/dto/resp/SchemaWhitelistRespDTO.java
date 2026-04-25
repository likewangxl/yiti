package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SQL 探查白名单展示（D.4 GET /sql-probe/schema-whitelist，Task M4.3.3）.
 *
 * <p>由 Service 直接读 {@code rpt.sql.probe.whitelist-tables} 配置返回，
 * 前端在 SQL 编辑器侧栏渲染。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SchemaWhitelistRespDTO {

    /** 白名单表名列表（小写，按字典序排列） */
    private List<String> tables;

    /** 禁用关键字（大写） */
    private List<String> forbiddenKeywords;

    /** 最大返回行数 */
    private Integer maxRows;

    /** 子查询深度上限 */
    private Integer maxSubqueryDepth;

    /** SQL 长度上限 */
    private Integer maxSqlLength;
}
