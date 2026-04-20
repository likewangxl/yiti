package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.exception.AuthException;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.bank.branch.platform.performance.support.RunTaskTestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PerfRunTaskController IT: 覆盖 2 只读端点 (list / getById) 正反向 + 鉴权注解校验.
 *
 * <p>Plan Task 4.4 (L1487-1491) 钦定必含场景:
 * <ul>
 *   <li>get_whenUnauthenticated_returns401 (未登录 → AuthException → 401)</li>
 *   <li>list_regularUser_onlyShowsOwnStarted (普通用户 + 非 ALL 数据范围 → 仅见自己 started_by)</li>
 *   <li>get_whenTaskIdNotExist_returns404 (任务不存在 → PERF-40405, 业务 200 + 错误码)</li>
 * </ul>
 *
 * <p>Plan L1492 钦定: GET 方法只有 {@code @BizAuth}, 无 {@code @AuditLog} (读操作不审计)。
 *
 * <p>测试数据前缀: TEST_RT_* (由 {@link RunTaskTestDataBuilder} 统一管理);
 * Mapper 只读故通过 {@link JdbcTemplate} 直插, 基类 {@code @Transactional + @Rollback} 自动清理.
 *
 * <p>Mock 策略: 覆盖 {@link PerformanceControllerTestBase} 的默认 {@link CurrentUserApi}/{@link BizScopeApi}
 * 以便单用例按需切换 "管理员全见 / 普通用户仅见自己 / 未登录" 三类上下文。
 */
class PerfRunTaskControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.PerfRunTaskController";

    @MockBean
    private CurrentUserApi currentUserApi;

    @MockBean
    private BizScopeApi bizScopeApi;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 每个测试前默认装配 "管理员 + ALL 范围" 上下文; 个别用例可 override. */
    @BeforeEach
    void resetCurrentUser() {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        Mockito.when(currentUserApi.getCurrentOrgCode()).thenReturn("HQ");
        Mockito.when(currentUserApi.getCurrentRoleIds()).thenReturn(Set.of("R_ADMIN"));
        Mockito.when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_ADMIN"));
        Mockito.when(currentUserApi.getCurrentCandidateGroupKeys()).thenReturn(Set.of());
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(true);
        Mockito.when(bizScopeApi.resolveScope(Mockito.anyString(), Mockito.any(BizType.class)))
                .thenReturn(DataScopeType.ALL);
    }

    /** 直接走 JDBC 插入 (Mapper 只读). */
    private void insertRaw(PerfRunTask t) {
        jdbcTemplate.update(
                "INSERT INTO perf_run_task (id, task_type, task_key, data_date, data_version, "
                        + "params_json, status, started_by, start_time, end_time, error_msg, result_preview_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                t.getId(), t.getTaskType(), t.getTaskKey(), t.getDataDate(), t.getDataVersion(),
                t.getParamsJson(), t.getStatus(), t.getStartedBy(), t.getStartTime(),
                t.getEndTime(), t.getErrorMsg(), t.getResultPreviewJson()
        );
    }

    // =================== list ===================

    @Test
    void list_withDefaultPaging_returns200() throws Exception {
        PerfRunTask a = RunTaskTestDataBuilder.task("DEF_A", "USER_DEF");
        PerfRunTask b = RunTaskTestDataBuilder.task("DEF_B", "USER_DEF");
        insertRaw(a);
        insertRaw(b);

        mockMvc.perform(get("/api/perf/run-tasks")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.pageNo").value(1))
                .andExpect(jsonPath("$.page.pageSize").value(20));
    }

    @Test
    void list_withFilterByStatus_returns200() throws Exception {
        PerfRunTask running = RunTaskTestDataBuilder.task("FLT_RUN", "METRIC_RUN",
                LocalDate.now(), "RUNNING", "USER_FLT");
        PerfRunTask success = RunTaskTestDataBuilder.task("FLT_SUC", "METRIC_RUN",
                LocalDate.now(), "SUCCESS", "USER_FLT");
        insertRaw(running);
        insertRaw(success);

        mockMvc.perform(get("/api/perf/run-tasks")
                        .param("status", "SUCCESS")
                        .param("pageNo", "1")
                        .param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    /**
     * Plan L1489 钦定: 登录普通用户 + 非 ALL 数据范围 → Service 注入 started_by 过滤片段,
     * 3 条不同 started_by 数据中仅能返回自己发起的.
     */
    @Test
    void list_regularUser_onlyShowsOwnStarted() throws Exception {
        // 切换为普通用户上下文
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("USER_OWN");
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(false);
        // 非 ALL 范围触发 Service resolveScopeFilter 拼接 AND started_by = '<empId>'
        Mockito.when(bizScopeApi.resolveScope(Mockito.eq("USER_OWN"), Mockito.any(BizType.class)))
                .thenReturn(DataScopeType.SELF);

        // 3 条不同 started_by 数据: 仅 USER_OWN 的应出现在结果里
        insertRaw(RunTaskTestDataBuilder.task("OWN_MINE", "USER_OWN"));
        insertRaw(RunTaskTestDataBuilder.task("OWN_OTHER_A", "USER_A"));
        insertRaw(RunTaskTestDataBuilder.task("OWN_OTHER_B", "USER_B"));

        mockMvc.perform(get("/api/perf/run-tasks")
                        .param("pageNo", "1")
                        .param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                // 只应看到自己一条, started_by 统一为 USER_OWN
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].taskKey").value("TEST_RT_OWN_MINE"))
                .andExpect(jsonPath("$.page.records[0].startedBy").value("USER_OWN"));
    }

    // =================== getById ===================

    @Test
    void getById_whenExists_returns200() throws Exception {
        PerfRunTask plan = RunTaskTestDataBuilder.task("GET_EXIST", "USER_GET");
        insertRaw(plan);

        mockMvc.perform(get("/api/perf/run-tasks/{id}", plan.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(plan.getId()))
                .andExpect(jsonPath("$.data.taskKey").value("TEST_RT_GET_EXIST"))
                .andExpect(jsonPath("$.data.startedBy").value("USER_GET"));
    }

    /** Plan L1490 钦定: 任务 id 不存在 → PERF-40405. */
    @Test
    void get_whenTaskIdNotExist_returns404() throws Exception {
        mockMvc.perform(get("/api/perf/run-tasks/{id}", "NON_EXIST_RT_ID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-40405"));
    }

    /**
     * Plan L1488 钦定: 未登录时 Controller 调 {@code currentUserApi.getCurrentEmpId()}
     * 抛 {@link AuthException}, GlobalExceptionHandler 映射为 HTTP 401.
     */
    @Test
    void get_whenUnauthenticated_returns401() throws Exception {
        // 模拟未登录: getCurrentEmpId 抛 AuthException (对应真实运行时 AuthenticationFilter 行为)
        Mockito.when(currentUserApi.getCurrentEmpId())
                .thenThrow(new AuthException("AUTH-40105", "未登录"));

        mockMvc.perform(get("/api/perf/run-tasks/{id}", "ANY_ID"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-40105"));
    }

    // =================== 注解约束 ===================

    @Test
    void readMethods_shouldDeclareBizAuth() throws Exception {
        assertBizAuth("list", new Class<?>[]{
                String.class, String.class, LocalDate.class, int.class, int.class
        }, BizAction.LIST);
        assertBizAuth("getById", new Class<?>[]{String.class}, BizAction.READ);
    }

    /** Plan L1492 钦定: 读操作端点不得有 @AuditLog. */
    @Test
    void readMethods_shouldNotHaveAuditLog() throws Exception {
        assertNoAuditLog("list", new Class<?>[]{
                String.class, String.class, LocalDate.class, int.class, int.class
        });
        assertNoAuditLog("getById", new Class<?>[]{String.class});
    }

    // =================== helpers ===================

    private void assertBizAuth(String methodName, Class<?>[] parameterTypes, BizAction action) throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        Method method = controllerClass.getDeclaredMethod(methodName, parameterTypes);
        BizAuth bizAuth = method.getAnnotation(BizAuth.class);
        assertThat(bizAuth).as("method %s 应有 @BizAuth", methodName).isNotNull();
        assertThat(bizAuth.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(bizAuth.action()).isEqualTo(action);
    }

    private void assertNoAuditLog(String methodName, Class<?>[] parameterTypes) throws Exception {
        Class<?> controllerClass = Class.forName(CONTROLLER_FQCN);
        Method method = controllerClass.getDeclaredMethod(methodName, parameterTypes);
        AuditLog auditLog = method.getAnnotation(AuditLog.class);
        assertThat(auditLog)
                .as("method %s 为只读端点, 不得带 @AuditLog (plan Task 4.4 L1492)", methodName)
                .isNull();
    }
}
