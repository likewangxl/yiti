package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.service.cmd.UpsertTargetValueCmd;
import com.bank.branch.platform.performance.support.TargetTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TargetValueService 单元测试.
 *
 * <p>覆盖 plan Task 3.2 的 TargetValueService DoD 必含场景 + Task 3.1 I-2/I-3 契约落实：
 * <ul>
 *   <li>size > 500 抛 TARGET_BATCH_TOO_BIG (Plan L1372 / §5.3)</li>
 *   <li>全部成功返回 MySQL 受影响行数 (Plan L1373 / §5.3)</li>
 *   <li>单值 upsertOne 委托批量 (Plan L1374)</li>
 *   <li>empty list early return 0 (I-3 落实)</li>
 *   <li>createdBy 被强制覆盖为 operator (I-2 落实)</li>
 *   <li>listByPlan 入口 planId 非空校验 (I-3 落实)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TargetValueServiceTest {

    @Mock
    private PerfTargetValueMapper targetValueMapper;

    @InjectMocks
    private TargetValueService service;

    // ------------------------------- upsertBatch: 核心 5 场景 -------------------------------

    @Test
    @DisplayName("upsertBatch: size > 500 抛 TARGET_BATCH_TOO_BIG(PERF-40910), 不落库")
    void upsertBatch_whenSizeExceeds500_throws40910() {
        List<PerfTargetValue> list = buildValues(501);

        assertThatThrownBy(() -> service.upsertBatch(list, "admin"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT));
        verify(targetValueMapper, never()).upsertBatch(anyList());
    }

    @Test
    @DisplayName("upsertBatch: size == 500 为边界合法值, 透传到 Mapper")
    void upsertBatch_whenSizeEquals500_delegatesMapper() {
        List<PerfTargetValue> list = buildValues(500);
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(500);

        int affected = service.upsertBatch(list, "admin");

        assertThat(affected).isEqualTo(500);
        verify(targetValueMapper, times(1)).upsertBatch(anyList());
    }

    @Test
    @DisplayName("upsertBatch: 正常场景返回 Mapper 的 MySQL 受影响行数")
    void upsertBatch_whenAllSuccess_returnsAffectedCount() {
        List<PerfTargetValue> list = buildValues(3);
        // MySQL ON DUPLICATE KEY UPDATE: 新增 1 / 更新 2, 3 条里 2 新 1 更 → 5
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(5);

        int affected = service.upsertBatch(list, "admin");

        assertThat(affected).isEqualTo(5);
    }

    @Test
    @DisplayName("upsertBatch: 空列表 early return 0, 不调 Mapper (落实 I-3)")
    void upsertBatch_whenEmptyList_returnsZero() {
        int affected = service.upsertBatch(Collections.emptyList(), "admin");

        assertThat(affected).isEqualTo(0);
        verify(targetValueMapper, never()).upsertBatch(anyList());
    }

    @Test
    @DisplayName("upsertBatch: null 列表 early return 0, 不调 Mapper")
    void upsertBatch_whenNullList_returnsZero() {
        int affected = service.upsertBatch(null, "admin");

        assertThat(affected).isEqualTo(0);
        verify(targetValueMapper, never()).upsertBatch(anyList());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("upsertBatch: 覆写每个 entity.createdBy 为当前操作人, 防止调用方伪造 (落实 I-2)")
    void upsertBatch_overwritesCreatedByToCurrentUser() {
        // 伪造调用方传入 createdBy="hacker" 的场景
        PerfTargetValue v1 = TargetTestDataBuilder.value(
                "P1", "EMP", "E001", "2026", "M_A", new BigDecimal("100"));
        v1.setCreatedBy("hacker");
        PerfTargetValue v2 = TargetTestDataBuilder.value(
                "P1", "EMP", "E002", "2026", "M_A", new BigDecimal("200"));
        v2.setCreatedBy("hacker");
        List<PerfTargetValue> list = List.of(v1, v2);
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(2);

        service.upsertBatch(list, "admin");

        ArgumentCaptor<List<PerfTargetValue>> captor =
                (ArgumentCaptor<List<PerfTargetValue>>) (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(targetValueMapper).upsertBatch(captor.capture());
        assertThat(captor.getValue())
                .extracting(PerfTargetValue::getCreatedBy)
                .containsOnly("admin");
    }

    // ------------------------------- upsertOne 委托场景 -------------------------------

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("upsertOne: 单值命令委托到 upsertBatch (List.of(v), operator)")
    void upsertOne_singleCall_delegatesBatch() {
        UpsertTargetValueCmd cmd = UpsertTargetValueCmd.builder()
                .planId("P1")
                .subjectType("EMP")
                .subjectId("E001")
                .cycleKey("2026")
                .metricCode("M_A")
                .targetValue(new BigDecimal("1000"))
                .baseValue(new BigDecimal("100"))
                .operator("admin")
                .build();
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(1);

        int affected = service.upsertOne(cmd);

        assertThat(affected).isEqualTo(1);
        ArgumentCaptor<List<PerfTargetValue>> captor =
                (ArgumentCaptor<List<PerfTargetValue>>) (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(targetValueMapper).upsertBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        PerfTargetValue actual = captor.getValue().get(0);
        assertThat(actual.getPlanId()).isEqualTo("P1");
        assertThat(actual.getSubjectType()).isEqualTo("EMP");
        assertThat(actual.getSubjectId()).isEqualTo("E001");
        assertThat(actual.getCycleKey()).isEqualTo("2026");
        assertThat(actual.getMetricCode()).isEqualTo("M_A");
        assertThat(actual.getTargetValue()).isEqualByComparingTo("1000");
        assertThat(actual.getBaseValue()).isEqualByComparingTo("100");
        assertThat(actual.getCreatedBy()).isEqualTo("admin");
        assertThat(actual.getId()).isNotBlank();
    }

    // ------------------------------- getByUniqueKey / listByPlan 场景 -------------------------------

    @Test
    @DisplayName("getByUniqueKey: Mapper 返回 null 时包装为 Optional.empty")
    void getByUniqueKey_whenNotFound_returnsEmpty() {
        when(targetValueMapper.selectByUniqueKey("P1", "EMP", "E001", "2026", "M_A"))
                .thenReturn(null);

        Optional<PerfTargetValue> opt = service.getByUniqueKey("P1", "EMP", "E001", "2026", "M_A");

        assertThat(opt).isEmpty();
    }

    @Test
    @DisplayName("getByUniqueKey: Mapper 返回实体时包装为 Optional.of")
    void getByUniqueKey_whenFound_returnsOptional() {
        PerfTargetValue v = TargetTestDataBuilder.value(
                "P1", "EMP", "E001", "2026", "M_A", new BigDecimal("100"));
        when(targetValueMapper.selectByUniqueKey("P1", "EMP", "E001", "2026", "M_A"))
                .thenReturn(v);

        Optional<PerfTargetValue> opt = service.getByUniqueKey("P1", "EMP", "E001", "2026", "M_A");

        assertThat(opt).isPresent();
        assertThat(opt.get().getSubjectId()).isEqualTo("E001");
    }

    @Test
    @DisplayName("listByPlan: planId 空白抛 PARAM_INVALID (落实 I-3)")
    void listByPlan_whenPlanIdBlank_throws() {
        assertThatThrownBy(() -> service.listByPlan("  ", null, null, null, 1, 20))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
        verify(targetValueMapper, never()).listByPlan(eq("  "), isNull(), isNull(), isNull(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("listByPlan: planId 为 null 抛 PARAM_INVALID (落实 I-3)")
    void listByPlan_whenPlanIdNull_throws() {
        assertThatThrownBy(() -> service.listByPlan(null, null, null, null, 1, 20))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("listByPlan: total=0 返回空分页, 跳过 select")
    void listByPlan_whenNoData_returnsEmptyPage() {
        when(targetValueMapper.countByPlan("P1", null, null, null)).thenReturn(0L);

        PageResult<PerfTargetValue> result = service.listByPlan("P1", null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getRecords()).isEmpty();
        verify(targetValueMapper, never()).listByPlan(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("listByPlan: total>0 返回分页结果 (pageNo/pageSize 透传)")
    void listByPlan_returnsPageResult() {
        when(targetValueMapper.countByPlan("P1", "EMP", null, "2026")).thenReturn(1L);
        PerfTargetValue v = TargetTestDataBuilder.value(
                "P1", "EMP", "E001", "2026", "M_A", new BigDecimal("100"));
        when(targetValueMapper.listByPlan("P1", "EMP", null, "2026", 0, 20))
                .thenReturn(List.of(v));

        PageResult<PerfTargetValue> result = service.listByPlan("P1", "EMP", null, "2026", 1, 20);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
    }

    // ------------------------------- helpers -------------------------------

    private static List<PerfTargetValue> buildValues(int n) {
        List<PerfTargetValue> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(TargetTestDataBuilder.value(
                    "P1", "EMP", "E" + i, "2026", "M_A", new BigDecimal(100 + i)));
        }
        return list;
    }
}
