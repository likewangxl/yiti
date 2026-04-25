package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * SQL 探查执行结果（D.1 POST /sql-probe/execute，Task M4.2.1）.
 *
 * <p>规模约束（plan L2700-L2714 + 02 §5）：
 * <ul>
 *   <li>{@code rows} 行数 &le; 1000（statement.setMaxRows + LIMIT 双层兜底）</li>
 *   <li>{@code columns} 顺序与 SELECT 投影顺序一致</li>
 *   <li>{@code executionTimeMs} 含连接 + 执行 + 取行总耗时</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlProbeExecuteRespDTO {

    /** sql_probe_history.id（写入审计后生成的 UUID） */
    private String historyId;

    /** 列名（顺序与 SELECT 投影一致） */
    private List<String> columns;

    /** 行数据（每行一个 columnName → value 的 Map，最多 1000 行） */
    private List<Map<String, Object>> rows;

    /** 实际返回行数 */
    private Integer rowCount;

    /** 执行耗时（毫秒，连接 + 执行 + 取行） */
    private Integer executionTimeMs;
}
