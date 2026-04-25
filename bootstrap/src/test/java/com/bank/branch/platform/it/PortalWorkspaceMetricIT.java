package com.bank.branch.platform.it;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.it.config.TestMockConfig;
import com.bank.branch.platform.it.config.TestSecurityConfig;
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
 * <p><strong>fake data 设计</strong>：</p>
 * <ul>
 *   <li>perf_metric_def: DEPOSIT/EMP/val_slot=1/ACTIVE</li>
 *   <li>perf_kpi_scheme: SCHEME_A/MONTHLY/ACTIVE</li>
 *   <li>perf_kpi_item: 引入 DEPOSIT</li>
 *   <li>sys_control: EMP 维度 latest=2026-04-01 version=V1 is_valid=1</li>
 *   <li>emp_index_result: 2026-04-01 val_1=1200000 + 2026-03-01 val_1=1000000</li>
 *   <li>预期 mom = (1200000-1000000)/1000000*100 = 20.00 → trend = "UP"</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestMockConfig.class, TestSecurityConfig.class})
@AutoConfigureMockMvc
class PortalWorkspaceMetricIT {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 仅 mock CurrentUserApi，让 WorkspaceService 拿到固定的 empId=E10001。
     * 业务模块的其他 *Api（performance.MetricApi / portal.adapter.MetricApi 等）
     * 全部走真实 Spring 装配链路，不能 mock。
     */
    @MockBean
    private CurrentUserApi currentUserApi;

    @BeforeEach
    void setUp() {
        // mock 当前用户 empId 返回 E10001（与 fake data 行键对齐）
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        Mockito.when(currentUserApi.getCurrentOrgCode()).thenReturn("HQ");
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(true);
    }

    /**
     * 端到端验证：GET /api/portal/workspace 应：
     * <ul>
     *   <li>HTTP 200</li>
     *   <li>response.data.metricCards 数组非空（至少 1 张）</li>
     *   <li>第一张卡片 metricCode = "DEPOSIT"</li>
     *   <li>trend = "UP"（来自 mom=20.00 的推导）</li>
     *   <li>currentValue = "1200000.00"（MetricCardProjection 格式化两位小数）</li>
     * </ul>
     */
    @Test
    @DisplayName("Option B.1 - GET /api/portal/workspace 应返回 metricCards 含 DEPOSIT 卡片，trend=UP")
    // UTF-8 编码由 root pom.xml surefire/failsafe argLine 全局 -Dfile.encoding=UTF-8 保证，
    // 不再需要 @SqlConfig(encoding="UTF-8") 局部声明（V1.6 reviewer §E nitpick 整改）
    @Sql(scripts = "/it/perf-fake-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/it/perf-fake-data-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_METHOD)
    void workspace_shouldReturnMetricCardsWithUpTrend_whenEmpIndexResultPresent() throws Exception {
        // mock session 通过 TestSecurityConfig 的最小化 filter（其逻辑：session.empId != null 即放行）
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("empId", "E10001");

        mockMvc.perform(get("/api/portal/workspace").session(session))
                .andExpect(status().isOk())
                // ResponseWrapper.code == 0（成功）
                .andExpect(jsonPath("$.code").value(0))
                // metricCards 数组存在且至少含 1 张卡片
                .andExpect(jsonPath("$.data.metricCards").isArray())
                .andExpect(jsonPath("$.data.metricCards.length()").value(1))
                // 第一张卡片字段断言
                .andExpect(jsonPath("$.data.metricCards[0].metricCode").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.metricCards[0].metricName").value("存款余额"))
                .andExpect(jsonPath("$.data.metricCards[0].trend").value("UP"))
                // MetricCardProjection 把 currentValue 格式化为两位小数字符串
                .andExpect(jsonPath("$.data.metricCards[0].currentValue").value("1200000.00"))
                // 单位透传
                .andExpect(jsonPath("$.data.metricCards[0].unit").value("元"));
    }
}
