package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricSlotService 单元测试.
 */
@ExtendWith(MockitoExtension.class)
class MetricSlotServiceTest {

    @Mock
    private PerfMetricDefMapper mapper;

    @InjectMocks
    private MetricSlotService service;

    @Test
    @DisplayName("L1 首次分配返回 1")
    void allocSlot_L1FirstAllocation_returns1() {
        when(mapper.selectOccupiedSlots("EMP")).thenReturn(Set.of());

        int slot = service.allocSlot("EMP", 1, null);

        assertThat(slot).isEqualTo(1);
    }

    @Test
    @DisplayName("L1 有空缺时回填最小空槽位")
    void allocSlot_L1HasGap_fillsSmallestFreeSlot() {
        when(mapper.selectOccupiedSlots("EMP")).thenReturn(Set.of(1, 3));

        int slot = service.allocSlot("EMP", 1, null);

        assertThat(slot).isEqualTo(2);
    }

    @Test
    @DisplayName("L2 从 101 开始分配")
    void allocSlot_L2_returnsFrom101Range() {
        when(mapper.selectOccupiedSlots("EMP")).thenReturn(Set.of());

        int slot = service.allocSlot("EMP", 2, null);

        assertThat(slot).isEqualTo(101);
    }

    @Test
    @DisplayName("L3 从 151 开始分配")
    void allocSlot_L3_returnsFrom151Range() {
        when(mapper.selectOccupiedSlots("ORG")).thenReturn(Set.of());

        int slot = service.allocSlot("ORG", 3, null);

        assertThat(slot).isEqualTo(151);
    }

    @Test
    @DisplayName("指定槽位空闲时直接返回指定槽位")
    void allocSlot_preferredSlotAvailable_returnsPreferred() {
        when(mapper.selectOccupiedSlots("EMP")).thenReturn(Set.of(1, 2));

        int slot = service.allocSlot("EMP", 1, 50);

        assertThat(slot).isEqualTo(50);
    }

    @Test
    @DisplayName("指定槽位已占用时抛槽位冲突")
    void allocSlot_preferredSlotOccupied_throws40901() {
        when(mapper.selectOccupiedSlots("EMP")).thenReturn(Set.of(1, 50));

        assertThatThrownBy(() -> service.allocSlot("EMP", 1, 50))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.METRIC_SLOT_CONFLICT));
    }

    @Test
    @DisplayName("指定槽位超出层级区间时抛参数非法")
    void allocSlot_preferredSlotOutOfLevelRange_throws40001() {
        assertThatThrownBy(() -> service.allocSlot("EMP", 1, 200))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("层级区间耗尽时抛业务异常")
    void allocSlot_levelRangeExhausted_throwsPerfException() {
        Set<Integer> full = new HashSet<>();
        for (int i = 1; i <= 100; i++) {
            full.add(i);
        }
        when(mapper.selectOccupiedSlots("EMP")).thenReturn(full);

        assertThatThrownBy(() -> service.allocSlot("EMP", 1, null))
                .isInstanceOf(PerfException.class);
    }

    @Test
    @DisplayName("释放槽位时状态不是 DISABLED 则拒绝")
    void releaseSlot_whenStatusNotDisabled_throws40905() {
        PerfMetricDef active = new PerfMetricDef();
        active.setId("M001");
        active.setStatus("ACTIVE");
        when(mapper.selectById("M001")).thenReturn(active);

        assertThatThrownBy(() -> service.releaseSlot("M001", "admin", "测试"))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("已停用指标可调用 Mapper 释放槽位")
    void releaseSlot_whenDisabled_callsMapperRelease() {
        PerfMetricDef disabled = new PerfMetricDef();
        disabled.setId("M002");
        disabled.setStatus("DISABLED");
        when(mapper.selectById("M002")).thenReturn(disabled);
        when(mapper.releaseSlotById(eq("M002"), anyString())).thenReturn(1);

        service.releaseSlot("M002", "admin", "停用后释放");

        verify(mapper).releaseSlotById("M002", "admin");
    }
}
