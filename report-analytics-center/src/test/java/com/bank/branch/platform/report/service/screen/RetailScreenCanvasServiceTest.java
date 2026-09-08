package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.report.dto.req.CanvasComponentDTO;
import com.bank.branch.platform.report.dto.req.CanvasStyleDTO;
import com.bank.branch.platform.report.dto.req.CodeScreenPresentationDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasSaveReqDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenCanvasMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.bank.branch.platform.report.mapper.RptScreenPublishLogMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RETAIL CODE 在画布保存、发布入口的纯单元测试。 */
@ExtendWith(MockitoExtension.class)
class RetailScreenCanvasServiceTest {

    @Mock private RptScreenMapper screenMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private RptScreenDatasourceMapper dsMapper;
    @Mock private RptScreenCanvasMapper canvasMapper;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private RptScreenPublishLogMapper publishLogMapper;
    @Mock private AuditApi auditApi;

    private ScreenCanvasServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenCanvasServiceImpl(screenMapper, blockMapper, dsMapper, canvasMapper,
                currentUserApi, publishLogMapper, auditApi);
    }

    @Test
    void saveRejectsRetailTemplateOnCommonScreenBeforeCanvasCas() {
        RptScreen screen = screen(7L, 3, "COMMON");
        when(screenMapper.selectById(7L)).thenReturn(screen);

        assertThatThrownBy(() -> service.saveCanvas(saveRequest(screen.getId(), 3, retailAumChart(), retailStyle())))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(canvasMapper, never()).bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void saveRejectsCommonDatasourceForRetailTemplateEvenOnRetailScreen() {
        RptScreen screen = screen(7L, 3, "RETAIL");
        RptScreenDatasource datasource = datasource(12L, "COMMON");
        when(screenMapper.selectById(7L)).thenReturn(screen);
        when(dsMapper.selectById(12L)).thenReturn(datasource);

        assertThatThrownBy(() -> service.saveCanvas(saveRequest(screen.getId(), 3, retailAumChart(), retailStyle())))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(canvasMapper, never()).bumpVersion(anyLong(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void publishRejectsCommonDatasourceForRetailTemplateBeforePublishedCas() {
        RptScreen screen = screen(7L, 3, "RETAIL");
        screen.setCanvasStyleJson(styleJson(retailStyle()));
        screen.setCanvasDraftJson("{\"schemaVersion\":1,\"components\":["
                + "{\"component\":\"ChartWidget\",\"id\":\"w-aum\",\"blockId\":31,"
                + "\"propValue\":{\"bindingKey\":\"retailAum\"},"
                + "\"bindJson\":\"{\\\"dsId\\\":12,\\\"period\\\":\\\"LATEST\\\","
                + "\\\"fields\\\":{\\\"value\\\":\\\"aum\\\"},"
                + "\\\"units\\\":{\\\"value\\\":\\\"YUAN\\\"}}\"}]}" );
        RptScreenBlock block = new RptScreenBlock();
        block.setId(31L);
        block.setScreenId(7L);
        block.setComponentType("CODE");
        block.setBindJson("{\"dsId\":12,\"period\":\"LATEST\","
                + "\"fields\":{\"value\":\"aum\"},\"units\":{\"value\":\"YUAN\"}}");
        block.setStyleJson("{}");
        block.setDrillJson("{}");
        when(screenMapper.selectById(7L)).thenReturn(screen);
        when(blockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(block));
        when(dsMapper.selectById(12L)).thenReturn(datasource(12L, "COMMON"));

        ScreenCanvasPublishReqDTO req = new ScreenCanvasPublishReqDTO();
        req.setScreenId(7L);
        req.setExpectedVersion(3);
        req.setReason("发布零售屏");

        assertThatThrownBy(() -> service.publishCanvas(req))
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_BIZ_LINE_MISMATCH.getCode());
        verify(canvasMapper, never()).applyPublishedCas(anyLong(), anyInt(), anyString(), anyString(),
                anyString(), anyInt(), anyString());
    }

    private RptScreen screen(long id, int version, String bizLine) {
        RptScreen screen = new RptScreen();
        screen.setId(id);
        screen.setScreenCode("SCR_RETAIL");
        screen.setScreenName("零售总览");
        screen.setViewLevel("BRANCH");
        screen.setBizLine(bizLine);
        screen.setOrgScopeMode("LEGACY_CONTEXT");
        screen.setCanvasVersion(version);
        return screen;
    }

    private RptScreenDatasource datasource(long id, String bizLine) {
        RptScreenDatasource datasource = new RptScreenDatasource();
        datasource.setId(id);
        datasource.setBizLine(bizLine);
        datasource.setDsType("SINGLE");
        datasource.setSourceKind("WIDE_TABLE");
        datasource.setStatus("ACTIVE");
        datasource.setConfigJson("{\"table\":\"ORG_INDEX_RESULT\","
                + "\"metrics\":[{\"metricCode\":\"AUM\",\"metricName\":\"aum\",\"slot\":1}]}");
        return datasource;
    }

    private CanvasStyleDTO retailStyle() {
        CodeScreenPresentationDTO presentation = new CodeScreenPresentationDTO();
        presentation.setType("CODE");
        presentation.setTemplate("retail-overview-v1");
        CanvasStyleDTO style = new CanvasStyleDTO();
        style.setPresentation(presentation);
        return style;
    }

    private String styleJson(CanvasStyleDTO style) {
        return "{\"presentation\":{\"type\":\"CODE\",\"template\":\""
                + style.getPresentation().getTemplate() + "\"}}";
    }

    private ScreenCanvasSaveReqDTO saveRequest(long screenId, int expected,
                                               CanvasComponentDTO chart, CanvasStyleDTO style) {
        ScreenCanvasSaveReqDTO req = new ScreenCanvasSaveReqDTO();
        req.setScreenId(screenId);
        req.setExpectedVersion(expected);
        req.setCanvasStyle(style);
        req.setComponents(List.of(chart));
        return req;
    }

    private CanvasComponentDTO retailAumChart() {
        CanvasComponentDTO chart = new CanvasComponentDTO();
        chart.setId("w-aum");
        chart.setComponent("ChartWidget");
        chart.setPropValue(Map.of("bindingKey", "retailAum"));
        chart.setBindJson("{\"dsId\":12,\"period\":\"LATEST\","
                + "\"fields\":{\"value\":\"aum\"},\"units\":{\"value\":\"YUAN\"}}");
        return chart;
    }
}
