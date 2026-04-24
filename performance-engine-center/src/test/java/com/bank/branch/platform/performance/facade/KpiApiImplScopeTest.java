package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.performance.api.dto.KpiResultDTO;
import com.bank.branch.platform.performance.entity.KpiResult;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import com.bank.branch.platform.performance.service.KpiItemService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KpiApiImpl Task Q7.3 数据范围注入示范测试.
 *
 * <p>Q7.3 以 KpiApi.getKpiHistoryWithScope 为 KPI 查询示范：按 3 种 scope 验证片段 + 参数注入.
 * <ul>
 *   <li>ALL → scopeFragment=空 / scopeParams=空（管理员 KPI 全见）</li>
 *   <li>SELF → scopeFragment="emp_id = #{scopeParams.ownerEmpId}"</li>
 *   <li>ctx=null → fail-close "1=0"</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class KpiApiImplScopeTest {

    @Mock
    private KpiSchemeService kpiSchemeService;

    @Mock
    private KpiItemService kpiItemService;

    @Mock
    private KpiResultMapper kpiResultMapper;

    @Mock
    private BizScopeApi bizScopeApi;

    @Mock
    private CurrentUserApi currentUserApi;

    private PerfScopeHelper perfScopeHelper;

    private KpiApiImpl facade;

    @BeforeEach
    void setUp() {
        this.perfScopeHelper = new PerfScopeHelper(bizScopeApi, null);
        this.facade = new KpiApiImpl(kpiSchemeService, kpiItemService, kpiResultMapper,
                currentUserApi, perfScopeHelper);
    }

    @Test
    @DisplayName("getKpiHistoryWithScope: ALL → 透传空 scope 片段，看到全部历史 KPI")
    void whenScopeAll_noFilter() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.buildScopeContext(eq("admin"), eq(BizType.PERF_CONFIG), eq(BizAction.LIST)))
                .thenReturn(new DataScopeContext(
                        DataScopeType.ALL, "admin", "HQ", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));
        KpiResult k = new KpiResult();
        k.setId(100L);
        k.setEmpId("TGT_EMP");
        k.setCycleType("MONTHLY");
        k.setCycleDate(LocalDate.of(2026, 4, 1));
        k.setAsOfDate(LocalDate.of(2026, 4, 23));
        k.setKpiTotalScore(new BigDecimal("88.50"));
        when(kpiResultMapper.selectByEmpCycleRangeWithScope(
                eq("TGT_EMP"), eq("MONTHLY"), any(LocalDate.class), any(LocalDate.class),
                eq(""), any()))
                .thenReturn(List.of(k));

        List<KpiResultDTO> result = facade.getKpiHistoryWithScope(
                "TGT_EMP", "MONTHLY", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));

        assertThat(result).hasSize(1);
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> paramsCap = ArgumentCaptor.forClass(Map.class);
        verify(kpiResultMapper).selectByEmpCycleRangeWithScope(
                eq("TGT_EMP"), eq("MONTHLY"), any(LocalDate.class), any(LocalDate.class),
                sqlCap.capture(), paramsCap.capture());
        assertThat(sqlCap.getValue()).isEmpty();
        assertThat(paramsCap.getValue()).isEmpty();
    }

    @Test
    @DisplayName("getKpiHistoryWithScope: SELF → 注入 emp_id 过滤片段")
    void whenScopeSelf_injectsEmpIdFragment() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_SELF");
        when(bizScopeApi.buildScopeContext(any(), any(), any()))
                .thenReturn(new DataScopeContext(
                        DataScopeType.SELF, "USER_SELF", "BRANCH_01", Set.of(),
                        BizType.PERF_CONFIG, BizAction.LIST));
        when(kpiResultMapper.selectByEmpCycleRangeWithScope(
                anyString(), anyString(), any(LocalDate.class), any(LocalDate.class),
                anyString(), any()))
                .thenReturn(Collections.emptyList());

        facade.getKpiHistoryWithScope("USER_SELF", "MONTHLY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> paramsCap = ArgumentCaptor.forClass(Map.class);
        verify(kpiResultMapper).selectByEmpCycleRangeWithScope(
                eq("USER_SELF"), eq("MONTHLY"), any(LocalDate.class), any(LocalDate.class),
                sqlCap.capture(), paramsCap.capture());
        assertThat(sqlCap.getValue()).isEqualTo("emp_id = #{scopeParams.ownerEmpId}");
        assertThat(paramsCap.getValue()).containsEntry("ownerEmpId", "USER_SELF");
    }

    @Test
    @DisplayName("getKpiHistoryWithScope: ctx=null → fail-close, Mapper 接收 \"1=0\"")
    void whenNoContext_failClose() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_NO");
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(null);
        when(kpiResultMapper.selectByEmpCycleRangeWithScope(
                anyString(), anyString(), any(LocalDate.class), any(LocalDate.class),
                eq("1=0"), any()))
                .thenReturn(Collections.emptyList());

        List<KpiResultDTO> result = facade.getKpiHistoryWithScope(
                "USER_NO", "MONTHLY", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));

        assertThat(result).isEmpty();
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(kpiResultMapper).selectByEmpCycleRangeWithScope(
                eq("USER_NO"), eq("MONTHLY"), any(LocalDate.class), any(LocalDate.class),
                sqlCap.capture(), any());
        assertThat(sqlCap.getValue()).isEqualTo("1=0");
    }
}
