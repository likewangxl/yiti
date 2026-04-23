package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.SysControlMapper;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import com.bank.branch.platform.performance.support.SysControlTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysControlService#rollback} 单元测试 (V1.2 Q1.2).
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>rollback 成功：旧版本置失效 + 插入新版本 publishSource=ROLLBACK + remark=reason</li>
 *   <li>rollbackTo 版本不存在 → PERF-40012</li>
 *   <li>当前无生效版本 → PERF-40012</li>
 *   <li>DB UK 冲突 → PERF-40903</li>
 * </ul>
 */
class SysControlServiceRollbackTest extends PerformanceServiceTestBase {

    @Mock
    private SysControlMapper sysControlMapper;

    @InjectMocks
    private SysControlService service;

    @Test
    @DisplayName("rollback 成功：旧版本置失效 + 插入 publishSource=ROLLBACK 的新记录")
    void rollback_success_writesRollbackRecord() {
        // Given：history 中含 V_RB_1（失效）和 V_RB_2（当前）
        SysControl v1 = SysControlTestDataBuilder.buildTest(
                "RB1", "EMP", LocalDate.of(2099, 1, 1), "V_RB_1", 0);
        SysControl v2 = SysControlTestDataBuilder.buildTest(
                "RB2", "EMP", LocalDate.of(2099, 2, 1), "V_RB_2", 1);
        when(sysControlMapper.listByScope(eq("EMP"), anyInt())).thenReturn(List.of(v2, v1));
        when(sysControlMapper.selectByScopeAndValid("EMP")).thenReturn(v2);
        when(sysControlMapper.insert(any())).thenReturn(1);

        // When
        SysControl result = service.rollback("EMP", "V_RB_1", "紧急回滚", "admin");

        // Then：旧版置失效
        verify(sysControlMapper).updateIsValid(v2.getId(), 0);

        // 断言新插入记录字段
        ArgumentCaptor<SysControl> captor = ArgumentCaptor.forClass(SysControl.class);
        verify(sysControlMapper).insert(captor.capture());
        SysControl inserted = captor.getValue();
        assertThat(inserted.getCurrentVersion()).isEqualTo("V_RB_1");
        assertThat(inserted.getScopeDim()).isEqualTo("EMP");
        assertThat(inserted.getIsValid()).isEqualTo(1);
        assertThat(inserted.getPublishSource()).isEqualTo("ROLLBACK");
        assertThat(inserted.getPublishBy()).isEqualTo("admin");
        assertThat(inserted.getUpdatedBy()).isEqualTo("admin");
        assertThat(inserted.getRemark()).isEqualTo("紧急回滚");
        assertThat(inserted.getPublishTime()).isNotNull();
        assertThat(inserted.getLatestDataDate()).isEqualTo(LocalDate.now());

        // 返回值
        assertThat(result.getCurrentVersion()).isEqualTo("V_RB_1");
        assertThat(result.getPublishSource()).isEqualTo("ROLLBACK");
    }

    @Test
    @DisplayName("rollback：rollbackTo 历史不存在 → PERF-40012")
    void rollback_whenRollbackToNotExists_throws40012() {
        // Given：history 只含 V_RB_2
        SysControl v2 = SysControlTestDataBuilder.buildTest(
                "RB2", "EMP", LocalDate.of(2099, 2, 1), "V_RB_2", 1);
        when(sysControlMapper.listByScope(eq("EMP"), anyInt())).thenReturn(List.of(v2));

        // When / Then
        assertThatThrownBy(() -> service.rollback("EMP", "V_NOT_EXIST", "尝试", "admin"))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.SYS_CONTROL_VERSION_NOT_FOUND));

        verify(sysControlMapper, never()).insert(any());
        verify(sysControlMapper, never()).updateIsValid(anyString(), anyInt());
    }

    @Test
    @DisplayName("rollback：当前无生效版本 → PERF-40012")
    void rollback_whenNoCurrentVersion_throws40012() {
        // Given：history 存在 V_RB_1 但 selectByScopeAndValid 返回 null（无生效记录）
        SysControl v1 = SysControlTestDataBuilder.buildTest(
                "RB1", "EMP", LocalDate.of(2099, 1, 1), "V_RB_1", 0);
        when(sysControlMapper.listByScope(eq("EMP"), anyInt())).thenReturn(List.of(v1));
        when(sysControlMapper.selectByScopeAndValid("EMP")).thenReturn(null);

        assertThatThrownBy(() -> service.rollback("EMP", "V_RB_1", "测试", "admin"))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.SYS_CONTROL_VERSION_NOT_FOUND));

        verify(sysControlMapper, never()).insert(any());
        verify(sysControlMapper, never()).updateIsValid(anyString(), anyInt());
    }

    @Test
    @DisplayName("rollback：DB UK 冲突 → PERF-40903")
    void rollback_whenUkConflict_throws40903() {
        SysControl v1 = SysControlTestDataBuilder.buildTest(
                "RB1", "EMP", LocalDate.of(2099, 1, 1), "V_RB_1", 0);
        SysControl v2 = SysControlTestDataBuilder.buildTest(
                "RB2", "EMP", LocalDate.of(2099, 2, 1), "V_RB_2", 1);
        when(sysControlMapper.listByScope(eq("EMP"), anyInt())).thenReturn(List.of(v2, v1));
        when(sysControlMapper.selectByScopeAndValid("EMP")).thenReturn(v2);
        when(sysControlMapper.insert(any()))
                .thenThrow(new DuplicateKeyException("UK conflict"));

        assertThatThrownBy(() -> service.rollback("EMP", "V_RB_1", "并发", "admin"))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.SYS_CONTROL_VERSION_CONFLICT));
    }
}
