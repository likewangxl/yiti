package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceProbeReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenTryRunReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.PerfKpiScheme;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.entity.RptScreenPublishLog;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.ScreenKpiSchemeMapper;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.bank.branch.platform.report.mapper.RptScreenPublishLogMapper;
import com.bank.branch.platform.report.support.ScreenMetricSlotDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.bank.branch.platform.report.dto.resp.ScreenDatasourceRespDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScreenDatasourceService 单测（Mockito，不起 Spring）.
 */
@ExtendWith(MockitoExtension.class)
class ScreenDatasourceServiceTest {

    @Mock private RptScreenDatasourceMapper dsMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private RptScreenMapper screenMapper;
    @Mock private RptScreenPublishLogMapper publishLogMapper;
    @Mock private ScreenQueryEngine engine;
    @Mock private ScreenMetricSlotDao slotDao;
    @Mock private ScreenKpiSchemeMapper kpiSchemeMapper;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private AuditApi auditApi;
    @Mock private ScreenDataScopeGuard scopeGuard;

    private ScreenDatasourceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenDatasourceServiceImpl(dsMapper, blockMapper, engine, slotDao, kpiSchemeMapper,
                currentUserApi, auditApi, scopeGuard);
        ReflectionTestUtils.setField(service, "screenMapper", screenMapper);
        ReflectionTestUtils.setField(service, "publishLogMapper", publishLogMapper);
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    }

    private ScreenDatasourceSaveReqDTO wideReq() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("员工存款");
        req.setBizLine("COMMON");
        req.setSourceKind("WIDE_TABLE");
        req.setConfigJson("{\"table\":\"EMP_INDEX_RESULT\",\"metrics\":[{\"metricCode\":\"M_0001\"}]}");
        req.setReason("测试保存数据源");
        return req;
    }

    /** 直接调用服务层也不能绕过 HTTP DTO 的高危审计原因校验。 */
    @Test
    void save_missingReasonFailsClosedBeforeInsert() {
        when(slotDao.selectByCodes(anyList())).thenReturn(
                List.of(new ScreenMetricSlotDao.MetricSlot("M_0001", "存款余额", 3, "EMP")));

        ScreenDatasourceSaveReqDTO req = wideReq();
        req.setReason(null);
        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43022");
        verify(dsMapper, never()).insert(any(RptScreenDatasource.class));
    }

    /** 配置态试跑缺失原因时不得触发查询引擎，避免把审计绕过变成只读数据泄露。 */
    @Test
    void tryRun_missingReasonFailsClosedBeforeEngine() {
        ScreenTryRunReqDTO req = new ScreenTryRunReqDTO();
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("SINGLE");
        req.setConfigJson("{\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK\",\"dateCol\":null}");

        assertThatThrownBy(() -> service.tryRun(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43022");
        verify(engine, never()).tryRun(any(), any(), any());
    }

    /** 保存审计必须落真实 CONFIG 动作、保留 timeParamJson 和调用方理由。 */
    @Test
    void save_auditIsStructuredConfigAndIncludesTimeParamJson() {
        when(slotDao.selectByCodes(anyList())).thenReturn(
                List.of(new ScreenMetricSlotDao.MetricSlot("M_0001", "存款余额", 3, "EMP")));
        ScreenDatasourceSaveReqDTO req = wideReq();
        req.setReason("新增存款指标数据源");
        req.setTimeParamJson("[{\"period\":\"MONTH\"}]");

        service.save(req);

        ArgumentCaptor<com.bank.branch.platform.governance.api.dto.AuditLogCmd> audit =
                ArgumentCaptor.forClass(com.bank.branch.platform.governance.api.dto.AuditLogCmd.class);
        verify(auditApi).log(audit.capture());
        assertThat(audit.getValue().getBizAction()).isEqualTo("CONFIG");
        assertThat(audit.getValue().getReason()).isEqualTo("新增存款指标数据源");
        assertThat(audit.getValue().getAfterSnapshot()).contains("timeParamJson");
    }

    @Test
    void save_wideTable_translatesSlotsAndForcesTimeseries() {
        when(slotDao.selectByCodes(anyList())).thenReturn(
                List.of(new ScreenMetricSlotDao.MetricSlot("M_0001", "存款余额", 3, "EMP")));

        service.save(wideReq());

        ArgumentCaptor<RptScreenDatasource> cap = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(dsMapper).insert(cap.capture());
        RptScreenDatasource saved = cap.getValue();
        assertThat(saved.getDsType()).isEqualTo("TIMESERIES");
        assertThat(saved.getDsCode()).startsWith("SCRDS_");
        assertThat(saved.getCreatedBy()).isEqualTo("E001");
        assertThat(saved.getConfigJson()).contains("\"slot\":3");
        assertThat(saved.getConfigJson()).contains("\"metricName\":\"存款余额\"");
        assertThat(saved.getConfigJson()).contains("\"subjectParam\":\"empId\"");
    }

    /** 状态是存储安全边界，大小写输入必须规范化；未知状态不得写入后在运行期绕过 DISABLED。 */
    @Test
    void save_statusIsStrictAndCanonicalized() {
        when(slotDao.selectByCodes(anyList())).thenReturn(
                List.of(new ScreenMetricSlotDao.MetricSlot("M_0001", "存款余额", 3, "EMP")));
        ScreenDatasourceSaveReqDTO lowerCase = wideReq();
        lowerCase.setStatus("active");

        service.save(lowerCase);

        ArgumentCaptor<RptScreenDatasource> saved = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(dsMapper).insert(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("ACTIVE");

        ScreenDatasourceSaveReqDTO illegal = wideReq();
        illegal.setStatus("PENDING");
        assertThatThrownBy(() -> service.save(illegal))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_wideTable_unknownMetric_throws43009() {
        when(slotDao.selectByCodes(anyList())).thenReturn(List.of());
        assertThatThrownBy(() -> service.save(wideReq()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
        verify(dsMapper, never()).insert(any(RptScreenDatasource.class));
    }

    @Test
    void save_wideTable_dimMismatch_throws43009() {
        when(slotDao.selectByCodes(anyList())).thenReturn(
                List.of(new ScreenMetricSlotDao.MetricSlot("M_0001", "存款余额", 3, "ORG")));
        assertThatThrownBy(() -> service.save(wideReq()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_kpi_illegalCycleType_throws43009() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("KPI");
        req.setSourceKind("KPI_RESULT");
        req.setConfigJson("{\"cycleType\":\"HOURLY\"}");
        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_customSql_timeseriesWithoutDateCol_throws43003() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("趋势SQL");
        req.setBizLine("COMMON");
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("TIMESERIES");
        req.setConfigJson("{\"sql\":\"SELECT 1\",\"dateCol\":null}");
        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43003");
    }

    @Test
    void save_customSql_invalidSql_propagates43002() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("坏SQL");
        req.setBizLine("COMMON");
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("SINGLE");
        req.setConfigJson("{\"sql\":\"SELECT * FROM PT_USER\",\"dateCol\":null}");
        doThrow(new RptException(RptErrorCode.SCREEN_DS_SQL_INVALID))
                .when(engine).validateCustomSql("SELECT * FROM PT_USER");
        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
    }

    @Test
    void save_customSql_ok_audits() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("流程数");
        req.setBizLine("COMMON");
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("SINGLE");
        req.setConfigJson("{\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK\",\"dateCol\":null}");
        req.setReason("配置大屏流程组件");

        service.save(req);

        verify(dsMapper).insert(any(RptScreenDatasource.class));
        verify(auditApi).log(any());
    }

    // ===== KPI_DETAIL =====

    private ScreenDatasourceSaveReqDTO kpiDetailReq(String configJson) {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("KPI细项");
        req.setBizLine("COMMON");
        req.setSourceKind("KPI_DETAIL");
        req.setConfigJson(configJson);
        req.setReason("测试保存 KPI 细项数据源");
        return req;
    }

    @Test
    void save_kpiDetailSnapshot_forcesSingleDsType() {
        when(kpiSchemeMapper.selectCount(any())).thenReturn(1L);
        service.save(kpiDetailReq(
                "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"EMP\",\"mode\":\"SNAPSHOT\"}"));

        ArgumentCaptor<RptScreenDatasource> cap = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(dsMapper).insert(cap.capture());
        assertThat(cap.getValue().getSourceKind()).isEqualTo("KPI_DETAIL");
        // SNAPSHOT 保存时强制 ds_type=SINGLE
        assertThat(cap.getValue().getDsType()).isEqualTo("SINGLE");
        assertThat(cap.getValue().getConfigJson()).contains("\"schemeCode\":\"KPI_2026_STD\"");
    }

    @Test
    void save_kpiDetailTrend_forcesTimeseriesDsType() {
        when(kpiSchemeMapper.selectCount(any())).thenReturn(1L);
        service.save(kpiDetailReq(
                "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"ORG\",\"mode\":\"TREND\","
                        + "\"metrics\":[{\"metricCode\":\"M_D1\",\"metricName\":\"存款细项\"}]}"));

        ArgumentCaptor<RptScreenDatasource> cap = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(dsMapper).insert(cap.capture());
        // TREND 保存时强制 ds_type=TIMESERIES
        assertThat(cap.getValue().getDsType()).isEqualTo("TIMESERIES");
    }

    @Test
    void save_kpiDetail_blankSchemeCode_throws43009() {
        assertThatThrownBy(() -> service.save(kpiDetailReq(
                "{\"schemaVersion\":2,\"subjectType\":\"EMP\",\"mode\":\"SNAPSHOT\"}")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
        verify(dsMapper, never()).insert(any(RptScreenDatasource.class));
    }

    @Test
    void save_kpiDetail_schemeNotActive_throws43009() {
        // 方案不存在或非 ACTIVE：PERF_KPI_SCHEME 命中数为 0
        when(kpiSchemeMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.save(kpiDetailReq(
                "{\"schemaVersion\":2,\"schemeCode\":\"KPI_GONE\",\"subjectType\":\"EMP\",\"mode\":\"SNAPSHOT\"}")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
        verify(dsMapper, never()).insert(any(RptScreenDatasource.class));
    }

    @Test
    void save_kpiDetail_illegalSubjectType_throws43009() {
        when(kpiSchemeMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.save(kpiDetailReq(
                "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"CUST\",\"mode\":\"SNAPSHOT\"}")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_kpiDetail_illegalMode_throws43009() {
        when(kpiSchemeMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.save(kpiDetailReq(
                "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"EMP\",\"mode\":\"REALTIME\"}")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_kpiDetail_trendWithoutMetrics_throws43009() {
        when(kpiSchemeMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.save(kpiDetailReq(
                "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"EMP\",\"mode\":\"TREND\"}")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_kpiDetail_trendMetricMissingName_throws43009() {
        // metricName 由前端传入快照，保存时后端非空校验
        when(kpiSchemeMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.save(kpiDetailReq(
                "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"EMP\",\"mode\":\"TREND\","
                        + "\"metrics\":[{\"metricCode\":\"M_D1\"}]}")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_kpiDetail_dsTypeMismatch_throws43009() {
        // SNAPSHOT 只能是 SINGLE，显式传 TIMESERIES 属违规
        when(kpiSchemeMapper.selectCount(any())).thenReturn(1L);
        ScreenDatasourceSaveReqDTO req = kpiDetailReq(
                "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"EMP\",\"mode\":\"SNAPSHOT\"}");
        req.setDsType("TIMESERIES");
        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_kpiDetail_illegalValueCol_throws43009() {
        when(kpiSchemeMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.save(kpiDetailReq(
                "{\"schemaVersion\":2,\"schemeCode\":\"KPI_2026_STD\",\"subjectType\":\"EMP\",\"mode\":\"TREND\","
                        + "\"valueCol\":\"weight\","
                        + "\"metrics\":[{\"metricCode\":\"M_D1\",\"metricName\":\"存款细项\"}]}")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void listKpiSchemes_mapsCodeAndName() {
        PerfKpiScheme s = new PerfKpiScheme();
        s.setSchemeCode("KPI_2026_STD");
        s.setSchemeName("2026标准方案");
        when(kpiSchemeMapper.selectList(any())).thenReturn(List.of(s));

        var result = service.listKpiSchemes();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSchemeCode()).isEqualTo("KPI_2026_STD");
        assertThat(result.get(0).getSchemeName()).isEqualTo("2026标准方案");
    }

    @Test
    void delete_referenced_throws43007() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setId(9L);
        when(dsMapper.selectById(9L)).thenReturn(ds);
        when(blockMapper.countByDsId(9L)).thenReturn(2);
        assertThatThrownBy(() -> service.delete(9L, "验证已引用数据源"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43007");
        verify(dsMapper, never()).deleteById(9L);
    }

    @Test
    void delete_unreferenced_logicDeletes() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setId(9L);
        when(dsMapper.selectById(9L)).thenReturn(ds);
        when(blockMapper.countByDsId(9L)).thenReturn(0);
        service.delete(9L, "重复配置清理");
        verify(dsMapper).deleteById(9L);
    }

    /** 删除审计动作必须是 DELETE 而不是内部事件名。显式 reason URL 契约由 controller 测试守护。 */
    @Test
    void delete_withReasonWritesDeleteAudit() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setId(9L);
        ds.setDsCode("SCRDS_9");
        ds.setDsName("待删除数据源");
        ds.setStatus("ACTIVE");
        when(dsMapper.selectById(9L)).thenReturn(ds);
        when(blockMapper.countByDsId(9L)).thenReturn(0);

        service.delete(9L, "重复配置清理");

        ArgumentCaptor<com.bank.branch.platform.governance.api.dto.AuditLogCmd> audit =
                ArgumentCaptor.forClass(com.bank.branch.platform.governance.api.dto.AuditLogCmd.class);
        verify(auditApi).log(audit.capture());
        assertThat(audit.getValue().getBizAction()).isEqualTo("DELETE");
        assertThat(audit.getValue().getReason()).isEqualTo("重复配置清理");
        assertThat(audit.getValue().getRequestMethod()).isEqualTo("DELETE");
    }

    @Test
    void queryData_notFoundOrDisabled_throws43001() {
        when(dsMapper.selectById(1L)).thenReturn(null);
        when(screenMapper.selectList(any())).thenReturn(List.of(legacyRuntimeScreen(1L)),
                List.of(legacyRuntimeScreen(2L)));
        ScreenDataReqDTO missingReq = legacyRuntimeRequest(1L);
        assertThatThrownBy(() -> service.queryData(missingReq))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43001");

        RptScreenDatasource disabled = new RptScreenDatasource();
        disabled.setId(2L);
        disabled.setStatus("DISABLED");
        when(dsMapper.selectById(2L)).thenReturn(disabled);
        ScreenDataReqDTO disabledReq = legacyRuntimeRequest(2L);
        assertThatThrownBy(() -> service.queryData(disabledReq))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43001");
    }

    // ===== DATA_SCOPE 守卫编排（spec 2026-07-17 §4）=====

    /** /api/screen/data 执行前必须过 ScreenDataScopeGuard：拒绝时 43013 透传且不触发引擎执行. */
    @Test
    void queryData_scopeDenied_throws43013AndSkipsEngine() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setId(3L);
        ds.setStatus("ACTIVE");
        ds.setSourceKind("WIDE_TABLE");
        when(dsMapper.selectById(3L)).thenReturn(ds);
        when(screenMapper.selectList(any())).thenReturn(List.of(legacyRuntimeScreen(3L)));
        doThrow(new RptException(RptErrorCode.SCREEN_DATA_SCOPE_DENIED))
                .when(scopeGuard).check(any(), any());

        ScreenDataReqDTO req = legacyRuntimeRequest(3L);
        assertThatThrownBy(() -> service.queryData(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43013");
        verify(engine, never()).query(any(), any());
    }

    /** 守卫放行后才执行引擎取数（先 check 后 query 的编排顺序）. */
    @Test
    void queryData_scopePassed_checksGuardThenQueries() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setId(3L);
        ds.setStatus("ACTIVE");
        when(dsMapper.selectById(3L)).thenReturn(ds);
        when(screenMapper.selectList(any())).thenReturn(List.of(legacyRuntimeScreen(3L)));
        when(engine.query(any(), any())).thenReturn(new ScreenDataRespDTO(List.of("c"), List.of()));

        ScreenDataReqDTO req = legacyRuntimeRequest(3L);
        service.queryData(req);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(scopeGuard, engine);
        order.verify(scopeGuard).check(any(), any());
        order.verify(engine).query(any(), any());
    }

    // ===== scopeMode（spec 2026-07-17 §4，config_json 可选 SUBJECT|GLOBAL）=====

    @Test
    void save_scopeMode_illegalValue_throws43009() {
        // 槽位翻译先 mock 成功，确保 43009 只能来自 scopeMode 枚举校验（区分度）；
        // scopeMode 校验位于分派前，实现后 slotDao 不再被调用，故用 lenient
        lenient().when(slotDao.selectByCodes(anyList())).thenReturn(
                List.of(new ScreenMetricSlotDao.MetricSlot("M_0001", "存款余额", 3, "EMP")));
        assertThatThrownBy(() -> service.save(wideReqWith("{\"table\":\"EMP_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\"}],\"scopeMode\":\"BOGUS\"}")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
        verify(dsMapper, never()).insert(any(RptScreenDatasource.class));
    }

    @Test
    void save_scopeMode_global_preservedThroughWideTableRewrite() {
        // WIDE_TABLE 保存时重写 config，scopeMode 必须随重写落库（否则执行期 Guard 判定丢失）
        mockSlotEmp();
        service.save(wideReqWith("{\"table\":\"EMP_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\"}],"
                + "\"aggregation\":{\"groupBy\":\"NONE\",\"agg\":\"SUM\"},\"scopeMode\":\"GLOBAL\"}"));

        ArgumentCaptor<RptScreenDatasource> cap = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(dsMapper).insert(cap.capture());
        assertThat(cap.getValue().getConfigJson()).contains("\"scopeMode\":\"GLOBAL\"");
    }

    @Test
    void save_scopeMode_onKpiResult_illegalValueAlsoValidated_throws43009() {
        // scopeMode 校验对全 source_kind 生效（与 fieldMeta 同位）
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("KPI");
        req.setSourceKind("KPI_RESULT");
        req.setConfigJson("{\"cycleType\":\"MONTHLY\",\"scopeMode\":\"WORLD\"}");
        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void tryRun_delegatesToEngineWithLimit10_andAudits() {
        ScreenTryRunReqDTO req = new ScreenTryRunReqDTO();
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("SINGLE");
        req.setConfigJson("{\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK\",\"dateCol\":null}");
        req.setContextParams(Map.of("orgCode", "610100"));
        req.setReason("试跑");
        when(engine.tryRun(any(), any(), any())).thenReturn(new ScreenDataRespDTO(List.of("cnt"), List.of()));

        ScreenDataRespDTO resp = service.tryRun(req);

        assertThat(resp.getColumns()).containsExactly("cnt");
        verify(engine).tryRun(any(), any(), any());
        verify(auditApi).log(any());
    }

    @Test
    void tryRun_engineThrows_stillAudits() {
        ScreenTryRunReqDTO req = new ScreenTryRunReqDTO();
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("SINGLE");
        req.setConfigJson("{\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK\",\"dateCol\":null}");
        req.setReason("试跑失败场景");
        doThrow(new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED))
                .when(engine).tryRun(any(), any(), any());

        assertThatThrownBy(() -> service.tryRun(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43008");
        // 修复①验证点：即使 engine.tryRun 执行抛异常，safelyAudit 仍需在 finally 中留痕
        verify(auditApi).log(any());
    }

    /** 设计器列探测只能使用已保存数据源和独立管理审计，不得借运行时 schema1 路径。 */
    @Test
    void probeColumns_usesSavedDatasourceAndWritesDedicatedStructuredAudit() {
        RptScreenDatasource saved = new RptScreenDatasource();
        saved.setId(72L);
        saved.setDsCode("SCRDS_72");
        saved.setDsName("机构指标");
        saved.setDsType("SINGLE");
        saved.setSourceKind("WIDE_TABLE");
        saved.setBizLine("COMMON");
        saved.setStatus("ACTIVE");
        saved.setConfigJson("{\"scopeMode\":\"SUBJECT\",\"table\":\"ORG_INDEX_RESULT\",\"subjectCol\":\"org_code\"}");
        when(dsMapper.selectById(72L)).thenReturn(saved);
        when(engine.tryRun(any(), any(), any())).thenReturn(new ScreenDataRespDTO(List.of("余额"), List.of()));
        ScreenDatasourceProbeReqDTO req = new ScreenDatasourceProbeReqDTO();
        req.setReason("设计器绑定前核对列");

        ScreenDataRespDTO response = service.probeColumns(72L, req);

        assertThat(response.getColumns()).containsExactly("余额");
        ArgumentCaptor<com.bank.branch.platform.governance.api.dto.AuditLogCmd> audit =
                ArgumentCaptor.forClass(com.bank.branch.platform.governance.api.dto.AuditLogCmd.class);
        verify(auditApi).log(audit.capture());
        assertThat(audit.getValue().getTargetType()).isEqualTo("RPT_SCREEN_DATASOURCE");
        assertThat(audit.getValue().getTargetId()).isEqualTo("72");
        assertThat(audit.getValue().getResourceUrl())
                .isEqualTo("/api/screen/admin/datasources/72/probe-columns");
        assertThat(audit.getValue().getReason()).isEqualTo("设计器绑定前核对列");
    }

    @Test
    void update_notFound_throws43001() {
        when(dsMapper.selectById(5L)).thenReturn(null);
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("不存在的数据源");
        req.setSourceKind("KPI_RESULT");
        req.setConfigJson("{\"cycleType\":\"MONTHLY\"}");

        assertThatThrownBy(() -> service.update(5L, req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43001");
        verify(dsMapper, never()).updateById(any(RptScreenDatasource.class));
    }

    @Test
    void update_keepsDsCodeUnchanged() {
        RptScreenDatasource existing = new RptScreenDatasource();
        existing.setId(6L);
        existing.setDsCode("SCRDS_OLD");
        when(dsMapper.selectById(6L)).thenReturn(existing);

        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("更新后的名称");
        req.setSourceKind("KPI_RESULT");
        req.setConfigJson("{\"cycleType\":\"QUARTERLY\"}");
        req.setReason("更新数据源定义");

        service.update(6L, req);

        ArgumentCaptor<RptScreenDatasource> cap = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(dsMapper).updateById(cap.capture());
        // dsCode 由 save 时一次性生成，update 不应重新生成/覆盖
        assertThat(cap.getValue().getDsCode()).isEqualTo("SCRDS_OLD");
        assertThat(cap.getValue().getDsName()).isEqualTo("更新后的名称");
    }

    /** 已发布屏快照引用的数据源不得原地改变查询语义，错误必须带完整屏编码。 */
    @Test
    void update_publishedReferencesRejectSemanticChangeAndReturnAllScreenCodes() {
        RptScreenDatasource existing = new RptScreenDatasource();
        existing.setId(6L);
        existing.setDsCode("SCRDS_LOCKED");
        existing.setDsName("季度指标");
        existing.setDsType("TIMESERIES");
        existing.setSourceKind("KPI_RESULT");
        existing.setBizLine("COMMON");
        existing.setStatus("ACTIVE");
        existing.setConfigJson("{\"cycleType\":\"MONTHLY\"}");
        when(dsMapper.selectById(6L)).thenReturn(existing);
        RptScreen alpha = new RptScreen();
        alpha.setId(71L);
        alpha.setScreenCode("SCR_ALPHA");
        alpha.setCanvasPublishedJson("{\"bindSnapshots\":{\"11\":{\"bind\":{\"dsId\":6}}}}");
        RptScreen beta = new RptScreen();
        beta.setId(72L);
        beta.setScreenCode("SCR_BETA");
        beta.setCanvasPublishedJson("{\"bindSnapshots\":{\"12\":{\"bind\":{\"dsId\":6}}}}");
        when(screenMapper.selectList(any())).thenReturn(List.of(beta, alpha));

        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("季度指标");
        req.setSourceKind("KPI_RESULT");
        req.setBizLine("COMMON");
        req.setStatus("ACTIVE");
        req.setConfigJson("{\"cycleType\":\"QUARTERLY\"}");
        req.setReason("尝试改变查询周期");

        assertThatThrownBy(() -> service.update(6L, req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_IN_USE.getCode())
                .hasMessageContaining("SCR_ALPHA")
                .hasMessageContaining("SCR_BETA");
        verify(dsMapper, never()).updateById(any(RptScreenDatasource.class));
    }

    /** 已发布引用仅允许改名称/备注等非查询语义元数据。 */
    @Test
    void update_publishedReferencesAllowsCosmeticMetadataOnlyChange() {
        RptScreenDatasource existing = new RptScreenDatasource();
        existing.setId(6L);
        existing.setDsCode("SCRDS_LOCKED");
        existing.setDsName("旧名称");
        existing.setDsType("TIMESERIES");
        existing.setSourceKind("KPI_RESULT");
        existing.setBizLine("COMMON");
        existing.setStatus("ACTIVE");
        existing.setConfigJson("{\"cycleType\":\"MONTHLY\"}");
        when(dsMapper.selectById(6L)).thenReturn(existing);
        RptScreen screen = new RptScreen();
        screen.setId(71L);
        screen.setScreenCode("SCR_ALPHA");
        screen.setCanvasPublishedJson("{\"bindSnapshots\":{\"11\":{\"bind\":{\"dsId\":6}}}}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));

        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("新名称");
        req.setSourceKind("KPI_RESULT");
        req.setBizLine("COMMON");
        req.setStatus("ACTIVE");
        req.setConfigJson("{\"cycleType\":\"MONTHLY\"}");
        req.setRemark("只改展示说明");
        req.setReason("修正文案");

        service.update(6L, req);

        verify(dsMapper).updateById(any(RptScreenDatasource.class));
    }

    /** 删除时也要扫描归档发布包，不能只按当前草稿 block 或当前发布包判断。 */
    @Test
    void delete_archivedPublishedReferenceRejectsAndReturnsCompleteScreenCode() {
        RptScreenDatasource existing = new RptScreenDatasource();
        existing.setId(6L);
        when(dsMapper.selectById(6L)).thenReturn(existing);
        RptScreen screen = new RptScreen();
        screen.setId(71L);
        screen.setScreenCode("SCR_ARCHIVED");
        screen.setCanvasPublishedJson("{\"bindSnapshots\":{}}");
        RptScreenPublishLog archive = new RptScreenPublishLog();
        archive.setScreenId(71L);
        archive.setSnapshotJson("{\"bindSnapshots\":{\"11\":{\"bind\":{\"dsId\":6}}}}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        when(publishLogMapper.selectList(any())).thenReturn(List.of(archive));

        assertThatThrownBy(() -> service.delete(6L, "验证归档引用"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_IN_USE.getCode())
                .hasMessageContaining("SCR_ARCHIVED");
        verify(dsMapper, never()).deleteById(6L);
    }

    /** 无快照归档无法证明“不引用”任一数据源，因此冻结扫描必须保守拒绝删除并返回该屏编码。 */
    @Test
    void delete_untrustedArchivedPackageFailsClosedForUnknownReference() {
        RptScreenDatasource existing = new RptScreenDatasource();
        existing.setId(6L);
        when(dsMapper.selectById(6L)).thenReturn(existing);
        RptScreen screen = new RptScreen();
        screen.setId(71L);
        screen.setScreenCode("SCR_UNTRUSTED_ARCHIVE");
        screen.setCanvasPublishedJson("{\"components\":[],\"bindSnapshots\":{}}");
        RptScreenPublishLog archive = new RptScreenPublishLog();
        archive.setScreenId(71L);
        archive.setSnapshotJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":11}]}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        when(publishLogMapper.selectList(any())).thenReturn(List.of(archive));

        assertThatThrownBy(() -> service.delete(6L, "验证不可信归档"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_IN_USE.getCode())
                .hasMessageContaining("SCR_UNTRUSTED_ARCHIVE");
        verify(dsMapper, never()).deleteById(6L);
    }

    /** 草稿也引用时不能先返回泛化错误而吞掉已发布引用屏；发布冻结优先级更高。 */
    @Test
    void delete_draftAndPublishedReferencesReturnsAllPublishedScreenCodes() {
        RptScreenDatasource existing = new RptScreenDatasource();
        existing.setId(6L);
        when(dsMapper.selectById(6L)).thenReturn(existing);
        RptScreen alpha = new RptScreen();
        alpha.setId(71L);
        alpha.setScreenCode("SCR_ALPHA");
        alpha.setCanvasPublishedJson("{\"bindSnapshots\":{\"11\":{\"bind\":{\"dsId\":6}}}}");
        RptScreen beta = new RptScreen();
        beta.setId(72L);
        beta.setScreenCode("SCR_BETA");
        beta.setCanvasPublishedJson("{\"bindSnapshots\":{\"12\":{\"bind\":{\"dsId\":6}}}}");
        when(screenMapper.selectList(any())).thenReturn(List.of(beta, alpha));

        assertThatThrownBy(() -> service.delete(6L, "验证当前发布引用"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_IN_USE.getCode())
                .hasMessageContaining("SCR_ALPHA")
                .hasMessageContaining("SCR_BETA");
        verify(dsMapper, never()).deleteById(6L);
    }

    // ===== fieldMeta（spec 2026-07-17 §3.2，全 source_kind 通用）=====

    /** 携带指定 configJson 的宽表保存请求（槽位翻译 mock 为 M_0001 → val_3/EMP） */
    private ScreenDatasourceSaveReqDTO wideReqWith(String configJson) {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("员工存款");
        req.setBizLine("COMMON");
        req.setSourceKind("WIDE_TABLE");
        req.setConfigJson(configJson);
        req.setReason("测试保存宽表数据源");
        return req;
    }

    private void mockSlotEmp() {
        when(slotDao.selectByCodes(anyList())).thenReturn(
                List.of(new ScreenMetricSlotDao.MetricSlot("M_0001", "存款余额", 3, "EMP")));
    }

    /** 构造可验证的 schema1 历史屏：运行请求不能再仅凭客户端 dsId 取数。 */
    private RptScreen legacyRuntimeScreen(Long dsId) {
        RptScreen screen = new RptScreen();
        screen.setId(17L);
        screen.setScreenCode("SCR_LEGACY");
        screen.setStatus("ACTIVE");
        screen.setBizLine("COMMON");
        screen.setOrgScopeMode("LEGACY_CONTEXT");
        screen.setPublishStatus(1);
        screen.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\","
                + "\"blockId\":11}],\"bindSnapshots\":{\"11\":{\"bind\":{\"dsId\":" + dsId + "}}}}");
        return screen;
    }

    private ScreenDataReqDTO legacyRuntimeRequest(Long dsId) {
        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setScreenCode("SCR_LEGACY");
        req.setDsId(dsId);
        return req;
    }

    @Test
    void save_fieldMeta_valid_preservedThroughWideTableRewrite() {
        mockSlotEmp();
        service.save(wideReqWith("{\"table\":\"EMP_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\"}],"
                + "\"fieldMeta\":[{\"col\":\"存款余额\",\"alias\":\"一般性存款\",\"role\":\"METRIC\","
                + "\"unit\":\"万元\",\"decimals\":2},{\"col\":\"data_date\",\"role\":\"DIM\"}]}"));

        ArgumentCaptor<RptScreenDatasource> cap = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(dsMapper).insert(cap.capture());
        // WIDE_TABLE 保存时重写 config，fieldMeta 必须原样带过去（否则执行时 columnsMeta 丢失）
        assertThat(cap.getValue().getConfigJson()).contains("\"fieldMeta\"");
        assertThat(cap.getValue().getConfigJson()).contains("一般性存款");
    }

    @Test
    void save_fieldMeta_illegalRole_throws43009() {
        assertThatThrownBy(() -> service.save(wideReqWith("{\"table\":\"EMP_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\"}],"
                + "\"fieldMeta\":[{\"col\":\"存款余额\",\"role\":\"MEASURE\"}]}")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
        verify(dsMapper, never()).insert(any(RptScreenDatasource.class));
    }

    @Test
    void save_fieldMeta_blankCol_throws43009() {
        assertThatThrownBy(() -> service.save(wideReqWith("{\"table\":\"EMP_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\"}],"
                + "\"fieldMeta\":[{\"col\":\"\",\"role\":\"METRIC\"}]}")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_fieldMeta_duplicateCol_throws43009() {
        assertThatThrownBy(() -> service.save(wideReqWith("{\"table\":\"EMP_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_0001\"}],"
                + "\"fieldMeta\":[{\"col\":\"存款余额\",\"role\":\"METRIC\"},"
                + "{\"col\":\"存款余额\",\"role\":\"DIM\"}]}")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_fieldMeta_onKpiResult_alsoValidated_throws43009() {
        // fieldMeta 对全 source_kind 生效：KPI_RESULT 上的非法 role 一样拦截
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("KPI");
        req.setSourceKind("KPI_RESULT");
        req.setConfigJson("{\"cycleType\":\"MONTHLY\","
                + "\"fieldMeta\":[{\"col\":\"KPI总分\",\"role\":\"X\"}]}");
        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    // ===== WIDE_TABLE aggregation（spec 2026-07-17 §3.3）=====

    private String wideAggCfg(String aggregationJson) {
        return "{\"table\":\"EMP_INDEX_RESULT\",\"metrics\":[{\"metricCode\":\"M_0001\"}],"
                + "\"aggregation\":" + aggregationJson + "}";
    }

    @Test
    void save_wideAgg_groupByDate_forcesTimeseries_andPreservesAggregation() {
        mockSlotEmp();
        service.save(wideReqWith(wideAggCfg("{\"groupBy\":\"DATE\",\"agg\":\"SUM\"}")));

        ArgumentCaptor<RptScreenDatasource> cap = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(dsMapper).insert(cap.capture());
        assertThat(cap.getValue().getDsType()).isEqualTo("TIMESERIES");
        // aggregation 必须随重写后的 config 落库
        assertThat(cap.getValue().getConfigJson()).contains("\"groupBy\":\"DATE\"");
    }

    @Test
    void save_wideAgg_groupByNone_forcesSingle() {
        mockSlotEmp();
        service.save(wideReqWith(wideAggCfg("{\"groupBy\":\"NONE\",\"agg\":\"SUM\"}")));

        ArgumentCaptor<RptScreenDatasource> cap = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(dsMapper).insert(cap.capture());
        assertThat(cap.getValue().getDsType()).isEqualTo("SINGLE");
    }

    @Test
    void save_wideAgg_groupBySubject_forcesSingle() {
        mockSlotEmp();
        service.save(wideReqWith(wideAggCfg("{\"groupBy\":\"SUBJECT\",\"agg\":\"AVG\"}")));

        ArgumentCaptor<RptScreenDatasource> cap = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(dsMapper).insert(cap.capture());
        assertThat(cap.getValue().getDsType()).isEqualTo("SINGLE");
    }

    @Test
    void save_wideAgg_illegalGroupBy_throws43009() {
        mockSlotEmp();
        assertThatThrownBy(() -> service.save(wideReqWith(
                wideAggCfg("{\"groupBy\":\"WEEK\",\"agg\":\"SUM\"}"))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
        verify(dsMapper, never()).insert(any(RptScreenDatasource.class));
    }

    @Test
    void save_wideAgg_illegalAggFunc_throws43009() {
        mockSlotEmp();
        assertThatThrownBy(() -> service.save(wideReqWith(
                wideAggCfg("{\"groupBy\":\"NONE\",\"agg\":\"MEDIAN\"}"))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_wideAgg_filterIllegalCol_throws43009() {
        // val_99 不在该数据源已配置槽位（仅 val_3）
        mockSlotEmp();
        assertThatThrownBy(() -> service.save(wideReqWith(wideAggCfg(
                "{\"groupBy\":\"NONE\",\"agg\":\"SUM\","
                        + "\"filters\":[{\"col\":\"val_99\",\"op\":\"EQ\",\"value\":\"1\"}]}"))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_wideAgg_filterIllegalOp_throws43009() {
        mockSlotEmp();
        assertThatThrownBy(() -> service.save(wideReqWith(wideAggCfg(
                "{\"groupBy\":\"NONE\",\"agg\":\"SUM\","
                        + "\"filters\":[{\"col\":\"emp_id\",\"op\":\"LIKE\",\"value\":\"E%\"}]}"))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void list_filtersByDsTypeAndKeyword() {
        RptScreenDatasource e = new RptScreenDatasource();
        e.setId(7L);
        e.setDsCode("SCRDS_ABC12345");
        e.setDsName("存款趋势-关键指标");
        e.setDsType("TIMESERIES");
        e.setCreatedTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(dsMapper.selectList(any())).thenReturn(List.of(e));

        List<ScreenDatasourceRespDTO> result = service.list("TIMESERIES", "关键");

        assertThat(result).hasSize(1);
        ScreenDatasourceRespDTO dto = result.get(0);
        assertThat(dto.getDsCode()).isEqualTo("SCRDS_ABC12345");
        assertThat(dto.getDsName()).isEqualTo("存款趋势-关键指标");
        assertThat(dto.getDsType()).isEqualTo("TIMESERIES");
        assertThat(dto.getCreatedTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}
