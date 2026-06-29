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
