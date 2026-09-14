package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.ResourceApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.entity.RptScreenPublishLog;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.mapper.ScreenKpiSchemeMapper;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.bank.branch.platform.report.mapper.RptScreenPublishLogMapper;
import com.bank.branch.platform.report.support.ScreenMetricSlotDao;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 已引用 RETAIL CODE 屏的数据源编辑引用矩阵纯单测。 */
@ExtendWith(MockitoExtension.class)
class RetailScreenDatasourceServiceTest {

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
    @Mock private ResourceApi resourceApi;

    private ScreenDatasourceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenDatasourceServiceImpl(dsMapper, blockMapper, engine, slotDao, kpiSchemeMapper,
                currentUserApi, auditApi, scopeGuard);
        ReflectionTestUtils.setField(service, "screenMapper", screenMapper);
        ReflectionTestUtils.setField(service, "publishLogMapper", publishLogMapper);
        ReflectionTestUtils.setField(service, "resourceApi", resourceApi);
    }

    @Test
    void updateReferencedRetailCodeDatasourceCannotChangeToCommonLine() {
        RptScreenDatasource existing = datasource(12L, "RETAIL", "MONTHLY");
        RptScreen screen = new RptScreen();
        screen.setId(7L);
        screen.setScreenCode("SCR_RETAIL");
        screen.setBizLine("RETAIL");
        screen.setOrgScopeMode("LEGACY_CONTEXT");
        screen.setCanvasStyleJson("{\"presentation\":{\"type\":\"CODE\","
                + "\"template\":\"retail-overview-v1\"}}");
        RptScreenBlock block = new RptScreenBlock();
        block.setId(31L);
        block.setScreenId(7L);
        block.setBindJson("{\"dsId\":12}");
        when(dsMapper.selectById(12L)).thenReturn(existing);
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(screen));
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(block));

        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("零售指标");
        req.setBizLine("COMMON");
        req.setSourceKind("KPI_RESULT");
        req.setConfigJson("{\"cycleType\":\"QUARTERLY\"}");
        req.setReason("修改零售数据源");

        assertThatThrownBy(() -> service.update(12L, req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(dsMapper, never()).updateById(any(RptScreenDatasource.class));
    }

    @Test
    void updatePublishedRetailDraftBranchStillRejectsCommonDatasource() {
        RptScreenDatasource existing = datasource(12L, "RETAIL", "MONTHLY");
        RptScreen screen = retailReferenceScreen("branch-overview-v1", "retail-overview-v1", false);
        when(dsMapper.selectById(12L)).thenReturn(existing);
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(screen));
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        ScreenDatasourceSaveReqDTO req = datasourceUpdateToCommon();

        assertThatThrownBy(() -> service.update(12L, req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(dsMapper, never()).updateById(any(RptScreenDatasource.class));
    }

    @Test
    void updatePublishedBranchDraftRetailStillChecksDraftRetailReference() {
        RptScreenDatasource existing = datasource(12L, "RETAIL", "MONTHLY");
        RptScreen screen = retailReferenceScreen("retail-overview-v1", "branch-overview-v1", true);
        RptScreenBlock block = new RptScreenBlock();
        block.setId(31L);
        block.setScreenId(7L);
        block.setBindJson("{\"dsId\":12}");
        when(dsMapper.selectById(12L)).thenReturn(existing);
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(screen));
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(block));

        assertThatThrownBy(() -> service.update(12L, datasourceUpdateToCommon()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(dsMapper, never()).updateById(any(RptScreenDatasource.class));
    }

    @Test
    void publishedBranchTemplateDrivesFormalQueryWhenMutableDraftIsRetail() {
        RptScreen screen = retailReferenceScreen("retail-overview-v1", "branch-overview-v1", false);
        RptScreenDatasource datasource = datasource(12L, "COMMON", "MONTHLY");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(screen));
        when(dsMapper.selectById(12L)).thenReturn(datasource);
        when(engine.query(any(), any())).thenReturn(new ScreenDataRespDTO(List.of("value"), List.of()));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setScreenCode("SCR_RETAIL");
        req.setDsId(12L);

        service.queryData(req);

        verify(scopeGuard).check(datasource, req);
        verify(engine).query(datasource, req);
    }

    @Test
    void unrelatedRetailArchiveDoesNotImposeRetailRuleOnBranchDatasourceReference() {
        RptScreenDatasource existing = datasource(12L, "RETAIL", "MONTHLY");
        RptScreen screen = retailReferenceScreen("branch-overview-v1", "branch-overview-v1", false);
        RptScreenPublishLog unrelatedArchive = new RptScreenPublishLog();
        unrelatedArchive.setScreenId(7L);
        unrelatedArchive.setSnapshotJson(publishedPackageWithDatasource("retail-overview-v1", "retailAum", 99L));
        when(dsMapper.selectById(12L)).thenReturn(existing);
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(screen));
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(publishLogMapper.selectList(any(Wrapper.class))).thenReturn(List.of(unrelatedArchive));

        service.update(12L, datasourceUpdateToCommon());

        verify(dsMapper).updateById(any(RptScreenDatasource.class));
    }

    @Test
    void updateReferencedCorporateCodeDatasourceCannotChangeToCommonLine() {
        RptScreenDatasource existing = datasource(12L, "CORP", "MONTHLY");
        RptScreen screen = corporateReferenceScreen();
        RptScreenBlock block = new RptScreenBlock();
        block.setId(31L);
        block.setScreenId(7L);
        block.setBindJson("{\"dsId\":12}");
        when(dsMapper.selectById(12L)).thenReturn(existing);
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(screen));
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(block));

        assertThatThrownBy(() -> service.update(12L, datasourceUpdateToCommon()))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(dsMapper, never()).updateById(any(RptScreenDatasource.class));
    }

    @Test
    void queryCorporateCodeScreenRejectsCommonDatasourceBeforeEngine() {
        RptScreen screen = corporateReferenceScreen();
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(screen));
        when(dsMapper.selectById(12L)).thenReturn(datasource(12L, "COMMON", "MONTHLY"));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setScreenCode("SCR_CORP_OVERVIEW");
        req.setDsId(12L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(engine, never()).query(any(), any());
    }

    private RptScreenDatasource datasource(long id, String bizLine, String cycleType) {
        RptScreenDatasource datasource = new RptScreenDatasource();
        datasource.setId(id);
        datasource.setDsCode("SCRDS_RETAIL");
        datasource.setDsName("零售指标");
        datasource.setBizLine(bizLine);
        datasource.setDsType("TIMESERIES");
        datasource.setSourceKind("KPI_RESULT");
        datasource.setStatus("ACTIVE");
        datasource.setConfigJson("{\"cycleType\":\"" + cycleType + "\"}");
        return datasource;
    }

    private ScreenDatasourceSaveReqDTO datasourceUpdateToCommon() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("零售指标");
        req.setBizLine("COMMON");
        req.setSourceKind("KPI_RESULT");
        req.setConfigJson("{\"cycleType\":\"QUARTERLY\"}");
        req.setReason("修改零售引用数据源");
        return req;
    }

    private RptScreen retailReferenceScreen(String draftTemplate, String publishedTemplate,
                                            boolean draftReferences) {
        RptScreen screen = new RptScreen();
        screen.setId(7L);
        screen.setScreenCode("SCR_RETAIL");
        screen.setStatus("ACTIVE");
        screen.setBizLine("RETAIL");
        screen.setOrgScopeMode("LEGACY_CONTEXT");
        screen.setCanvasStyleJson("{\"presentation\":{\"type\":\"CODE\",\"template\":\""
                + draftTemplate + "\"}}");
        if (draftReferences) {
            screen.setCanvasDraftJson("{\"components\":[{\"component\":\"ChartWidget\","
                    + "\"blockId\":31,\"propValue\":{\"bindingKey\":\"retailAum\"}}]}");
        }
        screen.setCanvasPublishedJson(publishedPackage(publishedTemplate,
                "retail-overview-v1".equals(publishedTemplate) ? "retailAum" : "deposit"));
        return screen;
    }

    private RptScreen corporateReferenceScreen() {
        RptScreen screen = new RptScreen();
        screen.setId(7L);
        screen.setScreenCode("SCR_CORP_OVERVIEW");
        screen.setStatus("ACTIVE");
        screen.setBizLine("CORP");
        screen.setOrgScopeMode("LEGACY_CONTEXT");
        screen.setCanvasStyleJson("{\"presentation\":{\"type\":\"CODE\","
                + "\"template\":\"corporate-overview-v1\"}}");
        screen.setCanvasDraftJson("{\"components\":[{\"component\":\"ChartWidget\","
                + "\"blockId\":31,\"propValue\":{\"bindingKey\":\"corpDeposit\"}}]}");
        screen.setCanvasPublishedJson("{\"schemaVersion\":1,\"canvasStyle\":"
                + screen.getCanvasStyleJson() + ",\"components\":[{\"component\":\"ChartWidget\","
                + "\"blockId\":31,\"propValue\":{\"bindingKey\":\"corpDeposit\"}}],"
                + "\"bindSnapshots\":{\"31\":{\"componentType\":\"CODE\","
                + "\"bind\":{\"dsId\":12},\"styleCfg\":{},\"drill\":{}}}}");
        return screen;
    }

    private String publishedPackage(String template, String bindingKey) {
        return "{\"schemaVersion\":1,\"canvasStyle\":{\"presentation\":{\"type\":\"CODE\","
                + "\"template\":\"" + template + "\"}},\"components\":[{"
                + "\"component\":\"ChartWidget\",\"blockId\":31,"
                + "\"propValue\":{\"bindingKey\":\"" + bindingKey + "\"}}],"
                + "\"bindSnapshots\":{\"31\":{\"componentType\":\"CODE\","
                + "\"bind\":{\"dsId\":12},\"styleCfg\":{},\"drill\":{}}}}";
    }

    private String publishedPackageWithDatasource(String template, String bindingKey, long dsId) {
        return publishedPackage(template, bindingKey).replace("\"dsId\":12", "\"dsId\":" + dsId);
    }
}
