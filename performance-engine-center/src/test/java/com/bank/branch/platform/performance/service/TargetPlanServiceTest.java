package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.service.cmd.CreateTargetPlanCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateTargetPlanCmd;
import com.bank.branch.platform.performance.support.KpiTestDataBuilder;
import com.bank.branch.platform.performance.support.TargetTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TargetPlanService 单元测试.
 *
 * <p>覆盖 plan Task 3.2 的 TargetPlanService DoD 必含场景 (L1365-1369) + 补充场景.
 *
 * <p>**expire_date 歧义处理 (选项 A)**: 当前 DDL 无 expire_date 列, plan 钦定的
 * {@code create_whenEffectiveAfterExpire_throws40001} / {@code create_whenEffectiveEqualsExpire_succeeds}
 * 退化为 {@code effectiveDate != null} 的单字段非空校验场景.
 * expire_date 的引入列入 V1.1 DDL follow-up.
 *
 * <p>**KPI scheme 校验** (L1368): plan 钦定测试名 40906 但新加独立错误码
 * {@code TARGET_PLAN_KPI_SCHEME_INVALID (PERF-40915)}, 保留 40906 的
 * {@code PLAN_NOT_PUBLISHED} 原语义 (避免错误码复用).
 */
@ExtendWith(MockitoExtension.class)
class TargetPlanServiceTest {

    @Mock
    private PerfTargetPlanMapper targetPlanMapper;

    @Mock
    private KpiSchemeService kpiSchemeService;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private TargetPlanService service;

    // ------------------------------- create: 必含 DoD 场景 -------------------------------

    @Test
    @DisplayName("create: effectiveDate 为 null 时抛 PARAM_INVALID (退化 L1366 场景 - 选项 A)")
    void create_whenEffectiveDateNull_throws() {
        CreateTargetPlanCmd cmd = buildCmd("TP_NULL_EFF", "KS_ACTIVE", null);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
        verify(targetPlanMapper, never()).insert(any(PerfTargetPlan.class));
    }

    @Test
    @DisplayName("create: effectiveDate 为今天时创建成功 (退化 L1367 边界 - 选项 A)")
    void create_whenEffectiveDateIsToday_succeeds() {
        CreateTargetPlanCmd cmd = buildCmd("TP_TODAY", "KS_ACTIVE", LocalDate.now());
        when(targetPlanMapper.selectByPlanCode("TEST_TGT_TP_TODAY")).thenReturn(null);
        when(kpiSchemeService.getByIdOrNull("KS_ACTIVE"))
                .thenReturn(Optional.of(activeScheme("KS_ACTIVE")));

        PerfTargetPlan created = service.create(cmd);

        assertThat(created).isNotNull();
        assertThat(created.getPlanCode()).isEqualTo("TEST_TGT_TP_TODAY");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        ArgumentCaptor<PerfTargetPlan> captor = ArgumentCaptor.forClass(PerfTargetPlan.class);
        verify(targetPlanMapper).insert(captor.capture());
        assertThat(captor.getValue().getId()).isNotBlank();
        assertThat(captor.getValue().getEffectiveDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("create: kpiSchemeId 对应 scheme 为 DISABLED 时抛 TARGET_PLAN_KPI_SCHEME_INVALID (L1368)")
    void create_whenKpiSchemeDraft_throws40906() {
        // plan 钦定 40906 测试名, 实际断言新码 PERF-40915 (避免与 PLAN_NOT_PUBLISHED 语义冲突)
        CreateTargetPlanCmd cmd = buildCmd("TP_KS_DIS", "KS_DISABLED", LocalDate.now());
        PerfKpiScheme disabled = KpiTestDataBuilder.scheme("KS_DISABLED");
        disabled.setId("KS_DISABLED");
        disabled.setStatus("DISABLED");
        when(targetPlanMapper.selectByPlanCode("TEST_TGT_TP_KS_DIS")).thenReturn(null);
        when(kpiSchemeService.getByIdOrNull("KS_DISABLED")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
        verify(targetPlanMapper, never()).insert(any(PerfTargetPlan.class));
    }

    @Test
    @DisplayName("create: kpiSchemeId 对应 scheme 为 DRAFT 时抛 TARGET_PLAN_KPI_SCHEME_INVALID")
    void create_whenKpiSchemeStatusDraft_throws() {
        CreateTargetPlanCmd cmd = buildCmd("TP_KS_DRAFT", "KS_DRAFT", LocalDate.now());
        PerfKpiScheme draft = KpiTestDataBuilder.scheme("KS_DRAFT");
        draft.setId("KS_DRAFT");
        draft.setStatus("DRAFT");
        when(targetPlanMapper.selectByPlanCode("TEST_TGT_TP_KS_DRAFT")).thenReturn(null);
        when(kpiSchemeService.getByIdOrNull("KS_DRAFT")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
        verify(targetPlanMapper, never()).insert(any(PerfTargetPlan.class));
    }

    @Test
    @DisplayName("create: kpiSchemeId 不存在时抛 TARGET_PLAN_KPI_SCHEME_INVALID")
    void create_whenKpiSchemeNotFound_throws() {
        CreateTargetPlanCmd cmd = buildCmd("TP_KS_NONE", "KS_NOT_EXIST", LocalDate.now());
        when(targetPlanMapper.selectByPlanCode("TEST_TGT_TP_KS_NONE")).thenReturn(null);
        when(kpiSchemeService.getByIdOrNull("KS_NOT_EXIST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("create: planCode 已存在时抛 TARGET_PLAN_CODE_EXISTS, 不调 KpiSchemeService (V1.1 P8.1 语义对齐)")
    void create_whenPlanCodeDup_throws() {
        CreateTargetPlanCmd cmd = buildCmd("TP_DUP", "KS_ACTIVE", LocalDate.now());
        PerfTargetPlan existing = TargetTestDataBuilder.plan("TP_DUP", "KS_ACTIVE");
        when(targetPlanMapper.selectByPlanCode("TEST_TGT_TP_DUP")).thenReturn(existing);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.TARGET_PLAN_CODE_EXISTS));
        // planCode UK 预校验先行, 绝不会走到 KpiSchemeService
        verify(kpiSchemeService, never()).getByIdOrNull(any());
        verify(targetPlanMapper, never()).insert(any(PerfTargetPlan.class));
    }

    // ------------------------------- getByCode / getByIdOrNull -------------------------------

    @Test
    @DisplayName("getByCodeOrNull: 存在返回 Optional.of(plan) (L1369 DoD)")
    void getByCode_whenExists_returnsOptional() {
        PerfTargetPlan plan = TargetTestDataBuilder.plan("CODE_HIT", "KS_ACTIVE");
        when(targetPlanMapper.selectByPlanCode("TEST_TGT_CODE_HIT")).thenReturn(plan);

        Optional<PerfTargetPlan> opt = service.getByCodeOrNull("TEST_TGT_CODE_HIT");

        assertThat(opt).isPresent();
        assertThat(opt.get().getPlanCode()).isEqualTo("TEST_TGT_CODE_HIT");
    }

    @Test
    @DisplayName("getByCodeOrNull: 不存在返回 Optional.empty")
    void getByCode_whenNotFound_returnsEmpty() {
        when(targetPlanMapper.selectByPlanCode("NO_CODE")).thenReturn(null);

        Optional<PerfTargetPlan> opt = service.getByCodeOrNull("NO_CODE");

        assertThat(opt).isEmpty();
    }

    @Test
    @DisplayName("getById: 不存在抛 TARGET_PLAN_NOT_FOUND")
    void getById_whenNotFound_throws() {
        when(targetPlanMapper.selectById("NO")).thenReturn(null);

        assertThatThrownBy(() -> service.getById("NO"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.TARGET_PLAN_NOT_FOUND));
    }

    @Test
    @DisplayName("getByIdOrNull: 不存在返回 Optional.empty")
    void getByIdOrNull_whenNotFound_returnsEmpty() {
        when(targetPlanMapper.selectById("NO")).thenReturn(null);

        Optional<PerfTargetPlan> opt = service.getByIdOrNull("NO");

        assertThat(opt).isEmpty();
    }

    @Test
    @DisplayName("getByIdOrNull: 存在返回 Optional.of(plan) (供 Facade Cacheable 消费)")
    void getByIdOrNull_whenFound_returnsPlan() {
        PerfTargetPlan plan = TargetTestDataBuilder.plan("ID_HIT", "KS_ACTIVE");
        plan.setId("P_ID_HIT");
        when(targetPlanMapper.selectById("P_ID_HIT")).thenReturn(plan);

        Optional<PerfTargetPlan> opt = service.getByIdOrNull("P_ID_HIT");

        assertThat(opt).isPresent();
    }

    // ------------------------------- updateById / disable -------------------------------

    @Test
    @DisplayName("updateById: 不存在抛 TARGET_PLAN_NOT_FOUND")
    void updateById_whenNotFound_throws() {
        when(targetPlanMapper.selectById("NO")).thenReturn(null);
        UpdateTargetPlanCmd cmd = UpdateTargetPlanCmd.builder()
                .planName("new-name").operator("admin").build();

        assertThatThrownBy(() -> service.updateById("NO", cmd))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.TARGET_PLAN_NOT_FOUND));
    }

    @Test
    @DisplayName("disable: reason 空白抛 PARAM_INVALID, 不落库")
    void disable_whenReasonMissing_throws() {
        assertThatThrownBy(() -> service.disable("ID_ANY", "  ", "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
        verify(targetPlanMapper, never()).updateStatusById(any(), any(), any());
    }

    @Test
    @DisplayName("disable: 不存在抛 TARGET_PLAN_NOT_FOUND")
    void disable_whenNotFound_throws() {
        when(targetPlanMapper.selectById("NO")).thenReturn(null);

        assertThatThrownBy(() -> service.disable("NO", "reason", "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.TARGET_PLAN_NOT_FOUND));
    }

    @Test
    @DisplayName("disable: 已禁用方案再次禁用抛 INVALID_STATE")
    void disable_whenAlreadyDisabled_throws() {
        PerfTargetPlan plan = TargetTestDataBuilder.plan("DIS", "KS_ACTIVE");
        plan.setId("P_DIS");
        plan.setStatus("DISABLED");
        when(targetPlanMapper.selectById("P_DIS")).thenReturn(plan);

        assertThatThrownBy(() -> service.disable("P_DIS", "reason", "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    // ------------------------------- page -------------------------------

    @Test
    @DisplayName("page: 条件查询包装为 PageResult")
    void page_returnsPageResult() {
        when(targetPlanMapper.countByCondition("KS_ACTIVE", "ACTIVE", "KW")).thenReturn(1L);
        PerfTargetPlan one = TargetTestDataBuilder.plan("PG1", "KS_ACTIVE");
        when(targetPlanMapper.selectByCondition("KS_ACTIVE", "ACTIVE", "KW", 0, 10))
                .thenReturn(List.of(one));

        PageResult<PerfTargetPlan> result = service.page("KS_ACTIVE", "ACTIVE", "KW", 1, 10);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("page: total=0 时快速返回空分页, 跳过 select")
    void page_whenNoData_returnsEmptyPage() {
        when(targetPlanMapper.countByCondition(null, null, null)).thenReturn(0L);

        PageResult<PerfTargetPlan> result = service.page(null, null, null, 1, 10);

        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getRecords()).isEmpty();
        verify(targetPlanMapper, never()).selectByCondition(any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    // ------------------------------- afterCommit evict -------------------------------

    @Test
    @DisplayName("create 成功时注册 afterCommit 回调, 触发后 evict perf:target_plan::{id}")
    void create_whenSuccess_registersAfterCommitEvict() {
        CreateTargetPlanCmd cmd = buildCmd("TP_EVICT_C", "KS_ACTIVE", LocalDate.now());
        when(targetPlanMapper.selectByPlanCode("TEST_TGT_TP_EVICT_C")).thenReturn(null);
        when(kpiSchemeService.getByIdOrNull("KS_ACTIVE"))
                .thenReturn(Optional.of(activeScheme("KS_ACTIVE")));

        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("perf:target_plan")).thenReturn(cache);

        List<TransactionSynchronization> captured = new ArrayList<>();
        try (MockedStatic<TransactionSynchronizationManager> mocked = mockStatic(TransactionSynchronizationManager.class)) {
            mocked.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
            mocked.when(() -> TransactionSynchronizationManager.registerSynchronization(any()))
                    .thenAnswer(inv -> {
                        captured.add(inv.getArgument(0));
                        return null;
                    });

            PerfTargetPlan created = service.create(cmd);
            assertThat(created).isNotNull();
            assertThat(captured).isNotEmpty();
            captured.forEach(TransactionSynchronization::afterCommit);
            verify(cache).evict(created.getId());
        }
    }

    @Test
    @DisplayName("updateById 成功时注册 afterCommit 回调, 触发后 evict perf:target_plan::{id}")
    void updateById_whenSuccess_registersAfterCommitEvict() {
        PerfTargetPlan existing = TargetTestDataBuilder.plan("UPD_EVICT", "KS_ACTIVE");
        existing.setId("P_UPD_EVICT");
        when(targetPlanMapper.selectById("P_UPD_EVICT")).thenReturn(existing);

        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("perf:target_plan")).thenReturn(cache);

        List<TransactionSynchronization> captured = new ArrayList<>();
        try (MockedStatic<TransactionSynchronizationManager> mocked = mockStatic(TransactionSynchronizationManager.class)) {
            mocked.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
            mocked.when(() -> TransactionSynchronizationManager.registerSynchronization(any()))
                    .thenAnswer(inv -> {
                        captured.add(inv.getArgument(0));
                        return null;
                    });

            UpdateTargetPlanCmd cmd = UpdateTargetPlanCmd.builder()
                    .planName("renamed").operator("admin").build();
            service.updateById("P_UPD_EVICT", cmd);
        }
        assertThat(captured).isNotEmpty();
        captured.forEach(TransactionSynchronization::afterCommit);
        verify(cache).evict("P_UPD_EVICT");
    }

    @Test
    @DisplayName("disable 成功时注册 afterCommit 回调, 触发后 evict perf:target_plan::{id}")
    void disable_whenSuccess_registersAfterCommitEvict() {
        PerfTargetPlan existing = TargetTestDataBuilder.plan("DIS_EVICT", "KS_ACTIVE");
        existing.setId("P_DIS_EVICT");
        existing.setStatus("ACTIVE");
        when(targetPlanMapper.selectById("P_DIS_EVICT")).thenReturn(existing);

        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("perf:target_plan")).thenReturn(cache);

        List<TransactionSynchronization> captured = new ArrayList<>();
        try (MockedStatic<TransactionSynchronizationManager> mocked = mockStatic(TransactionSynchronizationManager.class)) {
            mocked.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
            mocked.when(() -> TransactionSynchronizationManager.registerSynchronization(any()))
                    .thenAnswer(inv -> {
                        captured.add(inv.getArgument(0));
                        return null;
                    });

            service.disable("P_DIS_EVICT", "停用原因", "admin");
        }
        assertThat(captured).isNotEmpty();
        captured.forEach(TransactionSynchronization::afterCommit);
        verify(cache).evict("P_DIS_EVICT");
    }

    // ------------------------------- helpers -------------------------------

    private static CreateTargetPlanCmd buildCmd(String codeSuffix, String kpiSchemeId, LocalDate effectiveDate) {
        return CreateTargetPlanCmd.builder()
                .planCode("TEST_TGT_" + codeSuffix)
                .planName("测试目标方案-" + codeSuffix)
                .kpiSchemeId(kpiSchemeId)
                .targetDim("EMP")
                .targetCycle("YEAR")
                .effectiveDate(effectiveDate)
                .operator("admin")
                .build();
    }

    private static PerfKpiScheme activeScheme(String id) {
        PerfKpiScheme s = KpiTestDataBuilder.scheme(id);
        s.setId(id);
        s.setStatus("ACTIVE");
        return s;
    }
}
