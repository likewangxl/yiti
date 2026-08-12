package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.support.ScreenConfigSchema;
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
 * <p>四类数据源统一出口：宽表引导式（EMP/ORG/CUST_INDEX_RESULT，按 SYS_CONTROL 当前版本 + 周期过滤）、
 * KPI 结果引导式（KPI_RESULT 行式表，每周期取 as_of_date 最新一条）、KPI 细项引导式
 * （PERF_KPI_SCORE T+1 快照，SNAPSHOT 细项明细 / TREND 按细项透视）、自定义 SQL
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

    // KPI_RESULT 允许的周期类型：库中真实口径以 YEARLY 为主（MONTHLY/QUARTERLY 亦保留），
    // 缺 YEARLY 会导致个人屏 KPI 卡/趋势即使 empId 正确也永远空 rows（FIX-2）
    private static final Set<String> KPI_CYCLE_TYPES = Set.of("MONTHLY", "QUARTERLY", "YEARLY");

    private final DataSource readOnlyDataSource;
    private final SqlSafeValidator validator;
    /** 大屏白名单（大写表名）——SqlSafeValidator 自 3f22660c 起不再做白名单拒绝（SQL 探查产品决策），大屏按 D1 决策在引擎侧自查 */
    private final Set<String> whitelistUpper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxRows;

    public ScreenQueryEngine(
            @Qualifier("rptReadOnlyDataSource") DataSource readOnlyDataSource,
            @Value("#{'${rpt.screen.whitelist-tables:EMP_INDEX_RESULT,ORG_INDEX_RESULT,CUST_INDEX_RESULT,"
                    + "KPI_RESULT,PERF_KPI_SCORE,PERF_KPI_SCHEME,PERF_METRIC_DEF,PERF_TARGET_VALUE,"
                    + "PERF_TARGET_PLAN,SYS_CONTROL,EXT_ORG_INFO,"
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
        ScreenDataRespDTO resp = execute(q);
        fillColumnsMeta(resp, ds.getConfigJson());
        return resp;
    }

    /** 配置态试跑（LIMIT = 10），与正式取数一样透出 columnsMeta */
    public ScreenDataRespDTO tryRun(String sourceKind, String configJson, ScreenDataReqDTO req) {
        BuiltQuery q = build(sourceKind, configJson, req, 10, LocalDate.now());
        ScreenDataRespDTO resp = execute(q);
        fillColumnsMeta(resp, configJson);
        return resp;
    }

    /**
     * 按列名把 config_json.fieldMeta 匹配到响应 columnsMeta（spec 2026-07-17 §3.2，全 source_kind 通用）.
     *
     * <p>实现选择（测试固化）：仅包含配置过 fieldMeta 的列，顺序跟随 columns；
     * 数据源未配置 fieldMeta（或无任何列命中）时 columnsMeta 保持 null，旧调用方零影响。
     * 包级可见，供单测直接验证（execute 触库，纯逻辑在此收口）。
     */
    void fillColumnsMeta(ScreenDataRespDTO resp, String configJson) {
        JsonNode fieldMeta = readConfig(configJson).path("fieldMeta");
        if (!fieldMeta.isArray() || fieldMeta.isEmpty() || resp.getColumns() == null) {
            return;
        }
        Map<String, ScreenDataRespDTO.ColumnMeta> byCol = new HashMap<>();
        for (JsonNode n : fieldMeta) {
            String col = n.path("col").asText();
            byCol.put(col, new ScreenDataRespDTO.ColumnMeta(
                    col,
                    n.hasNonNull("alias") ? n.path("alias").asText() : null,
                    n.hasNonNull("role") ? n.path("role").asText() : null,
                    n.hasNonNull("unit") ? n.path("unit").asText() : null,
                    n.hasNonNull("decimals") ? n.path("decimals").asInt() : null));
        }
        List<ScreenDataRespDTO.ColumnMeta> metas = new ArrayList<>();
        for (String column : resp.getColumns()) {
            ScreenDataRespDTO.ColumnMeta m = byCol.get(column);
            if (m != null) {
                metas.add(m);
            }
        }
        if (!metas.isEmpty()) {
            resp.setColumnsMeta(metas);
        }
    }

    /** 构造结果：最终 SQL（已含 LIMIT）+ 有序绑定参数 */
    record BuiltQuery(String sql, List<Object> params) {
    }

    /** 包级可见，供单测注入固定 today */
    BuiltQuery build(String sourceKind, String configJson, ScreenDataReqDTO req, int limit, LocalDate today) {
        JsonNode cfg = readConfig(configJson);
        if (isNamedGroup(cfg, req) && !"WIDE_TABLE".equals(sourceKind)) {
            // 本期只为内置机构宽表建立了可证明的机构范围约束；CUSTOM_SQL 延期，不能留下
            // 看似有标记/外层包装但无法长期审计等价性的旁路。
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        return switch (sourceKind == null ? "" : sourceKind) {
            case "WIDE_TABLE" -> buildWideTableQuery(cfg, req, limit, today);
            case "KPI_RESULT" -> buildKpiQuery(cfg, req, limit, today);
            case "KPI_DETAIL" -> buildKpiDetailQuery(cfg, req, limit, today);
            case "CUSTOM_SQL" -> buildCustomQuery(cfg, req, limit, today);
            default -> throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        };
    }

    private JsonNode readConfig(String configJson) {
        try {
            // schemaVersion 读时兼容集中在 ScreenConfigSchema 唯一入口（旧数据视为版本 1）
            return ScreenConfigSchema.withDefaults(objectMapper.readTree(configJson == null ? "{}" : configJson));
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, e);
        }
    }

    /** 宽表聚合 groupBy 白名单（spec 2026-07-17 §3.3） */
    private static final Set<String> WIDE_AGG_GROUP_BYS = Set.of("NONE", "SUBJECT", "DATE");

    /** 宽表聚合函数白名单 */
    private static final Set<String> WIDE_AGG_FUNCS = Set.of("SUM", "AVG", "MAX", "MIN", "COUNT");

    /** 宽表 filters 操作符白名单 → SQL 运算符（IN 单独处理为多 ? 绑定） */
    private static final Map<String, String> WIDE_FILTER_OPS = Map.of(
            "EQ", "=", "NE", "<>", "GT", ">", "GE", ">=", "LT", "<", "LE", "<=");

    private BuiltQuery buildWideTableQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today) {
        String table = cfg.path("table").asText();
        String[] meta = WIDE_TABLES.get(table);
        if (meta == null) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        if (isNamedGroup(cfg, req) && !"ORG_INDEX_RESULT".equals(table)) {
            // org_code 由内置 WIDE_TABLES 元数据推导，而不是信任 configJson 中可伪造的 subjectCol。
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        JsonNode metrics = cfg.path("metrics");
        if (!metrics.isArray() || metrics.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        if (cfg.path("aggregation").isObject()) {
            // 有 aggregation → 聚合形态（跨主体，不再要求主体上下文参数）；无则明细行为完全不变
            return buildWideTableAggQuery(cfg, meta, table, req, limit, today);
        }
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

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ").append(cols)
                .append(" FROM ").append(table)
                .append(" WHERE 1=1");
        if (isNamedGroup(cfg, req)) {
            appendOrgScopePredicate(sql, params, meta[0], req);
        } else {
            String subjectVal = ctxParam(req, meta[1]);
            sql.append(" AND ").append(meta[0]).append(" = ?");
            params.add(subjectVal);
        }
        sql.append(" AND version = COALESCE((SELECT current_version FROM SYS_CONTROL WHERE scope_dim = '")
                .append(meta[2]).append("' AND is_valid = 1 ORDER BY latest_data_date DESC LIMIT 1), 'V1')");
        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);
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

    /**
     * WIDE_TABLE 聚合形态（spec 2026-07-17 §3.3）：SELECT [维度列,] AGG(val_N) AS `指标名`...
     *
     * <p>groupBy=NONE 跨主体聚合单行 / SUBJECT 按主体列分组（排名对比，按第一个聚合列倒序）/
     * DATE 按 data_date 分组时序。三态均不再强制主体上下文参数；仍按 SYS_CONTROL 最新版本过滤；
     * LATEST=当前版本内最新 data_date 单日（跨主体不能再用 ORDER BY ... LIMIT 1）。
     * filters 的 col 仅允许主体列/data_date/已配置槽位列，op 白名单枚举，值全部 ? 绑定（IN 逗号拆分多 ?），
     * 禁止任何字符串拼接用户值。
     */
    private BuiltQuery buildWideTableAggQuery(JsonNode cfg, String[] meta, String table,
                                              ScreenDataReqDTO req, int limit, LocalDate today) {
        JsonNode agg = cfg.path("aggregation");
        String groupBy = agg.path("groupBy").asText();
        if (!WIDE_AGG_GROUP_BYS.contains(groupBy)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String aggFunc = agg.path("agg").asText();
        if (!WIDE_AGG_FUNCS.contains(aggFunc)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }

        // 聚合列清单 + 已配置槽位列集合（filters 列白名单用）+ 第一个聚合别名（SUBJECT 排名排序用）
        Set<String> slotCols = new java.util.LinkedHashSet<>();
        StringBuilder aggCols = new StringBuilder();
        String firstAlias = null;
        for (JsonNode m : cfg.path("metrics")) {
            int slot = m.path("slot").asInt();
            if (slot < 1 || slot > 400) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            slotCols.add("val_" + slot);
            String alias = m.path("metricName").asText().replaceAll("[`'\"\\\\]", "");
            if (firstAlias == null) {
                firstAlias = alias;
            }
            if (!aggCols.isEmpty()) {
                aggCols.append(", ");
            }
            aggCols.append(aggFunc).append("(val_").append(slot).append(") AS `").append(alias).append("`");
        }

        String versionCond = "version = COALESCE((SELECT current_version FROM SYS_CONTROL WHERE scope_dim = '"
                + meta[2] + "' AND is_valid = 1 ORDER BY latest_data_date DESC LIMIT 1), 'V1')";
        String selectPrefix = switch (groupBy) {
            case "SUBJECT" -> meta[0] + ", ";
            case "DATE" -> "data_date, ";
            default -> "";
        };
        StringBuilder sql = new StringBuilder("SELECT ").append(selectPrefix).append(aggCols)
                .append(" FROM ").append(table)
                .append(" WHERE ").append(versionCond);

        List<Object> params = new ArrayList<>();
        if (isNamedGroup(cfg, req)) {
            appendOrgScopePredicate(sql, params, meta[0], req);
        }
        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);
        if (p.latestOnly()) {
            sql.append(" AND data_date = (SELECT MAX(data_date) FROM ").append(table)
                    .append(" WHERE ").append(versionCond).append(")");
        } else {
            sql.append(" AND data_date BETWEEN ? AND ?");
            params.add(p.from());
            params.add(p.to());
            if (p.eomOnly()) {
                sql.append(" AND data_date = LAST_DAY(data_date)");
            }
        }
        appendWideFilters(sql, params, agg.path("filters"), meta[0], slotCols);

        switch (groupBy) {
            case "SUBJECT" -> sql.append(" GROUP BY ").append(meta[0])
                    .append(" ORDER BY `").append(firstAlias).append("` DESC LIMIT ").append(limit);
            case "DATE" -> sql.append(" GROUP BY data_date ORDER BY data_date LIMIT ").append(limit);
            default -> sql.append(" LIMIT ").append(limit);
        }
        return new BuiltQuery(sql.toString(), params);
    }

    /** filters 逐条追加（col/op 白名单校验，违规 43009；值全部 ? 绑定，IN 逗号拆分多 ?） */
    private void appendWideFilters(StringBuilder sql, List<Object> params, JsonNode filters,
                                   String subjectCol, Set<String> slotCols) {
        if (filters.isMissingNode() || filters.isNull()) {
            return;
        }
        if (!filters.isArray()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        for (JsonNode f : filters) {
            String col = f.path("col").asText();
            // 列白名单：主体列 / data_date / 该数据源已配置槽位列，越界即配置非法
            if (!col.equals(subjectCol) && !"data_date".equals(col) && !slotCols.contains(col)) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            JsonNode value = f.path("value");
            if (value.isMissingNode() || value.isNull()) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            String op = f.path("op").asText();
            if ("IN".equals(op)) {
                List<String> parts = new ArrayList<>();
                for (String part : value.asText().split(",")) {
                    if (!part.trim().isEmpty()) {
                        parts.add(part.trim());
                    }
                }
                if (parts.isEmpty()) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                sql.append(" AND ").append(col).append(" IN (")
                        .append(String.join(", ", java.util.Collections.nCopies(parts.size(), "?")))
                        .append(")");
                params.addAll(parts);
            } else {
                String sqlOp = WIDE_FILTER_OPS.get(op);
                if (sqlOp == null) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                sql.append(" AND ").append(col).append(" ").append(sqlOp).append(" ?");
                // 统一按字符串绑定（MySQL 对数值列自动类型转换），杜绝任何拼接
                params.add(value.asText());
            }
        }
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

    /** KPI_DETAIL 主体类型 → 上下文参数名（EMP→empId、ORG→orgCode），subject_id 按该参数取值 */
    private static final Map<String, String> KPI_DETAIL_SUBJECT_PARAMS = Map.of("EMP", "empId", "ORG", "orgCode");

    /** 完成率算式：target=0 经 NULLIF 置 NULL（前端显示"—"），快照列与 TREND completeRate 透视共用 */
    private static final String KPI_COMPLETE_RATE_EXPR =
            "ROUND(s.actual_value / NULLIF(s.target_value, 0) * 100, 2)";

    /**
     * KPI 细项引导式（PERF_KPI_SCORE T+1 每日快照，spec §3.1）：
     * SNAPSHOT=最新快照日全部细项一行一项（目标/实际/权重/得分/完成率/缺口现算）；
     * TREND=按周期透视，data_date × 每个配置细项一列（MAX(CASE WHEN metric_code=?)，列值 score 或完成率）。
     */
    private BuiltQuery buildKpiDetailQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today) {
        String schemeCode = cfg.path("schemeCode").asText();
        if (schemeCode.isBlank()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String subjectType = cfg.path("subjectType").asText();
        String ctxName = KPI_DETAIL_SUBJECT_PARAMS.get(subjectType);
        if (ctxName == null) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String mode = cfg.path("mode").asText();
        return switch (mode) {
            case "SNAPSHOT" -> buildKpiDetailSnapshot(schemeCode, subjectType, ctxParam(req, ctxName), limit);
            case "TREND" -> buildKpiDetailTrend(cfg, schemeCode, subjectType, ctxParam(req, ctxName), req, limit, today);
            default -> throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        };
    }

    /** SNAPSHOT：主体最新 data_date 当日全部细项，列清单固定（一行一细项） */
    private BuiltQuery buildKpiDetailSnapshot(String schemeCode, String subjectType, String subjectId, int limit) {
        String sql = "SELECT s.metric_code, COALESCE(d.metric_name, s.metric_code) AS `细项名称`, "
                + "s.target_value AS `目标值`, s.actual_value AS `实际值`, s.weight AS `权重`, s.score AS `得分`, "
                + KPI_COMPLETE_RATE_EXPR + " AS `完成率`, "
                + "s.target_value - s.actual_value AS `缺口` "
                + "FROM PERF_KPI_SCORE s "
                // 已删除的指标定义不取名（回落 metric_code），条件放 ON 侧保住 LEFT 语义
                + "LEFT JOIN PERF_METRIC_DEF d ON d.metric_code = s.metric_code AND d.deleted = 0 "
                + "WHERE s.scheme_code = ? AND s.subject_type = ? AND s.subject_id = ? "
                + "AND s.data_date = (SELECT MAX(data_date) FROM PERF_KPI_SCORE"
                + " WHERE scheme_code = ? AND subject_type = ? AND subject_id = ?) "
                + "ORDER BY s.metric_code LIMIT " + limit;
        return new BuiltQuery(sql, new ArrayList<>(List.of(
                schemeCode, subjectType, subjectId, schemeCode, subjectType, subjectId)));
    }

    /** TREND：data_date + 每个配置细项一列（metricCode 全部 ? 绑定，列名=metricName） */
    private BuiltQuery buildKpiDetailTrend(JsonNode cfg, String schemeCode, String subjectType, String subjectId,
                                           ScreenDataReqDTO req, int limit, LocalDate today) {
        JsonNode metrics = cfg.path("metrics");
        if (!metrics.isArray() || metrics.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String valueCol = cfg.path("valueCol").asText("score");
        String expr = switch (valueCol) {
            case "score" -> "s.score";
            case "completeRate" -> KPI_COMPLETE_RATE_EXPR;
            default -> throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        };
        StringBuilder cols = new StringBuilder("s.data_date");
        List<Object> params = new ArrayList<>();
        for (JsonNode m : metrics) {
            String metricCode = m.path("metricCode").asText();
            if (metricCode.isBlank()) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            // 列名用细项名快照（保存时已强制非空），剔除可破坏反引号包裹的字符，兜底回落 metricCode
            String alias = m.path("metricName").asText().replaceAll("[`'\"\\\\]", "");
            if (alias.isBlank()) {
                alias = metricCode;
            }
            cols.append(", MAX(CASE WHEN s.metric_code = ? THEN ").append(expr)
                    .append(" END) AS `").append(alias).append("`");
            params.add(metricCode);
        }
        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);
        StringBuilder sql = new StringBuilder("SELECT ").append(cols)
                .append(" FROM PERF_KPI_SCORE s")
                .append(" WHERE s.scheme_code = ? AND s.subject_type = ? AND s.subject_id = ?");
        params.add(schemeCode);
        params.add(subjectType);
        params.add(subjectId);
        if (p.latestOnly()) {
            sql.append(" GROUP BY s.data_date ORDER BY s.data_date DESC LIMIT 1");
        } else {
            sql.append(" AND s.data_date BETWEEN ? AND ?");
            params.add(p.from());
            params.add(p.to());
            sql.append(" GROUP BY s.data_date ORDER BY s.data_date LIMIT ").append(limit);
        }
        return new BuiltQuery(sql.toString(), params);
    }

    private BuiltQuery buildCustomQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today) {
        String template = cfg.path("sql").asText();
        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);

        Map<String, Object> vals = new HashMap<>();
        vals.put("orgCode", req.getContextParams() == null ? null : req.getContextParams().get("orgCode"));
        vals.put("empId", req.getContextParams() == null ? null : req.getContextParams().get("empId"));
        vals.put("dateFrom", p.from());
        vals.put("dateTo", p.to());

        List<Object> params = new ArrayList<>();
        validateCustomSql(template);
        ScreenSqlTemplate.Parsed parsed = ScreenSqlTemplate.parse(template);
        for (String name : parsed.paramNames()) {
            Object v = vals.get(name);
            if (v == null) {
                // SQL 用到了 orgCode/empId 占位但上下文没传 → 入参校验失败（43010），非 SQL 执行失败（43008）
                throw new RptException(RptErrorCode.SCREEN_CTX_PARAM_MISSING);
            }
            params.add(v);
        }
        String jdbcSql = parsed.jdbcSql();
        String sql = "SELECT * FROM (" + jdbcSql + ") rpt_scr_q LIMIT " + limit;
        return new BuiltQuery(sql, params);
    }

    private boolean isNamedGroup(JsonNode cfg, ScreenDataReqDTO req) {
        return "NAMED_GROUP".equalsIgnoreCase(cfg.path("scopeMode").asText())
                || req.isNamedGroup();
    }

    /** 追加服务端机构范围谓词，机构编码永不拼接到 SQL。 */
    private void appendOrgScopePredicate(StringBuilder sql, List<Object> params,
                                         String subjectCol, ScreenDataReqDTO req) {
        if (!"org_code".equals(subjectCol)) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        List<String> codes = req.getServerOrgCodes();
        if (codes == null || codes.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        sql.append(" AND ").append(subjectCol).append(" IN (")
                .append(String.join(", ", java.util.Collections.nCopies(codes.size(), "?")))
                .append(")");
        params.addAll(codes);
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
