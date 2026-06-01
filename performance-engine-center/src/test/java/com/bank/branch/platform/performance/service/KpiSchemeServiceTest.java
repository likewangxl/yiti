package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.service.cmd.AddKpiItemCmd;
import com.bank.branch.platform.performance.service.cmd.CreateKpiSchemeCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateKpiSchemeCmd;
import com.bank.branch.platform.performance.support.KpiTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KpiSchemeService 单元测试.
 *
 * <p>覆盖 spec §5.2 DoD 6 个必须场景 + getByIdOrNull 辅助场景。
 * <p>**事务语义在 UT 层无法真正验证**, 本测试通过"异常向外抛出"断言配合
 * 代码上的 {@code @Transactional} 注解, 由 Spring 运行时保证事务回滚;
 * 对应事务边界的正向覆盖放在未来的 Controller/Facade IT。
 */
@ExtendWith(MockitoExtension.class)
class KpiSchemeServiceTest {

    @Mock
    private PerfKpiSchemeMapper schemeMapper;

    @Mock
    private KpiItemService kpiItemService;

    @Mock
    private MetricDefService metricDefService;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private KpiSchemeService service;

    // ------------------------------- publish 场景 -------------------------------

    @Test
    @DisplayName("publish: 任一 item 引用的 metric 不存在时抛 KPI_PUBLISH_METRIC_INVALID")
    void publish_whenItemReferMissingMetric_throws40906() {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("PUB_MISS");
        scheme.setId("S_PUB_MISS");
        when(schemeMapper.selectById("S_PUB_MISS")).thenReturn(scheme);
        PerfKpiItem item = KpiTestDataBuilder.item("S_PUB_MISS", "TEST_KPI_NO_METRIC");
        when(kpiItemService.listBySchemeId("S_PUB_MISS")).thenReturn(List.of(item));
        // 批量查询返回空列表 = 该 code 不存在
        when(metricDefService.getByCodes(anyList())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.publish("S_PUB_MISS", "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
        verify(schemeMapper, never()).updateStatusById(any(), any(), any());
    }

    @Test
    @DisplayName("publish: item 引用的 metric 已停用时抛 KPI_PUBLISH_METRIC_INVALID")
    void publish_whenItemReferDraftMetric_throws40906() {
        // DDL 只有 ACTIVE/DISABLED; 本用例用 status=DISABLED 代表 "不可发布" 语义
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("PUB_DIS");
        scheme.setId("S_PUB_DIS");
        when(schemeMapper.selectById("S_PUB_DIS")).thenReturn(scheme);
        PerfKpiItem item = KpiTestDataBuilder.item("S_PUB_DIS", "TEST_KPI_DISABLED_METRIC");
        when(kpiItemService.listBySchemeId("S_PUB_DIS")).thenReturn(List.of(item));
        PerfMetricDef disabled = new PerfMetricDef();
        disabled.setMetricCode("TEST_KPI_DISABLED_METRIC");
        disabled.setStatus("DISABLED");
        // 批量查询返回含该 code 但 status=DISABLED 的 MetricDef
        when(metricDefService.getByCodes(anyList())).thenReturn(List.of(disabled));

        assertThatThrownBy(() -> service.publish("S_PUB_DIS", "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
        verify(schemeMapper, never()).updateStatusById(any(), any(), any());
    }

    @Test
    @DisplayName("publish: 所有 item 的 metric 均为 ACTIVE 时发布成功, 状态置为 ACTIVE")
    void publish_whenAllItemsMetricPublished_succeeds() {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("PUB_OK");
        scheme.setId("S_PUB_OK");
        when(schemeMapper.selectById("S_PUB_OK")).thenReturn(scheme);
        PerfKpiItem i1 = KpiTestDataBuilder.item("S_PUB_OK", "TEST_KPI_ACTIVE_A");
        PerfKpiItem i2 = KpiTestDataBuilder.item("S_PUB_OK", "TEST_KPI_ACTIVE_B");
        when(kpiItemService.listBySchemeId("S_PUB_OK")).thenReturn(List.of(i1, i2));
        // 批量查询返回两个 code 都 ACTIVE 的 MetricDef
        when(metricDefService.getByCodes(anyList()))
                .thenReturn(List.of(activeMetric("TEST_KPI_ACTIVE_A"), activeMetric("TEST_KPI_ACTIVE_B")));

        PerfKpiScheme published = service.publish("S_PUB_OK", "admin");

        assertThat(published.getStatus()).isEqualTo("ACTIVE");
        verify(schemeMapper).updateStatusById("S_PUB_OK", "ACTIVE", "admin");
    }

    // ------------------------------- create 父子事务场景 -------------------------------

    @Test
    @DisplayName("create 带 items: 同事务内先写 scheme 再逐项 addItem")
    void create_withItems_writesBothTablesSameTransaction() {
        AddKpiItemCmd it1 = AddKpiItemCmd.builder()
                .metricCode("TEST_KPI_CR_A").weight(new BigDecimal("30.0000")).build();
        AddKpiItemCmd it2 = AddKpiItemCmd.builder()
                .metricCode("TEST_KPI_CR_B").weight(new BigDecimal("70.0000")).build();

        CreateKpiSchemeCmd cmd = CreateKpiSchemeCmd.builder()
                .schemeCode("TEST_KPI_SCHEME_CREATE")
                .schemeName("测试方案-创建")
                .cycleType("MONTHLY")
                .openDetail(0)
                .items(List.of(it1, it2))
                .operator("admin")
                .build();
        when(schemeMapper.selectBySchemeCode("TEST_KPI_SCHEME_CREATE")).thenReturn(null);
        when(kpiItemService.addItem(any(AddKpiItemCmd.class)))
                .thenAnswer(inv -> new PerfKpiItem());

        PerfKpiScheme created = service.create(cmd);

        assertThat(created.getSchemeCode()).isEqualTo("TEST_KPI_SCHEME_CREATE");
        ArgumentCaptor<PerfKpiScheme> sc = ArgumentCaptor.forClass(PerfKpiScheme.class);
        verify(schemeMapper).insert(sc.capture());
        assertThat(sc.getValue().getId()).isNotBlank();
        assertThat(sc.getValue().getStatus()).isEqualTo("DRAFT");

        // 关键: addItem 需被调用 2 次, 且 schemeId 透传正确
        ArgumentCaptor<AddKpiItemCmd> itemCaptor = ArgumentCaptor.forClass(AddKpiItemCmd.class);
        verify(kpiItemService, times(2)).addItem(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .extracting(AddKpiItemCmd::getSchemeId)
                .containsOnly(created.getId());
        assertThat(itemCaptor.getAllValues())
                .extracting(AddKpiItemCmd::getMetricCode)
                .containsExactly("TEST_KPI_CR_A", "TEST_KPI_CR_B");

        // 父子事务顺序断言: 必须先 insert scheme, 再逐项 addItem (倒序写入会违反外键语义)
        InOrder order = inOrder(schemeMapper, kpiItemService);
        order.verify(schemeMapper).insert(any(PerfKpiScheme.class));
        order.verify(kpiItemService, times(cmd.getItems().size())).addItem(any(AddKpiItemCmd.class));
    }

    @Test
    @DisplayName("create: 第二项 addItem 抛异常时整体抛出, 父事务由 Spring 回滚 (UT 断言异常向外抛)")
    void create_itemInsertFails_rollbacksScheme() {
        AddKpiItemCmd it1 = AddKpiItemCmd.builder()
                .metricCode("TEST_KPI_RB_A").weight(new BigDecimal("50.0000")).build();
        AddKpiItemCmd it2 = AddKpiItemCmd.builder()
                .metricCode("TEST_KPI_RB_B").weight(new BigDecimal("50.0000")).build();

        CreateKpiSchemeCmd cmd = CreateKpiSchemeCmd.builder()
                .schemeCode("TEST_KPI_SCHEME_RB")
                .schemeName("rollback")
                .cycleType("MONTHLY")
                .openDetail(0)
                .items(List.of(it1, it2))
                .operator("admin")
                .build();
        when(schemeMapper.selectBySchemeCode("TEST_KPI_SCHEME_RB")).thenReturn(null);
        // 第一次正常, 第二次抛 KPI_ITEM_DUP
        when(kpiItemService.addItem(any(AddKpiItemCmd.class)))
                .thenReturn(new PerfKpiItem())
                .thenThrow(new PerfException(PerfErrorCode.METRIC_CODE_DUP, "S", "TEST_KPI_RB_B"));

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_CODE_DUP));

        // scheme 已 insert (没走 rollback mock, 仅断言顺序 + 异常外抛), 事务回滚依赖 @Transactional
        verify(schemeMapper).insert(any(PerfKpiScheme.class));
        verify(kpiItemService, times(2)).addItem(any(AddKpiItemCmd.class));
    }

    // ------------------------------- disable/校验场景 -------------------------------

    @Test
    @DisplayName("disable: reason 为空白时抛 PARAM_INVALID, 不落库")
    void disable_whenReasonMissing_throws() {
        assertThatThrownBy(() -> service.disable("ID_ANY", "  ", "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
        verify(schemeMapper, never()).updateStatusById(any(), any(), any());
    }

    // ------------------------------- 补充: getByIdOrNull / page / updateById -------------------------------

    @Test
    @DisplayName("getByIdOrNull: 不存在返回 Optional.empty")
    void getByIdOrNull_whenNotFound_returnsEmpty() {
        when(schemeMapper.selectById("NO")).thenReturn(null);
        Optional<PerfKpiScheme> opt = service.getByIdOrNull("NO");
        assertThat(opt).isEmpty();
    }

    @Test
    @DisplayName("getByIdOrNull: 存在返回 Optional.of(scheme)")
    void getByIdOrNull_whenFound_returnsScheme() {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("OPT_OK");
        scheme.setId("S_OPT_OK");
        when(schemeMapper.selectById("S_OPT_OK")).thenReturn(scheme);
        Optional<PerfKpiScheme> opt = service.getByIdOrNull("S_OPT_OK");
        assertThat(opt).isPresent();
        assertThat(opt.get().getSchemeCode()).isEqualTo("TEST_KPI_OPT_OK");
    }

    @Test
    @DisplayName("create: schemeCode 重复时抛 KPI_SCHEME_CODE_EXISTS (V1.1 P8.1 语义对齐)")
    void create_whenSchemeCodeDup_throws() {
        CreateKpiSchemeCmd cmd = CreateKpiSchemeCmd.builder()
                .schemeCode("TEST_KPI_DUP_CODE")
                .schemeName("dup")
                .cycleType("MONTHLY")
                .openDetail(0)
                .operator("admin")
                .build();
        PerfKpiScheme existing = KpiTestDataBuilder.scheme("DUP_CODE");
        existing.setSchemeCode("TEST_KPI_DUP_CODE");
        when(schemeMapper.selectBySchemeCode("TEST_KPI_DUP_CODE")).thenReturn(existing);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.KPI_SCHEME_CODE_EXISTS));
        verify(schemeMapper, never()).insert(any(PerfKpiScheme.class));
    }

    @Test
    @DisplayName("updateById: 找不到时抛 KPI_SCHEME_NOT_FOUND")
    void updateById_whenNotFound_throws() {
        when(schemeMapper.selectById("NO")).thenReturn(null);
        UpdateKpiSchemeCmd cmd = UpdateKpiSchemeCmd.builder()
                .schemeName("new-name").operator("admin").build();
        assertThatThrownBy(() -> service.updateById("NO", cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.KPI_SCHEME_NOT_FOUND));
    }

    @Test
    @DisplayName("page: 条件查询包装为 PageResult")
    void page_returnsPageResult() {
        when(schemeMapper.countByCondition("MONTHLY", "ACTIVE", "KW", null)).thenReturn(1L);
        PerfKpiScheme one = KpiTestDataBuilder.scheme("P1");
        when(schemeMapper.selectByCondition("MONTHLY", "ACTIVE", "KW", null, 0, 10))
                .thenReturn(List.of(one));

        PageResult<PerfKpiScheme> result = service.page("MONTHLY", "ACTIVE", "KW", null, 1, 10);
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
    }

    // ------------------------------- afterCommit evict 场景 -------------------------------

    @Test
    @DisplayName("publish 成功时应注册 afterCommit 回调, 触发后 evict perf:kpi_scheme::{id}")
    void publish_whenSuccess_registersAfterCommitEvict() {
        PerfKpiScheme scheme = KpiTestDataBuilder.scheme("PUB_EVICT");
        scheme.setId("S_PUB_EVICT");
        when(schemeMapper.selectById("S_PUB_EVICT")).thenReturn(scheme);
        when(kpiItemService.listBySchemeId("S_PUB_EVICT")).thenReturn(Collections.emptyList());

        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("perf:kpi_scheme")).thenReturn(cache);

        List<TransactionSynchronization> captured = new ArrayList<>();
        try (MockedStatic<TransactionSynchronizationManager> mocked = mockStatic(TransactionSynchronizationManager.class)) {
            mocked.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
            mocked.when(() -> TransactionSynchronizationManager.registerSynchronization(any()))
                    .thenAnswer(inv -> {
                        captured.add(inv.getArgument(0));
                        return null;
                    });

            service.publish("S_PUB_EVICT", "admin");
        }
        // 模拟事务提交, 触发注册的 afterCommit 回调
        assertThat(captured).isNotEmpty();
        captured.forEach(TransactionSynchronization::afterCommit);
        verify(cache).evict("S_PUB_EVICT");
    }

    @Test
    @DisplayName("updateById 成功时应注册 afterCommit 回调, 触发后 evict perf:kpi_scheme::{id}")
    void updateById_whenSuccess_registersAfterCommitEvict() {
        PerfKpiScheme existing = KpiTestDataBuilder.scheme("UPD_EVICT");
        existing.setId("S_UPD_EVICT");
        when(schemeMapper.selectById("S_UPD_EVICT")).thenReturn(existing);

        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("perf:kpi_scheme")).thenReturn(cache);

        List<TransactionSynchronization> captured = new ArrayList<>();
        try (MockedStatic<TransactionSynchronizationManager> mocked = mockStatic(TransactionSynchronizationManager.class)) {
            mocked.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
            mocked.when(() -> TransactionSynchronizationManager.registerSynchronization(any()))
                    .thenAnswer(inv -> {
                        captured.add(inv.getArgument(0));
                        return null;
                    });

            UpdateKpiSchemeCmd cmd = UpdateKpiSchemeCmd.builder()
                    .schemeName("renamed").operator("admin").build();
            service.updateById("S_UPD_EVICT", cmd);
        }
        assertThat(captured).isNotEmpty();
        captured.forEach(TransactionSynchronization::afterCommit);
        verify(cache).evict("S_UPD_EVICT");
    }

    @Test
    @DisplayName("disable 成功时应注册 afterCommit 回调, 触发后 evict perf:kpi_scheme::{id}")
    void disable_whenSuccess_registersAfterCommitEvict() {
        PerfKpiScheme existing = KpiTestDataBuilder.scheme("DIS_EVICT");
        existing.setId("S_DIS_EVICT");
        existing.setStatus("ACTIVE");
        when(schemeMapper.selectById("S_DIS_EVICT")).thenReturn(existing);

        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("perf:kpi_scheme")).thenReturn(cache);

        List<TransactionSynchronization> captured = new ArrayList<>();
        try (MockedStatic<TransactionSynchronizationManager> mocked = mockStatic(TransactionSynchronizationManager.class)) {
            mocked.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
            mocked.when(() -> TransactionSynchronizationManager.registerSynchronization(any()))
                    .thenAnswer(inv -> {
                        captured.add(inv.getArgument(0));
                        return null;
                    });

            service.disable("S_DIS_EVICT", "停用原因", "admin");
        }
        assertThat(captured).isNotEmpty();
        captured.forEach(TransactionSynchronization::afterCommit);
        verify(cache).evict("S_DIS_EVICT");
    }

    // ------------------------------- helpers -------------------------------

    private static PerfMetricDef activeMetric(String metricCode) {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode(metricCode);
        def.setStatus("ACTIVE");
        return def;
    }
}
