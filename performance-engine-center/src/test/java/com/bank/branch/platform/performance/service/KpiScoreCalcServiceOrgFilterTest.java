package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.performance.controller.dto.KpiScoreGroupPageDTO;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfKpiScore;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.KpiScopeFilter;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiCalcLogMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiScoreMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricCalcTaskMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.service.engine.SqlExecutor;
import com.bank.branch.platform.governance.api.PersonTagApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 带 orgCode 的 KPI 结果查询范围交集测试。
 *
 * <p>这些测试只验证服务端范围解析和 Mapper 入参，不连接真实数据库。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KpiScoreCalcServiceOrgFilterTest {

    private static final LocalDate DATA_DATE = LocalDate.of(2026, 9, 28);

    @Mock private PerfMetricCalcTaskMapper taskMapper;
    @Mock private PerfKpiSchemeMapper schemeMapper;
    @Mock private PerfKpiItemMapper itemMapper;
    @Mock private PerfTargetPlanMapper targetPlanMapper;
    @Mock private PerfTargetValueMapper targetValueMapper;
    @Mock private PerfKpiScoreMapper scoreMapper;
    @Mock private MetricDefService metricDefService;
    @Mock private KpiScoreFormulaService formulaService;
    @Mock private EmpIndexResultMapper empIndexResultMapper;
    @Mock private OrgIndexResultMapper orgIndexResultMapper;
    @Mock private CustIndexResultMapper custIndexResultMapper;
    @Mock private PerfKpiCalcLogMapper kpiCalcLogMapper;
    @Mock private UserApi userApi;
    @Mock private OrgApi orgApi;
    @Mock private PersonTagApi personTagApi;
    @Mock private SqlExecutor sqlExecutor;

    @InjectMocks private KpiScoreCalcService service;

    @AfterEach
    void clearScope() {
        DataScopeContext.clear();
    }

    @Test
    void orgScopedResults_allScopeIntersectsRequestedOrgAndPublishesScopeMarker() {
        setScope(DataScopeType.ALL, null, null, null);
        stubRequestedOrg("ORG1", List.of("U1", "U2"), user("U1", "E001"), user("U2", "E002"));
        stubEmptyResultQuery();

        KpiScoreGroupPageDTO page = service.pageScoreGroups(
                DATA_DATE, "KPI_A", null, null, " ORG1 ", 1, 20);

        KpiScopeFilter scope = capturedScope();
        assertThat(scope.isScopeAll()).isFalse();
        assertThat(scope.getOrgCodes()).containsExactly("ORG1");
        assertThat(scope.getEmpIds()).containsExactly("E001", "E002");
        assertThat(page.getScopeOrgCode()).isEqualTo("ORG1");
    }

    @Test
    void orgScopedResults_orgScopeForDifferentOrgReturnsFailClosedFilter() {
        setScope(DataScopeType.ORG, "U_MANAGER", "ORG9", null);
        stubRequestedOrg("ORG1", List.of("U1"), user("U1", "E001"));
        stubEmptyResultQuery();

        service.pageScoreGroups(DATA_DATE, "KPI_A", null, null, "ORG1", 1, 20);

        KpiScopeFilter scope = capturedScope();
        assertThat(scope.isScopeAll()).isFalse();
        assertThat(scope.getEmpIds()).isEmpty();
        assertThat(scope.getOrgCodes()).isEmpty();
    }

    @Test
    void orgScopedResults_orgSubtreeAllowsOnlyMemberOrg() {
        setScope(DataScopeType.ORG_SUBTREE, "U_MANAGER", "ROOT", java.util.Set.of("ROOT", "ORG1"));
        stubRequestedOrg("ORG1", List.of("U1"), user("U1", "E001"));
        stubEmptyResultQuery();

        service.pageScoreGroups(DATA_DATE, "KPI_A", null, null, "ORG1", 1, 20);

        KpiScopeFilter scope = capturedScope();
        assertThat(scope.isScopeAll()).isFalse();
        assertThat(scope.getOrgCodes()).containsExactly("ORG1");
        assertThat(scope.getEmpIds()).containsExactly("E001");
    }

    @Test
    void orgScopedResults_selfScopeIntersectsRequestedOrgEmployeesOnly() {
        setScope(DataScopeType.SELF, "U1", "ORG9", null);
        when(userApi.getEmpIdsByOrg("ORG1")).thenReturn(List.of("U1", "U2"));
        when(userApi.getUserByEmpIds(List.of("U1", "U2")))
                .thenReturn(List.of(user("U1", "E001"), user("U2", "E002")));
        when(userApi.getUserByEmpIds(List.of("U1")))
                .thenReturn(List.of(user("U1", "E001")));
        stubEmptyResultQuery();

        service.pageScoreGroups(DATA_DATE, "KPI_A", "EMP", null, "ORG1", 1, 20);

        KpiScopeFilter scope = capturedScope("EMP");
        assertThat(scope.isScopeAll()).isFalse();
        assertThat(scope.getEmpIds()).containsExactly("E001");
        assertThat(scope.getOrgCodes()).isEmpty();
    }

    @Test
    void orgScopedResults_keywordCannotExpandRequestedOrg() {
        setScope(DataScopeType.ALL, null, null, null);
        stubRequestedOrg("ORG1", List.of("U1"), user("U1", "E001"));
        when(userApi.pageUsers(eq("outside"), eq(1), eq(5000)))
                .thenReturn(com.bank.branch.platform.common.web.PageResult.of(
                        1, 5000, 1L, List.of(user("U9", "E999"))));
        when(scoreMapper.countSubjectGroups(eq(DATA_DATE), eq("KPI_A"), isNull(), any()))
                .thenReturn(1L);

        KpiScoreGroupPageDTO page = service.pageScoreGroups(
                DATA_DATE, "KPI_A", null, "outside", "ORG1", 1, 20);

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
        verify(scoreMapper, never()).selectSubjectGroups(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void orgScopedResults_nullOrExceptionOrgResolutionFailsClosed() {
        setScope(DataScopeType.ALL, null, null, null);
        when(userApi.getEmpIdsByOrg("ORG1")).thenReturn(null);
        stubEmptyResultQuery();

        service.pageScoreGroups(DATA_DATE, "KPI_A", null, null, "ORG1", 1, 20);

        KpiScopeFilter nullScope = capturedScope();
        assertThat(nullScope.isScopeAll()).isFalse();
        assertThat(nullScope.getEmpIds()).isEmpty();
        assertThat(nullScope.getOrgCodes()).isEmpty();

        when(userApi.getEmpIdsByOrg("ORG1")).thenThrow(new IllegalStateException("directory unavailable"));
        clearInvocations(scoreMapper);
        service.pageScoreGroups(DATA_DATE, "KPI_A", null, null, "ORG1", 1, 20);

        KpiScopeFilter exceptionScope = capturedScope();
        assertThat(exceptionScope.isScopeAll()).isFalse();
        assertThat(exceptionScope.getEmpIds()).isEmpty();
        assertThat(exceptionScope.getOrgCodes()).isEmpty();
    }

    @Test
    void orgScopedResults_nullUserResolutionFailsClosed() {
        setScope(DataScopeType.ALL, null, null, null);
        when(userApi.getEmpIdsByOrg("ORG1")).thenReturn(List.of("U1"));
        when(userApi.getUserByEmpIds(List.of("U1"))).thenReturn(null);
        stubEmptyResultQuery();

        service.pageScoreGroups(DATA_DATE, "KPI_A", null, null, "ORG1", 1, 20);

        KpiScopeFilter scope = capturedScope();
        assertThat(scope.isScopeAll()).isFalse();
        assertThat(scope.getEmpIds()).isEmpty();
        assertThat(scope.getOrgCodes()).isEmpty();
    }

    @Test
    void orgScopedResults_emptyOrgOrUserListsFailClosed() {
        setScope(DataScopeType.ALL, null, null, null);
        when(userApi.getEmpIdsByOrg("ORG1")).thenReturn(List.of());
        stubEmptyResultQuery();

        service.pageScoreGroups(DATA_DATE, "KPI_A", null, null, "ORG1", 1, 20);

        KpiScopeFilter emptyOrgScope = capturedScope();
        assertThat(emptyOrgScope.isScopeAll()).isFalse();
        assertThat(emptyOrgScope.getEmpIds()).isEmpty();
        assertThat(emptyOrgScope.getOrgCodes()).isEmpty();

        clearInvocations(scoreMapper);
        when(userApi.getEmpIdsByOrg("ORG1")).thenReturn(List.of("U1"));
        when(userApi.getUserByEmpIds(List.of("U1"))).thenReturn(List.of());
        service.pageScoreGroups(DATA_DATE, "KPI_A", null, null, "ORG1", 1, 20);

        KpiScopeFilter emptyUserScope = capturedScope();
        assertThat(emptyUserScope.isScopeAll()).isFalse();
        assertThat(emptyUserScope.getEmpIds()).isEmpty();
        assertThat(emptyUserScope.getOrgCodes()).isEmpty();
    }

    @Test
    void orgScopedResults_requiresExplicitDateAndSchemeAndRejectsBlankOrgCode() {
        assertThatThrownBy(() -> service.pageScoreGroups(
                null, "KPI_A", null, null, "ORG1", 1, 20))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("dataDate");
        assertThatThrownBy(() -> service.pageScoreGroups(
                DATA_DATE, " ", null, null, "ORG1", 1, 20))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("schemeCode");
        assertThatThrownBy(() -> service.pageScoreGroups(
                DATA_DATE, "KPI_A", null, null, " ", 1, 20))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("orgCode");
        verify(scoreMapper, never()).countSubjectGroups(any(), any(), any(), any());
    }

    @Test
    void legacyResultsWithoutOrgCodeKeepScopeMarkerEmptyAndLegacySignature() {
        stubEmptyResultQuery();

        KpiScoreGroupPageDTO page = service.pageScoreGroups(DATA_DATE, "KPI_A", null, null, 1, 20);

        assertThat(page.getScopeOrgCode()).isNull();
        assertThat(capturedScope().isScopeAll()).isTrue();
    }

    @Test
    void orgScopedResults_withoutKpiScopeContextFailsClosed() {
        stubRequestedOrg("ORG1", List.of("U1"), user("U1", "E001"));
        stubEmptyResultQuery();

        service.pageScoreGroups(DATA_DATE, "KPI_A", null, null, "ORG1", 1, 20);

        KpiScopeFilter scope = capturedScope();
        assertThat(scope.isScopeAll()).isFalse();
        assertThat(scope.getEmpIds()).isEmpty();
        assertThat(scope.getOrgCodes()).isEmpty();
    }

    @Test
    void orgScopedResults_keepsInternalOrgCodeAfterNameEnrichment() {
        setScope(DataScopeType.ALL, null, null, null);
        stubRequestedOrg("ORG1", List.of("U1"), user("U1", "E001"));

        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S1");
        scheme.setSchemeCode("KPI_A");
        when(schemeMapper.selectBySchemeCode("KPI_A")).thenReturn(scheme);
        PerfKpiItem item = new PerfKpiItem();
        item.setMetricCode("M_ORG");
        when(itemMapper.selectBySchemeId("S1")).thenReturn(List.of(item));
        com.bank.branch.platform.performance.entity.PerfMetricDef metric =
                new com.bank.branch.platform.performance.entity.PerfMetricDef();
        metric.setMetricCode("M_ORG");
        metric.setMetricName("机构指标");
        metric.setBaseDim("ORG");
        when(metricDefService.getByCodeOrNull("M_ORG")).thenReturn(metric);

        when(scoreMapper.countSubjectGroups(eq(DATA_DATE), eq("KPI_A"), eq("ORG"), any()))
                .thenReturn(1L);
        com.bank.branch.platform.performance.mapper.KpiSubjectGroupRow group =
                new com.bank.branch.platform.performance.mapper.KpiSubjectGroupRow();
        group.setSubjectType("ORG");
        group.setSubjectId("ORG1");
        group.setTotalScore(java.math.BigDecimal.ONE);
        when(scoreMapper.selectSubjectGroups(eq(DATA_DATE), eq("KPI_A"), eq("ORG"), any(), eq(0), eq(20)))
                .thenReturn(List.of(group));
        PerfKpiScore score = new PerfKpiScore();
        score.setSubjectType("ORG");
        score.setSubjectId("ORG1");
        score.setMetricCode("M_ORG");
        score.setActualValue(java.math.BigDecimal.TEN);
        score.setTargetValue(java.math.BigDecimal.TEN);
        score.setBaseValue(java.math.BigDecimal.ZERO);
        score.setScore(java.math.BigDecimal.ONE);
        when(scoreMapper.selectByDateSchemeSubjects(eq(DATA_DATE), eq("KPI_A"), anyList()))
                .thenReturn(List.of(score));
        com.bank.branch.platform.auth.api.dto.OrgDTO org = new com.bank.branch.platform.auth.api.dto.OrgDTO();
        org.setOrgCode("ORG1");
        org.setOrgName("一支行");
        org.setDeptNo("D100");
        when(orgApi.getOrgsByCodes(List.of("ORG1"))).thenReturn(List.of(org));

        KpiScoreGroupPageDTO page = service.pageScoreGroups(
                DATA_DATE, "KPI_A", "ORG", null, "ORG1", 1, 20);

        assertThat(page.getScopeOrgCode()).isEqualTo("ORG1");
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getSubjectId()).isEqualTo("ORG1");
        assertThat(page.getRecords().get(0).getSubjectName()).isEqualTo("一支行");
        assertThat(capturedScope("ORG").getOrgCodes()).containsExactly("ORG1");
    }

    private void setScope(DataScopeType scope, String empId, String orgCode,
                          java.util.Set<String> subtreeCodes) {
        DataScopeContext ctx = new DataScopeContext();
        ctx.setBizType(BizType.KPI_CALC);
        ctx.setScope(scope);
        ctx.setEmpId(empId);
        ctx.setOrgCode(orgCode);
        ctx.setOrgSubtreeCodes(subtreeCodes);
        DataScopeContext.set(ctx);
    }

    private void stubRequestedOrg(String orgCode, List<String> userIds, UserDTO... users) {
        when(userApi.getEmpIdsByOrg(orgCode)).thenReturn(userIds);
        when(userApi.getUserByEmpIds(userIds)).thenReturn(List.of(users));
    }

    private void stubEmptyResultQuery() {
        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId("S1");
        scheme.setSchemeCode("KPI_A");
        when(schemeMapper.selectBySchemeCode("KPI_A")).thenReturn(scheme);
        when(itemMapper.selectBySchemeId("S1")).thenReturn(List.<PerfKpiItem>of());
        when(scoreMapper.countSubjectGroups(eq(DATA_DATE), eq("KPI_A"), isNull(), any()))
                .thenReturn(0L);
    }

    private KpiScopeFilter capturedScope() {
        return capturedScope(null);
    }

    private KpiScopeFilter capturedScope(String subjectType) {
        ArgumentCaptor<KpiScopeFilter> captor = ArgumentCaptor.forClass(KpiScopeFilter.class);
        if (subjectType == null) {
            verify(scoreMapper).countSubjectGroups(eq(DATA_DATE), eq("KPI_A"), isNull(), captor.capture());
        } else {
            verify(scoreMapper).countSubjectGroups(eq(DATA_DATE), eq("KPI_A"), eq(subjectType), captor.capture());
        }
        return captor.getValue();
    }

    private static UserDTO user(String empId, String username) {
        UserDTO user = new UserDTO();
        user.setEmpId(empId);
        user.setUsername(username);
        return user;
    }
}
