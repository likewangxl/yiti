package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.support.RunTaskTestDataBuilder;
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

    @InjectMocks
    private PerfRunTaskService service;

    // ------------------------------- page: 数据范围分支 -------------------------------

    @Test
    @DisplayName("page: 管理员 (scope=ALL) 时 Mapper 调用的 dataScopeFilter 为 null")
    void page_whenAdmin_returnsAll() {
        // Arrange: 管理员 → resolveScope 返回 ALL, Service 应把 filter 以 null 传下去
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.resolveScope("admin", BizType.PERF_CONFIG)).thenReturn(DataScopeType.ALL);
        when(runTaskMapper.countByCondition(any(), any(), any(), any(), isNull())).thenReturn(3L);
        PerfRunTask a = RunTaskTestDataBuilder.task("ADMIN_A", "USER_X");
        PerfRunTask b = RunTaskTestDataBuilder.task("ADMIN_B", "USER_Y");
        PerfRunTask c = RunTaskTestDataBuilder.task("ADMIN_C", "USER_Z");
        when(runTaskMapper.selectByCondition(any(), any(), any(), any(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(a, b, c));

        // Act
        PageResult<PerfRunTask> result = service.page(null, null, null, null, 1, 20);

        // Assert: Mapper 确实被以 null filter 调用（两次: select + count）
        assertThat(result.getTotal()).isEqualTo(3L);
        assertThat(result.getRecords()).hasSize(3);
        verify(runTaskMapper).selectByCondition(isNull(), isNull(), isNull(), isNull(),
                isNull(), eq(0), eq(20));
        verify(runTaskMapper).countByCondition(isNull(), isNull(), isNull(), isNull(), isNull());
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
        when(runTaskMapper.countByCondition(any(), any(), any(), any(), eq(expectedFilter))).thenReturn(1L);
        PerfRunTask own = RunTaskTestDataBuilder.task("OWN_1", "USER_A");
        when(runTaskMapper.selectByCondition(any(), any(), any(), any(), eq(expectedFilter), anyInt(), anyInt()))
                .thenReturn(List.of(own));

        // Act
        PageResult<PerfRunTask> result = service.page("METRIC_RUN", null, "RUNNING", null, 1, 20);

        // Assert: 断言 filter 参数值精确等于 "AND started_by = 'USER_A'"
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).extracting(PerfRunTask::getStartedBy).containsOnly("USER_A");
        ArgumentCaptor<String> filterCaptor = ArgumentCaptor.forClass(String.class);
        verify(runTaskMapper).selectByCondition(eq("METRIC_RUN"), isNull(), eq("RUNNING"), isNull(),
                filterCaptor.capture(), eq(0), eq(20));
        assertThat(filterCaptor.getValue()).isEqualTo(expectedFilter);
        verify(runTaskMapper).countByCondition(eq("METRIC_RUN"), isNull(), eq("RUNNING"), isNull(),
                eq(expectedFilter));
    }

    @Test
    @DisplayName("page: total=0 时跳过 select, 返回空分页")
    void page_whenNoData_returnsEmptyPage() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        when(bizScopeApi.resolveScope("admin", BizType.PERF_CONFIG)).thenReturn(DataScopeType.ALL);
        when(runTaskMapper.countByCondition(any(), any(), any(), any(), isNull())).thenReturn(0L);

        PageResult<PerfRunTask> result = service.page(null, null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getRecords()).isEmpty();
        verify(runTaskMapper, never()).selectByCondition(anyString(), anyString(), anyString(),
                any(), anyString(), anyInt(), anyInt());
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
}
