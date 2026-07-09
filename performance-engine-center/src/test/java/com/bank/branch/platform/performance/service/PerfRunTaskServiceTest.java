package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.service.dto.RunTaskQuery;
import com.bank.branch.platform.performance.support.RunTaskTestDataBuilder;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PerfRunTaskService 单元测试.
 *
 * <p>覆盖 plan Task 4.2 DoD 必含场景:
 * <ul>
 *   <li>{@code page_whenAdmin_returnsAll} —— 管理员 (BizScopeApi.resolveScope 返回 ALL)
 *       时传入 Mapper 的 {@code dataScopeFilter} 必须为 {@code null}（Mapper 据此不加过滤）。</li>
 *   <li>{@code page_whenRegularUser_returnsOnlyOwnStarted} —— 普通用户 (非 ALL 范围)
 *       时 Service 自行组装 {@code "AND started_by = '<empId>'"} 片段并传 Mapper。</li>
 *   <li>{@code getById} / {@code getByTaskNo} 不存在/存在两种分支。</li>
 *   <li>{@code countByTypeAndDate} 参数透传 Mapper。</li>
 * </ul>
 *
 * <p>数据范围注入说明：Spec §5.4 钦定 "由 auth-permission-center 的 DataScopeApi.resolveScope
 * 注入 filter 片段"，而实际 auth 对外接口名为 {@link BizScopeApi}（两者同义）。本测试以
 * {@code BizScopeApi.resolveScope(empId, BizType.PERF_CONFIG)} 返回的 {@link DataScopeType}
 * 作为 "管理员 vs 普通用户" 分支信号：{@code ALL} → filter=null；其他 → filter="AND started_by='<empId>'"。
 * SQL 注入侧面向 empId 来自认证 ThreadLocal 已可信，详见 Service Javadoc。
 */
@ExtendWith(MockitoExtension.class)
class PerfRunTaskServiceTest {

    @Mock
    private PerfRunTaskMapper runTaskMapper;

    @Mock
    private CurrentUserApi currentUserApi;

    @Mock
    private BizScopeApi bizScopeApi;

    @Mock
    private PerfMetricDefMapper metricDefMapper;

    @Mock
    private AddressBookApi addressBookApi;

    @InjectMocks
    private PerfRunTaskService service;

    // ------------------------------- page: 数据范围分支 -------------------------------

    /** 全部条件为空的 RunTaskQuery（管理员/无过滤场景复用）. */
    private static final RunTaskQuery EMPTY_QUERY =
            new RunTaskQuery(null, null, null, null, null, null, null, null);

    @Test
    @DisplayName("page: 管理员 (scope=ALL) 时 Mapper 调用的 dataScopeFilter 为 null")
    void page_whenAdmin_returnsAll() {
        // Arrange: 管理员 → resolveScope 返回 ALL, Service 应把 filter 以 null 传下去
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.resolveScope("admin", BizType.PERF_CONFIG)).thenReturn(DataScopeType.ALL);
        when(runTaskMapper.countByCondition(any(), isNull())).thenReturn(3L);
        PerfRunTask a = RunTaskTestDataBuilder.task("ADMIN_A", "USER_X");
        PerfRunTask b = RunTaskTestDataBuilder.task("ADMIN_B", "USER_Y");
        PerfRunTask c = RunTaskTestDataBuilder.task("ADMIN_C", "USER_Z");
        when(runTaskMapper.selectByCondition(any(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(a, b, c));

        // Act
        PageResult<PerfRunTask> result = service.page(EMPTY_QUERY, 1, 20);

        // Assert: Mapper 确实被以 null filter 调用（两次: select + count）
        assertThat(result.getTotal()).isEqualTo(3L);
        assertThat(result.getRecords()).hasSize(3);
        verify(runTaskMapper).selectByCondition(eq(EMPTY_QUERY), isNull(), eq(0), eq(20));
        verify(runTaskMapper).countByCondition(eq(EMPTY_QUERY), isNull());
    }

    @Test
    @DisplayName("page: 普通用户 (scope=SELF_CREATED) 时 filter=\"AND started_by = '<empId>'\" 传给 Mapper")
    void page_whenRegularUser_returnsOnlyOwnStarted() {
        // Arrange: 普通用户 empId=USER_A → resolveScope 返回 SELF_CREATED (非 ALL), Service
        //         应组装 "AND started_by = 'USER_A'" 片段
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_A");
        when(bizScopeApi.resolveScope("USER_A", BizType.PERF_CONFIG))
                .thenReturn(DataScopeType.SELF_CREATED);
        String expectedFilter = "AND started_by = 'USER_A'";
        RunTaskQuery query = new RunTaskQuery("METRIC_RUN", null, null, "RUNNING", null, null, null, null);
        when(runTaskMapper.countByCondition(eq(query), eq(expectedFilter))).thenReturn(1L);
        PerfRunTask own = RunTaskTestDataBuilder.task("OWN_1", "USER_A");
        when(runTaskMapper.selectByCondition(eq(query), eq(expectedFilter), anyInt(), anyInt()))
                .thenReturn(List.of(own));

        // Act
        PageResult<PerfRunTask> result = service.page(query, 1, 20);

        // Assert: 断言 filter 参数值精确等于 "AND started_by = 'USER_A'"
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).extracting(PerfRunTask::getStartedBy).containsOnly("USER_A");
        ArgumentCaptor<String> filterCaptor = ArgumentCaptor.forClass(String.class);
        verify(runTaskMapper).selectByCondition(eq(query), filterCaptor.capture(), eq(0), eq(20));
        assertThat(filterCaptor.getValue()).isEqualTo(expectedFilter);
        verify(runTaskMapper).countByCondition(eq(query), eq(expectedFilter));
    }

    @Test
    @DisplayName("page: total=0 时跳过 select, 返回空分页")
    void page_whenNoData_returnsEmptyPage() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.resolveScope("admin", BizType.PERF_CONFIG)).thenReturn(DataScopeType.ALL);
        when(runTaskMapper.countByCondition(any(), isNull())).thenReturn(0L);

        PageResult<PerfRunTask> result = service.page(EMPTY_QUERY, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getRecords()).isEmpty();
        verify(runTaskMapper, never()).selectByCondition(any(), anyString(), anyInt(), anyInt());
    }

    // ------------------------------- getById / getByTaskNo 场景 -------------------------------

    @Test
    @DisplayName("getById: Mapper 返回 null → Optional.empty")
    void getById_whenNotFound_returnsEmpty() {
        when(runTaskMapper.selectById("NO_SUCH")).thenReturn(null);

        Optional<PerfRunTask> opt = service.getById("NO_SUCH");

        assertThat(opt).isEmpty();
    }

    @Test
    @DisplayName("getById: Mapper 返回实体 → Optional.of")
    void getById_whenFound_returnsOptional() {
        PerfRunTask t = RunTaskTestDataBuilder.task("GET_ID", "USER_X");
        when(runTaskMapper.selectById(t.getId())).thenReturn(t);

        Optional<PerfRunTask> opt = service.getById(t.getId());

        assertThat(opt).isPresent();
        assertThat(opt.get().getTaskKey()).isEqualTo("TEST_RT_GET_ID");
    }

    @Test
    @DisplayName("getByTaskNo: Mapper 返回实体 → Optional.of")
    void getByTaskNo_whenFound_returnsOptional() {
        PerfRunTask t = RunTaskTestDataBuilder.task("BY_NO", "USER_Y");
        when(runTaskMapper.selectByTaskNo("TEST_RT_BY_NO")).thenReturn(t);

        Optional<PerfRunTask> opt = service.getByTaskNo("TEST_RT_BY_NO");

        assertThat(opt).isPresent();
        assertThat(opt.get().getStartedBy()).isEqualTo("USER_Y");
    }

    @Test
    @DisplayName("getByTaskNo: Mapper 返回 null → Optional.empty")
    void getByTaskNo_whenNotFound_returnsEmpty() {
        when(runTaskMapper.selectByTaskNo("NO_SUCH_TASK")).thenReturn(null);

        Optional<PerfRunTask> opt = service.getByTaskNo("NO_SUCH_TASK");

        assertThat(opt).isEmpty();
    }

    // ------------------------------- countByTypeAndDate 场景 -------------------------------

    @Test
    @DisplayName("countByTypeAndDate: 参数透传 Mapper, 返回其结果")
    void countByTypeAndDate_delegatesMapper() {
        LocalDate today = LocalDate.now();
        when(runTaskMapper.countByTypeAndDate("METRIC_RUN", today)).thenReturn(7L);

        long count = service.countByTypeAndDate("METRIC_RUN", today);

        assertThat(count).isEqualTo(7L);
        verify(runTaskMapper).countByTypeAndDate("METRIC_RUN", today);
    }

    // ------------------------------- pageDto: DTO 增强场景 (V1.13 Task 5) -------------------------------

    @Test
    @DisplayName("pageDto: 批量补齐 taskKeyName(指标中文名) 与 startedByName(发起人姓名)，零 N+1")
    void pageDto_enrichesMetricNameAndStartedByName() {
        // Arrange: 管理员场景（scope=ALL），避免 dataScopeFilter 分支干扰本用例
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.resolveScope("admin", BizType.PERF_CONFIG)).thenReturn(DataScopeType.ALL);

        PerfRunTask t = new PerfRunTask();
        t.setId("R1");
        t.setTaskType("METRIC_RUN");
        t.setTaskKey("M_0046");
        t.setStartedBy("12094108");
        when(runTaskMapper.countByCondition(any(), isNull())).thenReturn(1L);
        when(runTaskMapper.selectByCondition(any(), isNull(), anyInt(), anyInt())).thenReturn(List.of(t));

        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_0046");
        def.setMetricName("零售一般性存款余额-员工");
        when(metricDefMapper.selectByMetricCodes(List.of("M_0046"))).thenReturn(List.of(def));

        EmployeeDTO emp = EmployeeDTO.builder().empId("12094108").empName("雷栋").build();
        when(addressBookApi.getEmployees(List.of("12094108"))).thenReturn(List.of(emp));

        RunTaskQuery query = new RunTaskQuery("METRIC_RUN", "RECALC", null, null, null, null, null, null);

        // Act
        PageResult<com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO> page =
                service.pageDto(query, 1, 20);

        // Assert
        com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO dto = page.getRecords().get(0);
        assertThat(dto.getTaskKeyName()).isEqualTo("零售一般性存款余额-员工");
        assertThat(dto.getStartedByName()).isEqualTo("雷栋");
    }

    @Test
    @DisplayName("pageDto: 指标名/发起人姓名查不到时保留 null，不抛异常")
    void pageDto_whenNameNotFound_leavesNull() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.resolveScope("admin", BizType.PERF_CONFIG)).thenReturn(DataScopeType.ALL);

        PerfRunTask t = new PerfRunTask();
        t.setId("R2");
        t.setTaskType("METRIC_RUN");
        t.setTaskKey("M_NOT_EXIST");
        t.setStartedBy("NOBODY");
        when(runTaskMapper.countByCondition(any(), isNull())).thenReturn(1L);
        when(runTaskMapper.selectByCondition(any(), isNull(), anyInt(), anyInt())).thenReturn(List.of(t));
        when(metricDefMapper.selectByMetricCodes(List.of("M_NOT_EXIST"))).thenReturn(List.of());
        when(addressBookApi.getEmployees(List.of("NOBODY"))).thenReturn(List.of());

        PageResult<com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO> page =
                service.pageDto(EMPTY_QUERY, 1, 20);

        com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO dto = page.getRecords().get(0);
        assertThat(dto.getTaskKeyName()).isNull();
        assertThat(dto.getStartedByName()).isNull();
    }
}
