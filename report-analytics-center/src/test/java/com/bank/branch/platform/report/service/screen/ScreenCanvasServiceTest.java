package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.CanvasComponentDTO;
import com.bank.branch.platform.report.dto.req.CanvasStyleDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasDiscardReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasSaveReqDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenCanvasMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ScreenCanvasService 保存/加载单测(乐观锁冲突、白名单、归属、上限、范围). */
@ExtendWith(MockitoExtension.class)
class ScreenCanvasServiceTest {

    @Mock private RptScreenMapper screenMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private RptScreenDatasourceMapper dsMapper;
    @Mock private RptScreenCanvasMapper canvasMapper;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private com.bank.branch.platform.report.mapper.RptScreenPublishLogMapper publishLogMapper;
    @Mock private com.bank.branch.platform.governance.api.AuditApi auditApi;
    @Mock private ScreenScopeAuthorizationService scopeAuthorizationService;

    private ScreenCanvasServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenCanvasServiceImpl(screenMapper, blockMapper, dsMapper, canvasMapper, currentUserApi,
                publishLogMapper, auditApi);
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    }

    private RptScreen screen(long id, int version) {
        RptScreen s = new RptScreen();
        s.setId(id);
        s.setScreenCode("SCR_T");
        s.setViewLevel("BRANCH");
        s.setCanvasVersion(version);
        return s;
    }

    private CanvasComponentDTO comp(String component, String innerType, Map<String, Object> style) {
        CanvasComponentDTO c = new CanvasComponentDTO();
        c.setId("w-1");
        c.setComponent(component);
        c.setInnerType(innerType);
        c.setStyle(style);
        return c;
    }

    private ScreenCanvasSaveReqDTO req(long screenId, int expected, CanvasComponentDTO... comps) {
        ScreenCanvasSaveReqDTO r = new ScreenCanvasSaveReqDTO();
        r.setScreenId(screenId);
        r.setExpectedVersion(expected);
        r.setCanvasStyle(new CanvasStyleDTO());
        r.setComponents(List.of(comps));
        return r;
    }

    private ScreenCanvasDiscardReqDTO discardReq(long screenId, int expected) {
        ScreenCanvasDiscardReqDTO req = new ScreenCanvasDiscardReqDTO();
        req.setScreenId(screenId);
        req.setExpectedVersion(expected);
        req.setReason("放弃草稿");
        return req;
    }

    private CanvasComponentDTO boundChart(String innerType, long dsId) {
        CanvasComponentDTO chart = comp("ChartWidget", innerType,
                Map.of("top", 10, "left", 10, "width", 100, "height", 40));
        chart.setBindJson("{\"dsId\":" + dsId + "}");
        return chart;
    }

    private RptScreenDatasource datasource(long id, String dsType, String sourceKind) {
        RptScreenDatasource datasource = new RptScreenDatasource();
        datasource.setId(id);
        datasource.setDsType(dsType);
        datasource.setSourceKind(sourceKind);
        datasource.setBizLine("COMMON");
        datasource.setStatus("ACTIVE");
        return datasource;
    }

    private RptScreenDatasource codeDatasource(long id) {
        RptScreenDatasource datasource = datasource(id, "SINGLE", "WIDE_TABLE");
        datasource.setConfigJson("{\"table\":\"ORG_INDEX_RESULT\",\"subjectCol\":\"org_code\","
                + "\"metrics\":[{\"metricCode\":\"M1\",\"metricName\":\"balance\",\"slot\":1}]}");
        return datasource;
    }

    private RptScreenDatasource m98Datasource(long id, String profile) {
        RptScreenDatasource datasource = datasource(id, "SINGLE", "M98_STAT");
        datasource.setBizLine(profile.startsWith("CORP") ? "CORP" : "RETAIL");
        datasource.setConfigJson("{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\","
                + "\"profile\":\"" + profile + "\",\"mode\":\"SUMMARY\"}");
        return datasource;
    }

    private RptScreenDatasource namedKpiDatasource(long id) {
        RptScreenDatasource datasource = datasource(id, "SINGLE", "KPI_DETAIL");
        datasource.setBizLine("CORP");
        datasource.setConfigJson("{\"schemaVersion\":2,\"scopeMode\":\"NAMED_GROUP\","
                + "\"schemeCode\":\"KPI0724\",\"subjectType\":\"ORG\","
                + "\"mode\":\"SNAPSHOT\"}");
        return datasource;
    }

    private RptScreenBlock publishBlock(long id, String bindJson) {
        RptScreenBlock block = new RptScreenBlock();
        block.setId(id);
        block.setScreenId(7L);
        block.setComponentType("TABLE_LIST");
        block.setBindJson(bindJson);
        block.setStyleJson("{}");
        block.setDrillJson("{}");
        return block;
    }

    @Test
    void save_unknownScreen_throws43004() {
        when(screenMapper.selectById(7L)).thenReturn(null);
        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0,
                comp("TextLabel", null, Map.of("top", 10, "left", 10, "width", 100, "height", 40)))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43004");
    }

    @Test
    void save_unknownComponentType_throws43006() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0,
                comp("EvilWidget", null, Map.of("top", 10, "left", 10, "width", 100, "height", 40)))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    @Test
    void save_negativeCoordinate_throws43006() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0,
                comp("TextLabel", null, Map.of("top", -5, "left", 10, "width", 100, "height", 40)))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    @Test
    void save_versionConflict_throws43012() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 3));
        // 乐观锁自增命中 0 行 = 冲突
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(0);
        assertThatThrownBy(() -> service.saveCanvas(req(7L, 3,
                comp("TextLabel", null, Map.of("top", 10, "left", 10, "width", 100, "height", 40)))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43012");
    }

    @Test
    void save_ok_bumpsVersionAndReturnsDraft() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 3));
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);
        var resp = service.saveCanvas(req(7L, 3,
                comp("TextLabel", null, Map.of("top", 10, "left", 10, "width", 100, "height", 40))));
        org.assertj.core.api.Assertions.assertThat(resp.getCanvasVersion()).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(resp.getCanvasDraftJson()).contains("TextLabel");
        assertThat(resp.getCanvasDraftJson()).contains("\"schemaVersion\":1");
    }

    @Test
    void save_preservesSourcePresentationMetadataInCanvasStyleJson() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 3));
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);
        ScreenCanvasSaveReqDTO request = req(7L, 3,
                comp("TextLabel", null, Map.of("top", 10, "left", 10, "width", 100, "height", 40)));
        CanvasStyleDTO style = new CanvasStyleDTO();
        style.setDataNotice("系统联调数据：当前指标结果含测试计算");
        style.setMetricLabels(Map.of("deposit", "一般性存款余额", "loan", "对公一般性贷款余额"));
        request.setCanvasStyle(style);

        service.saveCanvas(request);

        ArgumentCaptor<String> styleJson = ArgumentCaptor.forClass(String.class);
        verify(canvasMapper).bumpVersion(eq(7L), eq(3), styleJson.capture(), anyString(), anyString());
        assertThat(styleJson.getValue())
                .contains("\"dataNotice\":\"系统联调数据：当前指标结果含测试计算\"")
                .contains("\"deposit\":\"一般性存款余额\"")
                .contains("\"loan\":\"对公一般性贷款余额\"");
    }

    @Test
    void save_namedGroupAlwaysWritesDraftSchemaVersion2() {
        RptScreen namedGroup = screen(7L, 3);
        namedGroup.setOrgScopeMode("NAMED_GROUP");
        when(screenMapper.selectById(7L)).thenReturn(namedGroup);
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);

        var resp = service.saveCanvas(req(7L, 3,
                comp("TextLabel", null, Map.of("top", 10, "left", 10, "width", 100, "height", 40))));

        assertThat(resp.getCanvasDraftJson()).contains("\"schemaVersion\":2");
    }

    @Test
    void save_namedGroupM98SummaryDatasource_isAcceptedBySharedSafetyPolicy() {
        RptScreen namedGroup = screen(7L, 3);
        namedGroup.setBizLine("CORP");
        namedGroup.setOrgScopeMode("NAMED_GROUP");
        when(screenMapper.selectById(7L)).thenReturn(namedGroup);
        when(dsMapper.selectById(3L)).thenReturn(m98Datasource(3L, "CORP_REVENUE"));
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);

        var resp = service.saveCanvas(req(7L, 3, boundChart("METRIC_CARD", 3L)));

        assertThat(resp.getCanvasVersion()).isEqualTo(4);
        assertThat(resp.getCanvasDraftJson()).contains("\\\"dsId\\\":3");
    }

    @Test
    void save_namedGroupKpiOrgSnapshot_targetAndAttentionDatasource_isAccepted() {
        RptScreen namedGroup = screen(7L, 3);
        namedGroup.setBizLine("CORP");
        namedGroup.setOrgScopeMode("NAMED_GROUP");
        when(screenMapper.selectById(7L)).thenReturn(namedGroup);
        when(dsMapper.selectById(3L)).thenReturn(namedKpiDatasource(3L));
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);
        CanvasComponentDTO target = boundChart("KPI_DETAIL_TABLE", 3L);
        CanvasComponentDTO attention = boundChart("KPI_DETAIL_TABLE", 3L);
        attention.setId("w-attention");

        var resp = service.saveCanvas(req(7L, 3, target, attention));

        assertThat(resp.getCanvasVersion()).isEqualTo(4);
        verify(blockMapper, org.mockito.Mockito.times(2)).insert(any(RptScreenBlock.class));
    }

    @Test
    void publish_namedGroupKpiOrgSnapshot_targetAndAttentionBindings_areAccepted() {
        RptScreen screen = screen(7L, 5);
        screen.setBizLine("CORP");
        screen.setOrgScopeMode("NAMED_GROUP");
        screen.setOrgGroupCode("GROUP_1");
        screen.setCanvasStyleJson("{\"schemaVersion\":2,\"presentation\":{\"type\":\"CODE\","
                + "\"template\":\"corporate-overview-v1\"}}");
        String targetBind = "{\"dsId\":3,\"period\":\"LATEST\",\"fields\":{"
                + "\"name\":\"org_name\",\"actual\":\"actual_value\",\"target\":\"target_value\"},"
                + "\"units\":{\"actual\":\"YUAN\",\"target\":\"YUAN\"}}";
        String attentionBind = "{\"dsId\":3,\"period\":\"LATEST\",\"fields\":{"
                + "\"label\":\"attention_label\",\"count\":\"attention_count\"},"
                + "\"units\":{\"count\":\"COUNT\"}}";
        screen.setCanvasDraftJson("{\"schemaVersion\":2,\"components\":["
                + "{\"id\":\"w-target\",\"component\":\"ChartWidget\",\"innerType\":\"TABLE_LIST\","
                + "\"blockId\":1001,\"propValue\":{\"bindingKey\":\"corpTargets\"},"
                + "\"bindJson\":" + new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(targetBind)
                + ",\"style\":{\"top\":0,\"left\":0,\"width\":100,\"height\":100}},"
                + "{\"id\":\"w-attention\",\"component\":\"ChartWidget\",\"innerType\":\"TABLE_LIST\","
                + "\"blockId\":1002,\"propValue\":{\"bindingKey\":\"corpAttention\"},"
                + "\"bindJson\":" + new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(attentionBind)
                + ",\"style\":{\"top\":0,\"left\":0,\"width\":100,\"height\":100}}]}");
        when(screenMapper.selectById(7L)).thenReturn(screen);
        RptScreenBlock target = publishBlock(1001L, targetBind);
        RptScreenBlock attention = publishBlock(1002L, attentionBind);
        when(blockMapper.selectList(any())).thenReturn(List.of(target, attention));
        when(dsMapper.selectById(3L)).thenReturn(namedKpiDatasource(3L));
        when(canvasMapper.applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(1);
        when(publishLogMapper.selectList(any())).thenReturn(List.of());
        org.mockito.Mockito.doNothing().when(scopeAuthorizationService).validatePublishRoles(any(RptScreen.class));
        ReflectionTestUtils.setField(service, "scopeAuthorizationService", scopeAuthorizationService);

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);
        req.setReason("发布 KPI 目标和关注项");

        service.publishCanvas(req);

        verify(canvasMapper).applyPublishedCas(eq(7L), eq(5), anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void save_legacyScreenWithV2MapKeepsDraftSchemaVersion2() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 3));
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);
        CanvasComponentDTO map = comp("MapCenter", null,
                Map.of("top", 96, "left", 640, "width", 640, "height", 880));
        map.setPropValue(Map.of("schemaVersion", 2, "mode", "XIAN_COMPOSITE"));

        var resp = service.saveCanvas(req(7L, 3, map));

        assertThat(resp.getCanvasDraftJson()).contains("\"schemaVersion\":2");
    }

    /** MapCenter(省级屏地图,Task10 渲染层已支持独立渲染分支)必须在组件白名单内可保存,
     * 且不应触发仅对 ChartWidget 生效的 innerType 校验(无 innerType/blockId 也应正常通过). */
    @Test
    void save_mapCenterComponent_acceptedWithoutInnerTypeOrBlockId() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 3));
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);
        var resp = service.saveCanvas(req(7L, 3,
                comp("MapCenter", null, Map.of("top", 96, "left", 640, "width", 640, "height", 880))));
        assertThat(resp.getCanvasVersion()).isEqualTo(4);
        assertThat(resp.getCanvasDraftJson()).contains("MapCenter");
    }

    /** 归属校验:ChartWidget 携带的 blockId 指向他屏 block 行,必须拦截,禁止越权改写. */
    @Test
    void save_blockIdOwnedByOtherScreen_throws43006() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        RptScreenBlock other = new RptScreenBlock();
        other.setId(99L);
        other.setScreenId(888L); // 属于别的屏
        when(blockMapper.selectById(99L)).thenReturn(other);

        CanvasComponentDTO c = comp("ChartWidget", "METRIC_CARD",
                Map.of("top", 10, "left", 10, "width", 100, "height", 40));
        c.setBlockId(99L);

        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0, c)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    /** JSON 上限:draftJson 序列化后超过 2MB 必须拦截(与实现口径一致,按字符数近似). */
    @Test
    void save_draftJsonExceeds2MB_throws43006() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        CanvasComponentDTO c = comp("TextLabel", null,
                Map.of("top", 10, "left", 10, "width", 100, "height", 40));
        // propValue 携带超过 2MB 的字符串,使序列化后的 draftJson 突破上限
        c.setPropValue(Map.of("pad", "x".repeat(2 * 1024 * 1024 + 100)));

        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0, c)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    /** upsert 保 id 契约:既有 blockId 走 update 且 id 不变;新节点(blockId=null)走 insert 并把
     * 生成的新 id 回吐进组件树 —— 禁止先删后插以免 blockId 引用失效. */
    @Test
    void save_upsertKeepsExistingBlockIdAndResolvesNewOne() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);

        RptScreenBlock existing = new RptScreenBlock();
        existing.setId(50L);
        existing.setScreenId(7L);
        when(blockMapper.selectById(50L)).thenReturn(existing);
        // 模拟 MyBatis-Plus insert 副作用:自增主键回填进传入对象
        doAnswer(invocation -> {
            RptScreenBlock e = invocation.getArgument(0);
            e.setId(88L);
            return 1;
        }).when(blockMapper).insert(any(RptScreenBlock.class));

        CanvasComponentDTO existingNode = comp("ChartWidget", "METRIC_CARD",
                Map.of("top", 10, "left", 10, "width", 100, "height", 40));
        existingNode.setBlockId(50L);
        CanvasComponentDTO newNode = comp("ChartWidget", "LINE_TREND",
                Map.of("top", 60, "left", 10, "width", 100, "height", 40));

        var resp = service.saveCanvas(req(7L, 0, existingNode, newNode));

        ArgumentCaptor<RptScreenBlock> updateCaptor = ArgumentCaptor.forClass(RptScreenBlock.class);
        verify(blockMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(50L); // 既有 block 走 update,id 不变
        verify(blockMapper).insert(any(RptScreenBlock.class));      // 新节点走 insert
        verify(blockMapper, never()).deleteById(anyLong());          // 无孤儿,不触发先删后插

        assertThat(existingNode.getBlockId()).isEqualTo(50L);
        assertThat(newNode.getBlockId()).isEqualTo(88L);             // insert 生成的新 id 回吐进组件树
        assertThat(resp.getCanvasDraftJson()).contains("\"blockId\":50").contains("\"blockId\":88");
    }

    /**
     * 组件树是草稿 block 集合的唯一真相：CAS 成功后，缺席的同屏 block 才能被删除。
     * 已发布包只持有 bindSnapshots，不依赖这些草稿行；删除不会重建保留块，也不能触及他屏块。
     */
    @Test
    void save_omittedDraftBlockDeletesOnlyOwnedBlockAfterCas_andEditorNoLongerReturnsIt() {
        RptScreen screen = screen(7L, 3);
        when(screenMapper.selectById(7L)).thenReturn(screen);
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);

        RptScreenBlock retained = new RptScreenBlock();
        retained.setId(50L);
        retained.setScreenId(7L);
        RptScreenBlock omitted = new RptScreenBlock();
        omitted.setId(88L);
        omitted.setScreenId(7L);
        RptScreenBlock otherScreen = new RptScreenBlock();
        otherScreen.setId(99L);
        otherScreen.setScreenId(888L);
        List<RptScreenBlock> draftRows = new ArrayList<>(List.of(retained, omitted, otherScreen));
        when(blockMapper.selectById(50L)).thenReturn(retained);
        // 模拟 Mapper 的 screen_id 查询条件：编辑态只返回当前屏；99 仍留在底层集合以证明未误删。
        when(blockMapper.selectList(any())).thenAnswer(invocation -> draftRows.stream()
                .filter(block -> Long.valueOf(7L).equals(block.getScreenId())).toList());
        doAnswer(invocation -> {
            Long ownerId = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            Set<Long> ids = invocation.getArgument(1);
            draftRows.removeIf(block -> ownerId.equals(block.getScreenId()) && ids.contains(block.getId()));
            return ids.size();
        }).when(blockMapper).deleteByScreenIdAndIds(anyLong(), any());

        CanvasComponentDTO retainedNode = comp("ChartWidget", "METRIC_CARD",
                Map.of("top", 10, "left", 10, "width", 100, "height", 40));
        retainedNode.setBlockId(50L);
        service.saveCanvas(req(7L, 3, retainedNode));

        org.mockito.InOrder order = inOrder(canvasMapper, blockMapper);
        order.verify(canvasMapper).bumpVersion(eq(7L), eq(3), anyString(), anyString(), anyString());
        order.verify(blockMapper).deleteByScreenIdAndIds(7L, Set.of(88L));
        assertThat(retainedNode.getBlockId()).isEqualTo(50L);
        assertThat(draftRows).extracting(RptScreenBlock::getId).contains(99L).doesNotContain(88L);
        assertThat(service.loadCanvas(7L).getBlocks()).extracting(block -> block.getId())
                .containsExactly(50L);
    }

    /** CAS 未命中时不能在失败路径上删除任何草稿 block。 */
    @Test
    void save_omittedDraftBlockOnCasConflict_neverDeletesIt() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 3));
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(0);

        RptScreenBlock existing = new RptScreenBlock();
        existing.setId(50L);
        existing.setScreenId(7L);
        when(blockMapper.selectById(50L)).thenReturn(existing);
        CanvasComponentDTO retainedNode = comp("ChartWidget", "METRIC_CARD",
                Map.of("top", 10, "left", 10, "width", 100, "height", 40));
        retainedNode.setBlockId(50L);

        assertThatThrownBy(() -> service.saveCanvas(req(7L, 3, retainedNode)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43012");
        // 旧实现会在 CAS 前 updateById；现在冲突路径没有任何既有 block 写入或孤儿删除。
        verify(blockMapper, never()).updateById(any(RptScreenBlock.class));
        verify(blockMapper, never()).deleteByScreenIdAndIds(anyLong(), any());
    }

    // ===== Group 成组容器（2026-07-17 画布多选成组，补齐 TDD 缺口）=====

    /** 成组保存：Group.children 里的 ChartWidget 参与 block upsert（insert 生成 id 回吐进子节点），
     * draftJson 序列化时嵌套 children 同步携带 resolved blockId，且组内图表 block 不被孤儿清理误删. */
    @Test
    void save_groupWithChartChild_upsertsChildBlockAndResolvesId() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> {
            RptScreenBlock e = invocation.getArgument(0);
            e.setId(88L);
            return 1;
        }).when(blockMapper).insert(any(RptScreenBlock.class));

        CanvasComponentDTO child = comp("ChartWidget", "METRIC_CARD",
                Map.of("top", 10, "left", 10, "width", 100, "height", 40));
        CanvasComponentDTO group = comp("Group", null,
                Map.of("top", 100, "left", 100, "width", 400, "height", 300));
        group.setChildren(List.of(child));

        var resp = service.saveCanvas(req(7L, 0, group));

        verify(blockMapper).insert(any(RptScreenBlock.class)); // 组内图表占 block 行
        verify(blockMapper, never()).deleteById(anyLong());     // 不被孤儿清理误删
        assertThat(child.getBlockId()).isEqualTo(88L);          // 新 id 回吐进 children 节点
        assertThat(resp.getCanvasDraftJson())
                .contains("\"component\":\"Group\"")
                .contains("\"blockId\":88");
    }

    /** 成组保存：children 里的非法组件类型必须与顶层同样被白名单拦截（摊平校验语义）. */
    @Test
    void save_groupWithIllegalChildComponentType_throws43006() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        CanvasComponentDTO evilChild = comp("EvilWidget", null,
                Map.of("top", 10, "left", 10, "width", 100, "height", 40));
        CanvasComponentDTO group = comp("Group", null,
                Map.of("top", 100, "left", 100, "width", 400, "height", 300));
        group.setChildren(List.of(evilChild));

        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0, group)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
        verify(blockMapper, never()).insert(any(RptScreenBlock.class));
    }

    /** 成组发布：blockId 递归收集覆盖 Group.children，组内图表快照进 bindSnapshots（漏收=线上无数据）. */
    @Test
    void publish_groupChildChartBlockId_collectedIntoBindSnapshots() {
        RptScreen s = screen(7L, 5);
        s.setCanvasStyleJson("{\"schemaVersion\":1}");
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":["
                + "{\"id\":\"g-1\",\"component\":\"Group\","
                + "\"style\":{\"top\":0,\"left\":0,\"width\":800,\"height\":600},\"children\":["
                + "{\"id\":\"w-1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\","
                + "\"blockId\":1001,\"style\":{\"top\":0,\"left\":0,\"width\":100,\"height\":100}}]}]}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        RptScreenBlock b = new RptScreenBlock();
        b.setId(1001L);
        b.setScreenId(7L);
        b.setComponentType("METRIC_CARD");
        b.setBindJson("{\"dsId\":9001,\"period\":\"LATEST\"}");
        when(blockMapper.selectList(any())).thenReturn(List.of(b));
        when(canvasMapper.applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString())).thenReturn(1);
        when(publishLogMapper.selectList(any())).thenReturn(List.of());

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);
        req.setReason("发布成组图表");
        service.publishCanvas(req);

        ArgumentCaptor<String> pkgCaptor = ArgumentCaptor.forClass(String.class);
        verify(canvasMapper).applyPublishedCas(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(5), pkgCaptor.capture(),
                anyString(), anyString(), anyInt(), anyString());
        assertThat(pkgCaptor.getValue())
                .contains("\"component\":\"Group\"")
                .contains("\"bindSnapshots\":{\"1001\":{")
                .doesNotContain("\"1001\":{}");
    }

    /** 成组发布：children 里的 ChartWidget 缺 blockId 视为非法草稿（与顶层同语义）. */
    @Test
    void publish_groupChildChartMissingBlockId_throws43006() {
        RptScreen s = screen(7L, 5);
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":["
                + "{\"id\":\"g-1\",\"component\":\"Group\","
                + "\"style\":{\"top\":0,\"left\":0,\"width\":800,\"height\":600},\"children\":["
                + "{\"id\":\"w-1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\","
                + "\"style\":{\"top\":0,\"left\":0,\"width\":100,\"height\":100}}]}]}");
        when(screenMapper.selectById(7L)).thenReturn(s);

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);
        assertThatThrownBy(() -> service.publishCanvas(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    // ===== 图表 innerType 白名单扩充（六种常用图表）=====

    /** 新增六种图表 innerType 必须全部进入画布保存白名单. */
    @Test
    void save_newChartInnerTypes_allAccepted() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);
        lenient().when(blockMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            RptScreenBlock e = invocation.getArgument(0);
            e.setId(90L);
            return 1;
        }).when(blockMapper).insert(any(RptScreenBlock.class));

        for (String innerType : List.of("BAR_COMPARE", "AREA_STACK", "GAUGE", "TABLE_LIST",
                "KPI_DETAIL_TABLE", "KPI_RADAR", "LIQUID_PROGRESS", "PROGRESS_LIST",
                "COMBO_CHART", "FUNNEL_CHART", "SCATTER_BUBBLE", "HEATMAP_MATRIX",
                "SUNBURST_CHART", "SPARKLINE_CARD")) {
            CanvasComponentDTO c = comp("ChartWidget", innerType,
                    Map.of("top", 10, "left", 10, "width", 100, "height", 40));
            var resp = service.saveCanvas(req(7L, 0, c));
            assertThat(resp.getCanvasDraftJson()).as("innerType %s 应可保存", innerType).contains(innerType);
        }
    }

    /** 白名单外的 innerType 仍然拒绝（防御性回归）. */
    @Test
    void save_unknownInnerType_stillRejected() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0,
                comp("ChartWidget", "PIVOT_3D",
                        Map.of("top", 10, "left", 10, "width", 100, "height", 40)))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    /** 旧整体保存入口废弃后，同一数据源/组件约束必须在唯一的 canvas/save 写入口继续执行。 */
    @Test
    void save_boundUnknownDatasource_throws43001() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        when(dsMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0, boundChart("METRIC_CARD", 99L))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43001");
    }

    @Test
    void save_lineTrendOnSingleDatasource_throws43005() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        when(dsMapper.selectById(1L)).thenReturn(datasource(1L, "SINGLE", "WIDE_TABLE"));

        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0, boundChart("LINE_TREND", 1L))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    @Test
    void save_sparklineCardOnSingleDatasource_throws43005() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        when(dsMapper.selectById(1L)).thenReturn(datasource(1L, "SINGLE", "WIDE_TABLE"));

        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0, boundChart("SPARKLINE_CARD", 1L))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    @Test
    void save_drillEnabledOnSingleDatasource_throws43005() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        when(dsMapper.selectById(1L)).thenReturn(datasource(1L, "SINGLE", "WIDE_TABLE"));
        CanvasComponentDTO chart = boundChart("METRIC_CARD", 1L);
        chart.setDrillJson("{\"drillEnabled\":true,\"drillPeriods\":[\"LAST_10D\"]}");

        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0, chart)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    @Test
    void save_areaStackOnSingleDatasource_throws43005() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        when(dsMapper.selectById(1L)).thenReturn(datasource(1L, "SINGLE", "WIDE_TABLE"));

        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0, boundChart("AREA_STACK", 1L))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    @Test
    void save_kpiDetailTableOnNonKpiDetailDatasource_throws43005() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        when(dsMapper.selectById(1L)).thenReturn(datasource(1L, "SINGLE", "WIDE_TABLE"));

        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0, boundChart("KPI_DETAIL_TABLE", 1L))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    @Test
    void save_kpiRadarOnNonKpiDetailDatasource_throws43005() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        when(dsMapper.selectById(1L)).thenReturn(datasource(1L, "TIMESERIES", "KPI_RESULT"));

        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0, boundChart("KPI_RADAR", 1L))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    @Test
    void save_progressListOnNonKpiDetailDatasource_throws43005() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        when(dsMapper.selectById(1L)).thenReturn(datasource(1L, "SINGLE", "CUSTOM_SQL"));

        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0, boundChart("PROGRESS_LIST", 1L))))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    // ===== PeriodFilter 全屏周期过滤器（spec 2026-07-17 §5.3，每屏最多 1 个）=====

    /** PeriodFilter 必须进入组件类型白名单（单个可正常保存，素材类组件不占 block 行）. */
    @Test
    void save_singlePeriodFilter_accepted() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        when(canvasMapper.bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);
        var resp = service.saveCanvas(req(7L, 0,
                comp("PeriodFilter", null, Map.of("top", 88, "left", 1400, "width", 420, "height", 44))));
        assertThat(resp.getCanvasVersion()).isEqualTo(1);
        assertThat(resp.getCanvasDraftJson()).contains("PeriodFilter");
    }

    /** 每屏最多 1 个 PeriodFilter：保存时超过 1 个抛 RPT-43006（布局非法语义）. */
    @Test
    void save_twoPeriodFilters_throws43006() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        CanvasComponentDTO f1 = comp("PeriodFilter", null,
                Map.of("top", 88, "left", 1400, "width", 420, "height", 44));
        CanvasComponentDTO f2 = comp("PeriodFilter", null,
                Map.of("top", 200, "left", 1400, "width", 420, "height", 44));
        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0, f1, f2)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    /** 计数按摊平语义：Group.children 里的 PeriodFilter 与顶层节点合并计数，规避成组绕过限制. */
    @Test
    void save_periodFilterInGroupPlusTopLevel_throws43006() {
        when(screenMapper.selectById(7L)).thenReturn(screen(7L, 0));
        CanvasComponentDTO top = comp("PeriodFilter", null,
                Map.of("top", 88, "left", 1400, "width", 420, "height", 44));
        CanvasComponentDTO child = comp("PeriodFilter", null,
                Map.of("top", 10, "left", 10, "width", 300, "height", 40));
        CanvasComponentDTO group = comp("Group", null,
                Map.of("top", 100, "left", 100, "width", 400, "height", 300));
        group.setChildren(List.of(child));
        assertThatThrownBy(() -> service.saveCanvas(req(7L, 0, top, group)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    /** 发布侧防线：草稿 JSON 里出现 2 个 PeriodFilter（含 Group 嵌套）同样拒绝发布（保存/发布双侧校验）. */
    @Test
    void publish_twoPeriodFiltersInDraft_throws43006() {
        RptScreen s = screen(7L, 5);
        s.setCanvasStyleJson("{\"schemaVersion\":1}");
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":["
                + "{\"id\":\"w-1\",\"component\":\"PeriodFilter\","
                + "\"style\":{\"top\":88,\"left\":1400,\"width\":420,\"height\":44}},"
                + "{\"id\":\"g-1\",\"component\":\"Group\","
                + "\"style\":{\"top\":100,\"left\":100,\"width\":400,\"height\":300},\"children\":["
                + "{\"id\":\"w-2\",\"component\":\"PeriodFilter\","
                + "\"style\":{\"top\":10,\"left\":10,\"width\":300,\"height\":40}}]}]}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        // 实现应在 blockId 一致性校验前就拦下（lenient：Red 阶段与实现落点无关的桩不因未消费而报错）
        lenient().when(blockMapper.selectList(any())).thenReturn(List.of());

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);
        assertThatThrownBy(() -> service.publishCanvas(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
        verify(canvasMapper, never()).applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    @org.junit.jupiter.api.Test
    void publish_blockIdSetMismatch_throws43006() {
        RptScreen s = screen(7L, 5);
        // 草稿引用 blockId=1001,但本屏 block 行集合不含它 → 一致性校验失败
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":["
                + "{\"id\":\"w-1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\","
                + "\"blockId\":1001,\"style\":{\"top\":0,\"left\":0,\"width\":100,\"height\":100}}]}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        when(blockMapper.selectList(any())).thenReturn(java.util.List.of()); // 无 block 行
        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);
        assertThatThrownBy(() -> service.publishCanvas(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    /** Set 去重不能掩盖两个已发布 ChartWidget 冒用同一 blockId。 */
    @Test
    void publish_duplicateChartWidgetBlockId_throws43006BeforeSnapshotGeneration() {
        RptScreen s = screen(7L, 5);
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":["
                + "{\"id\":\"w-1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":1001,\"style\":{\"top\":0,\"left\":0,\"width\":100,\"height\":100}},"
                + "{\"id\":\"w-2\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":1001,\"style\":{\"top\":120,\"left\":0,\"width\":100,\"height\":100}}]}");
        RptScreenBlock b = new RptScreenBlock();
        b.setId(1001L);
        b.setScreenId(7L);
        b.setComponentType("METRIC_CARD");
        b.setBindJson("{\"dsId\":9001}");
        when(screenMapper.selectById(7L)).thenReturn(s);

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);
        assertThatThrownBy(() -> service.publishCanvas(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
        verify(canvasMapper, never()).applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    /** 发布包身份字段必须是原生整数：浮点 blockId/dsId 不能被 asLong 静默截断。 */
    @Test
    void rollback_rejectsFloatingPointPublishedIdentityNodes() {
        RptScreen s = screen(7L, 5);
        when(screenMapper.selectById(7L)).thenReturn(s);
        com.bank.branch.platform.report.entity.RptScreenPublishLog logEntry =
                new com.bank.branch.platform.report.entity.RptScreenPublishLog();
        logEntry.setId(200L);
        logEntry.setScreenId(7L);
        logEntry.setSnapshotJson("{\"schemaVersion\":1.0,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":1001.0}],"
                + "\"bindSnapshots\":{\"1001\":{\"componentType\":\"METRIC_CARD\",\"bind\":{\"dsId\":12.0}}}}");
        when(publishLogMapper.selectById(200L)).thenReturn(logEntry);

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO();
        req.setScreenId(7L);
        req.setPublishLogId(200L);
        req.setExpectedVersion(5);
        assertThatThrownBy(() -> service.rollbackCanvas(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
        verify(canvasMapper, never()).applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    @org.junit.jupiter.api.Test
    void publish_ok_writesPublishedAndArchives() {
        RptScreen s = screen(7L, 5);
        s.setCanvasStyleJson("{\"schemaVersion\":1,\"adaptor\":\"keepProportion\"}");
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":["
                + "{\"id\":\"w-1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\","
                + "\"blockId\":1001,\"style\":{\"top\":0,\"left\":0,\"width\":100,\"height\":100}}]}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        com.bank.branch.platform.report.entity.RptScreenBlock b =
                new com.bank.branch.platform.report.entity.RptScreenBlock();
        b.setId(1001L);
        b.setScreenId(7L);
        b.setComponentType("METRIC_CARD");
        b.setBindJson("{\"dsId\":9001,\"period\":\"LATEST\"}");
        when(blockMapper.selectList(any())).thenReturn(java.util.List.of(b));
        when(canvasMapper.applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString())).thenReturn(1);
        // 归档滚动:selectList 查历史条数(返回空即无需裁剪)
        when(publishLogMapper.selectList(any())).thenReturn(java.util.List.of());

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);
        req.setReason("发布指标组件");
        service.publishCanvas(req);

        org.mockito.Mockito.verify(canvasMapper).applyPublishedCas(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.contains("bindSnapshots"),
                anyString(), anyString(), anyInt(), anyString());
        org.mockito.Mockito.verify(publishLogMapper).insert(
                any(com.bank.branch.platform.report.entity.RptScreenPublishLog.class));
        // 高危发布操作必须留痕:审计恰好写入 1 次(rev-t3 补测)
        org.mockito.Mockito.verify(auditApi, org.mockito.Mockito.times(1)).log(any());
    }

    /** 服务层必须拒绝缺失 reason 的直接发布调用，不能只依赖 Controller 的 @Valid。 */
    @Test
    void publish_missingReasonFailsClosedBeforePublishedCas() {
        RptScreen s = screen(7L, 5);
        s.setCanvasStyleJson("{}");
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":[]}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        when(blockMapper.selectList(any())).thenReturn(List.of());

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);

        assertThatThrownBy(() -> service.publishCanvas(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43022");
        verify(canvasMapper, never()).applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    /** 发布审计记录真实包间 block 差异，而不是整个草稿集合或内部事件名。 */
    @Test
    void publish_auditUsesReasonAndTrueBlockDiff() {
        RptScreen s = screen(7L, 5);
        s.setCanvasStyleJson("{}");
        s.setCanvasPublishedJson(trustedPublishedPackage(1001L, 9001L));
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":[{\"id\":\"w-2\",\"component\":\"ChartWidget\","
                + "\"innerType\":\"METRIC_CARD\",\"blockId\":1002,\"style\":{\"top\":0,\"left\":0,\"width\":100,\"height\":100}}]}");
        RptScreenBlock block = new RptScreenBlock();
        block.setId(1002L);
        block.setScreenId(7L);
        block.setComponentType("METRIC_CARD");
        block.setBindJson("{\"dsId\":9002}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        when(blockMapper.selectList(any())).thenReturn(List.of(block));
        when(canvasMapper.applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString())).thenReturn(1);
        when(publishLogMapper.selectList(any())).thenReturn(List.of());

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);
        req.setReason("替换指标组件");
        service.publishCanvas(req);

        ArgumentCaptor<com.bank.branch.platform.governance.api.dto.AuditLogCmd> audit =
                ArgumentCaptor.forClass(com.bank.branch.platform.governance.api.dto.AuditLogCmd.class);
        verify(auditApi).log(audit.capture());
        assertThat(audit.getValue().getBizAction()).isEqualTo("PUBLISH");
        assertThat(audit.getValue().getReason()).isEqualTo("替换指标组件");
        assertThat(audit.getValue().getBeforeSnapshot()).isEqualTo(s.getCanvasPublishedJson());
        assertThat(audit.getValue().getAddedItems()).isEqualTo("[1002]");
        assertThat(audit.getValue().getRemovedItems()).isEqualTo("[1001]");
    }

    /** 回滚同样需要理由，且 added/removed 必须描述实际发布包身份差异。 */
    @Test
    void rollback_missingReasonFailsClosedAndAuditUsesTrueBlockDiff() {
        RptScreen s = screen(7L, 5);
        s.setCanvasPublishedJson(trustedPublishedPackage(1001L, 9001L));
        com.bank.branch.platform.report.entity.RptScreenPublishLog logEntry =
                new com.bank.branch.platform.report.entity.RptScreenPublishLog();
        logEntry.setId(200L);
        logEntry.setScreenId(7L);
        logEntry.setSnapshotJson(trustedPublishedPackage(1002L, 9002L));
        when(screenMapper.selectById(7L)).thenReturn(s);
        when(publishLogMapper.selectById(200L)).thenReturn(logEntry);
        when(canvasMapper.applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString())).thenReturn(1);

        var missing = new com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO();
        missing.setScreenId(7L);
        missing.setPublishLogId(200L);
        missing.setExpectedVersion(5);
        assertThatThrownBy(() -> service.rollbackCanvas(missing))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43022");
        verify(canvasMapper, never()).applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString());

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO();
        req.setScreenId(7L);
        req.setPublishLogId(200L);
        req.setExpectedVersion(5);
        req.setReason("回退错误发布");
        service.rollbackCanvas(req);

        ArgumentCaptor<com.bank.branch.platform.governance.api.dto.AuditLogCmd> audit =
                ArgumentCaptor.forClass(com.bank.branch.platform.governance.api.dto.AuditLogCmd.class);
        verify(auditApi).log(audit.capture());
        assertThat(audit.getValue().getBizAction()).isEqualTo("ROLLBACK");
        assertThat(audit.getValue().getReason()).isEqualTo("回退错误发布");
        assertThat(audit.getValue().getAddedItems()).isEqualTo("[1002]");
        assertThat(audit.getValue().getRemovedItems()).isEqualTo("[1001]");
    }

    private String trustedPublishedPackage(long blockId, long datasourceId) {
        return "{\"schemaVersion\":1,\"components\":[{\"id\":\"w-" + blockId
                + "\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\",\"blockId\":" + blockId
                + "}],\"bindSnapshots\":{\"" + blockId + "\":{\"componentType\":\"METRIC_CARD\","
                + "\"bind\":{\"dsId\":" + datasourceId + "},\"styleCfg\":{},\"drill\":{}}}}";
    }

    @Test
    void publish_rechecksScreenAndDatasourceBizLine_throws43014() {
        RptScreen s = screen(7L, 5);
        s.setBizLine("CORP");
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":["
                + "{\"id\":\"w-1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\","
                + "\"blockId\":1001,\"style\":{\"top\":0,\"left\":0,\"width\":100,\"height\":100}}]}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        RptScreenBlock b = new RptScreenBlock();
        b.setId(1001L);
        b.setScreenId(7L);
        b.setComponentType("METRIC_CARD");
        b.setBindJson("{\"dsId\":9001}");
        when(blockMapper.selectList(any())).thenReturn(List.of(b));
        com.bank.branch.platform.report.entity.RptScreenDatasource retail =
                new com.bank.branch.platform.report.entity.RptScreenDatasource();
        retail.setId(9001L);
        retail.setBizLine("RETAIL");
        when(dsMapper.selectById(9001L)).thenReturn(retail);

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);

        assertThatThrownBy(() -> service.publishCanvas(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43014");
        verify(canvasMapper, never()).applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    /** MapCenter 节点(省级屏地图)不是 ChartWidget、无 blockId,发布时的 blockId 一致性校验只遍历
     * component=="ChartWidget" 的节点,天然跳过它;含 MapCenter 的画布应正常 publish 成功,且
     * MapCenter 节点原样保留在发布包 components 里(供运行时 ScreenRenderer 渲染),不进 bindSnapshots. */
    @org.junit.jupiter.api.Test
    void publish_withMapCenterNode_succeedsAndKeepsNodeOutOfBindSnapshots() {
        RptScreen s = screen(7L, 5);
        s.setCanvasStyleJson("{\"schemaVersion\":1,\"adaptor\":\"keepProportion\"}");
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":["
                + "{\"id\":\"w-1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\","
                + "\"blockId\":1001,\"style\":{\"top\":0,\"left\":0,\"width\":100,\"height\":100}},"
                + "{\"id\":\"w-map\",\"component\":\"MapCenter\","
                + "\"style\":{\"top\":96,\"left\":640,\"width\":640,\"height\":880}}]}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        com.bank.branch.platform.report.entity.RptScreenBlock b =
                new com.bank.branch.platform.report.entity.RptScreenBlock();
        b.setId(1001L);
        b.setScreenId(7L);
        b.setComponentType("METRIC_CARD");
        b.setBindJson("{\"dsId\":9001,\"period\":\"LATEST\"}");
        when(blockMapper.selectList(any())).thenReturn(java.util.List.of(b));
        when(canvasMapper.applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString())).thenReturn(1);
        when(publishLogMapper.selectList(any())).thenReturn(java.util.List.of());

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);
        req.setReason("发布含地图画布");

        // 不抛异常即证明 MapCenter 天然兼容发布链路(无需为它伪造 blockId)
        service.publishCanvas(req);

        ArgumentCaptor<String> pkgCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(canvasMapper).applyPublishedCas(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(5), pkgCaptor.capture(),
                anyString(), anyString(), anyInt(), anyString());
        assertThat(pkgCaptor.getValue())
                .contains("\"component\":\"MapCenter\"")     // 节点原样保留进发布包 components
                .contains("\"bindSnapshots\":{\"1001\":{")    // 仅 ChartWidget 的 blockId=1001 有快照且非空
                .doesNotContain("\"1001\":{}");
    }

    @Test
    void publish_schema2MapOnLegacyScope_rejectsWithoutNamedGroup() {
        RptScreen s = screen(7L, 5);
        s.setCanvasDraftJson("{\"schemaVersion\":2,\"components\":["
                + "{\"id\":\"w-map\",\"component\":\"MapCenter\",\"propValue\":{"
                + "\"schemaVersion\":2,\"mode\":\"XIAN_COMPOSITE\"}}]}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        ReflectionTestUtils.setField(service, "screenMapService", new ScreenMapService());

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);

        assertThatThrownBy(() -> service.publishCanvas(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43015");
        verify(canvasMapper, never()).applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    /** 归档滚动:超过 PUBLISH_LOG_KEEP(10)份时,只删最旧的那些(id 最小),不动最近 10 份(rev-t3 补测). */
    @org.junit.jupiter.api.Test
    void publish_archiveExceeds10_trimsOldestTwo() {
        RptScreen s = screen(7L, 5);
        s.setCanvasStyleJson("{\"schemaVersion\":1,\"adaptor\":\"keepProportion\"}");
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":["
                + "{\"id\":\"w-1\",\"component\":\"ChartWidget\",\"innerType\":\"METRIC_CARD\","
                + "\"blockId\":1001,\"style\":{\"top\":0,\"left\":0,\"width\":100,\"height\":100}}]}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        com.bank.branch.platform.report.entity.RptScreenBlock b =
                new com.bank.branch.platform.report.entity.RptScreenBlock();
        b.setId(1001L);
        b.setScreenId(7L);
        b.setComponentType("METRIC_CARD");
        b.setBindJson("{\"dsId\":9001,\"period\":\"LATEST\"}");
        when(blockMapper.selectList(any())).thenReturn(java.util.List.of(b));
        when(canvasMapper.applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString())).thenReturn(1);

        // 归档滚动:mock 12 条既有归档,按 id 倒序构造(对齐实现 orderByDesc(id) 的真实查询排序:新→旧)
        java.util.List<com.bank.branch.platform.report.entity.RptScreenPublishLog> archives =
                new java.util.ArrayList<>();
        for (long id = 12; id >= 1; id--) {
            com.bank.branch.platform.report.entity.RptScreenPublishLog l =
                    new com.bank.branch.platform.report.entity.RptScreenPublishLog();
            l.setId(id);
            l.setScreenId(7L);
            archives.add(l);
        }
        when(publishLogMapper.selectList(any())).thenReturn(archives);

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);
        req.setReason("发布并清理归档");
        service.publishCanvas(req);

        // 12 份中保留最近 10(id=3..12),删最旧 2 份(id=1,2);deleteById(Serializable) 重载用 anyLong() 显式定型避开
        // BaseMapper.deleteById(Serializable)/deleteById(T) 双重载的 any() 二义(与 updateById 同类 javac 限制)
        org.mockito.Mockito.verify(publishLogMapper, org.mockito.Mockito.times(2))
                .deleteById(org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.verify(publishLogMapper).deleteById(1L);
        org.mockito.Mockito.verify(publishLogMapper).deleteById(2L);
        org.mockito.Mockito.verify(publishLogMapper, org.mockito.Mockito.never()).deleteById(3L);
    }

    @org.junit.jupiter.api.Test
    void discard_copiesPublishedComponentsToDraftWithCas() {
        RptScreen s = screen(7L, 5);
        s.setCanvasPublishedJson("{\"schemaVersion\":1,\"canvasStyle\":{},"
                + "\"components\":[{\"id\":\"w-9\",\"component\":\"TextLabel\","
                + "\"style\":{\"top\":0,\"left\":0,\"width\":10,\"height\":10}}],\"bindSnapshots\":{}}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        when(canvasMapper.discardDraftCas(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);
        service.discardDraft(discardReq(7L, 5));
        // 放弃后 draft 组件树来自 published.components
        ArgumentCaptor<String> draft = ArgumentCaptor.forClass(String.class);
        verify(canvasMapper).discardDraftCas(eq(7L), eq(5), anyString(), draft.capture(), eq("E001"));
        assertThat(draft.getValue()).contains("w-9");
        verify(screenMapper, never()).updateById(any(RptScreen.class));
    }

    /**
     * 发布后草稿删除图表行时，discard 必须从不可变 bindSnapshots 恢复同一 blockId；否则
     * 发布图表→删草稿→放弃的编辑链路会永久丢失可编辑绑定。
     */
    @Test
    void discard_restoresMissingPublishedBlockWithStableIdAfterCas() {
        RptScreen s = screen(7L, 5);
        s.setBizLine("COMMON");
        s.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":1001}],"
                + "\"bindSnapshots\":{\"1001\":{\"componentType\":\"METRIC_CARD\","
                + "\"bind\":{\"dsId\":12},\"styleCfg\":{\"color\":\"blue\"},\"drill\":{}}}}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        when(canvasMapper.discardDraftCas(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);
        when(blockMapper.selectList(any())).thenReturn(List.of());
        when(dsMapper.selectById(12L)).thenReturn(datasource(12L, "SINGLE", "WIDE_TABLE"));

        service.discardDraft(discardReq(7L, 5));

        ArgumentCaptor<RptScreenBlock> restored = ArgumentCaptor.forClass(RptScreenBlock.class);
        verify(blockMapper).insert(restored.capture());
        assertThat(restored.getValue().getId()).isEqualTo(1001L);
        assertThat(restored.getValue().getScreenId()).isEqualTo(7L);
        assertThat(restored.getValue().getBindJson()).contains("\"dsId\":12");
        verify(auditApi).log(any());
    }

    /** discard 的草稿树只能保留发布组件：恢复发布块后必须删除发布组件之外的新增/孤儿块。 */
    @Test
    void discard_removesDraftOrphanBlocksOutsidePublishedComponents() {
        RptScreen s = screen(7L, 5);
        s.setBizLine("COMMON");
        s.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":1001}],"
                + "\"bindSnapshots\":{\"1001\":{\"componentType\":\"METRIC_CARD\",\"bind\":{\"dsId\":12},\"styleCfg\":{},\"drill\":{}}}}");
        RptScreenBlock published = new RptScreenBlock();
        published.setId(1001L);
        published.setScreenId(7L);
        RptScreenBlock orphan = new RptScreenBlock();
        orphan.setId(1002L);
        orphan.setScreenId(7L);
        when(screenMapper.selectById(7L)).thenReturn(s);
        when(canvasMapper.discardDraftCas(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);
        when(blockMapper.selectList(any())).thenReturn(List.of(published, orphan));
        when(dsMapper.selectById(12L)).thenReturn(datasource(12L, "SINGLE", "WIDE_TABLE"));

        service.discardDraft(discardReq(7L, 5));

        verify(blockMapper).deleteByScreenIdAndIds(7L, Set.of(1002L));
    }

    /** CAS 未命中时不能恢复/改写 block，也不能留下成功审计。 */
    @Test
    void discard_casConflictLeavesBlocksUntouched() {
        RptScreen s = screen(7L, 5);
        s.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[],\"bindSnapshots\":{}}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        when(canvasMapper.discardDraftCas(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(0);

        assertThatThrownBy(() -> service.discardDraft(discardReq(7L, 5)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43012");
        verify(blockMapper, never()).insert(any(RptScreenBlock.class));
        verify(blockMapper, never()).updateById(any(RptScreenBlock.class));
        verify(auditApi, never()).log(any());
    }

    /** 结构化审计失败必须向上传播，让 discard 的整个事务回滚。 */
    @Test
    void discard_auditFailureFailsClosed() {
        RptScreen s = screen(7L, 5);
        s.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[],\"bindSnapshots\":{}}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        when(canvasMapper.discardDraftCas(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);
        doThrow(new IllegalStateException("audit down")).when(auditApi).log(any());

        assertThatThrownBy(() -> service.discardDraft(discardReq(7L, 5)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-50001");
    }

    /** 回滚:归档快照原样覆盖 PUBLISHED_JSON. */
    @org.junit.jupiter.api.Test
    void rollback_ok_appliesArchivedSnapshot() {
        RptScreen s = screen(7L, 5);
        when(screenMapper.selectById(7L)).thenReturn(s);
        com.bank.branch.platform.report.entity.RptScreenPublishLog logEntry =
                new com.bank.branch.platform.report.entity.RptScreenPublishLog();
        logEntry.setId(200L);
        logEntry.setScreenId(7L);
        logEntry.setSnapshotJson("{\"schemaVersion\":1,\"components\":[],\"bindSnapshots\":{}}");
        when(publishLogMapper.selectById(200L)).thenReturn(logEntry);
        when(canvasMapper.applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString())).thenReturn(1);

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO();
        req.setScreenId(7L);
        req.setPublishLogId(200L);
        req.setExpectedVersion(5);
        req.setReason("回滚已发布画布");
        service.rollbackCanvas(req);

        org.mockito.Mockito.verify(canvasMapper).applyPublishedCas(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq(logEntry.getSnapshotJson()),
                anyString(), anyString(), org.mockito.ArgumentMatchers.eq(1), anyString());
        // 高危回滚操作必须留痕:审计恰好写入 1 次(rev-t3 补测)
        org.mockito.Mockito.verify(auditApi, org.mockito.Mockito.times(1)).log(any());
    }

    /** 回滚必须把归档 bindSnapshots 同步恢复为可编辑 block：缺失行插回、改绑行覆盖，
     * 当前归档之外的草稿孤儿行清掉；否则回滚后的 draft 会引用不存在或错误数据源的 block。 */
    @org.junit.jupiter.api.Test
    void rollback_restoresArchivedBlocksAndRemovesCurrentOrphans() {
        RptScreen s = screen(7L, 5);
        s.setCanvasPublishedJson("{\"schemaVersion\":1,\"components\":[],\"bindSnapshots\":{}}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        com.bank.branch.platform.report.entity.RptScreenPublishLog logEntry =
                new com.bank.branch.platform.report.entity.RptScreenPublishLog();
        logEntry.setId(200L);
        logEntry.setScreenId(7L);
        logEntry.setSnapshotJson("{\"schemaVersion\":1,\"components\":["
                + "{\"component\":\"ChartWidget\",\"blockId\":1001},"
                + "{\"component\":\"ChartWidget\",\"blockId\":1002}],"
                + "\"bindSnapshots\":{"
                + "\"1001\":{\"componentType\":\"METRIC_CARD\",\"bind\":{\"dsId\":12},"
                + "\"styleCfg\":{\"color\":\"green\"},\"drill\":{}},"
                + "\"1002\":{\"componentType\":\"LINE_TREND\",\"bind\":{\"dsId\":13},"
                + "\"styleCfg\":{},\"drill\":{}}}}");
        when(publishLogMapper.selectById(200L)).thenReturn(logEntry);

        RptScreenBlock rebound = new RptScreenBlock();
        rebound.setId(1001L);
        rebound.setScreenId(7L);
        rebound.setComponentType("METRIC_CARD");
        rebound.setBindJson("{\"dsId\":99}");
        RptScreenBlock orphan = new RptScreenBlock();
        orphan.setId(1003L);
        orphan.setScreenId(7L);
        orphan.setComponentType("METRIC_CARD");
        orphan.setBindJson("{\"dsId\":98}");
        when(blockMapper.selectList(any())).thenReturn(List.of(rebound, orphan));
        when(canvasMapper.applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(1);

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO();
        req.setScreenId(7L);
        req.setPublishLogId(200L);
        req.setExpectedVersion(5);
        req.setReason("回滚并恢复绑定");
        service.rollbackCanvas(req);

        ArgumentCaptor<RptScreenBlock> inserted = ArgumentCaptor.forClass(RptScreenBlock.class);
        verify(blockMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getId()).isEqualTo(1002L);
        assertThat(inserted.getValue().getBindJson()).isEqualTo("{\"dsId\":13}");

        ArgumentCaptor<RptScreenBlock> updated = ArgumentCaptor.forClass(RptScreenBlock.class);
        verify(blockMapper).updateById(updated.capture());
        assertThat(updated.getValue().getId()).isEqualTo(1001L);
        assertThat(updated.getValue().getBindJson()).isEqualTo("{\"dsId\":12}");
        assertThat(updated.getValue().getStyleJson()).isEqualTo("{\"color\":\"green\"}");
        verify(blockMapper).deleteByScreenIdAndIds(7L, Set.of(1003L));
    }

    /** 回滚归档的 CODE presentation 时，style 与 draft 必须和 published package 一起恢复，
     * 否则画布会出现“发布包是 CODE、编辑态仍是旧布局”的双态漂移。 */
    @org.junit.jupiter.api.Test
    void rollback_codeArchiveRestoresPresentationStyleAndDraft() {
        RptScreen s = screen(7L, 5);
        s.setCanvasStyleJson("{\"schemaVersion\":1}");
        s.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":[]}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        com.bank.branch.platform.report.entity.RptScreenPublishLog logEntry =
                new com.bank.branch.platform.report.entity.RptScreenPublishLog();
        logEntry.setId(201L);
        logEntry.setScreenId(7L);
        String codeStyle = "{\"presentation\":{\"type\":\"CODE\","
                + "\"template\":\"branch-overview-v1\"}}";
        String bind = "{\"dsId\":12,\"period\":\"LATEST\","
                + "\"fields\":{\"value\":\"balance\"},\"units\":{\"value\":\"YUAN\"}}";
        logEntry.setSnapshotJson("{\"schemaVersion\":1,\"canvasStyle\":" + codeStyle
                + ",\"components\":[{\"component\":\"ChartWidget\",\"id\":\"w-code\","
                + "\"blockId\":1201,\"propValue\":{\"bindingKey\":\"deposit\"},"
                + "\"bindJson\":" + new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(bind)
                + "}],\"bindSnapshots\":{\"1201\":{\"componentType\":\"CODE\","
                + "\"bind\":" + bind + ",\"styleCfg\":{},\"drill\":{}}}}");
        when(publishLogMapper.selectById(201L)).thenReturn(logEntry);
        when(blockMapper.selectList(any())).thenReturn(List.of());
        when(dsMapper.selectById(12L)).thenReturn(codeDatasource(12L));
        when(canvasMapper.applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(1);

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO();
        req.setScreenId(7L);
        req.setPublishLogId(201L);
        req.setExpectedVersion(5);
        req.setReason("回滚代码化画布");
        service.rollbackCanvas(req);

        verify(canvasMapper).applyPublishedCas(
                eq(7L), eq(5), eq(logEntry.getSnapshotJson()),
                org.mockito.ArgumentMatchers.contains("\"presentation\""),
                org.mockito.ArgumentMatchers.contains("\"bindingKey\":\"deposit\""),
                eq(1), anyString());
    }

    /** 旧归档缺少 immutable bindSnapshots 时不可直接回滚，不能由当前 block 行补证。 */
    @Test
    void rollback_untrustedArchiveWithoutSnapshotsFailsClosed() {
        RptScreen s = screen(7L, 5);
        when(screenMapper.selectById(7L)).thenReturn(s);
        com.bank.branch.platform.report.entity.RptScreenPublishLog logEntry =
                new com.bank.branch.platform.report.entity.RptScreenPublishLog();
        logEntry.setId(200L);
        logEntry.setScreenId(7L);
        logEntry.setSnapshotJson("{\"schemaVersion\":1,\"components\":[{\"component\":\"ChartWidget\",\"blockId\":1001}]}");
        when(publishLogMapper.selectById(200L)).thenReturn(logEntry);

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO();
        req.setScreenId(7L);
        req.setPublishLogId(200L);
        req.setExpectedVersion(5);
        assertThatThrownBy(() -> service.rollbackCanvas(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED.getCode());
        verify(canvasMapper, never()).applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    /** 归档条目所属屏与请求屏不一致(跨屏越权回滚)→ 拒绝. */
    @org.junit.jupiter.api.Test
    void rollback_logBelongsToOtherScreen_throws43004() {
        RptScreen s = screen(7L, 5);
        when(screenMapper.selectById(7L)).thenReturn(s);
        com.bank.branch.platform.report.entity.RptScreenPublishLog logEntry =
                new com.bank.branch.platform.report.entity.RptScreenPublishLog();
        logEntry.setId(200L);
        logEntry.setScreenId(888L); // 属于别的屏
        logEntry.setSnapshotJson("{}");
        when(publishLogMapper.selectById(200L)).thenReturn(logEntry);

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO();
        req.setScreenId(7L);
        req.setPublishLogId(200L);
        req.setExpectedVersion(5);
        assertThatThrownBy(() -> service.rollbackCanvas(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43004");
    }

    /** 归档列表:按 id 倒序,不回传 snapshotJson 全文. */
    @org.junit.jupiter.api.Test
    void listPublishLogs_returnsMappedList() {
        com.bank.branch.platform.report.entity.RptScreenPublishLog l1 =
                new com.bank.branch.platform.report.entity.RptScreenPublishLog();
        l1.setId(201L);
        l1.setScreenId(7L);
        l1.setPublishedBy("E001");
        l1.setPublishedAt(java.time.LocalDateTime.of(2026, 7, 12, 10, 0));
        when(publishLogMapper.selectList(any())).thenReturn(java.util.List.of(l1));

        var list = service.listPublishLogs(7L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo(201L);
        assertThat(list.get(0).getScreenId()).isEqualTo(7L);
        assertThat(list.get(0).getPublishedBy()).isEqualTo("E001");
    }
}
