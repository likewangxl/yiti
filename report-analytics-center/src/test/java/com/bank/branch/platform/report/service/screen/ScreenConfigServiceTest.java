package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import com.bank.branch.platform.report.dto.req.ScreenSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenViewRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.entity.RptScreenMapPoint;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapPointMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    private ScreenConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenConfigServiceImpl(screenMapper, blockMapper, dsMapper, pointMapper, currentUserApi);
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    }

    private ScreenBlockDTO block(String region, int rowNo, int colNo, int widthPct,
                                 String componentType, String bindJson, String drillJson) {
        ScreenBlockDTO b = new ScreenBlockDTO();
        b.setRegion(region);
        b.setRowNo(rowNo);
        b.setColNo(colNo);
        b.setWidthPct(widthPct);
        b.setHeightPct(50);
        b.setComponentType(componentType);
        b.setBindJson(bindJson);
        b.setDrillJson(drillJson);
        return b;
    }

    private ScreenSaveReqDTO reqWith(String viewLevel, ScreenBlockDTO... blocks) {
        ScreenSaveReqDTO r = new ScreenSaveReqDTO();
        r.setScreenName("测试屏");
        r.setViewLevel(viewLevel);
        r.setBlocks(List.of(blocks));
        return r;
    }

    private RptScreenDatasource ds(long id, String dsType) {
        return ds(id, dsType, "WIDE_TABLE");
    }

    private RptScreenDatasource ds(long id, String dsType, String sourceKind) {
        RptScreenDatasource d = new RptScreenDatasource();
        d.setId(id);
        d.setDsType(dsType);
        d.setSourceKind(sourceKind);
        d.setStatus("ACTIVE");
        return d;
    }

    @Test
    void saveScreen_provinceMainBlock_throws43006() {
        ScreenSaveReqDTO req = reqWith("PROVINCE",
                block("MAIN", 1, 1, 100, "METRIC_CARD", "{\"dsId\":1}", null));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    @Test
    void saveScreen_rowWidthOver100_throws43006() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ds(1L, "SINGLE")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 60, "METRIC_CARD", "{\"dsId\":1}", null),
                block("LEFT", 1, 2, 50, "METRIC_CARD", "{\"dsId\":1}", null));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    @Test
    void saveScreen_lineTrendOnSingleDs_throws43005() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ds(1L, "SINGLE")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 100, "LINE_TREND", "{\"dsId\":1}", null));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    @Test
    void saveScreen_drillEnabledOnSingleDs_throws43005() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ds(1L, "SINGLE")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 100, "METRIC_CARD", "{\"dsId\":1}",
                        "{\"drillEnabled\":true,\"drillPeriods\":[\"LAST_10D\"]}"));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    @Test
    void saveScreen_unknownDsId_throws43001() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 100, "METRIC_CARD", "{\"dsId\":99}", null));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43001");
    }

    @Test
    void saveScreen_ok_insertsScreenThenDeletesThenInsertsBlocks() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ds(1L, "TIMESERIES")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 100, "LINE_TREND", "{\"dsId\":1}", null),
                block("RIGHT", 1, 1, 100, "METRIC_CARD", "{\"dsId\":1}", null));

        service.saveScreen(req);

        InOrder order = inOrder(screenMapper, blockMapper);
        order.verify(screenMapper).insert(any(RptScreen.class));
        order.verify(blockMapper).delete(any(Wrapper.class));
        order.verify(blockMapper, times(2)).insert(any(RptScreenBlock.class));
    }

    // ===== 组件白名单扩充 + RPT-43005 联动（spec 2026-07-17 §5.1）=====

    /** 8 种新图表 component_type 进入区块保存白名单（KPI 专属 3 种绑 KPI_DETAIL 数据源）. */
    @Test
    void saveScreen_newComponentTypes_allAccepted() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                ds(1L, "SINGLE", "WIDE_TABLE"),
                ds(2L, "TIMESERIES", "WIDE_TABLE"),
                ds(3L, "SINGLE", "KPI_DETAIL")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 50, "BAR_COMPARE", "{\"dsId\":1}", null),
                block("LEFT", 2, 1, 50, "AREA_STACK", "{\"dsId\":2}", null),
                block("LEFT", 3, 1, 50, "GAUGE", "{\"dsId\":1}", null),
                block("LEFT", 4, 1, 50, "TABLE_LIST", "{\"dsId\":1}", null),
                block("RIGHT", 1, 1, 50, "KPI_DETAIL_TABLE", "{\"dsId\":3}", null),
                block("RIGHT", 2, 1, 50, "KPI_RADAR", "{\"dsId\":3}", null),
                block("RIGHT", 3, 1, 50, "LIQUID_PROGRESS", "{\"dsId\":1}", null),
                block("RIGHT", 4, 1, 50, "PROGRESS_LIST", "{\"dsId\":3}", null));

        service.saveScreen(req);

        verify(blockMapper, times(8)).insert(any(RptScreenBlock.class));
    }

    /** needTimeseries 联动：AREA_STACK 绑非 TIMESERIES 数据源 → 43005（与 LINE_TREND 同规则）. */
    @Test
    void saveScreen_areaStackOnSingleDs_throws43005() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ds(1L, "SINGLE")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 100, "AREA_STACK", "{\"dsId\":1}", null));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    /** needKinds 联动：KPI_DETAIL_TABLE 仅可绑 source_kind=KPI_DETAIL 数据源，越界 43005. */
    @Test
    void saveScreen_kpiDetailTableOnNonKpiDetailDs_throws43005() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ds(1L, "SINGLE", "WIDE_TABLE")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 100, "KPI_DETAIL_TABLE", "{\"dsId\":1}", null));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    @Test
    void saveScreen_kpiRadarOnNonKpiDetailDs_throws43005() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ds(1L, "TIMESERIES", "KPI_RESULT")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 100, "KPI_RADAR", "{\"dsId\":1}", null));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    @Test
    void saveScreen_progressListOnNonKpiDetailDs_throws43005() {
        when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ds(1L, "SINGLE", "CUSTOM_SQL")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 100, "PROGRESS_LIST", "{\"dsId\":1}", null));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43005");
    }

    /** 白名单外 component_type 仍拒绝（防御性回归）. */
    @Test
    void saveScreen_unknownComponentType_stillThrows43006() {
        lenient().when(dsMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ds(1L, "SINGLE")));
        ScreenSaveReqDTO req = reqWith("BRANCH",
                block("LEFT", 1, 1, 100, "PIVOT_3D", "{\"dsId\":1}", null));
        assertThatThrownBy(() -> service.saveScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43006");
    }

    @Test
    void getViewByCode_notFound_throws43004() {
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.getViewByCode("SCR_NONE"))
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
        assertThat(view.getMapPoints()).hasSize(1);
        assertThat(view.getMapPoints().get(0).getOrgCode()).isEqualTo("610100");
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
