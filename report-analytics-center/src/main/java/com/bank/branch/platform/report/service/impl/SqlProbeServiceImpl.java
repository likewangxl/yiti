package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.report.dto.req.SqlProbeExecuteReqDTO;
import com.bank.branch.platform.report.dto.resp.SchemaWhitelistRespDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeExecuteRespDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeHistoryRespDTO;
import com.bank.branch.platform.report.entity.SqlProbeHistory;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.SqlProbeHistoryMapper;
import com.bank.branch.platform.report.service.SqlProbeService;
import com.bank.branch.platform.report.support.SqlSafeResult;
import com.bank.branch.platform.report.support.SqlSafeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * {@link SqlProbeService} 实现（Task M4.2.1，Green）.
 *
 * <p>核心执行链（plan L2640-L2730）：
 * <ol>
 *   <li>角色校验：仅资财部负责人 FINANCE_LEADER 可访问（缺失 → RPT-40302）</li>
 *   <li>SqlSafeValidator 校验 + LIMIT 标准化</li>
 *   <li>INSERT SQL_PROBE_HISTORY(status=RUNNING) 占位（出失联场景能查到 RUNNING 行）</li>
 *   <li>Semaphore.tryAcquire (并发上限 10)</li>
 *   <li>readOnlyDataSource 取连接 + setReadOnly + setQueryTimeout 30s + setMaxRows 1000</li>
 *   <li>executeQuery + 行 Map 装配（最多 1000 行）</li>
 *   <li>UPDATE 终态 (SUCCESS / TIMEOUT / FAILED) + AuditApi.log 同步</li>
 * </ol>
 *
 * <p>所有失败路径（角色拒绝除外）必须落审计 + 更新历史，确保审计闭环。
 */
@Slf4j
@Service
public class SqlProbeServiceImpl implements SqlProbeService {

    // getCurrentRoleCodes() 返回 roleCode（如 FINANCE_LEADER），不是 roleId。
    // SQL 探查对「资财部负责人(FINANCE_LEADER) / 资财部经办人(BACK_FINANCE)」开放，其他角色一律拒绝。
    private static final Set<String> ROLE_SQL_PROBE_ALLOWED = Set.of("FINANCE_LEADER", "BACK_FINANCE");

    private static final int CONCURRENT_LIMIT = 10;

    private static final int QUERY_TIMEOUT_SEC = 30;

    private static final int MAX_ROWS = 1000;

    private final SqlSafeValidator validator;

    private final SqlProbeHistoryMapper historyMapper;

    private final CurrentUserApi currentUserApi;

    private final AuditApi auditApi;

    private final DataSource readOnlyDataSource;

    /** 白名单展示配置（D.4 用），由 RptSqlSafeConfig 提供同款配置项. */
    private final List<String> whitelistTablesView;

    private final List<String> forbiddenKeywordsView;

    private final int maxRowsView;

    private final int maxSqlLengthView;

    private final int maxSubqueryDepthView;

    private final Semaphore semaphore = new Semaphore(CONCURRENT_LIMIT);

    public SqlProbeServiceImpl(
            SqlSafeValidator validator,
            SqlProbeHistoryMapper historyMapper,
            CurrentUserApi currentUserApi,
            AuditApi auditApi,
            @Qualifier("rptReadOnlyDataSource") DataSource readOnlyDataSource,
            @Value("#{'${rpt.sql.probe.whitelist-tables:CUST_MASTER,CUST_LEAD,CUST_TAG,touch_record,EMP_INDEX_RESULT,ORG_INDEX_RESULT,CUST_INDEX_RESULT,KPI_RESULT,metric_def,SYS_DICT,sys_dict_item,EXT_ORG_INFO,EXT_USER_ORG}'.split(',')}") List<String> whitelistTablesView,
            @Value("#{'${rpt.sql.probe.forbidden-keywords:DROP,DELETE,UPDATE,INSERT,TRUNCATE,ALTER,CREATE,RENAME,REPLACE,GRANT,REVOKE,LOCK,UNLOCK,SET,CALL,EXEC,EXECUTE,LOAD,SHUTDOWN,USE,DESCRIBE,EXPLAIN,SHOW,COMMIT,ROLLBACK,SAVEPOINT,DECLARE,HANDLER,SIGNAL,RESIGNAL}'.split(',')}") List<String> forbiddenKeywordsView,
            @Value("${rpt.sql.probe.max-rows:1000}") int maxRowsView,
            @Value("${rpt.sql.probe.max-sql-length:5000}") int maxSqlLengthView,
            @Value("${rpt.sql.probe.max-subquery-depth:3}") int maxSubqueryDepthView) {
        this.validator = validator;
        this.historyMapper = historyMapper;
        this.currentUserApi = currentUserApi;
        this.auditApi = auditApi;
        this.readOnlyDataSource = readOnlyDataSource;
        this.whitelistTablesView = whitelistTablesView;
        this.forbiddenKeywordsView = forbiddenKeywordsView;
        this.maxRowsView = maxRowsView;
        this.maxSqlLengthView = maxSqlLengthView;
        this.maxSubqueryDepthView = maxSubqueryDepthView;
    }

    @Override
    public SqlProbeExecuteRespDTO execute(SqlProbeExecuteReqDTO req) {
        // 1) 角色校验
        Set<String> roles = currentUserApi.getCurrentRoleCodes();
        if (roles == null || roles.stream().noneMatch(ROLE_SQL_PROBE_ALLOWED::contains)) {
            log.warn("[SqlProbeService] 拒绝：仅资财部负责人/资财部经办人可访问 SQL 探查 roles={}", roles);
            throw new RptException(RptErrorCode.SQL_PROBE_NO_ACCESS);
        }
        String empId = currentUserApi.getCurrentEmpId();

        // 2) 校验 + 标准化 SQL（校验失败直接抛，不入库 / 不审计）
        SqlSafeResult safe = validator.validateAndNormalize(req.getSql());
        String normalizedSql = safe.getNormalizedSql();

        // 3) 占位 RUNNING 历史
        String historyId = UUID.randomUUID().toString().replace("-", "");
        SqlProbeHistory hist = new SqlProbeHistory();
        hist.setId(historyId);
        hist.setEmpId(empId);
        hist.setSqlText(normalizedSql);
        hist.setRemark(req.getRemark());
        hist.setStatus("RUNNING");
        hist.setCreatedTime(LocalDateTime.now());
        historyMapper.insert(hist);

        // 4) 并发控制 + 执行
        if (!semaphore.tryAcquire()) {
            log.warn("[SqlProbeService] 并发数超限（{}），拒绝 historyId={}", CONCURRENT_LIMIT, historyId);
            updateTerminal(historyId, "FAILED", null, null, "并发数超限");
            safelyAudit(empId, historyId, "FAILED", req.getRemark(), normalizedSql, "并发数超限");
            throw new RptException(RptErrorCode.SQL_CONCURRENT_LIMIT);
        }
        long startMs = System.currentTimeMillis();
        try (Connection conn = readOnlyDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(normalizedSql)) {
            // setReadOnly 双层防御（与 DataSource bean 内的 setDefaultReadOnly 互为兜底）
            try {
                conn.setReadOnly(true);
            } catch (SQLException ignore) {
                // 部分 mock / 异常实现可能不支持，忽略
            }
            stmt.setQueryTimeout(QUERY_TIMEOUT_SEC);
            stmt.setMaxRows(MAX_ROWS);

            try (ResultSet rs = stmt.executeQuery()) {
                List<String> columns = readColumns(rs);
                List<Map<String, Object>> rows = readRows(rs, columns);
                int elapsedMs = (int) (System.currentTimeMillis() - startMs);

                updateTerminal(historyId, "SUCCESS", rows.size(), elapsedMs, null);
                safelyAudit(empId, historyId, "SUCCESS", req.getRemark(), normalizedSql, null);

                return SqlProbeExecuteRespDTO.builder()
                        .historyId(historyId)
                        .columns(columns)
                        .rows(rows)
                        .rowCount(rows.size())
                        .executionTimeMs(elapsedMs)
                        .build();
            }
        } catch (SQLTimeoutException ex) {
            log.warn("[SqlProbeService] 超时 historyId={} cause={}", historyId, ex.getMessage());
            updateTerminal(historyId, "TIMEOUT", null,
                    (int) (System.currentTimeMillis() - startMs), ex.getMessage());
            safelyAudit(empId, historyId, "FAILED", req.getRemark(), normalizedSql, ex.getMessage());
            throw new RptException(RptErrorCode.SQL_EXECUTION_TIMEOUT);
        } catch (SQLException ex) {
            log.warn("[SqlProbeService] 执行失败 historyId={} cause={}", historyId, ex.getMessage());
            updateTerminal(historyId, "FAILED", null,
                    (int) (System.currentTimeMillis() - startMs), ex.getMessage());
            safelyAudit(empId, historyId, "FAILED", req.getRemark(), normalizedSql, ex.getMessage());
            throw new com.bank.branch.platform.common.web.exception.BizException(
                    RptErrorCode.SQL_EXECUTION_FAILED.getCode(),
                    "SQL 执行失败：" + ex.getMessage());
        } finally {
            semaphore.release();
        }
    }

    @Override
    public PageResult<SqlProbeHistoryRespDTO> queryHistory(PageRequest page) {
        String empId = currentUserApi.getCurrentEmpId();
        long total = historyMapper.countByEmpId(empId);
        List<SqlProbeHistory> records = historyMapper.selectByEmpIdPaged(
                empId, page.getOffset(), page.getPageSize());
        List<SqlProbeHistoryRespDTO> dtos = records.stream().map(this::toDto).toList();
        // 列表 sqlText 截断 200 字
        dtos.forEach(d -> {
            if (d.getSqlText() != null && d.getSqlText().length() > 200) {
                d.setSqlText(d.getSqlText().substring(0, 200) + "...");
            }
        });
        return PageResult.of(page.getPageNo(), page.getPageSize(), total, dtos);
    }

    @Override
    public SqlProbeHistoryRespDTO getHistoryDetail(String id) {
        SqlProbeHistory hist = historyMapper.selectById(id);
        if (hist == null) {
            throw new RptException(RptErrorCode.SAVED_QUERY_NOT_FOUND);
        }
        // 仅本人可查（empId 不一致 → RPT-40302 无权访问）
        String currentEmp = currentUserApi.getCurrentEmpId();
        if (!StringUtils.hasText(hist.getEmpId()) || !hist.getEmpId().equals(currentEmp)) {
            throw new RptException(RptErrorCode.SQL_PROBE_NO_ACCESS);
        }
        return toDto(hist);
    }

    @Override
    public SchemaWhitelistRespDTO getSchemaWhitelist() {
        // 白名单全部转小写返回，方便前端高亮匹配
        List<String> tables = whitelistTablesView.stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .sorted()
                .toList();
        List<String> kw = forbiddenKeywordsView.stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .sorted()
                .toList();
        return SchemaWhitelistRespDTO.builder()
                .tables(tables)
                .forbiddenKeywords(kw)
                .maxRows(maxRowsView)
                .maxSubqueryDepth(maxSubqueryDepthView)
                .maxSqlLength(maxSqlLengthView)
                .build();
    }

    /* =====================================================================
     * 辅助方法
     * ===================================================================== */

    private List<String> readColumns(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        List<String> cols = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            cols.add(md.getColumnLabel(i));
        }
        return cols;
    }

    private List<Map<String, Object>> readRows(ResultSet rs, List<String> columns) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        int rowsRead = 0;
        while (rs.next() && rowsRead < MAX_ROWS) {
            Map<String, Object> row = new HashMap<>(columns.size() * 2);
            for (int i = 0; i < columns.size(); i++) {
                row.put(columns.get(i), rs.getObject(i + 1));
            }
            out.add(row);
            rowsRead++;
        }
        return out;
    }

    private void updateTerminal(String historyId, String status, Integer rowCount,
                                Integer executionTimeMs, String errorMsg) {
        try {
            SqlProbeHistory upd = new SqlProbeHistory();
            upd.setId(historyId);
            upd.setStatus(status);
            upd.setRowCount(rowCount);
            upd.setExecutionTimeMs(executionTimeMs);
            upd.setErrorMsg(errorMsg);
            historyMapper.updateTerminalStatus(upd);
        } catch (RuntimeException e) {
            // 不让审计/历史更新失败掩盖原始错误
            log.error("[SqlProbeService] 历史更新失败 historyId={} status={} cause={}",
                    historyId, status, e.getMessage());
        }
    }

    /** 安全调用 AuditApi.log（任何异常仅 warn 不中断主流程，符合"审计失败不阻塞业务"原则）. */
    private void safelyAudit(String empId, String historyId, String resultStatus,
                              String reason, String sqlText, String errorMsg) {
        try {
            AuditLogCmd cmd = AuditLogCmd.builder()
                    .empId(empId)
                    .bizType("REPORT")
                    .bizAction("EXECUTE_SQL")
                    .resourceUrl("/api/reports/sql-probe/execute")
                    .requestMethod("POST")
                    .requestParams("historyId=" + historyId
                            + ", sql=" + truncateForAudit(sqlText))
                    .responseStatus("SUCCESS".equals(resultStatus) ? 200 : 500)
                    .errorMsg(errorMsg)
                    .reason(reason)
                    .build();
            auditApi.log(cmd);
        } catch (RuntimeException e) {
            log.warn("[SqlProbeService] AuditApi.log 失败 historyId={} cause={}",
                    historyId, e.getMessage());
        }
    }

    private String truncateForAudit(String sql) {
        if (sql == null) {
            return null;
        }
        return sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
    }

    private SqlProbeHistoryRespDTO toDto(SqlProbeHistory e) {
        return SqlProbeHistoryRespDTO.builder()
                .id(e.getId())
                .empId(e.getEmpId())
                .sqlText(e.getSqlText())
                .remark(e.getRemark())
                .rowCount(e.getRowCount())
                .executionTimeMs(e.getExecutionTimeMs())
                .status(e.getStatus())
                .errorMsg(e.getErrorMsg())
                .createdTime(e.getCreatedTime())
                .build();
    }
}
