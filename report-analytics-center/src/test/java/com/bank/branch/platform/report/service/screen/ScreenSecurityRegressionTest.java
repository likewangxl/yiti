package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.report.dto.req.CanvasComponentDTO;
import com.bank.branch.platform.report.dto.req.CanvasStyleDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenCanvasMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.bank.branch.platform.report.mapper.RptScreenPublishLogMapper;
import com.bank.branch.platform.report.mapper.ScreenKpiSchemeMapper;
import com.bank.branch.platform.report.support.ScreenMetricSlotDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Sol 安全评审的回归用例。
 *
 * <p>这些用例只钉住 fail-close 边界，不依赖真实数据库；任何“为了兼容而退回 dsId、当前草稿
 * block 或系统管理员旁路”的实现都会重新变红。</p>
 */
@ExtendWith(MockitoExtension.class)
class ScreenSecurityRegressionTest {

    @Mock private RptScreenDatasourceMapper dsMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private ScreenQueryEngine queryEngine;
    @Mock private ScreenMetricSlotDao slotDao;
    @Mock private ScreenKpiSchemeMapper kpiSchemeMapper;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private AuditApi auditApi;
    @Mock private ScreenDataScopeGuard dataScopeGuard;
    @Mock private RptScreenMapper screenMapper;
    @Mock private ScreenScopeAuthorizationService scopeAuthorizationService;
    @Mock private RptScreenCanvasMapper canvasMapper;
    @Mock private RptScreenPublishLogMapper publishLogMapper;

    private ScreenDatasourceServiceImpl datasourceService;
    private ScreenCanvasServiceImpl canvasService;

    @BeforeEach
    void setUp() {
        datasourceService = new ScreenDatasourceServiceImpl(dsMapper, blockMapper, queryEngine, slotDao,
                kpiSchemeMapper, currentUserApi, auditApi, dataScopeGuard);
        ReflectionTestUtils.setField(datasourceService, "screenMapper", screenMapper);
        ReflectionTestUtils.setField(datasourceService, "scopeAuthorizationService", scopeAuthorizationService);

        canvasService = new ScreenCanvasServiceImpl(screenMapper, blockMapper, dsMapper, canvasMapper,
                currentUserApi, publishLogMapper, auditApi);
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E_SECURITY");
    }

    @Test
    void schemaV2RequestWithoutScreenCode_mustNotFallBackToClientDsId() {
        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setDsId(12L);

        assertThatThrownBy(() -> datasourceService.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED.getCode());
        verify(dsMapper, never()).selectById(12L);
    }

    @Test
    void publishedStatusTwo_usesPublishedSnapshotEvenWhenDraftBlockWasRemoved() {
        RptScreen screen = namedScreen(2);
        RptScreenDatasource datasource = safeOrgDatasource(12L);
        ScreenDataRespDTO expected = new ScreenDataRespDTO(List.of("metric"), List.of());
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        lenient().when(scopeAuthorizationService.authorize(screen)).thenReturn(Set.of("128"));
        when(dsMapper.selectById(12L)).thenReturn(datasource);
        when(queryEngine.query(any(), any())).thenReturn(expected);

        ScreenDataReqDTO req = runtimeRequest(11L);
        req.setContextParams(Map.of()); // 本例验证发布身份；不请求越权支行。

        assertThat(datasourceService.queryData(req)).isSameAs(expected);
        verifyNoInteractions(blockMapper);
    }

    @Test
    void publishedSnapshotBlock_mustNotBeRejectedBecauseCurrentDraftBlockBelongsElsewhere() {
        RptScreen screen = namedScreen(1);
        RptScreenDatasource datasource = safeOrgDatasource(12L);
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        when(scopeAuthorizationService.authorize(screen)).thenReturn(Set.of("128"));
        when(dsMapper.selectById(12L)).thenReturn(datasource);
        when(queryEngine.query(any(), any())).thenReturn(new ScreenDataRespDTO(List.of(), List.of()));

        ScreenDataReqDTO req = runtimeRequest(11L);
        req.setContextParams(Map.of()); // 草稿归属与已发布身份隔离，与支行选择正交。
        datasourceService.queryData(req);

        verify(queryEngine).query(any(), any());
        verify(blockMapper, never()).selectById(11L);
    }

    @Test
    void namedGroupRuntime_rejectsKpiDatasourceWithoutSafeOrgCodeMapping() {
        RptScreen screen = namedScreen(1);
        RptScreenDatasource kpi = safeOrgDatasource(12L);
        kpi.setSourceKind("KPI_DETAIL");
        kpi.setConfigJson("{\"scopeMode\":\"SUBJECT\",\"subjectType\":\"EMP\",\"mode\":\"SNAPSHOT\"}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        lenient().when(scopeAuthorizationService.authorize(screen)).thenReturn(Set.of("128"));
        when(dsMapper.selectById(12L)).thenReturn(kpi);

        assertThatThrownBy(() -> datasourceService.queryData(runtimeRequest(11L)))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode());
        verify(queryEngine, never()).query(any(), any());
    }

    @Test
    void saveCanvas_rejectsCrossBizLineDatasourceBeforeItCanBecomeDraft() {
        RptScreen screen = new RptScreen();
        screen.setId(7L);
        screen.setBizLine("CORP");
        screen.setOrgScopeMode("LEGACY_CONTEXT");
        screen.setCanvasVersion(3);
        when(screenMapper.selectById(7L)).thenReturn(screen);

        RptScreenDatasource retail = safeOrgDatasource(12L);
        retail.setBizLine("RETAIL");
        when(dsMapper.selectById(12L)).thenReturn(retail);

        CanvasComponentDTO chart = chart(50L, 12L);
        ScreenCanvasSaveReqDTO req = new ScreenCanvasSaveReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(3);
        req.setCanvasStyle(new CanvasStyleDTO());
        req.setComponents(List.of(chart));

        assertThatThrownBy(() -> canvasService.saveCanvas(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(canvasMapper, never()).bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void saveCanvas_doesNotDeleteOldBlockIdsJustBecauseTheyAreAbsentFromDraft() {
        RptScreen screen = new RptScreen();
        screen.setId(7L);
        screen.setCanvasVersion(0);
        when(screenMapper.selectById(7L)).thenReturn(screen);
        RptScreenBlock publishedBlock = new RptScreenBlock();
        publishedBlock.setId(88L);
        publishedBlock.setScreenId(7L);
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);

        CanvasComponentDTO label = new CanvasComponentDTO();
        label.setId("label");
        label.setComponent("TextLabel");
        label.setStyle(Map.of("top", 0, "left", 0, "width", 100, "height", 40));
        ScreenCanvasSaveReqDTO req = new ScreenCanvasSaveReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(0);
        req.setCanvasStyle(new CanvasStyleDTO());
        req.setComponents(List.of(label));

        canvasService.saveCanvas(req);

        verify(blockMapper, never()).deleteById(88L);
    }

    @Test
    void genericScreenSaveDto_mustNotContainBlockOrRoleMutationFields() {
        Set<String> fieldNames = Arrays.stream(ScreenSaveReqDTO.class.getDeclaredFields())
                .map(Field::getName).collect(java.util.stream.Collectors.toSet());

        assertThat(fieldNames).doesNotContain("blocks", "allowedRoleCodes", "accessRoleCodes", "accessRoles");
    }

    @Test
    void newDatasource_requiresExplicitBizLineInsteadOfDatabaseDefault() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("新数据源");
        req.setSourceKind("WIDE_TABLE");
        req.setConfigJson("{\"table\":\"ORG_INDEX_RESULT\",\"metrics\":[{\"metricCode\":\"M_01\"}]}");
        lenient().when(slotDao.selectByCodes(any())).thenReturn(List.of(
                new ScreenMetricSlotDao.MetricSlot("M_01", "余额", 1, "ORG")));

        assertThatThrownBy(() -> datasourceService.save(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode());
        verify(dsMapper, never()).insert(any(RptScreenDatasource.class));
    }

    @Test
    void namedGroupDatasourceSave_rejectsEmpSubjectEvenWhenItHasADeclaredMarkerMode() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("员工宽表");
        req.setBizLine("COMMON");
        req.setSourceKind("WIDE_TABLE");
        req.setConfigJson("{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"EMP_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"M_01\"}]}");
        when(slotDao.selectByCodes(any())).thenReturn(List.of(
                new ScreenMetricSlotDao.MetricSlot("M_01", "余额", 1, "EMP")));

        assertThatThrownBy(() -> datasourceService.save(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode());
        verify(dsMapper, never()).insert(any(RptScreenDatasource.class));
    }

    private RptScreen namedScreen(int publishStatus) {
        RptScreen screen = new RptScreen();
        screen.setId(7L);
        screen.setScreenCode("SCR_RETAIL");
        screen.setStatus("ACTIVE");
        screen.setBizLine("RETAIL");
        screen.setOrgScopeMode("NAMED_GROUP");
        screen.setOrgGroupCode("ORG_GRP_RETAIL");
        screen.setPublishStatus(publishStatus);
        screen.setCanvasPublishedJson("{\"schemaVersion\":2,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":11}],"
                + "\"bindSnapshots\":{\"11\":{\"bind\":{\"dsId\":12}}}}");
        return screen;
    }

    private RptScreenDatasource safeOrgDatasource(Long id) {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setId(id);
        ds.setStatus("ACTIVE");
        ds.setBizLine("RETAIL");
        ds.setDsType("TIMESERIES");
        ds.setSourceKind("WIDE_TABLE");
        ds.setConfigJson("{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"subjectCol\":\"org_code\",\"metrics\":[{\"metricName\":\"余额\",\"slot\":3}]}");
        return ds;
    }

    private ScreenDataReqDTO runtimeRequest(Long blockId) {
        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(2);
        req.setScreenCode("SCR_RETAIL");
        req.setBlockId(blockId);
        req.setDsId(999L);
        req.setContextParams(Map.of("orgCode", "FORGED"));
        return req;
    }

    private CanvasComponentDTO chart(Long blockId, Long dsId) {
        CanvasComponentDTO chart = new CanvasComponentDTO();
        chart.setId("chart");
        chart.setComponent("ChartWidget");
        chart.setInnerType("METRIC_CARD");
        chart.setBlockId(blockId);
        chart.setBindJson("{\"dsId\":" + dsId + "}");
        chart.setStyle(Map.of("top", 0, "left", 0, "width", 300, "height", 200));
        return chart;
    }
}
