package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.performance.api.BranchDashboardBatchQueryApi;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchAttemptDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchRowDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardHistoryCoverageDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardMetricContractDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardQualityDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardSourceAsOfDTO;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void buildWideNamedGroup_latest_selectsLatestCompleteAuthorizedBatchWithBoundTodayAndSlots() {
        String cfg = "{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"subjectCol\":\"org_code\",\"subjectParam\":\"orgCode\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"存款余额\",\"slot\":3},"
                + "{\"metricCode\":\"M_0002\",\"metricName\":\"贷款余额\",\"slot\":7}],"
                + "\"aggregation\":{\"groupBy\":\"NONE\",\"agg\":\"SUM\"}}";
        ScreenDataReqDTO request = req("LATEST", Map.of());
        request.setNamedGroup(true);
        request.setServerOrgCodes(List.of("001", "002"));

        var q = engine.build("WIDE_TABLE", cfg, request, 1000, TODAY);

        assertThat(q.sql()).contains("data_date <= ?");
        assertThat(q.sql()).contains(
                "COUNT(DISTINCT CASE WHEN candidate.val_3 IS NOT NULL AND candidate.val_7 IS NOT NULL "
                        + "THEN candidate.org_code END) = ?");
        assertThat(q.sql()).contains("GROUP BY candidate.data_date");
        assertThat(q.sql()).contains("MAX(complete_batch.data_date)");
        assertThat(q.sql()).contains("org_code IN (?, ?)");
        assertThat(q.params()).containsExactly(
                "001", "002", TODAY, "001", "002", 2, 2);
    }

    @Test
    void buildWideNamedGroup_qualityPolicy_rejectsUnknownFields() {
        String cfg = "{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"metrics\":[{\"metricName\":\"存款余额\",\"slot\":3}],"
                + "\"qualityPolicy\":{\"maxAgeDays\":1,\"requiredComplete\":true,\"unknown\":true},"
                + "\"aggregation\":{\"groupBy\":\"NONE\",\"agg\":\"SUM\"}}";
        ScreenDataReqDTO request = req("LATEST", Map.of());
        request.setNamedGroup(true);
        request.setServerOrgCodes(List.of("001"));

        assertThatThrownBy(() -> engine.build("WIDE_TABLE", cfg, request, 1000, TODAY))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void queryWideNamedGroup_latest_fallsBackToOldCompleteDate_andReportsFreshQuality() throws Exception {
        RuntimeJdbcFixture jdbc = runtimeJdbc(
                "V2", LocalDate.of(2026, 7, 10), 2, true,
                List.of("存款余额"), List.of(List.of(300)));
        ScreenQueryEngine queryEngine = engine(jdbc.dataSource(), mock(OrgApi.class));
        RptScreenDatasource datasource = datasource(namedOrgQualityConfig(2));
        ScreenDataReqDTO request = namedGroupRequest("001", "002");

        ScreenDataRespDTO response = queryEngine.queryAt(datasource, request, TODAY, 1000);

        assertThat(response.getRows()).containsExactly(List.of(300));
        assertThat(response.getQuality()).isNotNull();
        assertThat(response.getQuality().getBatchId()).isEqualTo("ORG:V2:2026-07-10");
        assertThat(response.getQuality().getDataDate()).isEqualTo("2026-07-10");
        assertThat(response.getQuality().getVersion()).isEqualTo("V2");
        assertThat(response.getQuality().getStatus()).isEqualTo("COMPLETE");
        assertThat(response.getQuality().getExpectedSubjects()).isEqualTo(2);
        assertThat(response.getQuality().getReceivedSubjects()).isEqualTo(2);
        assertThat(response.getQuality().getMaxAgeDays()).isEqualTo(2);
        assertThat(response.getQuality().getAgeDays()).isEqualTo(2);
        verify(jdbc.preflight()).setObject(1, Date.valueOf(TODAY));
        verify(jdbc.preflight()).setObject(2, "001");
        verify(jdbc.preflight()).setObject(3, "002");
        verify(jdbc.preflight()).setObject(4, 2);
        verify(jdbc.preflight()).setObject(5, 2);
        verify(jdbc.main()).setObject(1, "V2");
        verify(jdbc.main()).setObject(2, "001");
        verify(jdbc.main()).setObject(3, "002");
        verify(jdbc.main()).setObject(4, Date.valueOf(LocalDate.of(2026, 7, 10)));
        verify(jdbc.connection(), times(2)).prepareStatement(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void queryWideNamedGroup_latest_staleBoundaryUsesStrictGreaterThan() throws Exception {
        RuntimeJdbcFixture jdbc = runtimeJdbc(
                "V2", LocalDate.of(2026, 7, 9), 2, true,
                List.of("存款余额"), List.of(List.of(300)));
        ScreenQueryEngine queryEngine = engine(jdbc.dataSource(), mock(OrgApi.class));

        ScreenDataRespDTO response = queryEngine.queryAt(
                datasource(namedOrgQualityConfig(2)), namedGroupRequest("001", "002"), TODAY, 1000);

        assertThat(response.getRows()).containsExactly(List.of(300));
        assertThat(response.getQuality().getStatus()).isEqualTo("STALE");
        assertThat(response.getQuality().getAgeDays()).isEqualTo(3);
    }

    @Test
    void queryWideNamedGroup_latest_withoutCompleteBatchReturnsNoRowsAndQuality() throws Exception {
        RuntimeJdbcFixture jdbc = runtimeJdbc(
                null, null, 0, false,
                List.of("存款余额"), List.of());
        ScreenQueryEngine queryEngine = engine(jdbc.dataSource(), mock(OrgApi.class));

        ScreenDataRespDTO response = queryEngine.queryAt(
                datasource(namedOrgQualityConfig(2)), namedGroupRequest("001", "002"), TODAY, 1000);

        assertThat(response.getRows()).isEmpty();
        assertThat(response.getQuality().getStatus()).isEqualTo("NO_COMPLETE_BATCH");
        assertThat(response.getQuality().getBatchId()).isNull();
        assertThat(response.getQuality().getExpectedSubjects()).isEqualTo(2);
        assertThat(response.getQuality().getReceivedSubjects()).isZero();
        assertThat(response.getQuality().getDataDate()).isNull();
    }

    @Test
    void tryRunWideNamedGroup_latest_usesSameQualityResolutionPath() throws Exception {
        RuntimeJdbcFixture jdbc = runtimeJdbc(
                "V2", LocalDate.of(2026, 7, 10), 2, true,
                List.of("存款余额"), List.of(List.of(300)));
        ScreenQueryEngine queryEngine = engine(jdbc.dataSource(), mock(OrgApi.class));

        ScreenDataRespDTO response = queryEngine.tryRunAt(
                "WIDE_TABLE", namedOrgQualityConfig(2), namedGroupRequest("001", "002"), TODAY, 10);

        assertThat(response.getQuality().getBatchId()).isEqualTo("ORG:V2:2026-07-10");
        assertThat(response.getQuality().getStatus()).isEqualTo("COMPLETE");
        assertThat(response.getQuality().getExpectedSubjects()).isEqualTo(2);
        assertThat(response.getRows()).containsExactly(List.of(300));
    }

    @Test
    void queryWideNamedGroup_emptyAuthorizedGroupFailsClosedBeforeDatabaseAccess() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        ScreenQueryEngine queryEngine = engine(dataSource, mock(OrgApi.class));

        ScreenDataReqDTO request = namedGroupRequest();

        assertThatThrownBy(() -> queryEngine.queryAt(
                datasource(namedOrgQualityConfig(2)), request, TODAY, 1000))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43015");
        verify(dataSource, never()).getConnection();
    }

    @Test
    void batchPolicy_byIdUsesOpaqueBatchAndFullAuthorizedScopeWhileSingleOrgOnlyFiltersOutput() {
        BranchDashboardBatchQueryApi batchApi = mock(BranchDashboardBatchQueryApi.class);
        BranchDashboardBatchDTO snapshot = BranchDashboardBatchDTO.builder()
                .batchId("RUN-20260710-01")
                .groupCode("GROUP_CORP")
                .dataDate(LocalDate.of(2026, 7, 10))
                .version("V2")
                .status("COMPLETE")
                .calculatedAt(LocalDateTime.of(2026, 7, 10, 18, 0))
                .memberOrgCodes(List.of("001", "002"))
                .sourceAsOf(BranchDashboardSourceAsOfDTO.builder()
                        .financial(LocalDate.of(2026, 7, 10))
                        .marketing(LocalDate.of(2026, 7, 10))
                        .target(LocalDate.of(2026, 7, 10))
                        .revenue(LocalDate.of(2026, 7, 10))
                        .build())
                .quality(BranchDashboardQualityDTO.builder()
                        .expected(2)
                        .received(2)
                        .expectedSubjects(2)
                        .receivedSubjects(2)
                        .selectedComplete(true)
                        .mixedPeriod(false)
                        .build())
                .metricContracts(Map.of("M_0001", BranchDashboardMetricContractDTO.builder()
                        .metricCode("M_0001").metricName("存款余额").unit("YUAN").decimalPlaces(2).build()))
                .rows(List.of(
                        BranchDashboardBatchRowDTO.builder().orgCode("001")
                                .dataDate(LocalDate.of(2026, 7, 10))
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(300))).build(),
                        BranchDashboardBatchRowDTO.builder().orgCode("002")
                                .dataDate(LocalDate.of(2026, 7, 10))
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(500))).build()))
                .build();
        when(batchApi.byId("RUN-20260710-01", List.of("001", "002")))
                .thenReturn(Optional.of(snapshot));

        OrgApi orgApi = mock(OrgApi.class);
        when(orgApi.getOrgsByCodes(List.of("002"))).thenReturn(List.of(org("002", "二支行")));
        ScreenQueryEngine queryEngine = engine(null, orgApi);
        queryEngine.setBatchQueryApi(batchApi);
        ScreenDataReqDTO request = namedGroupRequest("001", "002");
        request.setServerGroupCode("GROUP_CORP");
        request.setBatchId("RUN-20260710-01");
        request.setServerRequestedOrgCode("002");
        String config = "{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"存款余额\",\"slot\":3}],"
                + "\"aggregation\":{\"groupBy\":\"SUBJECT\",\"agg\":\"SUM\"},"
                + "\"batchPolicy\":{\"maxAgeDays\":2,\"requiredComplete\":true}}";

        ScreenDataRespDTO response = queryEngine.tryRunAt(
                "WIDE_TABLE", config, request, TODAY, 10);

        assertThat(response.getRows()).containsExactly(List.of("002", "二支行", BigDecimal.valueOf(500)));
        assertThat(response.getQuality().getBatchId()).isEqualTo("RUN-20260710-01");
        assertThat(response.getQuality().getStatus()).isEqualTo("COMPLETE");
        assertThat(response.getQuality().getSourceAsOf().get("financial")).isEqualTo("2026-07-10");
        verify(batchApi).byId("RUN-20260710-01", List.of("001", "002"));
    }

    @Test
    void batchPolicy_subjectKeepsOrgNameAndAlignsAllMetricColumns() {
        BranchDashboardBatchQueryApi batchApi = mock(BranchDashboardBatchQueryApi.class);
        OrgApi orgApi = mock(OrgApi.class);
        when(orgApi.getOrgsByCodes(List.of("002"))).thenReturn(List.of(org("002", "二支行")));
        BranchDashboardBatchDTO snapshot = BranchDashboardBatchDTO.builder()
                .batchId("RUN-20260710-SUBJECT")
                .groupCode("GROUP_CORP")
                .dataDate(LocalDate.of(2026, 7, 10))
                .version("V2")
                .status("COMPLETE")
                .memberOrgCodes(List.of("001", "002"))
                .quality(BranchDashboardQualityDTO.builder()
                        .expectedSubjects(2).receivedSubjects(2).selectedComplete(true).build())
                .metricContracts(Map.of(
                        "M_0001", BranchDashboardMetricContractDTO.builder()
                                .metricCode("M_0001").metricName("存款余额").unit("YUAN").build(),
                        "M_0002", BranchDashboardMetricContractDTO.builder()
                                .metricCode("M_0002").metricName("客户数").unit("COUNT").build()))
                .rows(List.of(
                        BranchDashboardBatchRowDTO.builder().orgCode("001")
                                .dataDate(LocalDate.of(2026, 7, 10))
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(300),
                                        "M_0002", BigDecimal.valueOf(4))).build(),
                        BranchDashboardBatchRowDTO.builder().orgCode("002")
                                .dataDate(LocalDate.of(2026, 7, 10))
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(500),
                                        "M_0002", BigDecimal.valueOf(8))).build()))
                .build();
        when(batchApi.byId("RUN-20260710-SUBJECT", List.of("001", "002")))
                .thenReturn(Optional.of(snapshot));

        ScreenQueryEngine queryEngine = engine(null, orgApi);
        queryEngine.setBatchQueryApi(batchApi);
        ScreenDataReqDTO request = namedGroupRequest("001", "002");
        request.setServerGroupCode("GROUP_CORP");
        request.setBatchId("RUN-20260710-SUBJECT");
        request.setServerRequestedOrgCode("002");
        String config = "{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"存款余额\",\"slot\":3},"
                + "{\"metricCode\":\"M_0002\",\"metricName\":\"客户数\",\"slot\":7}],"
                + "\"aggregation\":{\"groupBy\":\"SUBJECT\",\"agg\":\"SUM\"},"
                + "\"batchPolicy\":{\"requiredComplete\":true}}";

        ScreenDataRespDTO response = queryEngine.queryAt(
                datasource(config), request, TODAY, 10);

        assertThat(response.getColumns()).containsExactly("org_code", "org_name", "存款余额", "客户数");
        assertThat(response.getRows()).containsExactly(
                List.of("002", "二支行", BigDecimal.valueOf(500), BigDecimal.valueOf(8)));
        assertThat(response.getColumnsMeta()).extracting(ScreenDataRespDTO.ColumnMeta::getRole)
                .containsExactly("DIM", "DIM", "METRIC", "METRIC");
    }

    @Test
    void batchPolicy_rawWideTrendUsesSameSnapshotQualityAndFiltersOnlyRequestedOrg() {
        BranchDashboardBatchQueryApi batchApi = mock(BranchDashboardBatchQueryApi.class);
        BranchDashboardBatchDTO snapshot = BranchDashboardBatchDTO.builder()
                .batchId("RUN-20260710-02")
                .groupCode("GROUP_CORP")
                .dataDate(LocalDate.of(2026, 7, 10))
                .version("V2")
                .status("COMPLETE")
                .memberOrgCodes(List.of("001", "002"))
                .quality(BranchDashboardQualityDTO.builder()
                        .expectedSubjects(2).receivedSubjects(2).selectedComplete(true).build())
                .metricContracts(Map.of("M_0001", BranchDashboardMetricContractDTO.builder()
                        .metricCode("M_0001").metricName("存款余额").unit("YUAN").build()))
                .rows(List.of(
                        BranchDashboardBatchRowDTO.builder().orgCode("001")
                                .dataDate(LocalDate.of(2026, 7, 10))
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(300))).build(),
                        BranchDashboardBatchRowDTO.builder().orgCode("002")
                                .dataDate(LocalDate.of(2026, 7, 10))
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(500))).build()))
                .historyRows(List.of(
                        BranchDashboardBatchRowDTO.builder().orgCode("002")
                                .dataDate(LocalDate.of(2026, 7, 9))
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(480))).build(),
                        BranchDashboardBatchRowDTO.builder().orgCode("002")
                                .dataDate(LocalDate.of(2026, 7, 10))
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(500))).build()))
                .build();
        when(batchApi.latest("GROUP_CORP", List.of("001", "002"))).thenReturn(Optional.of(snapshot));

        ScreenQueryEngine queryEngine = engine(null, mock(OrgApi.class));
        queryEngine.setBatchQueryApi(batchApi);
        ScreenDataReqDTO request = namedGroupRequest("002");
        request.setServerAuthorizedOrgCodes(List.of("001", "002"));
        request.setServerGroupCode("GROUP_CORP");
        request.setServerRequestedOrgCode("002");
        request.setPeriod("LAST_1M");
        String config = "{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"存款余额\",\"slot\":3}],"
                + "\"batchPolicy\":{\"maxAgeDays\":2}}";

        ScreenDataRespDTO response = queryEngine.queryAt(
                datasource(config), request, TODAY, 10);

        assertThat(response.getColumns()).containsExactly("data_date", "存款余额");
        assertThat(response.getRows()).containsExactly(
                List.of("2026-07-09", BigDecimal.valueOf(480)),
                List.of("2026-07-10", BigDecimal.valueOf(500)));
        assertThat(response.getQuality().getBatchId()).isEqualTo("RUN-20260710-02");
        assertThat(response.getQuality().getExpectedSubjects()).isEqualTo(2);
        verify(batchApi).latest("GROUP_CORP", List.of("001", "002"));
    }

    @Test
    void batchPolicy_latestDateUsesSnapshotRowsBeforeHistoryRows() {
        BranchDashboardBatchQueryApi batchApi = mock(BranchDashboardBatchQueryApi.class);
        BranchDashboardBatchDTO snapshot = BranchDashboardBatchDTO.builder()
                .batchId("RUN-20260710-03").groupCode("GROUP_CORP")
                .dataDate(LocalDate.of(2026, 7, 10)).version("V2").status("COMPLETE")
                .memberOrgCodes(List.of("001", "002"))
                .quality(BranchDashboardQualityDTO.builder()
                        .expectedSubjects(2).receivedSubjects(2).selectedComplete(true).build())
                .metricContracts(Map.of(
                        "M_0001", BranchDashboardMetricContractDTO.builder()
                                .metricCode("M_0001").metricName("存款余额").unit("YUAN").build(),
                        "M_0002", BranchDashboardMetricContractDTO.builder()
                                .metricCode("M_0002").metricName("客户数").unit("COUNT").build()))
                .rows(List.of(
                        BranchDashboardBatchRowDTO.builder().orgCode("001")
                                .dataDate(LocalDate.of(2026, 7, 10))
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(300),
                                        "M_0002", BigDecimal.valueOf(4))).build(),
                        BranchDashboardBatchRowDTO.builder().orgCode("002")
                                .dataDate(LocalDate.of(2026, 7, 10))
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(500),
                                        "M_0002", BigDecimal.valueOf(6))).build()))
                // historyRows 只有金融列，LATEST 不能误读这份历史投影。
                .historyRows(List.of(
                        BranchDashboardBatchRowDTO.builder().orgCode("001")
                                .dataDate(LocalDate.of(2026, 7, 10))
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(300))).build(),
                        BranchDashboardBatchRowDTO.builder().orgCode("002")
                                .dataDate(LocalDate.of(2026, 7, 10))
                                .metricValues(Map.of("M_0001", BigDecimal.valueOf(500))).build()))
                .build();
        when(batchApi.latest("GROUP_CORP", List.of("001", "002"))).thenReturn(Optional.of(snapshot));

        ScreenQueryEngine queryEngine = engine(null, mock(OrgApi.class));
        queryEngine.setBatchQueryApi(batchApi);
        ScreenDataReqDTO request = namedGroupRequest("001", "002");
        request.setServerGroupCode("GROUP_CORP");
        String config = "{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"存款余额\",\"slot\":3},"
                + "{\"metricCode\":\"M_0002\",\"metricName\":\"客户数\",\"slot\":7}],"
                + "\"aggregation\":{\"groupBy\":\"DATE\",\"agg\":\"SUM\"},"
                + "\"batchPolicy\":{\"requiredComplete\":true}}";

        ScreenDataRespDTO response = queryEngine.queryAt(
                datasource(config), request, TODAY, 10);

        assertThat(response.getRows()).containsExactly(
                List.of("2026-07-10", BigDecimal.valueOf(800), BigDecimal.valueOf(10)));
        assertThat(response.getQuality().getStatus()).isEqualTo("COMPLETE");
    }

    @Test
    void batchPolicy_latestFailedAttemptIsVisibleWhileReturningLastCompleteSnapshot() {
        BranchDashboardBatchQueryApi batchApi = mock(BranchDashboardBatchQueryApi.class);
        BranchDashboardBatchDTO snapshot = BranchDashboardBatchDTO.builder()
                .batchId("RUN-20260712-FAILED-RETRY")
                .groupCode("GROUP_CORP")
                .dataDate(TODAY)
                .version("V2")
                .status("COMPLETE")
                .memberOrgCodes(List.of("001"))
                .latestAttempt(BranchDashboardBatchAttemptDTO.builder()
                        .attemptId("ATTEMPT-FAILED-20260712")
                        .status("FAILED").message("target source timeout").build())
                .quality(BranchDashboardQualityDTO.builder()
                        .expectedSubjects(1).receivedSubjects(1).selectedComplete(true).build())
                .metricContracts(Map.of("M_0001", BranchDashboardMetricContractDTO.builder()
                        .metricCode("M_0001").metricName("存款余额").unit("YUAN").build()))
                .rows(List.of(BranchDashboardBatchRowDTO.builder().orgCode("001")
                        .dataDate(TODAY).metricValues(Map.of("M_0001", BigDecimal.valueOf(300))).build()))
                .build();
        when(batchApi.latest("GROUP_CORP", List.of("001"))).thenReturn(Optional.of(snapshot));

        ScreenQueryEngine queryEngine = engine(null, mock(OrgApi.class));
        queryEngine.setBatchQueryApi(batchApi);
        ScreenDataReqDTO request = namedGroupRequest("001");
        request.setServerGroupCode("GROUP_CORP");
        String config = "{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"存款余额\",\"slot\":3}],"
                + "\"batchPolicy\":{\"requiredComplete\":true}}";

        ScreenDataRespDTO response = queryEngine.queryAt(
                datasource(config), request, TODAY, 10);

        assertThat(response.getRows()).containsExactly(List.of("2026-07-12", BigDecimal.valueOf(300)));
        assertThat(response.getQuality().getStatus()).isEqualTo("COMPLETE");
        assertThat(response.getQuality().getMessage())
                .contains("最近一次批次尝试状态=FAILED")
                .contains("最近尝试批次编号=ATTEMPT-FAILED-20260712")
                .contains("target source timeout");
        assertThat(response.getQuality().getBatchId()).isEqualTo("RUN-20260712-FAILED-RETRY");
    }

    @Test
    void batchPolicy_qualityCarriesHistoryCoverageAndDataClassification() {
        BranchDashboardBatchQueryApi batchApi = mock(BranchDashboardBatchQueryApi.class);
        LocalDate historyDate = LocalDate.of(2026, 7, 9);
        BranchDashboardBatchDTO snapshot = BranchDashboardBatchDTO.builder()
                .batchId("RUN-20260712-COVERAGE")
                .groupCode("GROUP_CORP")
                .dataDate(TODAY)
                .version("V2")
                .status("COMPLETE")
                .dataClassification("TEST")
                .memberOrgCodes(List.of("001"))
                .quality(BranchDashboardQualityDTO.builder()
                        .expectedSubjects(1).receivedSubjects(1).selectedComplete(true).build())
                .historyCoverage(List.of(BranchDashboardHistoryCoverageDTO.builder()
                        .dataDate(historyDate)
                        .expected(2).received(1)
                        .expectedSubjects(1).receivedSubjects(0)
                        .complete(false)
                        .missingSubjects(List.of("001"))
                        .missing(List.of("001:M_ACTUAL"))
                        .build()))
                .metricContracts(Map.of("M_0001", BranchDashboardMetricContractDTO.builder()
                        .metricCode("M_0001").metricName("存款余额").unit("YUAN").build()))
                .rows(List.of(BranchDashboardBatchRowDTO.builder().orgCode("001")
                        .dataDate(TODAY).metricValues(Map.of("M_0001", BigDecimal.valueOf(300))).build()))
                .historyRows(List.of(BranchDashboardBatchRowDTO.builder().orgCode("001")
                        .dataDate(historyDate).metricValues(Map.of("M_0001", BigDecimal.valueOf(280))).build()))
                .build();
        when(batchApi.latest("GROUP_CORP", List.of("001"))).thenReturn(Optional.of(snapshot));

        ScreenQueryEngine queryEngine = engine(null, mock(OrgApi.class));
        queryEngine.setBatchQueryApi(batchApi);
        ScreenDataReqDTO request = namedGroupRequest("001");
        request.setServerGroupCode("GROUP_CORP");
        request.setPeriod("LAST_1M");
        String config = "{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\",\"metricName\":\"存款余额\",\"slot\":3}],"
                + "\"batchPolicy\":{\"requiredComplete\":true}}";

        ScreenDataRespDTO response = queryEngine.queryAt(
                datasource(config), request, TODAY, 10);

        assertThat(response.getQuality().getDataClassification()).isEqualTo("TEST");
        assertThat(response.getQuality().getHistoryCoverage()).hasSize(1);
        ScreenDataRespDTO.HistoryCoverage coverage = response.getQuality().getHistoryCoverage().get(0);
        assertThat(coverage.getDataDate()).isEqualTo("2026-07-09");
        assertThat(coverage.getExpected()).isEqualTo(2);
        assertThat(coverage.getReceived()).isEqualTo(1);
        assertThat(coverage.getExpectedSubjects()).isEqualTo(1);
        assertThat(coverage.getReceivedSubjects()).isZero();
        assertThat(coverage.getComplete()).isFalse();
        assertThat(coverage.getMissingSubjects()).containsExactly("001");
        assertThat(coverage.getMissing()).containsExactly("001:M_ACTUAL");
    }

    @Test
    void batchPolicy_groupRateUsesContractNumeratorAndDenominatorInsteadOfSummingPercentages() {
        BranchDashboardBatchQueryApi batchApi = mock(BranchDashboardBatchQueryApi.class);
        Map<String, BranchDashboardMetricContractDTO> contracts = new LinkedHashMap<>();
        contracts.put("M_ACTUAL", BranchDashboardMetricContractDTO.builder()
                .metricCode("M_ACTUAL").metricName("实际").unit("YUAN").build());
        contracts.put("M_TARGET", BranchDashboardMetricContractDTO.builder()
                .metricCode("M_TARGET").metricName("目标").unit("YUAN").build());
        contracts.put("M_RATE", BranchDashboardMetricContractDTO.builder()
                .metricCode("M_RATE").metricName("完成率").unit("PERCENT")
                .numeratorMetricCode("M_ACTUAL").denominatorMetricCode("M_TARGET").build());
        BranchDashboardBatchDTO snapshot = BranchDashboardBatchDTO.builder()
                .batchId("RUN-20260710-04").groupCode("GROUP_CORP")
                .dataDate(LocalDate.of(2026, 7, 10)).version("V2").status("COMPLETE")
                .memberOrgCodes(List.of("001", "002"))
                .quality(BranchDashboardQualityDTO.builder()
                        .expectedSubjects(2).receivedSubjects(2).selectedComplete(true).build())
                .metricContracts(contracts)
                .rows(List.of(
                        BranchDashboardBatchRowDTO.builder().orgCode("001")
                                .dataDate(LocalDate.of(2026, 7, 10)).metricValues(Map.of(
                                        "M_ACTUAL", BigDecimal.valueOf(100),
                                        "M_TARGET", BigDecimal.valueOf(200),
                                        "M_RATE", BigDecimal.valueOf(50))).build(),
                        BranchDashboardBatchRowDTO.builder().orgCode("002")
                                .dataDate(LocalDate.of(2026, 7, 10)).metricValues(Map.of(
                                        "M_ACTUAL", BigDecimal.valueOf(300),
                                        "M_TARGET", BigDecimal.valueOf(300),
                                        "M_RATE", BigDecimal.valueOf(100))).build()))
                .build();
        when(batchApi.latest("GROUP_CORP", List.of("001", "002"))).thenReturn(Optional.of(snapshot));

        ScreenQueryEngine queryEngine = engine(null, mock(OrgApi.class));
        queryEngine.setBatchQueryApi(batchApi);
        ScreenDataReqDTO request = namedGroupRequest("001", "002");
        request.setServerGroupCode("GROUP_CORP");
        String config = "{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_RATE\",\"metricName\":\"完成率\",\"slot\":3}],"
                + "\"aggregation\":{\"groupBy\":\"NONE\",\"agg\":\"SUM\"},"
                + "\"batchPolicy\":{\"requiredComplete\":true}}";

        ScreenDataRespDTO response = queryEngine.queryAt(
                datasource(config), request, TODAY, 10);

        assertThat(response.getRows()).hasSize(1);
        assertThat((BigDecimal) response.getRows().get(0).get(0))
                .isEqualByComparingTo("80");
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

    private static String namedOrgQualityConfig(int maxAgeDays) {
        return "{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"metrics\":[{\"metricName\":\"存款余额\",\"slot\":3},"
                + "{\"metricName\":\"贷款余额\",\"slot\":7}],"
                + "\"qualityPolicy\":{\"maxAgeDays\":" + maxAgeDays
                + ",\"requiredComplete\":true},"
                + "\"aggregation\":{\"groupBy\":\"NONE\",\"agg\":\"SUM\"}}";
    }

    private static ScreenDataReqDTO namedGroupRequest(String... codes) {
        ScreenDataReqDTO request = new ScreenDataReqDTO();
        request.setPeriod("LATEST");
        request.setNamedGroup(true);
        request.setServerOrgCodes(Arrays.asList(codes));
        return request;
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

    private static RuntimeJdbcFixture runtimeJdbc(String version, LocalDate dataDate, int received,
                                                  boolean hasBatch, List<String> columns,
                                                  List<List<Object>> values) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preflight = mock(PreparedStatement.class);
        PreparedStatement main = mock(PreparedStatement.class);
        ResultSet batchResult = mock(ResultSet.class);
        ResultSet mainResult = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preflight, main);
        when(preflight.executeQuery()).thenReturn(batchResult);
        when(main.executeQuery()).thenReturn(mainResult);
        if (hasBatch) {
            when(batchResult.next()).thenReturn(true, false);
            when(batchResult.getString(1)).thenReturn(version);
            when(batchResult.getObject(2)).thenReturn(Date.valueOf(dataDate));
            when(batchResult.getInt(3)).thenReturn(received);
            when(batchResult.wasNull()).thenReturn(false);
        } else {
            when(batchResult.next()).thenReturn(false);
        }
        when(mainResult.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            when(metadata.getColumnLabel(i + 1)).thenReturn(columns.get(i));
        }
        Iterator<List<Object>> iterator = values.iterator();
        List<Object>[] current = new List[]{null};
        when(mainResult.next()).thenAnswer(invocation -> {
            if (!iterator.hasNext()) {
                return false;
            }
            current[0] = iterator.next();
            return true;
        });
        when(mainResult.getObject(anyInt())).thenAnswer(invocation ->
                current[0].get(invocation.getArgument(0, Integer.class) - 1));
        return new RuntimeJdbcFixture(dataSource, connection, preflight, main);
    }

    private record RuntimeJdbcFixture(DataSource dataSource, Connection connection,
                                      PreparedStatement preflight, PreparedStatement main) {
    }
}
