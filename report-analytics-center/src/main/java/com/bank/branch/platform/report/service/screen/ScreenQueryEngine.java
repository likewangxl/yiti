package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.support.ScreenPeriodResolver;
import com.bank.branch.platform.report.support.ScreenPeriodResolver.ResolvedPeriod;
import com.bank.branch.platform.report.support.ScreenSqlTemplate;
import com.bank.branch.platform.report.support.SqlSafeValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 大屏查询执行引擎（核心）.
 *
 * <p>三类数据源统一出口：宽表引导式（EMP/ORG/CUST_INDEX_RESULT，按 SYS_CONTROL 当前版本 + 周期过滤）、
 * KPI 结果引导式（KPI_RESULT 行式表，每周期取 as_of_date 最新一条）、自定义 SQL
 * （#{param} 占位 → PreparedStatement 绑定，JSqlParser 白名单校验，外层强制 LIMIT）。
 * 全部跑在 rptReadOnlyDataSource 只读数据源上，query timeout 5s。
 *
 * <p>SqlSafeValidator 在此内部 new（不注册 Bean），避免与 SQL 探查的同类型 Bean 注入冲突。
 */
@Slf4j
@Service
public class ScreenQueryEngine {

    /** 查询超时（秒），大屏接口要求快速失败 */
    private static final int QUERY_TIMEOUT_SEC = 5;

    /** 宽表引导式允许的表 → [主体列, 上下文参数名, SYS_CONTROL.scope_dim] */
    private static final Map<String, String[]> WIDE_TABLES = Map.of(
            "EMP_INDEX_RESULT", new String[]{"emp_id", "empId", "EMP"},
            "ORG_INDEX_RESULT", new String[]{"org_code", "orgCode", "ORG"},
            "CUST_INDEX_RESULT", new String[]{"cust_no", "custNo", "CUST"});

    private static final Set<String> KPI_CYCLE_TYPES = Set.of("MONTHLY", "QUARTERLY");

    private final DataSource readOnlyDataSource;
    private final SqlSafeValidator validator;
    /** 大屏白名单（大写表名）——SqlSafeValidator 自 3f22660c 起不再做白名单拒绝（SQL 探查产品决策），大屏按 D1 决策在引擎侧自查 */
    private final Set<String> whitelistUpper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxRows;

    public ScreenQueryEngine(
            @Qualifier("rptReadOnlyDataSource") DataSource readOnlyDataSource,
            @Value("#{'${rpt.screen.whitelist-tables:EMP_INDEX_RESULT,ORG_INDEX_RESULT,CUST_INDEX_RESULT,"
                    + "KPI_RESULT,PERF_METRIC_DEF,PERF_TARGET_VALUE,PERF_TARGET_PLAN,SYS_CONTROL,EXT_ORG_INFO,"
                    + "ADDRBOOK_EMPLOYEE,TOUCH_TASK,TOUCH_LOG,ACT_RU_TASK,ACT_HI_PROCINST,BIZ_PROCESS_MAP}'"
                    + ".split(',')}") List<String> whitelistTables,
            @Value("#{'${rpt.screen.forbidden-keywords:DROP,DELETE,UPDATE,INSERT,TRUNCATE,ALTER,CREATE,RENAME,"
                    + "REPLACE,GRANT,REVOKE,LOCK,UNLOCK,CALL,EXEC,EXECUTE,LOAD,SHUTDOWN,USE,DESCRIBE,EXPLAIN,SHOW,"
                    + "COMMIT,ROLLBACK,SAVEPOINT,DECLARE,HANDLER,SIGNAL,RESIGNAL}'.split(',')}")
                    List<String> forbiddenKeywords,
            @Value("${rpt.screen.max-rows:1000}") int maxRows) {
        this.readOnlyDataSource = readOnlyDataSource;
        this.maxRows = maxRows;
        this.validator = new SqlSafeValidator(whitelistTables, forbiddenKeywords, maxRows, 8000, 3);
        this.whitelistUpper = whitelistTables.stream()
                .map(t -> t.trim().toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 校验自定义 SQL 模板（保存/试跑/每次执行都调用），失败抛 RPT-43002 */
    public void validateCustomSql(String sqlTemplate) {
        com.bank.branch.platform.report.support.SqlSafeResult result;
        try {
            // SqlSafeValidator/ScreenSqlTemplate 抛的一切校验错（含 RPT-42xxx 语法/关键字/深度校验）
            // 统一收敛为大屏语义 43002（保留原因链）；SqlSafeValidator 自 3f22660c 起只提取表名不做白名单拒绝
            result = validator.validateAndNormalize(ScreenSqlTemplate.toValidatable(sqlTemplate));
        } catch (BizException e) {
            throw new RptException(RptErrorCode.SCREEN_DS_SQL_INVALID, e);
        }
        // 白名单拒绝在引擎侧自查（SqlSafeResult#getReferencedTables() 已统一大写，见源码 §referencedTables 字段）
        for (String table : result.getReferencedTables()) {
            if (!whitelistUpper.contains(table)) {
                log.warn("[ScreenQueryEngine] 自定义 SQL 命中白名单外表 {}", table);
                throw new RptException(RptErrorCode.SCREEN_DS_SQL_INVALID);
            }
        }
    }

    /** 正式取数（LIMIT = maxRows） */
    public ScreenDataRespDTO query(RptScreenDatasource ds, ScreenDataReqDTO req) {
        BuiltQuery q = build(ds.getSourceKind(), ds.getConfigJson(), req, maxRows, LocalDate.now());
        return execute(q);
    }

    /** 配置态试跑（LIMIT = 10） */
    public ScreenDataRespDTO tryRun(String sourceKind, String configJson, ScreenDataReqDTO req) {
        BuiltQuery q = build(sourceKind, configJson, req, 10, LocalDate.now());
        return execute(q);
    }

    /** 构造结果：最终 SQL（已含 LIMIT）+ 有序绑定参数 */
    record BuiltQuery(String sql, List<Object> params) {
    }

    /** 包级可见，供单测注入固定 today */
    BuiltQuery build(String sourceKind, String configJson, ScreenDataReqDTO req, int limit, LocalDate today) {
        JsonNode cfg = readConfig(configJson);
        return switch (sourceKind == null ? "" : sourceKind) {
            case "WIDE_TABLE" -> buildWideTableQuery(cfg, req, limit, today);
            case "KPI_RESULT" -> buildKpiQuery(cfg, req, limit, today);
            case "CUSTOM_SQL" -> buildCustomQuery(cfg, req, limit, today);
            default -> throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        };
    }

    private JsonNode readConfig(String configJson) {
        try {
            return objectMapper.readTree(configJson == null ? "{}" : configJson);
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, e);
        }
    }

    private BuiltQuery buildWideTableQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today) {
        String table = cfg.path("table").asText();
        String[] meta = WIDE_TABLES.get(table);
        if (meta == null) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        JsonNode metrics = cfg.path("metrics");
        if (!metrics.isArray() || metrics.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String subjectVal = ctxParam(req, meta[1]);

        StringBuilder cols = new StringBuilder("data_date");
        for (JsonNode m : metrics) {
            int slot = m.path("slot").asInt();
            if (slot < 1 || slot > 400) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            // 别名用指标名（剔除可破坏反引号包裹的字符），前端直接以列名展示
            String alias = m.path("metricName").asText().replaceAll("[`'\"\\\\]", "");
            cols.append(", val_").append(slot).append(" AS `").append(alias).append("`");
        }

        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);
        StringBuilder sql = new StringBuilder("SELECT ").append(cols)
                .append(" FROM ").append(table)
                .append(" WHERE ").append(meta[0]).append(" = ?")
                .append(" AND version = COALESCE((SELECT current_version FROM SYS_CONTROL WHERE scope_dim = '")
                .append(meta[2]).append("' AND is_valid = 1 ORDER BY latest_data_date DESC LIMIT 1), 'V1')");
        List<Object> params = new ArrayList<>();
        params.add(subjectVal);
        if (p.latestOnly()) {
            sql.append(" ORDER BY data_date DESC LIMIT 1");
        } else {
            sql.append(" AND data_date BETWEEN ? AND ?");
            params.add(p.from());
            params.add(p.to());
            if (p.eomOnly()) {
                sql.append(" AND data_date = LAST_DAY(data_date)");
            }
            sql.append(" ORDER BY data_date LIMIT ").append(limit);
        }
        return new BuiltQuery(sql.toString(), params);
    }

    private BuiltQuery buildKpiQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today) {
        String cycleType = cfg.path("cycleType").asText("MONTHLY");
        if (!KPI_CYCLE_TYPES.contains(cycleType)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String empId = ctxParam(req, "empId");
        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);

        // 每个周期日取 as_of_date 最新一次计算结果
        StringBuilder sql = new StringBuilder(
                "SELECT cycle_date AS data_date, kpi_total_score AS `KPI总分` FROM ("
                        + "SELECT cycle_date, kpi_total_score, "
                        + "ROW_NUMBER() OVER (PARTITION BY cycle_date ORDER BY as_of_date DESC) rn "
                        + "FROM KPI_RESULT WHERE emp_id = ? AND cycle_type = ?) t WHERE rn = 1");
        List<Object> params = new ArrayList<>(List.of(empId, cycleType));
        if (p.latestOnly()) {
            sql.append(" ORDER BY cycle_date DESC LIMIT 1");
        } else {
            sql.append(" AND cycle_date BETWEEN ? AND ?");
            params.add(p.from());
            params.add(p.to());
            sql.append(" ORDER BY cycle_date LIMIT ").append(limit);
        }
        return new BuiltQuery(sql.toString(), params);
    }

    private BuiltQuery buildCustomQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today) {
        String template = cfg.path("sql").asText();
        validateCustomSql(template);
        ScreenSqlTemplate.Parsed parsed = ScreenSqlTemplate.parse(template);
        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);

        Map<String, Object> vals = new HashMap<>();
        vals.put("orgCode", req.getContextParams() == null ? null : req.getContextParams().get("orgCode"));
        vals.put("empId", req.getContextParams() == null ? null : req.getContextParams().get("empId"));
        vals.put("dateFrom", p.from());
        vals.put("dateTo", p.to());

        List<Object> params = new ArrayList<>();
        for (String name : parsed.paramNames()) {
            Object v = vals.get(name);
            if (v == null) {
                // SQL 用到了 orgCode/empId 占位但上下文没传 → 入参校验失败（43010），非 SQL 执行失败（43008）
                throw new RptException(RptErrorCode.SCREEN_CTX_PARAM_MISSING);
            }
            params.add(v);
        }
        String sql = "SELECT * FROM (" + parsed.jdbcSql() + ") rpt_scr_q LIMIT " + limit;
        return new BuiltQuery(sql, params);
    }

    private String ctxParam(ScreenDataReqDTO req, String name) {
        String v = req.getContextParams() == null ? null : req.getContextParams().get(name);
        if (v == null || v.isBlank()) {
            // 必填上下文参数（empId/orgCode/custNo）缺失属入参校验失败（43010），前端据此渲染引导态而非当作 SQL 执行失败（43008）
            throw new RptException(RptErrorCode.SCREEN_CTX_PARAM_MISSING);
        }
        return v;
    }

    private ScreenDataRespDTO execute(BuiltQuery q) {
        try (Connection conn = readOnlyDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(q.sql())) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SEC);
            for (int i = 0; i < q.params().size(); i++) {
                Object v = q.params().get(i);
                if (v instanceof LocalDate d) {
                    stmt.setObject(i + 1, java.sql.Date.valueOf(d));
                } else {
                    stmt.setObject(i + 1, v);
                }
            }
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    columns.add(md.getColumnLabel(i));
                }
                List<List<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(columns.size());
                    for (int i = 1; i <= columns.size(); i++) {
                        row.add(normalize(rs.getObject(i)));
                    }
                    rows.add(row);
                }
                return new ScreenDataRespDTO(columns, rows);
            }
        } catch (SQLException e) {
            log.warn("[ScreenQueryEngine] 取数失败 sql={} cause={}", q.sql(), e.getMessage());
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, e);
        }
    }

    /** JDBC 值 → JSON 友好值（日期/时间戳转字符串，数值保持原样） */
    private Object normalize(Object v) {
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.toString();
        }
        return v;
    }
}
