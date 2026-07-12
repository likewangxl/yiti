package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.CanvasComponentDTO;
import com.bank.branch.platform.report.dto.req.CanvasStyleDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasSaveReqDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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
        when(blockMapper.selectList(any())).thenReturn(List.of());

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
        when(canvasMapper.applyPublished(anyLong(), anyString(), anyInt(), anyString())).thenReturn(1);
        // 归档滚动:selectList 查历史条数(返回空即无需裁剪)
        when(publishLogMapper.selectList(any())).thenReturn(java.util.List.of());

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(5);
        service.publishCanvas(req);

        org.mockito.Mockito.verify(canvasMapper).applyPublished(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.contains("bindSnapshots"),
                anyInt(), anyString());
        org.mockito.Mockito.verify(publishLogMapper).insert(
                any(com.bank.branch.platform.report.entity.RptScreenPublishLog.class));
        // 高危发布操作必须留痕:审计恰好写入 1 次(rev-t3 补测)
        org.mockito.Mockito.verify(auditApi, org.mockito.Mockito.times(1)).log(any());
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
        when(canvasMapper.applyPublished(anyLong(), anyString(), anyInt(), anyString())).thenReturn(1);

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
    void discard_copiesPublishedComponentsToDraft() {
        RptScreen s = screen(7L, 5);
        s.setCanvasPublishedJson("{\"schemaVersion\":1,\"canvasStyle\":{},"
                + "\"components\":[{\"id\":\"w-9\",\"component\":\"TextLabel\","
                + "\"style\":{\"top\":0,\"left\":0,\"width\":10,\"height\":10}}],\"bindSnapshots\":{}}");
        when(screenMapper.selectById(7L)).thenReturn(s);
        service.discardDraft(7L);
        // 放弃后 draft 组件树来自 published.components
        // (显式类型见证:BaseMapper 同时有 updateById(T)/updateById(Collection<T>) 两个重载,
        //  argThat 的 lambda 目标类型推断在重载消解阶段是二义的,不加 <RptScreen> 编译不过)
        org.mockito.Mockito.verify(screenMapper).updateById(
                org.mockito.ArgumentMatchers.<RptScreen>argThat(x ->
                        x.getCanvasDraftJson() != null && x.getCanvasDraftJson().contains("w-9")));
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
        logEntry.setSnapshotJson("{\"schemaVersion\":1,\"components\":[]}");
        when(publishLogMapper.selectById(200L)).thenReturn(logEntry);
        when(canvasMapper.applyPublished(anyLong(), anyString(), anyInt(), anyString())).thenReturn(1);

        var req = new com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO();
        req.setScreenId(7L);
        req.setPublishLogId(200L);
        service.rollbackCanvas(req);

        org.mockito.Mockito.verify(canvasMapper).applyPublished(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(logEntry.getSnapshotJson()),
                org.mockito.ArgumentMatchers.eq(1), anyString());
        // 高危回滚操作必须留痕:审计恰好写入 1 次(rev-t3 补测)
        org.mockito.Mockito.verify(auditApi, org.mockito.Mockito.times(1)).log(any());
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
