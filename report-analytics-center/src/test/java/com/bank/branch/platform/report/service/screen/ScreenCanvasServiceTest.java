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

    private ScreenCanvasServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenCanvasServiceImpl(screenMapper, blockMapper, dsMapper, canvasMapper, currentUserApi);
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
}
