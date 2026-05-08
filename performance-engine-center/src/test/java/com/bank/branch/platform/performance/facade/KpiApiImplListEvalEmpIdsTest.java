package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import com.bank.branch.platform.performance.service.KpiItemService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * V1.14 任务 A 单元测试：{@link KpiApiImpl#listEvalEmpIds}.
 *
 * <p>需求来源：报表「动态指标查询」页面的「考核员工选择器」需要 KPI 绑定员工真相数据。
 * 由于本期不建 KPI-员工绑定表，方法从 {@code kpi_result distinct emp_id} 反查近期被考核员工。
 *
 * <p>覆盖矩阵：
 * <ul>
 *   <li>normal：正常入参 → 透传 Mapper 结果</li>
 *   <li>nullOrgCodes：管理员场景，不限机构 → Mapper 收 null 机构集合不过滤</li>
 *   <li>emptyOrgCodes：fail-close，显式空集合 → 不查 Mapper 直接返回 empty</li>
 *   <li>nullSinceDate：参数缺失 → 不查 Mapper 直接返回 empty</li>
 *   <li>mapperReturnsNull：Mapper 返回 null → 适配为 empty list（非空契约）</li>
 * </ul>
 */
class KpiApiImplListEvalEmpIdsTest extends PerformanceServiceTestBase {

    @Mock
    private KpiSchemeService kpiSchemeService;

    @Mock
    private KpiItemService kpiItemService;

    @Mock
    private KpiResultMapper kpiResultMapper;

    @Mock
    private CurrentUserApi currentUserApi;

    @Mock
    private PerfScopeHelper perfScopeHelper;

    @InjectMocks
    private KpiApiImpl kpiApi;

    @Test
    @DisplayName("listEvalEmpIds: 正常入参 → 透传 Mapper 结果（机构子树过滤生效）")
    void listEvalEmpIds_normal() {
        LocalDate since = LocalDate.of(2026, 2, 5);
        Set<String> orgs = Set.of("BR_001", "SBR_001_01");
        when(kpiResultMapper.selectEvalEmpIds(since, orgs))
                .thenReturn(List.of("E001", "E002", "E003"));

        List<String> ids = kpiApi.listEvalEmpIds(since, orgs);

        assertThat(ids).containsExactly("E001", "E002", "E003");
    }

    @Test
    @DisplayName("listEvalEmpIds: orgCodes=null → 不限机构（管理员场景），透传 Mapper")
    void listEvalEmpIds_nullOrgCodes_passthrough() {
        LocalDate since = LocalDate.of(2026, 2, 5);
        when(kpiResultMapper.selectEvalEmpIds(since, null))
                .thenReturn(List.of("E001"));

        List<String> ids = kpiApi.listEvalEmpIds(since, null);

        assertThat(ids).containsExactly("E001");
    }

    @Test
    @DisplayName("listEvalEmpIds: orgCodes=空集合 → fail-close 直接返回空，不查 Mapper")
    void listEvalEmpIds_emptyOrgCodes_failClose() {
        Set<String> empty = Collections.emptySet();

        List<String> ids = kpiApi.listEvalEmpIds(LocalDate.now(), empty);

        assertThat(ids).isEmpty();
        verifyNoInteractions(kpiResultMapper);
    }

    @Test
    @DisplayName("listEvalEmpIds: sinceDate=null → 直接返回空，不查 Mapper")
    void listEvalEmpIds_nullSinceDate_returnsEmpty() {
        List<String> ids = kpiApi.listEvalEmpIds(null, Set.of("BR_001"));

        assertThat(ids).isEmpty();
        verifyNoInteractions(kpiResultMapper);
    }

    @Test
    @DisplayName("listEvalEmpIds: Mapper 返回 null → 适配为空列表（非空契约）")
    void listEvalEmpIds_mapperReturnsNull_returnsEmpty() {
        LocalDate since = LocalDate.now();
        when(kpiResultMapper.selectEvalEmpIds(since, null)).thenReturn(null);

        List<String> ids = kpiApi.listEvalEmpIds(since, null);

        assertThat(ids).isEmpty();
    }
}
