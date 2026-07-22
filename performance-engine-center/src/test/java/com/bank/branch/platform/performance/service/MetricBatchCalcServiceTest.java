package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricCalcTask;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.mapper.PerfMetricCalcLogMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricCalcTaskMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricBatchCalcService 单元测试。
 *
 * <p>核心：1/2/3 级指标批量计算只选「已发布」指标——status ∈ {ACTIVE, PUBLISHED}
 * （生产 DDL 默认值 ACTIVE 与显式 PUBLISHED 等价），排除草稿(DRAFT)/已停用(DISABLED)。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricBatchCalcServiceTest {

    @Mock
    private PerfMetricCalcTaskMapper taskMapper;
    @Mock
    private PerfMetricCalcLogMapper logMapper;
    @Mock
    private PerfMetricDefMapper metricDefMapper;
    @Mock
    private MetricCalcService metricCalcService;
    @Mock
    private PerfRunTaskMapper perfRunTaskMapper;

    @InjectMocks
    private MetricBatchCalcService service;

    /** 预热 PerfMetricDef 的 lambda 缓存，使 LambdaQueryWrapper.getTargetSql() 可在纯单测中渲染. */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PerfMetricDef.class);
    }

    @Test
    @DisplayName("execute: 选指标 status 条件为 IN (ACTIVE, PUBLISHED)，只取已发布指标")
    void execute_selectsOnlyPublishedMetrics() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<PerfMetricDef>> cap =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        // 返回空指标集合 → execute 在「无指标」处早退，不触发线程池/实际计算
        when(metricDefMapper.selectList(cap.capture())).thenReturn(Collections.emptyList());

        service.execute(1, LocalDate.of(2026, 6, 11));

        verify(metricDefMapper).selectList(any());
        LambdaQueryWrapper<PerfMetricDef> wrapper = cap.getValue();
        // 触发 SQL 片段生成，使 MyBatis-Plus 填充 paramNameValuePairs（懒填充）
        wrapper.getTargetSql();
        // 绑定参数应同时包含 ACTIVE 与 PUBLISHED（IN 条件两个取值），而不含 DRAFT/DISABLED
        var boundValues = wrapper.getParamNameValuePairs().values();
        assertThat(boundValues).contains("ACTIVE", "PUBLISHED");
        assertThat(boundValues).doesNotContain("DRAFT", "DISABLED");
    }

    @Test
    @DisplayName("execute(runLogId): 用 SYS_JOB_RUN_LOG.id 作为 PERF_METRIC_CALC_TASK.id，便于两表关联")
    void execute_withRunLogId_usesItAsTaskId() {
        when(metricDefMapper.selectList(any())).thenReturn(Collections.emptyList());
        ArgumentCaptor<PerfMetricCalcTask> cap = ArgumentCaptor.forClass(PerfMetricCalcTask.class);

        service.execute(1, LocalDate.of(2026, 6, 11), "RUNLOG1234567890ABCDEF1234567890");

        verify(taskMapper).insert(cap.capture());
        assertThat(cap.getValue().getId()).isEqualTo("RUNLOG1234567890ABCDEF1234567890");
    }

    @Test
    @DisplayName("execute(空 runLogId): 回退生成 32 位 UUID 作为 task id")
    void execute_blankRunLogId_generatesUuid() {
        when(metricDefMapper.selectList(any())).thenReturn(Collections.emptyList());
        ArgumentCaptor<PerfMetricCalcTask> cap = ArgumentCaptor.forClass(PerfMetricCalcTask.class);

        service.execute(2, LocalDate.of(2026, 6, 11), "  ");

        verify(taskMapper).insert(cap.capture());
        assertThat(cap.getValue().getId()).hasSize(32);
    }

    @Test
    @DisplayName("execute(2参兼容): 旧入口仍生成 UUID 作为 task id")
    void execute_twoArg_generatesUuid() {
        when(metricDefMapper.selectList(any())).thenReturn(Collections.emptyList());
        ArgumentCaptor<PerfMetricCalcTask> cap = ArgumentCaptor.forClass(PerfMetricCalcTask.class);

        service.execute(3, LocalDate.of(2026, 6, 11));

        verify(taskMapper).insert(cap.capture());
        assertThat(cap.getValue().getId()).hasSize(32);
    }

    @Test
    @DisplayName("execute(4参): 业绩分配日期 allocDate 透传到 calcMetricWithStats 第 5 入参")
    void execute_withAllocDate_passedToCalcMetricWithStats() {
        // given - 单个 SQL 类指标，使 calcSingleMetric 真正调到 calcMetricWithStats
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_0001");
        def.setMetricName("测试指标");
        def.setCalcLogicType("SQL");
        def.setSqlText("SELECT 1");
        when(metricDefMapper.selectList(any())).thenReturn(java.util.List.of(def));
        when(metricCalcService.calcMetricWithStats(
                any(), any(), any(), any(), any()))
                .thenReturn(new MetricCalcResult("RT1", 1, 1, 0));

        LocalDate dataDate = LocalDate.of(2026, 6, 11);
        LocalDate allocDate = LocalDate.of(2026, 6, 10);

        // when - 4 参入口携带 allocDate
        service.execute(1, dataDate, allocDate, "RUNLOG1234567890ABCDEF1234567890");

        // then - allocDate 作为第 5 入参透传
        verify(metricCalcService).calcMetricWithStats("M_0001", dataDate, "V1", "BATCH", allocDate);
    }

    @Test
    @DisplayName("execute: 机构(ORG)指标必须在员工(EMP)/客户(CUST)指标全部算完后才开始——两阶段执行")
    void execute_orgMetricsRunAfterEmpAndCust() throws Exception {
        // 构造混合维度指标：EMP / CUST / ORG / 维度无关(null) 各一，故意让 ORG 排在列表最前，
        // 若只按列表顺序并发提交，ORG 会先跑；两阶段执行则应保证 ORG 一定最后。
        PerfMetricDef org  = sqlMetric("M_ORG",  "机构指标", "ORG");
        PerfMetricDef emp  = sqlMetric("M_EMP",  "员工指标", "EMP");
        PerfMetricDef cust = sqlMetric("M_CUST", "客户指标", "CUST");
        PerfMetricDef nul  = sqlMetric("M_NULL", "维度无关", null);
        when(metricDefMapper.selectList(any()))
                .thenReturn(java.util.List.of(org, emp, cust, nul));

        // 记录每个指标「开始计算」的先后次序；ORG 的必须严格大于所有非 ORG 的
        java.util.Map<String, Integer> startOrder = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.concurrent.atomic.AtomicInteger seq = new java.util.concurrent.atomic.AtomicInteger(0);
        // 非 ORG 指标故意 sleep 一下，放大「ORG 抢跑」的窗口；两阶段实现应无视这点仍保证顺序
        when(metricCalcService.calcMetricWithStats(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    String code = inv.getArgument(0);
                    startOrder.put(code, seq.incrementAndGet());
                    if (!"M_ORG".equals(code)) Thread.sleep(60);
                    return new MetricCalcResult("RT", 1, 1, 0);
                });

        service.execute(1, LocalDate.of(2026, 6, 11), null, "RUNLOG1234567890ABCDEF1234567890");

        // ORG 的开始次序必须晚于 EMP / CUST / 维度无关三者
        int orgAt = startOrder.get("M_ORG");
        assertThat(orgAt).isGreaterThan(startOrder.get("M_EMP"));
        assertThat(orgAt).isGreaterThan(startOrder.get("M_CUST"));
        assertThat(orgAt).isGreaterThan(startOrder.get("M_NULL"));
    }

    private static PerfMetricDef sqlMetric(String code, String name, String baseDim) {
        PerfMetricDef d = new PerfMetricDef();
        d.setMetricCode(code);
        d.setMetricName(name);
        d.setCalcLogicType("SQL");
        d.setSqlText("SELECT 1");
        d.setBaseDim(baseDim);
        return d;
    }

    @Test
    @DisplayName("execute(3参兼容): allocDate 缺省以 null 透传，由计算引擎兜底 dataDate")
    void execute_threeArg_passesNullAllocDate() {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_0002");
        def.setMetricName("测试指标2");
        def.setCalcLogicType("SQL");
        def.setSqlText("SELECT 1");
        when(metricDefMapper.selectList(any())).thenReturn(java.util.List.of(def));
        when(metricCalcService.calcMetricWithStats(
                any(), any(), any(), any(), any()))
                .thenReturn(new MetricCalcResult("RT2", 1, 1, 0));

        LocalDate dataDate = LocalDate.of(2026, 6, 11);
        service.execute(1, dataDate, "RUNLOG1234567890ABCDEF1234567890");

        verify(metricCalcService).calcMetricWithStats("M_0002", dataDate, "V1", "BATCH", null);
    }
}
