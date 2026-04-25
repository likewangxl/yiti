package com.bank.branch.platform.it;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.it.config.TestMockConfig;
import com.bank.branch.platform.it.config.TestSecurityConfig;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Option B.1 — portal 工作台聚合端到端 IT。
 *
 * <p>验证 GET /api/portal/workspace 真实链路：</p>
 * <pre>
 *   WorkspaceController
 *     → WorkspaceService（CompletableFuture.allOf 6 路并行）
 *     → MetricAdapter.fetch(empId)
 *     → portal.adapter.MetricApi（防腐层抽象）
 *     → PerformanceMetricApiBridge（bootstrap 桥接 @Service）
 *     → performance.api.MetricApi
 *     → MetricApiImpl
 *     → KpiSchemeService.listActiveSchemes / KpiItemService / MetricDefService / SysControlService
 *     → EmpIndexResultMapper.selectSlotValuesByDates
 *     → emp_index_result 宽表
 * </pre>
 *
 * <p><strong>测试约束</strong>：</p>
 * <ul>
 *   <li>仅 mock {@link CurrentUserApi}（PT_USER 不便造 BCrypt 完整链路）</li>
 *   <li>不 mock 任何 *MetricApi（这是测试目标，必须走真实链路）</li>
 *   <li>WorkspaceService 内 6 路并行：metric 一路必须走真实链路；其他 5 路允许降级到空</li>
 *   <li>使用 mock session 携带 empId 通过 TestSecurityConfig 的最小化 filter</li>
 * </ul>
 *
 * <p><strong>fake data 4 边界 case 设计（V1.6 reviewer §D 整改）</strong>：</p>
 * <ul>
 *   <li>E10001: mom=+20.00 → trend="UP"（current 1.2M / previous 1.0M）</li>
 *   <li>E10002: mom=0.00   → trend="FLAT"（current 1.0M / previous 1.0M）</li>
 *   <li>E10003: mom=-20.00 → trend="DOWN"（current 0.8M / previous 1.0M）</li>
 *   <li>E10004: mom=null   → trend=null（previous=0 守护，calculateMom 返回 null）</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestMockConfig.class, TestSecurityConfig.class})
@AutoConfigureMockMvc
@Sql(scripts = "/it/perf-fake-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/it/perf-fake-data-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_METHOD)
class PortalWorkspaceMetricIT {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 仅 mock CurrentUserApi，让 WorkspaceService 拿到固定的 empId。
     * 业务模块的其他 *Api（performance.MetricApi / portal.adapter.MetricApi 等）
     * 全部走真实 Spring 装配链路，不能 mock。
     */
    @MockBean
    private CurrentUserApi currentUserApi;

    /**
     * 默认 mock 状态：empId=E10001（UP case）+ 其他方法 mock 默认空集，
     * 防止 WorkspaceService 内任一并行路径深入鉴权时 NPE（V1.6 reviewer §B-1 整改：
     * 原仅 mock 3 个方法易脆，现补齐全部 7 个方法的 stub）。
     * 各 @Test 方法可在 method 内 override empId stub 切换 case。
     */
    @BeforeEach
    void setUp() {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        Mockito.when(currentUserApi.getCurrentOrgCode()).thenReturn("HQ");
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(true);
        // V1.6 reviewer §B-1 补齐：避免未来扩展触发深层鉴权时 NPE
        Mockito.when(currentUserApi.getCurrentUserContext())
                .thenReturn(Mockito.mock(CurrentUserContext.class));
        Mockito.when(currentUserApi.getCurrentRoleIds()).thenReturn(Collections.emptySet());
        Mockito.when(currentUserApi.getCurrentRoleCodes()).thenReturn(Collections.emptySet());
        Mockito.when(currentUserApi.getCurrentCandidateGroupKeys()).thenReturn(Collections.emptySet());
    }

    // ======================================================================
    // 4 边界 case：UP / FLAT / DOWN / null
    // ======================================================================

    @Test
    @DisplayName("Option B.1 - empId=E10001 → mom=+20%, trend=UP")
    void workspace_shouldReturnTrendUp_whenMomPositive() throws Exception {
        // setUp 已默认 stub E10001，无需重复
        assertWorkspaceMetric("E10001")
                .andExpect(jsonPath("$.data.metricCards[0].trend").value("UP"))
                .andExpect(jsonPath("$.data.metricCards[0].currentValue").value("1200000.00"));
    }

    @Test
    @DisplayName("Option B.1 - empId=E10002 → mom=0, trend=FLAT")
    void workspace_shouldReturnTrendFlat_whenMomZero() throws Exception {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("E10002");
        assertWorkspaceMetric("E10002")
                .andExpect(jsonPath("$.data.metricCards[0].trend").value("FLAT"))
                .andExpect(jsonPath("$.data.metricCards[0].currentValue").value("1000000.00"));
    }

    @Test
    @DisplayName("Option B.1 - empId=E10003 → mom=-20%, trend=DOWN")
    void workspace_shouldReturnTrendDown_whenMomNegative() throws Exception {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("E10003");
        assertWorkspaceMetric("E10003")
                .andExpect(jsonPath("$.data.metricCards[0].trend").value("DOWN"))
                .andExpect(jsonPath("$.data.metricCards[0].currentValue").value("800000.00"));
    }

    @Test
    @DisplayName("Option B.1 - empId=E10004 → previous=0, mom=null, trend=null")
    void workspace_shouldReturnTrendNull_whenPreviousIsZero() throws Exception {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("E10004");
        assertWorkspaceMetric("E10004")
                // mom=null → trend=null（PerformanceMetricApiBridge.deriveTrend 返回 null 分支）
                .andExpect(jsonPath("$.data.metricCards[0].trend").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.metricCards[0].currentValue").value("1000000.00"));
    }

    // ======================================================================
    // helper：4 个 case 共享的基础断言（HTTP 200 + 卡片基础字段）
    // ======================================================================

    /**
     * 共享断言：GET /api/portal/workspace 返回成功响应 + metricCards 含 1 张 DEPOSIT 卡片。
     * 调用方继续链式 jsonPath 校验 trend/currentValue 等差异字段。
     */
    private ResultActions assertWorkspaceMetric(String empId) throws Exception {
        // mock session 携带 empId 通过 TestSecurityConfig 的最小化 filter
        // （filter 逻辑：session.empId != null 即放行）
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("empId", empId);

        return mockMvc.perform(get("/api/portal/workspace").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.metricCards").isArray())
                .andExpect(jsonPath("$.data.metricCards.length()").value(1))
                .andExpect(jsonPath("$.data.metricCards[0].metricCode").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.metricCards[0].metricName").value("存款余额"))
                .andExpect(jsonPath("$.data.metricCards[0].unit").value("元"));
    }
}
