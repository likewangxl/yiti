package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.entity.RptScreenPublishLog;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.bank.branch.platform.report.mapper.RptScreenPublishLogMapper;
import com.bank.branch.platform.report.mapper.ScreenKpiSchemeMapper;
import com.bank.branch.platform.report.support.ScreenMetricSlotDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** schemaVersion=2 取数契约：只按 screenCode+blockId 解析发布快照数据源。 */
@ExtendWith(MockitoExtension.class)
class ScreenDatasourceRuntimeContractTest {

    @Mock private RptScreenDatasourceMapper dsMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private ScreenQueryEngine engine;
    @Mock private ScreenMetricSlotDao slotDao;
    @Mock private ScreenKpiSchemeMapper kpiSchemeMapper;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private AuditApi auditApi;
    @Mock private ScreenDataScopeGuard scopeGuard;
    @Mock private RptScreenMapper screenMapper;
    @Mock private RptScreenPublishLogMapper publishLogMapper;
    @Mock private ScreenScopeAuthorizationService scopeAuthorizationService;

    private ScreenDatasourceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenDatasourceServiceImpl(dsMapper, blockMapper, engine, slotDao,
                kpiSchemeMapper, currentUserApi, auditApi, scopeGuard);
        ReflectionTestUtils.setField(service, "screenMapper", screenMapper);
        ReflectionTestUtils.setField(service, "publishLogMapper", publishLogMapper);
        ReflectionTestUtils.setField(service, "scopeAuthorizationService", scopeAuthorizationService);
    }

    private RptScreen namedScreen() {
        RptScreen screen = new RptScreen();
        screen.setId(7L);
        screen.setScreenCode("SCR_RETAIL");
        screen.setStatus("ACTIVE");
        screen.setBizLine("RETAIL");
        screen.setOrgScopeMode("NAMED_GROUP");
        screen.setOrgGroupCode("GROUP_RETAIL");
        screen.setPublishStatus(1);
        screen.setCanvasPublishedJson("{\"schemaVersion\":2,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":11}],"
                + "\"bindSnapshots\":{\"11\":{\"bind\":{\"dsId\":12}}}}");
        return screen;
    }

    private RptScreenDatasource datasource() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setId(12L);
        ds.setStatus("ACTIVE");
        ds.setSourceKind("WIDE_TABLE");
        ds.setBizLine("RETAIL");
        ds.setConfigJson("{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"subjectCol\":\"org_code\",\"metrics\":[{\"metricName\":\"余额\",\"slot\":3}]}");
        return ds;
    }

    private RptScreenDatasource freeReportDatasource() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setId(12L);
        ds.setStatus("ACTIVE");
        ds.setSourceKind("FREE_REPORT");
        ds.setDsType("SINGLE");
        ds.setBizLine("COMMON");
        ds.setConfigJson("{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\","
                + "\"dataClassification\":\"TEST\",\"profile\":\"BRANCH_OVERVIEW\","
                + "\"batchId\":\"TEST_BRANCH_OPERATING_20260921\"}");
        return ds;
    }

    private RptScreen legacyScreen() {
        RptScreen screen = new RptScreen();
        screen.setId(8L);
        screen.setScreenCode("SCR_LEGACY");
        screen.setStatus("ACTIVE");
        screen.setBizLine("COMMON");
        screen.setOrgScopeMode("LEGACY_CONTEXT");
        screen.setPublishStatus(1);
        screen.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[]}");
        return screen;
    }

    @Test
    void legacyScreenWithoutExplicitV1MustNotAcceptDsId() {
        when(screenMapper.selectList(any())).thenReturn(List.of(legacyScreen()));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setScreenCode("SCR_LEGACY");
        req.setDsId(12L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED.getCode());
        verify(dsMapper, never()).selectById(12L);
    }

    /**
     * v1 不是公开的 dsId 查询协议。即使调用方显式写 schemaVersion=1，也必须先给出可由
     * 服务端查证的屏身份；否则旧设计器探列入口会重新成为任意数据源读取后门。
     */
    @Test
    void schemaV1_withoutScreenCodeRejectsArbitraryDatasourceId() {
        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setDsId(12L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED.getCode());
        verify(dsMapper, never()).selectById(12L);
        verify(engine, never()).query(any(), any());
    }

    /** v1 的 dsId 必须来自该屏可验证的发布/历史绑定，不能只因屏为 legacy 就任意接受。 */
    @Test
    void schemaV1_rejectsDatasourceNotBoundToRequestedScreen() {
        RptScreen screen = legacyScreen();
        screen.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":41}],"
                + "\"bindSnapshots\":{\"41\":{\"bind\":{\"dsId\":12}}}}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setScreenCode("SCR_LEGACY");
        req.setDsId(999L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED.getCode());
        verify(dsMapper, never()).selectById(999L);
        verify(engine, never()).query(any(), any());
    }

    /** 合法 v1 仅在该屏不可变发布 bindSnapshots 明确包含 dsId 时可运行。 */
    @Test
    void schemaV1_acceptsOnlyServerVerifiedPublishedBinding() {
        RptScreen screen = legacyScreen();
        screen.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":41}],"
                + "\"bindSnapshots\":{\"41\":{\"bind\":{\"dsId\":12}}}}");
        RptScreenDatasource ds = datasource();
        ds.setBizLine("COMMON");
        ds.setConfigJson("{\"scopeMode\":\"SUBJECT\",\"table\":\"ORG_INDEX_RESULT\",\"subjectCol\":\"org_code\"}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        when(dsMapper.selectById(12L)).thenReturn(ds);
        when(engine.query(any(), any())).thenReturn(new ScreenDataRespDTO(List.of("c"), List.of()));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setScreenCode("SCR_LEGACY");
        req.setDsId(12L);
        req.setContextParams(java.util.Map.of("orgCode", "ORG_1"));

        service.queryData(req);

        verify(dsMapper).selectById(12L);
        verify(engine).query(any(), any());
    }

    @Test
    void runtimeRejectsFreeReportWhenPublishedCodeCanvasIsLive() {
        RptScreen screen = new RptScreen();
        screen.setId(7L);
        screen.setScreenCode("SCR_BRANCH");
        screen.setStatus("ACTIVE");
        screen.setBizLine("COMMON");
        screen.setOrgScopeMode("NAMED_GROUP");
        screen.setOrgGroupCode("GROUP_BRANCH");
        screen.setPublishStatus(1);
        screen.setCanvasPublishedJson("{\"schemaVersion\":2,\"canvasStyle\":{"
                + "\"dataClassification\":\"LIVE\",\"presentation\":{\"type\":\"CODE\","
                + "\"template\":\"branch-overview-v1\"}},"
                + "\"components\":[{\"component\":\"ChartWidget\",\"blockId\":11}],"
                + "\"bindSnapshots\":{\"11\":{\"bind\":{\"dsId\":12}}}}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        when(dsMapper.selectById(12L)).thenReturn(freeReportDatasource());

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(2);
        req.setScreenCode("SCR_BRANCH");
        req.setBlockId(11L);

        assertThatThrownBy(() -> service.queryData(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
        verify(engine, never()).query(any(), any());
    }

    @Test
    void runtimeRejectsFreeReportWhenPublishedCanvasHasNoCodeOrTestClassification() {
        RptScreen screen = new RptScreen();
        screen.setId(7L);
        screen.setScreenCode("SCR_BRANCH");
        screen.setStatus("ACTIVE");
        screen.setBizLine("COMMON");
        screen.setOrgScopeMode("NAMED_GROUP");
        screen.setOrgGroupCode("GROUP_BRANCH");
        screen.setPublishStatus(1);
        screen.setCanvasPublishedJson("{\"schemaVersion\":2,\"canvasStyle\":{"
                + "\"dataClassification\":\"LIVE\"},"
                + "\"components\":[{\"component\":\"ChartWidget\",\"blockId\":11}],"
                + "\"bindSnapshots\":{\"11\":{\"bind\":{\"dsId\":12}}}}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        when(dsMapper.selectById(12L)).thenReturn(freeReportDatasource());

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(2);
        req.setScreenCode("SCR_BRANCH");
        req.setBlockId(11L);

        assertThatThrownBy(() -> service.queryData(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
        verify(engine, never()).query(any(), any());
    }

    /**
     * 不能以当前可变 block 行为历史身份背书。无可信 bindSnapshots 的任何状态包都必须拒绝，
     * 包括已发布(1)与已发布但草稿待发(2)，避免通过状态切换重新开启 v1 任意数据源路径。
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void schemaV1_withoutImmutableSnapshots_rejectsAllPublishStates(int publishStatus) {
        RptScreen screen = legacyScreen();
        screen.setPublishStatus(publishStatus);
        screen.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":41}]}");
        RptScreenBlock block = new RptScreenBlock();
        block.setId(41L);
        block.setScreenId(screen.getId());
        block.setBindJson("{\"dsId\":12}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        lenient().when(blockMapper.selectById(41L)).thenReturn(block);

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setScreenCode("SCR_LEGACY");
        req.setDsId(12L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
        verify(dsMapper, never()).selectById(12L);
    }

    /** 无 immutable snapshot 的装饰节点包同样是不可信发布历史，而不是可降级的普通未绑定。 */
    @Test
    void schemaV1_rejectsNonChartPublishedNodeClaimingBlockId() {
        RptScreen screen = legacyScreen();
        screen.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"TextLabel\",\"blockId\":41}]}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setScreenCode("SCR_LEGACY");
        req.setDsId(12L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
        verify(dsMapper, never()).selectById(12L);
    }

    /** 同屏草稿 block 不能回填无 snapshot 发布包；当前可变行永远不再参与历史证明。 */
    @Test
    void schemaV1_rejectsSameScreenUnpublishedBlock() {
        RptScreen screen = legacyScreen();
        screen.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":41}]}");
        RptScreenBlock unpublished = new RptScreenBlock();
        unpublished.setId(99L);
        unpublished.setScreenId(screen.getId());
        unpublished.setBindJson("{\"dsId\":12}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        // 用 lenient 保留旧实现会错误扫描同屏草稿的回归夹具；新实现必须完全不读取它。
        lenient().when(blockMapper.selectList(any())).thenReturn(List.of(unpublished));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setScreenCode("SCR_LEGACY");
        req.setDsId(12L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
        verify(dsMapper, never()).selectById(12L);
    }

    /** 跨屏 block 即使恰好绑定同一个 dsId，也不能修复无 snapshot 历史包。 */
    @Test
    void schemaV1_rejectsCrossScreenBlock() {
        RptScreen screen = legacyScreen();
        screen.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":41}]}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setScreenCode("SCR_LEGACY");
        req.setDsId(12L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
        verify(dsMapper, never()).selectById(12L);
    }

    /** archive 不属于当前 runtime 身份；当前包未绑定 ds 时不能借旧 archive 放行。 */
    @Test
    void schemaV1_rejectsBindingOnlyPresentInPublishArchive() {
        RptScreen screen = legacyScreen();
        screen.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[],\"bindSnapshots\":{}}");
        RptScreenPublishLog archive = new RptScreenPublishLog();
        archive.setScreenId(screen.getId());
        archive.setSnapshotJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":41}],"
                + "\"bindSnapshots\":{\"41\":{\"bind\":{\"dsId\":12}}}}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        lenient().when(publishLogMapper.selectList(any())).thenReturn(List.of(archive));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setScreenCode("SCR_LEGACY");
        req.setDsId(12L);
        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED.getCode());
        verify(publishLogMapper, never()).selectList(any());
        verify(dsMapper, never()).selectById(12L);
    }

    /** 无关旧 archive 即使无快照，也不能阻断当前可信发布包的 v1 运行。 */
    @Test
    void schemaV1_currentTrustedBindingDoesNotReadOrDependOnArchive() {
        RptScreen screen = legacyScreen();
        screen.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":41}],"
                + "\"bindSnapshots\":{\"41\":{\"bind\":{\"dsId\":12}}}}");
        RptScreenPublishLog archive = new RptScreenPublishLog();
        archive.setScreenId(screen.getId());
        archive.setSnapshotJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":99}]}");
        RptScreenDatasource ds = datasource();
        ds.setBizLine("COMMON");
        ds.setConfigJson("{\"scopeMode\":\"SUBJECT\",\"table\":\"ORG_INDEX_RESULT\",\"subjectCol\":\"org_code\"}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        lenient().when(publishLogMapper.selectList(any())).thenReturn(List.of(archive));
        when(dsMapper.selectById(12L)).thenReturn(ds);
        when(engine.query(any(), any())).thenReturn(new ScreenDataRespDTO(List.of("c"), List.of()));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setScreenCode("SCR_LEGACY");
        req.setDsId(12L);
        req.setContextParams(java.util.Map.of("orgCode", "ORG_1"));

        service.queryData(req);

        verify(publishLogMapper, never()).selectList(any());
        verify(dsMapper).selectById(12L);
    }

    /** 损坏发布包在运行期必须以明确“不可信快照”错误拒绝。 */
    @Test
    void schemaV1_corruptPublishedSnapshotCannotAuthorizeArbitraryDatasource() {
        RptScreen screen = legacyScreen();
        screen.setCanvasPublishedJson("{");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setScreenCode("SCR_LEGACY");
        req.setDsId(12L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
        verify(dsMapper, never()).selectById(12L);
    }

    /** 同一 screenCode 出现多行时必须 fail-close，不能使用查询结果第一条。 */
    @Test
    void runtimeRejectsDuplicateActiveScreenCode() {
        RptScreen duplicate = legacyScreen();
        duplicate.setId(9L);
        when(screenMapper.selectList(any())).thenReturn(List.of(legacyScreen(), duplicate));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setScreenCode("SCR_LEGACY");
        req.setDsId(12L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_NOT_FOUND.getCode());
        verify(dsMapper, never()).selectById(anyLong());
    }

    @Test
    void namedGroupWithoutMap_stillRequiresScreenCodeAndBlockId() {
        RptScreen screen = namedScreen();
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setScreenCode("SCR_RETAIL");
        req.setDsId(999L); // 旧客户端/篡改值不能触发兼容路径

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED.getCode());
        verify(dsMapper, never()).selectById(999L);
    }

    @Test
    void schemaV2_rejectsCrossScreenOrUnpublishedBlockEvenWhenClientSuppliesDsId() {
        when(screenMapper.selectList(any())).thenReturn(List.of(namedScreen()));
        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setScreenCode("SCR_RETAIL");
        req.setBlockId(99L); // 可能是另一屏草稿块，当前发布快照不含它
        req.setDsId(12L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED.getCode());
        verify(dsMapper, never()).selectById(12L);
        verify(blockMapper, never()).selectById(99L);
    }

    @Test
    void schemaV2_resolvesDatasourceFromPublishedBlock_andIgnoresForgedDsId() {
        RptScreen screen = namedScreen();
        when(scopeAuthorizationService.authorize(any())).thenReturn(Set.of("ORG_1"));
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        when(dsMapper.selectById(12L)).thenReturn(datasource());
        when(engine.query(any(), any())).thenReturn(new ScreenDataRespDTO(List.of("c"), List.of()));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(2);
        req.setScreenCode("SCR_RETAIL");
        req.setBlockId(11L);
        req.setDsId(999L);
        req.setContextParams(java.util.Map.of("orgCode", "ORG_1", "orgGroupCode", "FORGED_GROUP"));

        service.queryData(req);

        verify(dsMapper).selectById(12L);
        verify(dsMapper, never()).selectById(999L);
        verify(blockMapper, never()).selectById(anyLong());
        verify(scopeGuard, never()).check(any(), any());
        ArgumentCaptor<ScreenDataReqDTO> request = ArgumentCaptor.forClass(ScreenDataReqDTO.class);
        verify(engine).query(any(), request.capture());
        assertThat(request.getValue().getServerOrgCodes()).containsExactly("ORG_1");
    }

    @Test
    void runtimeRejectsDatasourceFromAnotherBizLine() {
        RptScreen screen = namedScreen();
        RptScreenDatasource crossLine = datasource();
        crossLine.setBizLine("CORP");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        when(dsMapper.selectById(12L)).thenReturn(crossLine);

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(2);
        req.setScreenCode("SCR_RETAIL");
        req.setBlockId(11L);
        req.setDsId(999L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(engine, never()).query(any(), any());
    }

    /** schema2 只能接受整数 2；null、1、未知版本均不可藉此降级为 v1。 */
    @Test
    void schemaV2_requiresExactVersionTwo() {
        when(screenMapper.selectList(any())).thenReturn(List.of(namedScreen()));

        for (Integer invalidVersion : List.of(1, 3)) {
            ScreenDataReqDTO req = new ScreenDataReqDTO();
            req.setSchemaVersion(invalidVersion);
            req.setScreenCode("SCR_RETAIL");
            req.setBlockId(11L);

            assertThatThrownBy(() -> service.queryData(req))
                    .as("schemaVersion=%s", invalidVersion)
                    .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED.getCode());
        }
        ScreenDataReqDTO nullVersion = new ScreenDataReqDTO();
        nullVersion.setScreenCode("SCR_RETAIL");
        nullVersion.setBlockId(11L);
        assertThatThrownBy(() -> service.queryData(nullVersion))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED.getCode());
        verify(dsMapper, never()).selectById(anyLong());
    }

    /** v2 当前发布包缺少 immutable bindSnapshots 时，也必须给出明确迁移错误而非普通 block 未发布。 */
    @Test
    void schemaV2_currentPackageWithoutSnapshotsFailsClosedWithExplicitSnapshotCode() {
        RptScreen screen = namedScreen();
        screen.setCanvasPublishedJson("{\"schemaVersion\":2,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":11}]}");
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(2);
        req.setScreenCode("SCR_RETAIL");
        req.setBlockId(11L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
        verify(dsMapper, never()).selectById(anyLong());
    }

    /** 运行拒绝不是 logger 事件，必须有可持久化结构化审计目标、请求 URL 与拒绝原因。 */
    @Test
    void runtimeRejectionWritesStructuredAudit() {
        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(1);
        req.setDsId(12L);

        assertThatThrownBy(() -> service.queryData(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED.getCode());

        ArgumentCaptor<AuditLogCmd> audit = ArgumentCaptor.forClass(AuditLogCmd.class);
        verify(auditApi).log(audit.capture());
        assertThat(audit.getValue().getTargetType()).isEqualTo("RPT_SCREEN_RUNTIME");
        assertThat(audit.getValue().getResourceUrl()).isEqualTo("/api/screen/data");
        assertThat(audit.getValue().getReason()).isEqualTo(RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED.getCode());
        assertThat(audit.getValue().getTraceId()).isNotBlank();
    }

    /** 草稿画布删除 block 行后，status=2 仍必须只使用旧发布 bindSnapshots 运行。 */
    @Test
    void draftBlockDeletionDoesNotBreakStatus2PublishedSnapshotRuntime() {
        RptScreen screen = namedScreen();
        screen.setPublishStatus(2);
        when(scopeAuthorizationService.authorize(any())).thenReturn(Set.of("ORG_1"));
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        when(dsMapper.selectById(12L)).thenReturn(datasource());
        when(engine.query(any(), any())).thenReturn(new ScreenDataRespDTO(List.of("c"), List.of()));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(2);
        req.setScreenCode("SCR_RETAIL");
        req.setBlockId(11L);

        service.queryData(req);

        verify(blockMapper, never()).selectById(11L);
        verify(engine).query(any(), any());
    }

    @Test
    void displayRuntimeUsesPublishedDefinitionWhileCurrentDatasourceStillControlsStatusAndPermission() {
        RptScreen screen = namedScreen();
        RptScreenDatasource published = datasource();
        published.setDsType("SINGLE");
        screen.setCanvasPublishedJson(publishedPackageWithDefinition(published));
        RptScreenDatasource current = datasource();
        current.setDsType("SINGLE");
        current.setConfigJson("{\"scopeMode\":\"NAMED_GROUP\",\"table\":\"ORG_INDEX_RESULT\","
                + "\"subjectCol\":\"org_code\",\"metrics\":[{\"metricName\":\"已修改\",\"slot\":99}]}");
        when(scopeAuthorizationService.authorize(any())).thenReturn(Set.of("ORG_1"));
        when(screenMapper.selectList(any())).thenReturn(List.of(screen));
        when(dsMapper.selectById(12L)).thenReturn(current);
        when(engine.query(any(), any())).thenReturn(new ScreenDataRespDTO(List.of("c"), List.of()));

        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setSchemaVersion(2);
        req.setScreenCode("SCR_RETAIL");
        req.setBlockId(11L);
        service.queryData(req);

        ArgumentCaptor<RptScreenDatasource> used = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(engine).query(used.capture(), any());
        assertThat(used.getValue().getConfigJson()).contains("\"slot\":3").doesNotContain("\"slot\":99");
    }

    private String publishedPackageWithDefinition(RptScreenDatasource datasource) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.createObjectNode();
            root.put("schemaVersion", 2);
            var presentation = root.putObject("canvasStyle").putObject("presentation");
            presentation.put("type", "CODE");
            presentation.put("template", "retail-overview-v1");
            presentation.put("displaySchemaVersion", 1);
            var displayComponent = presentation.putObject("display").putArray("components").addObject();
            displayComponent.put("componentId", "retail-deposit");
            displayComponent.put("componentType", "METRIC_CARD");
            displayComponent.put("layoutRegion", "LEFT");
            displayComponent.put("order", 0);
            displayComponent.put("visible", true);
            displayComponent.putObject("text").put("titleMode", "AUTO").put("title", "");
            displayComponent.putObject("format").put("displayUnit", "YUAN").put("decimals", 2);
            displayComponent.putObject("content").put("mainField", "value");
            displayComponent.putObject("interaction").put("action", "NONE");
            displayComponent.putArray("dataRefs").addObject().put("blockId", 11).put("role", "PRIMARY")
                    .put("metricCode", "M1").put("metricName", "余额").put("unit", "YUAN").put("dimension", "ORG");
            root.putArray("components").addObject().put("component", "ChartWidget").put("blockId", 11);
            var snapshot = root.putObject("bindSnapshots").putObject("11");
            snapshot.putObject("bind").put("dsId", 12);
            com.bank.branch.platform.report.service.screen.presentation.PublishedDatasourceDefinition.write(
                    snapshot, datasource, mapper);
            return root.toString();
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
