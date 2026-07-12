package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenTryRunReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.support.ScreenMetricSlotDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.bank.branch.platform.report.dto.resp.ScreenDatasourceRespDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScreenDatasourceService 单测（Mockito，不起 Spring）.
 */
@ExtendWith(MockitoExtension.class)
class ScreenDatasourceServiceTest {

    @Mock private RptScreenDatasourceMapper dsMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private ScreenQueryEngine engine;
    @Mock private ScreenMetricSlotDao slotDao;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private AuditApi auditApi;

    private ScreenDatasourceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenDatasourceServiceImpl(dsMapper, blockMapper, engine, slotDao, currentUserApi, auditApi);
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    }

    private ScreenDatasourceSaveReqDTO wideReq() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("员工存款");
        req.setSourceKind("WIDE_TABLE");
        req.setConfigJson("{\"table\":\"EMP_INDEX_RESULT\",\"metrics\":[{\"metricCode\":\"M_0001\"}]}");
        return req;
    }

    @Test
    void save_wideTable_translatesSlotsAndForcesTimeseries() {
        when(slotDao.selectByCodes(anyList())).thenReturn(
                List.of(new ScreenMetricSlotDao.MetricSlot("M_0001", "存款余额", 3, "EMP")));

        service.save(wideReq());

        ArgumentCaptor<RptScreenDatasource> cap = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(dsMapper).insert(cap.capture());
        RptScreenDatasource saved = cap.getValue();
        assertThat(saved.getDsType()).isEqualTo("TIMESERIES");
        assertThat(saved.getDsCode()).startsWith("SCRDS_");
        assertThat(saved.getCreatedBy()).isEqualTo("E001");
        assertThat(saved.getConfigJson()).contains("\"slot\":3");
        assertThat(saved.getConfigJson()).contains("\"metricName\":\"存款余额\"");
        assertThat(saved.getConfigJson()).contains("\"subjectParam\":\"empId\"");
    }

    @Test
    void save_wideTable_unknownMetric_throws43009() {
        when(slotDao.selectByCodes(anyList())).thenReturn(List.of());
        assertThatThrownBy(() -> service.save(wideReq()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
        verify(dsMapper, never()).insert(any(RptScreenDatasource.class));
    }

    @Test
    void save_wideTable_dimMismatch_throws43009() {
        when(slotDao.selectByCodes(anyList())).thenReturn(
                List.of(new ScreenMetricSlotDao.MetricSlot("M_0001", "存款余额", 3, "ORG")));
        assertThatThrownBy(() -> service.save(wideReq()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_kpi_illegalCycleType_throws43009() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("KPI");
        req.setSourceKind("KPI_RESULT");
        req.setConfigJson("{\"cycleType\":\"HOURLY\"}");
        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43009");
    }

    @Test
    void save_customSql_timeseriesWithoutDateCol_throws43003() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("趋势SQL");
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("TIMESERIES");
        req.setConfigJson("{\"sql\":\"SELECT 1\",\"dateCol\":null}");
        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43003");
    }

    @Test
    void save_customSql_invalidSql_propagates43002() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("坏SQL");
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("SINGLE");
        req.setConfigJson("{\"sql\":\"SELECT * FROM PT_USER\",\"dateCol\":null}");
        doThrow(new RptException(RptErrorCode.SCREEN_DS_SQL_INVALID))
                .when(engine).validateCustomSql("SELECT * FROM PT_USER");
        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43002");
    }

    @Test
    void save_customSql_ok_audits() {
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("流程数");
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("SINGLE");
        req.setConfigJson("{\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK\",\"dateCol\":null}");
        req.setReason("配置大屏流程组件");

        service.save(req);

        verify(dsMapper).insert(any(RptScreenDatasource.class));
        verify(auditApi).log(any());
    }

    @Test
    void delete_referenced_throws43007() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setId(9L);
        when(dsMapper.selectById(9L)).thenReturn(ds);
        when(blockMapper.countByDsId(9L)).thenReturn(2);
        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43007");
        verify(dsMapper, never()).deleteById(9L);
    }

    @Test
    void delete_unreferenced_logicDeletes() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setId(9L);
        when(dsMapper.selectById(9L)).thenReturn(ds);
        when(blockMapper.countByDsId(9L)).thenReturn(0);
        service.delete(9L);
        verify(dsMapper).deleteById(9L);
    }

    @Test
    void queryData_notFoundOrDisabled_throws43001() {
        when(dsMapper.selectById(1L)).thenReturn(null);
        ScreenDataReqDTO req = new ScreenDataReqDTO();
        req.setDsId(1L);
        assertThatThrownBy(() -> service.queryData(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43001");

        RptScreenDatasource disabled = new RptScreenDatasource();
        disabled.setId(2L);
        disabled.setStatus("DISABLED");
        when(dsMapper.selectById(2L)).thenReturn(disabled);
        req.setDsId(2L);
        assertThatThrownBy(() -> service.queryData(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43001");
    }

    @Test
    void tryRun_delegatesToEngineWithLimit10_andAudits() {
        ScreenTryRunReqDTO req = new ScreenTryRunReqDTO();
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("SINGLE");
        req.setConfigJson("{\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK\",\"dateCol\":null}");
        req.setContextParams(Map.of("orgCode", "610100"));
        req.setReason("试跑");
        when(engine.tryRun(any(), any(), any())).thenReturn(new ScreenDataRespDTO(List.of("cnt"), List.of()));

        ScreenDataRespDTO resp = service.tryRun(req);

        assertThat(resp.getColumns()).containsExactly("cnt");
        verify(engine).tryRun(any(), any(), any());
        verify(auditApi).log(any());
    }

    @Test
    void tryRun_engineThrows_stillAudits() {
        ScreenTryRunReqDTO req = new ScreenTryRunReqDTO();
        req.setSourceKind("CUSTOM_SQL");
        req.setDsType("SINGLE");
        req.setConfigJson("{\"sql\":\"SELECT COUNT(*) AS cnt FROM ACT_RU_TASK\",\"dateCol\":null}");
        req.setReason("试跑失败场景");
        doThrow(new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED))
                .when(engine).tryRun(any(), any(), any());

        assertThatThrownBy(() -> service.tryRun(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43008");
        // 修复①验证点：即使 engine.tryRun 执行抛异常，safelyAudit 仍需在 finally 中留痕
        verify(auditApi).log(any());
    }

    @Test
    void update_notFound_throws43001() {
        when(dsMapper.selectById(5L)).thenReturn(null);
        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("不存在的数据源");
        req.setSourceKind("KPI_RESULT");
        req.setConfigJson("{\"cycleType\":\"MONTHLY\"}");

        assertThatThrownBy(() -> service.update(5L, req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-43001");
        verify(dsMapper, never()).updateById(any(RptScreenDatasource.class));
    }

    @Test
    void update_keepsDsCodeUnchanged() {
        RptScreenDatasource existing = new RptScreenDatasource();
        existing.setId(6L);
        existing.setDsCode("SCRDS_OLD");
        when(dsMapper.selectById(6L)).thenReturn(existing);

        ScreenDatasourceSaveReqDTO req = new ScreenDatasourceSaveReqDTO();
        req.setDsName("更新后的名称");
        req.setSourceKind("KPI_RESULT");
        req.setConfigJson("{\"cycleType\":\"QUARTERLY\"}");

        service.update(6L, req);

        ArgumentCaptor<RptScreenDatasource> cap = ArgumentCaptor.forClass(RptScreenDatasource.class);
        verify(dsMapper).updateById(cap.capture());
        // dsCode 由 save 时一次性生成，update 不应重新生成/覆盖
        assertThat(cap.getValue().getDsCode()).isEqualTo("SCRDS_OLD");
        assertThat(cap.getValue().getDsName()).isEqualTo("更新后的名称");
    }

    @Test
    void list_filtersByDsTypeAndKeyword() {
        RptScreenDatasource e = new RptScreenDatasource();
        e.setId(7L);
        e.setDsCode("SCRDS_ABC12345");
        e.setDsName("存款趋势-关键指标");
        e.setDsType("TIMESERIES");
        e.setCreatedTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(dsMapper.selectList(any())).thenReturn(List.of(e));

        List<ScreenDatasourceRespDTO> result = service.list("TIMESERIES", "关键");

        assertThat(result).hasSize(1);
        ScreenDatasourceRespDTO dto = result.get(0);
        assertThat(dto.getDsCode()).isEqualTo("SCRDS_ABC12345");
        assertThat(dto.getDsName()).isEqualTo("存款趋势-关键指标");
        assertThat(dto.getDsType()).isEqualTo("TIMESERIES");
        assertThat(dto.getCreatedTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}
