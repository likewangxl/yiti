package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.governance.api.dto.AuditLogDTO;
import com.bank.branch.platform.governance.api.dto.AuditLogQueryReqDTO;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * SQL探针服务
 * <p>
 * 提供受限的SQL查询执行能力，仅允许SELECT语句，
 * 并通过信号量控制并发数、强制LIMIT和超时保护。
 * 所有执行记录均写入审计日志。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlProbeService {

    private final AuditLogService auditLogService;
    private final DataSource dataSource;

    /** 最大并发执行数 */
    private final Semaphore semaphore = new Semaphore(3);

    /** 默认结果集行数上限 */
    private static final int DEFAULT_LIMIT = 1000;

    /** 查询超时秒数 */
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    /**
     * 执行SQL查询（仅SELECT）
     * <p>
     * 安全控制：
     * 1. 仅允许SELECT语句
     * 2. 自动追加LIMIT（若缺失）
     * 3. 信号量限制并发数
     * 4. 设置查询超时
     * 5. 执行结果写入审计日志
     * </p>
     *
     * @param sql           待执行的SQL语句
     * @param operatorEmpId 操作人工号
     * @param reason        执行原因
     * @return 查询结果列表，每行为一个 Map（列名 → 值）
     */
    public List<Map<String, Object>> executeSql(String sql, String operatorEmpId, String reason) {
        // 1. 校验仅允许SELECT语句
        if (sql == null || !sql.trim().toUpperCase().startsWith("SELECT")) {
            throw new BizException(
                    GovErrorCode.NOT_SELECT_SQL.getCode(),
                    GovErrorCode.NOT_SELECT_SQL.getMessage()
            );
        }

        // 2. 强制追加LIMIT（若SQL中未包含）
        if (!sql.toUpperCase().contains("LIMIT")) {
            sql = sql + " LIMIT " + DEFAULT_LIMIT;
        }

        // 3. 尝试获取信号量许可
        if (!semaphore.tryAcquire()) {
            throw new BizException(
                    GovErrorCode.SQL_CONCURRENCY_EXCEEDED.getCode(),
                    GovErrorCode.SQL_CONCURRENCY_EXCEEDED.getMessage()
            );
        }

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            // 4. 获取连接并执行查询
            conn = dataSource.getConnection();
            stmt = conn.createStatement();
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            rs = stmt.executeQuery(sql);

            // 5. 解析ResultSet为List<Map>
            List<Map<String, Object>> results = parseResultSet(rs);

            // 6. 写入审计日志
            auditLogService.log(AuditLogCmd.builder()
                    .empId(operatorEmpId)
                    .bizType("SQL_PROBE")
                    .bizAction("EXECUTE_SQL")
                    .requestParams(sql)
                    .responseStatus(200)
                    .reason(reason)
                    .build());

            log.info("[SqlProbeService.executeSql] empId={}, resultRows={}, sql={}",
                    operatorEmpId, results.size(), sql);

            return results;
        } catch (SQLException e) {
            log.error("[SqlProbeService.executeSql] SQL执行异常 empId={}, sql={}", operatorEmpId, sql, e);
            throw new BizException(
                    GovErrorCode.SQL_EXECUTION_TIMEOUT.getCode(),
                    GovErrorCode.SQL_EXECUTION_TIMEOUT.getMessage(),
                    e
            );
        } finally {
            // 7. 释放资源
            closeQuietly(rs);
            closeQuietly(stmt);
            closeQuietly(conn);
            semaphore.release();
        }
    }

    /**
     * 查询SQL探针执行历史
     *
     * @param operatorEmpId 操作人工号
     * @param pageNo        当前页码（从1开始）
     * @param pageSize      每页大小
     * @return 分页审计日志结果
     */
    public PageResult<AuditLogDTO> listHistory(String operatorEmpId, int pageNo, int pageSize) {
        log.debug("[SqlProbeService.listHistory] empId={}, pageNo={}, pageSize={}", operatorEmpId, pageNo, pageSize);

        AuditLogQueryReqDTO query = new AuditLogQueryReqDTO();
        query.setEmpId(operatorEmpId);
        query.setBizAction("EXECUTE_SQL");

        return auditLogService.queryLogs(query, pageNo, pageSize);
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /**
     * 将ResultSet解析为List<Map<String, Object>>
     *
     * @param rs 查询结果集
     * @return 结果列表
     * @throws SQLException 解析异常
     */
    private List<Map<String, Object>> parseResultSet(ResultSet rs) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnLabel(i), rs.getObject(i));
            }
            results.add(row);
        }
        return results;
    }

    /**
     * 静默关闭AutoCloseable资源
     *
     * @param closeable 待关闭的资源
     */
    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("[SqlProbeService] 关闭资源异常", e);
            }
        }
    }
}
