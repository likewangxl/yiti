package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScreenQueryEngine SQL 构造纯逻辑单测（不触库，DataSource 传 null）.
 */
class ScreenQueryEngineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 12);

    private final ScreenQueryEngine engine = new ScreenQueryEngine(
            null,
            mock(OrgApi.class),
            List.of("EMP_INDEX_RESULT", "ORG_INDEX_RESULT", "CUST_INDEX_RESULT", "KPI_RESULT",
                    "SYS_CONTROL", "EXT_ORG_INFO", "ACT_RU_TASK"),
            List.of("DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE", "ALTER", "CREATE", "GRANT"),
            1000);

    private ScreenDataReqDTO req(String period, Map<String, String> ctx) {
        ScreenDataReqDTO r = new ScreenDataReqDTO();
        r.setPeriod(period);
        r.setContextParams(ctx);
        return r;
    }

    // ===== WIDE_TABLE =====

    @Test
    void buildWide_latest_ordersByDateDescLimit1_withAliasAndVersion() {
        String cfg = "{\"table\":\"EMP_INDEX_RESULT\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"存款余额\",\"slot\":3}]}";
        var q = engine.build("WIDE_TABLE", cfg, req("LATEST", Map.of("empId", "E001")), 1000, TODAY);
        assertThat(q.sql()).contains("FROM EMP_INDEX_RESULT");
        assertThat(q.sql()).contains("val_3 AS `存款余额`");
        assertThat(q.sql()).contains("emp_id = ?");
        assertThat(q.sql()).contains("COALESCE((SELECT current_version FROM SYS_CONTROL WHERE scope_dim = 'EMP'");
        assertThat(q.sql()).endsWith("ORDER BY data_date DESC LIMIT 1");
        assertThat(q.params()).containsExactly("E001");
    }

    @Test
    void buildWide_last6mEom_addsEomFilterAndRange() {
        String cfg = "{\"table\":\"ORG_INDEX_RESULT\",\"subjectCol\":\"org_code\",\"subjectParam\":\"orgCode\","
                + "\"metrics\":[{\"metricCode\":\"M_0002\",\"metricName\":\"贷款余额\",\"slot\":7}]}";
        var q = engine.build("WIDE_TABLE", cfg, req("LAST_6M_EOM", Map.of("orgCode", "610100")), 1000, TODAY);
        assertThat(q.sql()).contains("data_date BETWEEN ? AND ?");
        assertThat(q.sql()).contains("data_date = LAST_DAY(data_date)");
        assertThat(q.sql()).endsWith("ORDER BY data_date LIMIT 1000");
        assertThat(q.params()).containsExactly("610100", LocalDate.of(2026, 1, 1), TODAY);
    }

    @Test
    void buildWide_tableNotAllowed_throws43009() {
        String cfg = "{\"table\":\"SECRET_T\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"x\",\"slot\":1}]}";
        assertThatThrownBy(() -> engine.build("WIDE_TABLE", cfg, req("LATEST", Map.of("empId", "E001")), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void buildWide_missingContextParam_throws43010() {
        // 上下文参数缺失属"入参校验失败"，应返回 43010（缺少必填上下文参数），与真实执行失败 43008 语义分离
        String cfg = "{\"table\":\"EMP_INDEX_RESULT\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"x\",\"slot\":1}]}";
        assertThatThrownBy(() -> engine.build("WIDE_TABLE", cfg, req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43010");
    }

    // ===== KPI_RESULT =====

    @Test
    void buildKpi_latest_rowNumberDedupByAsOfDate() {
        var q = engine.build("KPI_RESULT", "{\"cycleType\":\"MONTHLY\"}",
                req("LATEST", Map.of("empId", "E001")), 1000, TODAY);
        assertThat(q.sql()).contains("ROW_NUMBER() OVER (PARTITION BY cycle_date ORDER BY as_of_date DESC)");
        assertThat(q.sql()).contains("FROM KPI_RESULT WHERE emp_id = ? AND cycle_type = ?");
        assertThat(q.sql()).endsWith("ORDER BY cycle_date DESC LIMIT 1");
        assertThat(q.params()).containsExactly("E001", "MONTHLY");
    }

    @Test
    void buildKpi_yearly_supported() {
        // FIX-2: KPI_RESULT 库中真实口径为 YEARLY（MONTHLY 0 行），引擎必须支持 YEARLY，
        // 否则个人屏 KPI 卡/趋势即使 empId 正确也永远空 rows
        var q = engine.build("KPI_RESULT", "{\"cycleType\":\"YEARLY\"}",
                req("LATEST", Map.of("empId", "E001")), 1000, TODAY);
        assertThat(q.sql()).contains("FROM KPI_RESULT WHERE emp_id = ? AND cycle_type = ?");
        assertThat(q.sql()).endsWith("ORDER BY cycle_date DESC LIMIT 1");
        assertThat(q.params()).containsExactly("E001", "YEARLY");
    }

    @Test
    void buildKpi_illegalCycleType_throws43009() {
        assertThatThrownBy(() -> engine.build("KPI_RESULT", "{\"cycleType\":\"HOURLY\"}",
                req("LATEST", Map.of("empId", "E001")), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    // ===== CUSTOM_SQL =====

    @Test
    void buildCustom_placeholdersBoundInOrder_andWrappedWithLimit() {
        String cfg = "{\"sql\":\"SELECT org_code, COUNT(*) AS cnt FROM ACT_RU_TASK"
                + " WHERE tenant_id_ = #{orgCode} AND create_time_ BETWEEN #{dateFrom} AND #{dateTo}"
                + " GROUP BY org_code\",\"dateCol\":null}";
        var q = engine.build("CUSTOM_SQL", cfg,
                req("LAST_10D", Map.of("orgCode", "610100")), 10, TODAY);
        assertThat(q.sql()).startsWith("SELECT * FROM (");
        assertThat(q.sql()).endsWith(") rpt_scr_q LIMIT 10");
        assertThat(q.sql()).doesNotContain("#{");
        assertThat(q.params()).containsExactly("610100", LocalDate.of(2026, 7, 3), TODAY);
    }

    @Test
    void buildCustom_tableNotWhitelisted_throws43002() {
        String cfg = "{\"sql\":\"SELECT * FROM PT_USER\",\"dateCol\":null}";
        assertThatThrownBy(() -> engine.build("CUSTOM_SQL", cfg, req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
    }

    @Test
    void buildCustom_missingContextParam_throws43010() {
        // 自定义 SQL 用了 #{empId}/#{orgCode} 占位但上下文未传，同属入参校验失败 → 43010
        String cfg = "{\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK WHERE assignee_ = #{empId}\",\"dateCol\":null}";
        assertThatThrownBy(() -> engine.build("CUSTOM_SQL", cfg, req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43010");
    }

    // ===== KPI_DETAIL =====

    /** SNAPSHOT 基准配置（EMP 主体） */
    private static final String KD_SNAPSHOT_EMP_CFG =
            "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"EMP\",\"mode\":\"SNAPSHOT\"}";

    /** TREND 基准配置（EMP 主体，两个细项） */
    private static final String KD_TREND_EMP_CFG =
            "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"EMP\",\"mode\":\"TREND\","
                    + "\"metrics\":[{\"metricCode\":\"M_D1\",\"metricName\":\"存款细项\"},"
                    + "{\"metricCode\":\"M_D2\",\"metricName\":\"贷款细项\"}]}";

    @Test
    void buildKpiDetail_snapshot_fixedColumnsLatestDateSubqueryAndParamOrder() {
        var q = engine.build("KPI_DETAIL", KD_SNAPSHOT_EMP_CFG,
                req("LATEST", Map.of("empId", "E001")), 1000, TODAY);
        // 固定列清单：metric_code + 细项名称（LEFT JOIN 取名回落 metric_code）+ 目标/实际/权重/得分/完成率/缺口
        assertThat(q.sql()).contains("FROM PERF_KPI_SCORE s");
        assertThat(q.sql()).contains("LEFT JOIN PERF_METRIC_DEF d ON d.metric_code = s.metric_code");
        assertThat(q.sql()).contains("COALESCE(d.metric_name, s.metric_code) AS `细项名称`");
        assertThat(q.sql()).contains("s.target_value AS `目标值`");
        assertThat(q.sql()).contains("s.actual_value AS `实际值`");
        assertThat(q.sql()).contains("s.weight AS `权重`");
        assertThat(q.sql()).contains("s.score AS `得分`");
        // 完成率 target=0 → NULL；缺口允许负数
        assertThat(q.sql()).contains("ROUND(s.actual_value / NULLIF(s.target_value, 0) * 100, 2) AS `完成率`");
        assertThat(q.sql()).contains("s.target_value - s.actual_value AS `缺口`");
        // 最新快照日子查询
        assertThat(q.sql()).contains("s.data_date = (SELECT MAX(data_date) FROM PERF_KPI_SCORE"
                + " WHERE scheme_code = ? AND subject_type = ? AND subject_id = ?)");
        // 参数顺序：外层 3 个 + 子查询 3 个
        assertThat(q.params()).containsExactly(
                "KPI_2026_STD", "EMP", "E001", "KPI_2026_STD", "EMP", "E001");
    }

    @Test
    void buildKpiDetail_snapshot_orgSubject_takesOrgCodeContext() {
        String cfg = "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"ORG\",\"mode\":\"SNAPSHOT\"}";
        var q = engine.build("KPI_DETAIL", cfg, req("LATEST", Map.of("orgCode", "610100")), 1000, TODAY);
        assertThat(q.params()).containsExactly(
                "KPI_2026_STD", "ORG", "610100", "KPI_2026_STD", "ORG", "610100");
    }

    @Test
    void buildKpiDetail_trend_latest_pivotByMetricCodeBindings() {
        var q = engine.build("KPI_DETAIL", KD_TREND_EMP_CFG,
                req("LATEST", Map.of("empId", "E001")), 1000, TODAY);
        // 透视：每个配置细项一列，metricCode 全部 ? 绑定，列名 = metricName
        assertThat(q.sql()).contains("MAX(CASE WHEN s.metric_code = ? THEN s.score END) AS `存款细项`");
        assertThat(q.sql()).contains("MAX(CASE WHEN s.metric_code = ? THEN s.score END) AS `贷款细项`");
        assertThat(q.sql()).contains("GROUP BY s.data_date");
        assertThat(q.sql()).endsWith("ORDER BY s.data_date DESC LIMIT 1");
        // 参数顺序：SELECT 列的 metricCode 在前，WHERE 主体过滤在后
        assertThat(q.params()).containsExactly("M_D1", "M_D2", "KPI_2026_STD", "EMP", "E001");
    }

    @Test
    void buildKpiDetail_trend_range_appendsDateBetweenAscendingLimit() {
        ScreenDataReqDTO r = req("RANGE", Map.of("empId", "E001"));
        r.setDateFrom("2026-07-01");
        r.setDateTo("2026-07-10");
        var q = engine.build("KPI_DETAIL", KD_TREND_EMP_CFG, r, 1000, TODAY);
        assertThat(q.sql()).contains("s.data_date BETWEEN ? AND ?");
        assertThat(q.sql()).endsWith("ORDER BY s.data_date LIMIT 1000");
        assertThat(q.params()).containsExactly("M_D1", "M_D2", "KPI_2026_STD", "EMP", "E001",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10));
    }

    @Test
    void buildKpiDetail_trend_completeRateValueCol_switchesPivotExpr() {
        String cfg = "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"EMP\",\"mode\":\"TREND\","
                + "\"valueCol\":\"completeRate\","
                + "\"metrics\":[{\"metricCode\":\"M_D1\",\"metricName\":\"存款细项\"}]}";
        var q = engine.build("KPI_DETAIL", cfg, req("LATEST", Map.of("empId", "E001")), 1000, TODAY);
        // valueCol=completeRate 时透视表达式换成完成率算式
        assertThat(q.sql()).contains("MAX(CASE WHEN s.metric_code = ? THEN "
                + "ROUND(s.actual_value / NULLIF(s.target_value, 0) * 100, 2) END) AS `存款细项`");
        assertThat(q.params()).containsExactly("M_D1", "KPI_2026_STD", "EMP", "E001");
    }

    @Test
    void buildKpiDetail_illegalMode_throws43009() {
        String cfg = "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"EMP\",\"mode\":\"REALTIME\"}";
        assertThatThrownBy(() -> engine.build("KPI_DETAIL", cfg, req("LATEST", Map.of("empId", "E001")), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void buildKpiDetail_illegalSubjectType_throws43009() {
        String cfg = "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"CUST\",\"mode\":\"SNAPSHOT\"}";
        assertThatThrownBy(() -> engine.build("KPI_DETAIL", cfg, req("LATEST", Map.of("custNo", "C001")), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void buildKpiDetail_blankSchemeCode_throws43009() {
        String cfg = "{\"schemaVersion\":2,\"subjectType\":\"EMP\",\"mode\":\"SNAPSHOT\"}";
        assertThatThrownBy(() -> engine.build("KPI_DETAIL", cfg, req("LATEST", Map.of("empId", "E001")), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void buildKpiDetail_trendWithoutMetrics_throws43009() {
        String cfg = "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"EMP\",\"mode\":\"TREND\"}";
        assertThatThrownBy(() -> engine.build("KPI_DETAIL", cfg, req("LATEST", Map.of("empId", "E001")), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void buildKpiDetail_illegalValueCol_throws43009() {
        String cfg = "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"EMP\",\"mode\":\"TREND\","
                + "\"valueCol\":\"weight\","
                + "\"metrics\":[{\"metricCode\":\"M_D1\",\"metricName\":\"存款细项\"}]}";
        assertThatThrownBy(() -> engine.build("KPI_DETAIL", cfg, req("LATEST", Map.of("empId", "E001")), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void buildKpiDetail_missingContextParam_throws43010() {
        // subjectType=EMP 需要 contextParams.empId；缺参属入参校验失败（43010），与配置非法 43009 语义分离
        assertThatThrownBy(() -> engine.build("KPI_DETAIL", KD_SNAPSHOT_EMP_CFG, req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43010");
    }

    @Test
    void build_unknownSourceKind_throws43009() {
        assertThatThrownBy(() -> engine.build("MAGIC", "{}", req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void validateCustomSql_validatorError_convergesTo43002() {
        // 语法非法 SQL：SqlSafeValidator 会抛 RPT-42001，引擎必须收敛为 43002
        assertThatThrownBy(() -> engine.validateCustomSql("SELECT FROM WHERE"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
    }

    // ===== WIDE_TABLE aggregation（spec 2026-07-17 §3.3）=====

    /** EMP 宽表单指标（slot 3 存款余额）+ 指定 aggregation 的基准配置 */
    private static String wideAggCfg(String aggregationJson) {
        return "{\"table\":\"EMP_INDEX_RESULT\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"存款余额\",\"slot\":3}],"
                + "\"aggregation\":" + aggregationJson + "}";
    }

    @Test
    void buildWideAgg_groupByNone_latest_aggregatesSingleRowWithoutSubjectParam() {
        var q = engine.build("WIDE_TABLE", wideAggCfg("{\"groupBy\":\"NONE\",\"agg\":\"SUM\"}"),
                req("LATEST", Map.of()), 1000, TODAY);
        assertThat(q.sql()).startsWith("SELECT SUM(val_3) AS `存款余额` FROM EMP_INDEX_RESULT");
        // 仍按 SYS_CONTROL 最新版本过滤
        assertThat(q.sql()).contains(
                "version = COALESCE((SELECT current_version FROM SYS_CONTROL WHERE scope_dim = 'EMP'");
        // LATEST = 当前版本内最新快照日单日聚合（跨主体不能再用 ORDER BY ... LIMIT 1）
        assertThat(q.sql()).contains(
                "data_date = (SELECT MAX(data_date) FROM EMP_INDEX_RESULT WHERE version = COALESCE(");
        // 跨主体聚合：不再要求主体上下文参数
        assertThat(q.sql()).doesNotContain("emp_id = ?");
        assertThat(q.sql()).endsWith("LIMIT 1000");
        assertThat(q.params()).isEmpty();
    }

    @Test
    void buildWideAgg_groupByNone_range_bindsFromToInOrder() {
        var q = engine.build("WIDE_TABLE", wideAggCfg("{\"groupBy\":\"NONE\",\"agg\":\"SUM\"}"),
                req("LAST_10D", Map.of()), 1000, TODAY);
        assertThat(q.sql()).contains("data_date BETWEEN ? AND ?");
        assertThat(q.params()).containsExactly(LocalDate.of(2026, 7, 3), TODAY);
    }

    @Test
    void buildWideAgg_groupBySubject_groupsBySubjectColOrderedByAggDesc() {
        var q = engine.build("WIDE_TABLE", wideAggCfg("{\"groupBy\":\"SUBJECT\",\"agg\":\"MAX\"}"),
                req("LATEST", Map.of()), 1000, TODAY);
        assertThat(q.sql()).startsWith("SELECT emp_id, MAX(val_3) AS `存款余额` FROM EMP_INDEX_RESULT");
        assertThat(q.sql()).contains("GROUP BY emp_id");
        // 排名/对比场景：按第一个聚合列倒序
        assertThat(q.sql()).endsWith("ORDER BY `存款余额` DESC LIMIT 1000");
        assertThat(q.params()).isEmpty();
    }

    @Test
    void buildWideAgg_groupByDate_range_returnsSeriesWithFilterParamOrder() {
        String cfg = "{\"table\":\"ORG_INDEX_RESULT\",\"subjectCol\":\"org_code\",\"subjectParam\":\"orgCode\","
                + "\"metrics\":[{\"metricCode\":\"M_0002\",\"metricName\":\"贷款余额\",\"slot\":7}],"
                + "\"aggregation\":{\"groupBy\":\"DATE\",\"agg\":\"AVG\","
                + "\"filters\":[{\"col\":\"org_code\",\"op\":\"EQ\",\"value\":\"610100\"}]}}";
        var q = engine.build("WIDE_TABLE", cfg, req("LAST_10D", Map.of()), 1000, TODAY);
        assertThat(q.sql()).startsWith("SELECT data_date, AVG(val_7) AS `贷款余额` FROM ORG_INDEX_RESULT");
        assertThat(q.sql()).contains("AND org_code = ?");
        assertThat(q.sql()).contains("GROUP BY data_date");
        assertThat(q.sql()).endsWith("ORDER BY data_date LIMIT 1000");
        // 参数顺序：周期范围在前，filters 依次在后
        assertThat(q.params()).containsExactly(LocalDate.of(2026, 7, 3), TODAY, "610100");
    }

    @Test
    void buildWideAgg_filters_eachOpMapsToSqlOperator() {
        String filters = "[{\"col\":\"emp_id\",\"op\":\"EQ\",\"value\":\"E001\"},"
                + "{\"col\":\"data_date\",\"op\":\"NE\",\"value\":\"2026-07-01\"},"
                + "{\"col\":\"val_3\",\"op\":\"GT\",\"value\":\"1\"},"
                + "{\"col\":\"val_3\",\"op\":\"GE\",\"value\":\"100\"},"
                + "{\"col\":\"val_3\",\"op\":\"LT\",\"value\":\"9\"},"
                + "{\"col\":\"val_3\",\"op\":\"LE\",\"value\":\"8\"}]";
        var q = engine.build("WIDE_TABLE",
                wideAggCfg("{\"groupBy\":\"NONE\",\"agg\":\"SUM\",\"filters\":" + filters + "}"),
                req("LATEST", Map.of()), 1000, TODAY);
        assertThat(q.sql()).contains("AND emp_id = ?");
        assertThat(q.sql()).contains("AND data_date <> ?");
        assertThat(q.sql()).contains("AND val_3 > ?");
        assertThat(q.sql()).contains("AND val_3 >= ?");
        assertThat(q.sql()).contains("AND val_3 < ?");
        assertThat(q.sql()).contains("AND val_3 <= ?");
        // 值全部 ? 绑定且保持声明顺序（禁止任何字符串拼接用户值）
        assertThat(q.params()).containsExactly("E001", "2026-07-01", "1", "100", "9", "8");
    }

    @Test
    void buildWideAgg_filterIn_splitsCommaIntoMultipleBinds() {
        var q = engine.build("WIDE_TABLE",
                wideAggCfg("{\"groupBy\":\"SUBJECT\",\"agg\":\"SUM\","
                        + "\"filters\":[{\"col\":\"emp_id\",\"op\":\"IN\",\"value\":\"E001,E002,E003\"}]}"),
                req("LATEST", Map.of()), 1000, TODAY);
        assertThat(q.sql()).contains("AND emp_id IN (?, ?, ?)");
        assertThat(q.params()).containsExactly("E001", "E002", "E003");
    }

    @Test
    void buildWideAgg_filterColNotAllowed_throws43009() {
        // val_99 未配置为该数据源的槽位列 → 越界（仅允许主体列/data_date/已配置 val_N）
        assertThatThrownBy(() -> engine.build("WIDE_TABLE",
                wideAggCfg("{\"groupBy\":\"NONE\",\"agg\":\"SUM\","
                        + "\"filters\":[{\"col\":\"val_99\",\"op\":\"EQ\",\"value\":\"1\"}]}"),
                req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void buildWideAgg_filterOpNotAllowed_throws43009() {
        assertThatThrownBy(() -> engine.build("WIDE_TABLE",
                wideAggCfg("{\"groupBy\":\"NONE\",\"agg\":\"SUM\","
                        + "\"filters\":[{\"col\":\"emp_id\",\"op\":\"LIKE\",\"value\":\"E%\"}]}"),
                req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void buildWideAgg_illegalGroupBy_throws43009() {
        assertThatThrownBy(() -> engine.build("WIDE_TABLE",
                wideAggCfg("{\"groupBy\":\"CUSTOM\",\"agg\":\"SUM\"}"),
                req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void buildWideAgg_illegalAggFunc_throws43009() {
        assertThatThrownBy(() -> engine.build("WIDE_TABLE",
                wideAggCfg("{\"groupBy\":\"NONE\",\"agg\":\"MEDIAN\"}"),
                req("LATEST", Map.of()), 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void buildWide_withFieldMetaButNoAggregation_behaviorUnchanged() {
        // 回归保障：无 aggregation → 明细行为完全不变（fieldMeta 只影响响应元数据，不影响 SQL）
        String cfg = "{\"table\":\"EMP_INDEX_RESULT\",\"subjectCol\":\"emp_id\",\"subjectParam\":\"empId\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"存款余额\",\"slot\":3}],"
                + "\"fieldMeta\":[{\"col\":\"存款余额\",\"role\":\"METRIC\"}]}";
        var q = engine.build("WIDE_TABLE", cfg, req("LATEST", Map.of("empId", "E001")), 1000, TODAY);
        assertThat(q.sql()).contains("emp_id = ?");
        assertThat(q.sql()).endsWith("ORDER BY data_date DESC LIMIT 1");
        assertThat(q.params()).containsExactly("E001");
    }

    // ===== fieldMeta → columnsMeta（spec 2026-07-17 §3.2）=====

    @Test
    void fillColumnsMeta_matchesByColumnName_skipsUnconfiguredColumns() {
        String cfg = "{\"table\":\"EMP_INDEX_RESULT\","
                + "\"fieldMeta\":[{\"col\":\"存款余额\",\"alias\":\"一般性存款\",\"role\":\"METRIC\","
                + "\"unit\":\"万元\",\"decimals\":2},"
                + "{\"col\":\"data_date\",\"role\":\"DIM\"}]}";
        ScreenDataRespDTO resp = new ScreenDataRespDTO(
                List.of("data_date", "存款余额", "贷款余额"), List.of());
        engine.fillColumnsMeta(resp, cfg);
        // 固化实现选择：仅出现配置过 fieldMeta 的列，顺序跟随 columns；未配置列（贷款余额）不出现
        assertThat(resp.getColumnsMeta()).hasSize(2);
        assertThat(resp.getColumnsMeta().get(0).getCol()).isEqualTo("data_date");
        assertThat(resp.getColumnsMeta().get(0).getRole()).isEqualTo("DIM");
        assertThat(resp.getColumnsMeta().get(0).getAlias()).isNull();
        assertThat(resp.getColumnsMeta().get(1).getCol()).isEqualTo("存款余额");
        assertThat(resp.getColumnsMeta().get(1).getAlias()).isEqualTo("一般性存款");
        assertThat(resp.getColumnsMeta().get(1).getUnit()).isEqualTo("万元");
        assertThat(resp.getColumnsMeta().get(1).getDecimals()).isEqualTo(2);
        assertThat(resp.getColumnsMeta().get(1).getAmountScale()).isNull();
    }

    @Test
    void fillColumnsMeta_withoutFieldMeta_keepsNull() {
        // 固化实现选择：无 fieldMeta 配置 → columnsMeta 保持 null（旧调用方零影响）
        ScreenDataRespDTO resp = new ScreenDataRespDTO(List.of("cnt"), List.of());
        engine.fillColumnsMeta(resp, "{\"table\":\"EMP_INDEX_RESULT\"}");
        assertThat(resp.getColumnsMeta()).isNull();
    }

    @Test
    void fillColumnsMeta_amountScale_derivesUnitAndFixedDecimals_withoutScalingRows() {
        String cfg = "{\"table\":\"EMP_INDEX_RESULT\",\"fieldMeta\":["
                + "{\"col\":\"yuan\",\"role\":\"METRIC\",\"amountScale\":\"YUAN\"},"
                + "{\"col\":\"tenK\",\"role\":\"METRIC\",\"amountScale\":\"TEN_THOUSAND_YUAN\"},"
                + "{\"col\":\"hundredM\",\"role\":\"METRIC\",\"amountScale\":\"HUNDRED_MILLION_YUAN\"}]}";
        List<List<Object>> rows = List.of(List.of(10000, 20000, 30000));
        ScreenDataRespDTO resp = new ScreenDataRespDTO(
                List.of("yuan", "tenK", "hundredM"), rows);

        engine.fillColumnsMeta(resp, cfg);

        assertThat(resp.getColumnsMeta()).extracting(ScreenDataRespDTO.ColumnMeta::getUnit)
                .containsExactly("元", "万元", "亿元");
        assertThat(resp.getColumnsMeta()).extracting(ScreenDataRespDTO.ColumnMeta::getDecimals)
                .containsExactly(2, 2, 2);
        assertThat(resp.getColumnsMeta()).extracting(ScreenDataRespDTO.ColumnMeta::getAmountScale)
                .containsExactly("YUAN", "TEN_THOUSAND_YUAN", "HUNDRED_MILLION_YUAN");
        assertThat(resp.getRows()).isSameAs(rows);
        assertThat(resp.getRows()).containsExactly(List.of(10000, 20000, 30000));
    }

    @Test
    void query_orgSubjectAggregation_insertsOrgNameAfterOrgCode_andBatchesDistinctCodes() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        when(orgApi.getOrgsByCodes(List.of("001", "002", "003")))
                .thenReturn(List.of(org("001", "一支行"), org("002", "二支行")));
        JdbcFixture jdbc = jdbc(List.of("org_code", "存款余额"), List.of(
                List.of("001", 100),
                List.of(" 002 ", 200),
                List.of("003", 300),
                List.of("001", 400),
                List.of("", 500),
                Arrays.asList(null, 600)));
        ScreenQueryEngine queryEngine = engine(jdbc.dataSource(), orgApi);
        RptScreenDatasource datasource = datasource(orgSubjectConfig());

        ScreenDataRespDTO response = queryEngine.query(datasource, req("LATEST", Map.of()));

        assertThat(response.getColumns()).containsExactly("org_code", "org_name", "存款余额");
        assertThat(response.getRows()).containsExactly(
                List.of("001", "一支行", 100),
                List.of(" 002 ", "二支行", 200),
                Arrays.asList("003", null, 300),
                List.of("001", "一支行", 400),
                Arrays.asList("", null, 500),
                Arrays.asList(null, null, 600));
        assertThat(response.getColumnsMeta()).extracting(ScreenDataRespDTO.ColumnMeta::getCol)
                .containsExactly("org_code", "org_name", "存款余额");
        verify(orgApi).getOrgsByCodes(List.of("001", "002", "003"));
    }

    @Test
    void tryRun_orgSubjectAggregation_usesSameOrgNameEnrichmentPath() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        when(orgApi.getOrgsByCodes(List.of("001"))).thenReturn(List.of(org("001", "一支行")));
        JdbcFixture jdbc = jdbc(List.of("org_code", "存款余额"), List.of(List.of("001", 100)));
        ScreenQueryEngine queryEngine = engine(jdbc.dataSource(), orgApi);

        ScreenDataRespDTO response = queryEngine.tryRun("WIDE_TABLE", orgSubjectConfig(), req("LATEST", Map.of()));

        assertThat(response.getColumns()).containsExactly("org_code", "org_name", "存款余额");
        assertThat(response.getRows()).containsExactly(List.of("001", "一支行", 100));
        verify(orgApi).getOrgsByCodes(List.of("001"));
    }

    @Test
    void query_nonTargetWideConfigurations_doNotCallOrgApi_orChangeRows() throws Exception {
        assertNoOrgEnrichment(
                "{\"table\":\"ORG_INDEX_RESULT\",\"metrics\":[{\"metricName\":\"存款余额\",\"slot\":3}],"
                        + "\"aggregation\":{\"groupBy\":\"NONE\",\"agg\":\"SUM\"}}",
                List.of("存款余额"), List.of(List.of(100)));
        assertNoOrgEnrichment(
                "{\"table\":\"ORG_INDEX_RESULT\",\"metrics\":[{\"metricName\":\"存款余额\",\"slot\":3}],"
                        + "\"aggregation\":{\"groupBy\":\"DATE\",\"agg\":\"SUM\"}}",
                List.of("data_date", "存款余额"), List.of(List.of("2026-07-12", 100)));
        assertNoOrgEnrichment(
                "{\"table\":\"ORG_INDEX_RESULT\",\"metrics\":[{\"metricName\":\"存款余额\",\"slot\":3}]}",
                List.of("data_date", "存款余额"), List.of(List.of("2026-07-12", 100)));
        assertNoOrgEnrichment(
                "{\"table\":\"EMP_INDEX_RESULT\",\"metrics\":[{\"metricName\":\"存款余额\",\"slot\":3}],"
                        + "\"aggregation\":{\"groupBy\":\"SUBJECT\",\"agg\":\"SUM\"}}",
                List.of("emp_id", "存款余额"), List.of(List.of("E001", 100)));
    }

    @Test
    void query_orgApiFailure_convergesToScreenDataQueryFailed() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        when(orgApi.getOrgsByCodes(List.of("001"))).thenThrow(new IllegalStateException("upstream unavailable"));
        JdbcFixture jdbc = jdbc(List.of("org_code", "存款余额"), List.of(List.of("001", 100)));
        ScreenQueryEngine queryEngine = engine(jdbc.dataSource(), orgApi);

        assertThatThrownBy(() -> queryEngine.tryRun("WIDE_TABLE", orgSubjectConfig(), req("LATEST", Map.of())))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43008");
    }

    private void assertNoOrgEnrichment(String config, List<String> columns, List<List<Object>> rows)
            throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        JdbcFixture jdbc = jdbc(columns, rows);
        ScreenQueryEngine queryEngine = engine(jdbc.dataSource(), orgApi);

        ScreenDataRespDTO response = queryEngine.tryRun("WIDE_TABLE", config,
                req("LATEST", Map.of("orgCode", "001")));

        assertThat(response.getColumns()).containsExactlyElementsOf(columns);
        assertThat(response.getRows()).containsExactlyElementsOf(rows);
        verify(orgApi, never()).getOrgsByCodes(any());
    }

    private static String orgSubjectConfig() {
        return "{\"table\":\"ORG_INDEX_RESULT\",\"metrics\":[{\"metricName\":\"存款余额\",\"slot\":3}],"
                + "\"aggregation\":{\"groupBy\":\"SUBJECT\",\"agg\":\"SUM\"},"
                + "\"fieldMeta\":[{\"col\":\"org_code\",\"role\":\"DIM\"},"
                + "{\"col\":\"org_name\",\"role\":\"DIM\"},"
                + "{\"col\":\"存款余额\",\"role\":\"METRIC\",\"unit\":\"元\",\"decimals\":2}]}";
    }

    private static RptScreenDatasource datasource(String config) {
        RptScreenDatasource datasource = new RptScreenDatasource();
        datasource.setSourceKind("WIDE_TABLE");
        datasource.setConfigJson(config);
        return datasource;
    }

    private static OrgDTO org(String code, String name) {
        OrgDTO dto = new OrgDTO();
        dto.setOrgCode(code);
        dto.setOrgName(name);
        return dto;
    }

    private static ScreenQueryEngine engine(DataSource dataSource, OrgApi orgApi) {
        return new ScreenQueryEngine(
                dataSource,
                orgApi,
                List.of("EMP_INDEX_RESULT", "ORG_INDEX_RESULT", "CUST_INDEX_RESULT", "KPI_RESULT",
                        "SYS_CONTROL", "EXT_ORG_INFO", "ACT_RU_TASK"),
                List.of("DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE", "ALTER", "CREATE", "GRANT"),
                1000);
    }

    private static JdbcFixture jdbc(List<String> columns, List<List<Object>> values) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            when(metadata.getColumnLabel(i + 1)).thenReturn(columns.get(i));
        }
        Iterator<List<Object>> iterator = values.iterator();
        List<Object>[] current = new List[]{null};
        when(resultSet.next()).thenAnswer(invocation -> {
            if (!iterator.hasNext()) {
                return false;
            }
            current[0] = iterator.next();
            return true;
        });
        when(resultSet.getObject(anyInt())).thenAnswer(invocation ->
                current[0].get(invocation.getArgument(0, Integer.class) - 1));
        return new JdbcFixture(dataSource, resultSet);
    }

    private record JdbcFixture(DataSource dataSource, ResultSet resultSet) {
    }
}
