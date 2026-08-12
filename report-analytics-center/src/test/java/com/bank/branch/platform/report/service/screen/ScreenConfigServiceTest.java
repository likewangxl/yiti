package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.ResourceApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.report.dto.req.ScreenCreateReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenMetadataUpdateReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenViewRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenRenderRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenAccessRole;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.entity.RptScreenMapPoint;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenAccessRoleMapper;
import com.bank.branch.platform.report.mapper.RptScreenCanvasMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapPointMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScreenConfigService 单测（Mockito）.
 */
@ExtendWith(MockitoExtension.class)
class ScreenConfigServiceTest {

    @Mock private RptScreenMapper screenMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private RptScreenDatasourceMapper dsMapper;
    @Mock private RptScreenMapPointMapper pointMapper;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private ScreenScopeAuthorizationService scopeAuthorizationService;
    @Mock private RptScreenCanvasMapper canvasMapper;
    @Mock private RptScreenAccessRoleMapper accessRoleMapper;
    @Mock private AuditApi auditApi;
    @Mock private ResourceApi resourceApi;

    private ScreenConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenConfigServiceImpl(screenMapper, blockMapper, dsMapper, pointMapper, currentUserApi);
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        ReflectionTestUtils.setField(service, "canvasMapper", canvasMapper);
        ReflectionTestUtils.setField(service, "accessRoleMapper", accessRoleMapper);
        ReflectionTestUtils.setField(service, "auditApi", auditApi);
        ReflectionTestUtils.setField(service, "resourceApi", resourceApi);
    }

    private ScreenCreateReqDTO createReq() {
        ScreenCreateReqDTO req = new ScreenCreateReqDTO();
        req.setScreenName("测试屏");
        req.setViewLevel("BRANCH");
        req.setBizLine("COMMON");
        req.setOrgScopeMode("LEGACY_CONTEXT");
        return req;
    }

    private ScreenMetadataUpdateReqDTO metadataReq() {
        ScreenMetadataUpdateReqDTO req = new ScreenMetadataUpdateReqDTO();
        req.setScreenName("范围更新后名称");
        req.setViewLevel("BRANCH");
        req.setBizLine("COMMON");
        req.setOrgScopeMode("LEGACY_CONTEXT");
        req.setExpectedVersion(4);
        req.setReason("调整范围");
        return req;
    }

    private RptScreen configuredScreen(long id) {
        RptScreen screen = new RptScreen();
        screen.setId(id);
        screen.setScreenCode("SCR_EXISTING");
        screen.setScreenName("旧名称");
        screen.setViewLevel("BRANCH");
        screen.setBizLine("COMMON");
        screen.setOrgScopeMode("LEGACY_CONTEXT");
        screen.setStatus("ACTIVE");
        screen.setCanvasVersion(4);
        screen.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":[]}");
        screen.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[],\"bindSnapshots\":{}}");
        return screen;
    }

    @Test
    void createScreen_insertsMetadataOnly_andNeverCreatesBlocks() {
        doAnswer(invocation -> {
            RptScreen inserted = invocation.getArgument(0);
            inserted.setId(71L);
            return 1;
        }).when(screenMapper).insert(any(RptScreen.class));

        Long id = service.createScreen(createReq());

        assertThat(id).isEqualTo(71L);
        verify(screenMapper).insert(any(RptScreen.class));
        org.mockito.Mockito.verifyNoInteractions(blockMapper);
    }

    @Test
    void createScreen_requiresExplicitBusinessLineAndScopeMode() {
        ScreenCreateReqDTO req = createReq();
        req.setBizLine(null);

        assertThatThrownBy(() -> service.createScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
        verify(screenMapper, never()).insert(any(RptScreen.class));
    }

    /** 数据库的 active_screen_code 唯一键负责消除“预检之后并发创建”的竞态。 */
    @Test
    void createScreen_duplicateKeyRaceFailsAsScreenLayoutInvalid() {
        when(screenMapper.insert(any(RptScreen.class))).thenThrow(new DuplicateKeyException("duplicate screen_code"));

        assertThatThrownBy(() -> service.createScreen(createReq()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    /** 屏状态是运行时查找边界：小写需规范入库，任何非 ACTIVE/DISABLED 字面量拒绝。 */
    @Test
    void createScreen_statusIsStrictAndCanonicalized() {
        ScreenCreateReqDTO lower = createReq();
        lower.setStatus("active");
        service.createScreen(lower);

        ArgumentCaptor<RptScreen> inserted = ArgumentCaptor.forClass(RptScreen.class);
        verify(screenMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getStatus()).isEqualTo("ACTIVE");

        ScreenCreateReqDTO invalid = createReq();
        invalid.setStatus("ARCHIVED");
        assertThatThrownBy(() -> service.createScreen(invalid))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    @Test
    void updateMetadata_neverMutatesExistingBlocks() {
        RptScreen existing = configuredScreen(7L);
        when(screenMapper.selectById(7L)).thenReturn(existing);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(canvasMapper.updateMetadataCas(any(RptScreen.class), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(1);

        Long id = service.updateScreenMetadata(7L, metadataReq());

        assertThat(id).isEqualTo(7L);
        // 元数据 CAS 必须是 allowlist SQL，不能 updateById 整实体覆盖草稿/发布包。
        verify(canvasMapper).updateMetadataCas(any(RptScreen.class), org.mockito.ArgumentMatchers.eq(4),
                org.mockito.ArgumentMatchers.eq("E001"));
        verify(screenMapper, never()).updateById(any(RptScreen.class));
        verify(blockMapper, never()).insert(any(RptScreenBlock.class));
        verify(blockMapper, never()).updateById(any(RptScreenBlock.class));
        verify(blockMapper, never()).delete(any(Wrapper.class));
        verify(blockMapper, never()).deleteByScreenIdAndIds(any(), any());
    }

    /** 元数据 CAS 更新同样只能写规范大写状态，缺省时保留历史状态。 */
    @Test
    void updateMetadata_statusIsStrictAndCanonicalized() {
        RptScreen existing = configuredScreen(7L);
        existing.setStatus("ACTIVE");
        when(screenMapper.selectById(7L)).thenReturn(existing);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(canvasMapper.updateMetadataCas(any(RptScreen.class), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
        ScreenMetadataUpdateReqDTO req = metadataReq();
        req.setStatus("disabled");

        service.updateScreenMetadata(7L, req);

        ArgumentCaptor<RptScreen> updated = ArgumentCaptor.forClass(RptScreen.class);
        verify(canvasMapper).updateMetadataCas(updated.capture(), org.mockito.ArgumentMatchers.eq(4),
                org.mockito.ArgumentMatchers.eq("E001"));
        assertThat(updated.getValue().getStatus()).isEqualTo("DISABLED");

        ScreenMetadataUpdateReqDTO invalid = metadataReq();
        invalid.setStatus("PENDING");
        assertThatThrownBy(() -> service.updateScreenMetadata(7L, invalid))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    /** 显式空串是清空 orgGroupCode 的唯一语义；null 仍代表不触碰旧值。 */
    @Test
    void updateMetadata_explicitBlankOrgGroupCodePersistsNullThroughCas() {
        RptScreen existing = configuredScreen(7L);
        existing.setOrgGroupCode("GROUP_OLD");
        when(screenMapper.selectById(7L)).thenReturn(existing);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(canvasMapper.updateMetadataCas(any(RptScreen.class), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
        ScreenMetadataUpdateReqDTO req = metadataReq();
        req.setOrgGroupCode("");

        service.updateScreenMetadata(7L, req);

        ArgumentCaptor<RptScreen> captured = ArgumentCaptor.forClass(RptScreen.class);
        verify(canvasMapper).updateMetadataCas(captured.capture(), org.mockito.ArgumentMatchers.eq(4),
                org.mockito.ArgumentMatchers.eq("E001"));
        assertThat(captured.getValue().getOrgGroupCode()).isNull();
    }

    /** CAS 冲突时绝不能写审计或退回到全实体 updateById。 */
    @Test
    void updateMetadata_casConflictDoesNotWriteOrAudit() {
        RptScreen existing = configuredScreen(7L);
        when(screenMapper.selectById(7L)).thenReturn(existing);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(canvasMapper.updateMetadataCas(any(RptScreen.class), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(0);

        assertThatThrownBy(() -> service.updateScreenMetadata(7L, metadataReq()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43012");
        verify(screenMapper, never()).updateById(any(RptScreen.class));
        verify(auditApi, never()).log(any());
    }

    /** 元数据改变条线/范围时，发布快照的 bindSnapshots 也必须重做矩阵校验。 */
    @Test
    void updateMetadata_rejectsPublishedSnapshotDatasourceOutsideNewBizLine() {
        RptScreen existing = configuredScreen(7L);
        existing.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":101}],"
                + "\"bindSnapshots\":{\"101\":{\"componentType\":\"METRIC_CARD\",\"bind\":{\"dsId\":12},\"drill\":{}}}}");
        when(screenMapper.selectById(7L)).thenReturn(existing);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        RptScreenDatasource retailDatasource = new RptScreenDatasource();
        retailDatasource.setId(12L);
        retailDatasource.setBizLine("RETAIL");
        retailDatasource.setDsType("SINGLE");
        retailDatasource.setSourceKind("WIDE_TABLE");
        when(dsMapper.selectBatchIds(any())).thenReturn(List.of(retailDatasource));
        ScreenMetadataUpdateReqDTO req = metadataReq();
        req.setBizLine("CORP");

        assertThatThrownBy(() -> service.updateScreenMetadata(7L, req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43014");
        verify(canvasMapper, never()).updateMetadataCas(any(RptScreen.class), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString());
    }

    /** 无 bindSnapshots 的历史发布包也不能在元数据变更时被跳过；发布 ChartWidget 必须可证明。 */
    @Test
    void updateMetadata_rejectsLegacyPublishedChartWithoutVerifiableBlockBinding() {
        RptScreen existing = configuredScreen(7L);
        existing.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":101}]}");
        when(screenMapper.selectById(7L)).thenReturn(existing);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateScreenMetadata(7L, metadataReq()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
        verify(canvasMapper, never()).updateMetadataCas(any(RptScreen.class), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString());
    }

    /** 快照包不能用空 bindSnapshots 绕过发布 ChartWidget 的绑定复核。 */
    @Test
    void updateMetadata_rejectsPublishedChartMissingSnapshotBinding() {
        RptScreen existing = configuredScreen(7L);
        existing.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":101}],"
                + "\"bindSnapshots\":{}}");
        when(screenMapper.selectById(7L)).thenReturn(existing);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateScreenMetadata(7L, metadataReq()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
        verify(canvasMapper, never()).updateMetadataCas(any(RptScreen.class), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString());
    }

    /** 元数据变更不允许用当前可变 block 回填无 snapshot 发布包；必须先完成受控迁移/重新发布。 */
    @Test
    void updateMetadata_rejectsLegacyPublishedChartEvenWhenCurrentBlockMatches() {
        RptScreen existing = configuredScreen(7L);
        existing.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":101}]}");
        RptScreenBlock publishedBlock = new RptScreenBlock();
        publishedBlock.setId(101L);
        publishedBlock.setScreenId(7L);
        publishedBlock.setComponentType("METRIC_CARD");
        publishedBlock.setBindJson("{\"dsId\":12}");
        RptScreenDatasource datasource = new RptScreenDatasource();
        datasource.setId(12L);
        datasource.setBizLine("COMMON");
        datasource.setDsType("SINGLE");
        datasource.setSourceKind("WIDE_TABLE");
        when(screenMapper.selectById(7L)).thenReturn(existing);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        // 保留可变行夹具，证明实现不得再读取它作为历史身份迁移材料。
        lenient().when(blockMapper.selectById(101L)).thenReturn(publishedBlock);
        lenient().when(dsMapper.selectBatchIds(any())).thenReturn(List.of(datasource));

        assertThatThrownBy(() -> service.updateScreenMetadata(7L, metadataReq()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
        verify(canvasMapper, never()).updateMetadataCas(any(RptScreen.class), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString());
    }

    /** 高危元数据审计写入失败时必须抛错，使同一事务内的 CAS 回滚。 */
    @Test
    void updateMetadata_auditFailureFailsClosed() {
        RptScreen existing = configuredScreen(7L);
        when(screenMapper.selectById(7L)).thenReturn(existing);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(canvasMapper.updateMetadataCas(any(RptScreen.class), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
        doThrow(new IllegalStateException("audit down")).when(auditApi).log(any());

        assertThatThrownBy(() -> service.updateScreenMetadata(7L, metadataReq()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-50001");
    }

    /** 白名单覆盖保存必须带上更新人，保证审计列与实体映射一致。 */
    @Test
    void saveAccessRoles_setsUpdatedByOnInsertedWhitelistRows() {
        RptScreen existing = configuredScreen(7L);
        when(screenMapper.selectById(7L)).thenReturn(existing);
        when(accessRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(canvasMapper.bumpConfigVersion(7L, 4, "E001")).thenReturn(1);

        service.saveAccessRoleCodes(7L, List.of("ROLE_VIEW"), "调整可见角色", 4);

        ArgumentCaptor<RptScreenAccessRole> captured = ArgumentCaptor.forClass(RptScreenAccessRole.class);
        verify(accessRoleMapper).insert(captured.capture());
        assertThat(captured.getValue().getUpdatedBy()).isEqualTo("E001");
    }

    @Test
    void getViewByCode_notFound_throws43004() {
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.getViewByCode("SCR_NONE"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43004");
    }

    /** screenCode 是运行身份；脏数据重复时页面读取同样不能挑第一条继续。 */
    @Test
    void getViewByCode_duplicateActiveScreenCodeFailsClosed() {
        RptScreen one = configuredScreen(7L);
        RptScreen duplicate = configuredScreen(8L);
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(one, duplicate));

        assertThatThrownBy(() -> service.getViewByCode("SCR_EXISTING"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43004");
    }

    @Test
    void getViewByCode_province_includesActiveMapPoints() {
        RptScreen s = new RptScreen();
        s.setId(7L);
        s.setScreenCode("SCR_PROVINCE");
        s.setScreenName("总览");
        s.setViewLevel("PROVINCE");
        s.setStatus("ACTIVE");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        RptScreenMapPoint p = new RptScreenMapPoint();
        p.setOrgCode("610100");
        p.setOrgName("西安分行");
        p.setLng(new BigDecimal("108.948024"));
        p.setLat(new BigDecimal("34.263161"));
        when(pointMapper.selectList(any(Wrapper.class))).thenReturn(List.of(p));

        ScreenViewRespDTO view = service.getViewByCode("SCR_PROVINCE");

        assertThat(view.getScreen().getViewLevel()).isEqualTo("PROVINCE");
        assertThat(view.getOrgScopeMode()).isEqualTo("LEGACY_CONTEXT");
        assertThat(view.getRuntimeSchemaVersion()).isEqualTo(1);
        assertThat(view.getMapPoints()).hasSize(1);
        assertThat(view.getMapPoints().get(0).getOrgCode()).isEqualTo("610100");
    }

    @Test
    void getRenderByCode_exposesLegacyRuntimeContract() {
        RptScreen s = new RptScreen();
        s.setId(8L);
        s.setScreenCode("SCR_LEGACY_RUNTIME");
        s.setScreenName("旧屏");
        s.setViewLevel("BRANCH");
        s.setStatus("ACTIVE");
        s.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[]}");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));

        ScreenRenderRespDTO render = service.getRenderByCode(s.getScreenCode(), "published");

        assertThat(render.getOrgScopeMode()).isEqualTo("LEGACY_CONTEXT");
        assertThat(render.getRuntimeSchemaVersion()).isEqualTo(1);
    }

    @Test
    void getRenderByCode_namedGroupWithoutMap_advertisesSchema2() {
        RptScreen s = new RptScreen();
        s.setId(9L);
        s.setScreenCode("SCR_NAMED_RUNTIME");
        s.setScreenName("命名组屏");
        s.setViewLevel("BRANCH");
        s.setOrgScopeMode("NAMED_GROUP");
        s.setOrgGroupCode("ORG_GRP_TEST");
        s.setStatus("ACTIVE");
        s.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[]}");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));
        when(scopeAuthorizationService.authorize(s)).thenReturn(Set.of("ORG_1"));
        ReflectionTestUtils.setField(service, "scopeAuthorizationService", scopeAuthorizationService);

        ScreenRenderRespDTO render = service.getRenderByCode(s.getScreenCode(), "published");

        assertThat(render.getOrgScopeMode()).isEqualTo("NAMED_GROUP");
        assertThat(render.getRuntimeSchemaVersion()).isEqualTo(2);
    }

    /** SYS_ADMIN 不能绕过草稿管理资源；拒绝事件必须携带实际 GET URL 与屏目标。 */
    @Test
    void getRenderByCode_draftSystemAdminStillRequiresCanvasReadResourceAndAuditsDenial() {
        RptScreen s = configuredScreen(8L);
        s.setScreenCode("SCR_DRAFT_GUARD");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));
        when(resourceApi.hasResourcePermission("E001", "R_RPT_SCR_CV_GET")).thenReturn(false);

        assertThatThrownBy(() -> service.getRenderByCode("SCR_DRAFT_GUARD", "draft"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_ACCESS_DENIED.getCode());

        verify(resourceApi).hasResourcePermission("E001", "R_RPT_SCR_CV_GET");
        verify(currentUserApi, never()).isSystemAdmin();
        ArgumentCaptor<com.bank.branch.platform.governance.api.dto.AuditLogCmd> audit =
                ArgumentCaptor.forClass(com.bank.branch.platform.governance.api.dto.AuditLogCmd.class);
        verify(auditApi).log(audit.capture());
        assertThat(audit.getValue().getTargetId()).isEqualTo("8");
        assertThat(audit.getValue().getResourceUrl()).isEqualTo("/api/screen/view/SCR_DRAFT_GUARD");
        assertThat(audit.getValue().getRequestMethod()).isEqualTo("GET");
        assertThat(audit.getValue().getReason()).isEqualTo(RptErrorCode.SCREEN_ACCESS_DENIED.getCode());
    }

    @Test
    void saveMapPoints_null_throws43006_withoutDeleting() {
        assertThatThrownBy(() -> service.saveMapPoints(null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
        verify(pointMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void deleteScreen_logicDeletesScreenAndHardDeletesBlocks() {
        RptScreen s = new RptScreen();
        s.setId(7L);
        when(screenMapper.selectById(7L)).thenReturn(s);

        service.deleteScreen(7L);

        verify(screenMapper).deleteById(7L);
        verify(blockMapper).delete(any(Wrapper.class));
    }
}
