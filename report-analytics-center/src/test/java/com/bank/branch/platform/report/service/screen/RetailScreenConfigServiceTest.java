package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.report.dto.req.ScreenMetadataUpdateReqDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenCanvasMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapPointMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RETAIL CODE 模板在元数据/快照引用边界的纯单元测试。 */
@ExtendWith(MockitoExtension.class)
class RetailScreenConfigServiceTest {

    @Mock private RptScreenMapper screenMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private RptScreenDatasourceMapper dsMapper;
    @Mock private RptScreenMapPointMapper pointMapper;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private RptScreenCanvasMapper canvasMapper;
    @Mock private AuditApi auditApi;
    @Mock private ScreenScopeAuthorizationService scopeAuthorizationService;

    private ScreenConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenConfigServiceImpl(screenMapper, blockMapper, dsMapper, pointMapper, currentUserApi);
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        ReflectionTestUtils.setField(service, "canvasMapper", canvasMapper);
        ReflectionTestUtils.setField(service, "auditApi", auditApi);
    }

    @Test
    void metadataCannotMoveRetailTemplateToCommonScreen() {
        RptScreen screen = retailScreen(7L, "RETAIL", "LEGACY_CONTEXT");
        when(screenMapper.selectById(7L)).thenReturn(screen);

        ScreenMetadataUpdateReqDTO req = metadataRequest("COMMON", "LEGACY_CONTEXT");

        assertThatThrownBy(() -> service.updateScreenMetadata(7L, req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(canvasMapper, never()).updateMetadataCas(any(RptScreen.class), anyInt(), anyString());
    }

    @Test
    void metadataCannotMoveCorporateTemplateToRetailScreen() {
        RptScreen screen = corporateScreen(7L, "CORP", "LEGACY_CONTEXT");
        when(screenMapper.selectById(7L)).thenReturn(screen);

        ScreenMetadataUpdateReqDTO req = metadataRequest("RETAIL", "LEGACY_CONTEXT");

        assertThatThrownBy(() -> service.updateScreenMetadata(7L, req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(canvasMapper, never()).updateMetadataCas(any(RptScreen.class), anyInt(), anyString());
    }

    @Test
    void metadataCannotKeepRetailTemplateOnCommonDatasource() {
        RptScreen screen = retailScreen(7L, "RETAIL", "LEGACY_CONTEXT");
        RptScreenBlock block = codeBlock(31L, "{\"dsId\":12}");
        RptScreenDatasource datasource = datasource(12L, "COMMON");
        when(screenMapper.selectById(7L)).thenReturn(screen);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(block));
        when(dsMapper.selectBatchIds(any())).thenReturn(List.of(datasource));

        ScreenMetadataUpdateReqDTO req = metadataRequest("RETAIL", "LEGACY_CONTEXT");

        assertThatThrownBy(() -> service.updateScreenMetadata(7L, req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(canvasMapper, never()).updateMetadataCas(any(RptScreen.class), anyInt(), anyString());
    }

    @Test
    void namedGroupRetailRankingCannotForgeOrgIdentityInMetadataSnapshot() {
        RptScreen screen = retailScreen(7L, "RETAIL", "NAMED_GROUP");
        screen.setOrgGroupCode("GROUP_1");
        screen.setCanvasDraftJson("{\"schemaVersion\":2,\"components\":["
                + "{\"component\":\"ChartWidget\",\"blockId\":31,"
                + "\"propValue\":{\"bindingKey\":\"retailRanking\"}}]}");
        RptScreenBlock block = codeBlock(31L, "{\"dsId\":12,\"period\":\"LATEST\","
                + "\"fields\":{\"orgCode\":\"org_name\",\"name\":\"org_name\",\"aum\":\"aum\"},"
                + "\"units\":{\"aum\":\"YUAN\"}}");
        RptScreenDatasource datasource = retailSubjectDatasource(12L);
        when(screenMapper.selectById(7L)).thenReturn(screen);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(block));
        when(dsMapper.selectBatchIds(any())).thenReturn(List.of(datasource));
        doNothing().when(scopeAuthorizationService)
                .validateForSave(any(RptScreen.class), any(), any(boolean.class));
        ReflectionTestUtils.setField(service, "scopeAuthorizationService", scopeAuthorizationService);

        ScreenMetadataUpdateReqDTO req = metadataRequest("RETAIL", "NAMED_GROUP");

        assertThatThrownBy(() -> service.updateScreenMetadata(7L, req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode());
        verify(canvasMapper, never()).updateMetadataCas(any(RptScreen.class), anyInt(), anyString());
    }

    @Test
    void metadataPublishedRetailReferenceCannotUseCommonDatasourceWhenDraftStyleIsBranch() {
        RptScreen screen = retailScreen(7L, "RETAIL", "LEGACY_CONTEXT");
        screen.setCanvasStyleJson(styleJson("branch-overview-v1"));
        screen.setCanvasPublishedJson(publishedPackage("retail-overview-v1", "retailAum", 12L));
        RptScreenDatasource datasource = datasource(12L, "COMMON");
        when(screenMapper.selectById(7L)).thenReturn(screen);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(dsMapper.selectBatchIds(any())).thenReturn(List.of(datasource));

        assertThatThrownBy(() -> service.updateScreenMetadata(7L,
                metadataRequest("RETAIL", "LEGACY_CONTEXT")))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(canvasMapper, never()).updateMetadataCas(any(RptScreen.class), anyInt(), anyString());
    }

    @Test
    void metadataPublishedBranchReferenceUsesPublishedTemplateWhenDraftStyleIsRetail() {
        RptScreen screen = retailScreen(7L, "RETAIL", "LEGACY_CONTEXT");
        screen.setCanvasStyleJson(styleJson("retail-overview-v1"));
        screen.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":[]}");
        screen.setCanvasPublishedJson(publishedPackage("branch-overview-v1", "deposit", 12L));
        RptScreenDatasource datasource = datasource(12L, "COMMON");
        when(screenMapper.selectById(7L)).thenReturn(screen);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(dsMapper.selectBatchIds(any())).thenReturn(List.of(datasource));
        when(canvasMapper.updateMetadataCas(any(RptScreen.class), anyInt(), anyString())).thenReturn(1);

        service.updateScreenMetadata(7L, metadataRequest("RETAIL", "LEGACY_CONTEXT"));

        verify(canvasMapper).updateMetadataCas(any(RptScreen.class), anyInt(), anyString());
    }

    private RptScreen retailScreen(long id, String bizLine, String scopeMode) {
        RptScreen screen = new RptScreen();
        screen.setId(id);
        screen.setScreenCode("SCR_RETAIL");
        screen.setScreenName("零售经营总览");
        screen.setViewLevel("BRANCH");
        screen.setBizLine(bizLine);
        screen.setOrgScopeMode(scopeMode);
        screen.setStatus("ACTIVE");
        screen.setCanvasVersion(4);
        screen.setCanvasStyleJson("{\"presentation\":{\"type\":\"CODE\","
                + "\"template\":\"retail-overview-v1\"}}");
        screen.setCanvasDraftJson("{\"schemaVersion\":2,\"components\":[]}");
        screen.setCanvasPublishedJson("{\"schemaVersion\":2,\"canvasStyle\":"
                + screen.getCanvasStyleJson() + ",\"components\":[],\"bindSnapshots\":{}}");
        return screen;
    }

    private RptScreen corporateScreen(long id, String bizLine, String scopeMode) {
        RptScreen screen = retailScreen(id, bizLine, scopeMode);
        screen.setScreenCode("SCR_CORP_OVERVIEW");
        screen.setScreenName("对公经营总览");
        screen.setCanvasStyleJson("{\"presentation\":{\"type\":\"CODE\","
                + "\"template\":\"corporate-overview-v1\"}}");
        screen.setCanvasPublishedJson("{\"schemaVersion\":2,\"canvasStyle\":"
                + screen.getCanvasStyleJson() + ",\"components\":[],\"bindSnapshots\":{}}");
        return screen;
    }

    private ScreenMetadataUpdateReqDTO metadataRequest(String bizLine, String scopeMode) {
        ScreenMetadataUpdateReqDTO req = new ScreenMetadataUpdateReqDTO();
        req.setScreenName("零售经营总览");
        req.setViewLevel("BRANCH");
        req.setBizLine(bizLine);
        req.setOrgScopeMode(scopeMode);
        req.setExpectedVersion(4);
        req.setReason("零售模板边界测试");
        return req;
    }

    private RptScreenBlock codeBlock(long id, String bindJson) {
        RptScreenBlock block = new RptScreenBlock();
        block.setId(id);
        block.setScreenId(7L);
        block.setComponentType("CODE");
        block.setBindJson(bindJson);
        block.setDrillJson("{}");
        return block;
    }

    private RptScreenDatasource datasource(long id, String bizLine) {
        RptScreenDatasource datasource = new RptScreenDatasource();
        datasource.setId(id);
        datasource.setBizLine(bizLine);
        datasource.setDsType("SINGLE");
        datasource.setSourceKind("WIDE_TABLE");
        datasource.setConfigJson("{\"table\":\"ORG_INDEX_RESULT\",\"metrics\":["
                + "{\"metricCode\":\"AUM\",\"metricName\":\"aum\",\"slot\":1}]}");
        return datasource;
    }

    private RptScreenDatasource retailSubjectDatasource(long id) {
        RptScreenDatasource datasource = datasource(id, "RETAIL");
        datasource.setConfigJson("{\"table\":\"ORG_INDEX_RESULT\",\"subjectCol\":\"org_code\","
                + "\"aggregation\":{\"groupBy\":\"SUBJECT\",\"agg\":\"SUM\"},"
                + "\"metrics\":[{\"metricCode\":\"AUM\",\"metricName\":\"aum\",\"slot\":1}]}");
        return datasource;
    }

    private String styleJson(String template) {
        return "{\"presentation\":{\"type\":\"CODE\",\"template\":\"" + template + "\"}}";
    }

    private String publishedPackage(String template, String bindingKey, long dsId) {
        return "{\"schemaVersion\":1,\"canvasStyle\":" + styleJson(template)
                + ",\"components\":[{\"component\":\"ChartWidget\",\"blockId\":31,"
                + "\"propValue\":{\"bindingKey\":\"" + bindingKey + "\"}}],"
                + "\"bindSnapshots\":{\"31\":{\"componentType\":\"CODE\","
                + "\"bind\":{\"dsId\":" + dsId + "},\"styleCfg\":{},\"drill\":{}}}}";
    }
}
