package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.common.web.lock.LockManager;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.SysControlService;
import com.bank.branch.platform.performance.service.cmd.SwitchVersionCmd;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import com.bank.branch.platform.performance.support.SysControlTestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysControlFacade 纯单元测试.
 * <p>Facade 层负责分布式锁的申请/释放 (去 Redis 后改 PT_LOCK + LockManager CAS).
 */
class SysControlFacadeTest extends PerformanceServiceTestBase {

    @Mock
    private LockManager lockManager;

    @Mock
    private SysControlService sysControlService;

    @InjectMocks
    private SysControlFacade sysControlFacade;

    @Test
    @DisplayName("switchVersion 获取锁失败时抛 PERF-40904")
    void switchVersion_whenLockAcquireFailed_shouldThrow40904() {
        // Given: tryLock 返回 false (锁已被占)
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);

        SwitchVersionCmd cmd = SwitchVersionCmd.builder()
                .scopeDim("EMP")
                .dataDate(LocalDate.of(2099, 8, 1))
                .newVersion("V_X")
                .reason("try")
                .operator("admin")
                .build();

        // When / Then
        assertThatThrownBy(() -> sysControlFacade.switchVersion(cmd))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.SYS_CONTROL_VERSION_CONFLICT));

        // 验证: 未调 service, 未释放锁 (因为根本未获锁)
        verify(sysControlService, never()).doSwitchVersion(any());
        verify(lockManager, never()).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("switchVersion 成功时应 CAS 释放锁")
    void switchVersion_whenSuccess_shouldReleaseLock() {
        // Given
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        SysControl ok = SysControlTestDataBuilder.buildTest(
                "F01", "EMP", LocalDate.of(2099, 8, 1), "V_NEW", 1);
        when(sysControlService.doSwitchVersion(any())).thenReturn(ok);

        SwitchVersionCmd cmd = SwitchVersionCmd.builder()
                .scopeDim("EMP")
                .dataDate(LocalDate.of(2099, 8, 1))
                .newVersion("V_NEW")
                .reason("ok")
                .operator("admin")
                .build();

        // When
        SysControl res = sysControlFacade.switchVersion(cmd);

        // Then
        assertThat(res.getCurrentVersion()).isEqualTo("V_NEW");
        verify(sysControlService).doSwitchVersion(cmd);
        // 锁被 unlock（按 key 匹配）
        verify(lockManager).unlock(eq("perf:sys_control:switch:EMP"), anyString());
    }

    @Test
    @DisplayName("switchVersion service 抛异常时依然释放锁")
    void switchVersion_whenServiceThrows_shouldStillReleaseLock() {
        // Given
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(sysControlService.doSwitchVersion(any()))
                .thenThrow(new PerfException(PerfErrorCode.SYS_CONTROL_VERSION_NOT_FOUND, "EMP"));

        SwitchVersionCmd cmd = SwitchVersionCmd.builder()
                .scopeDim("EMP")
                .dataDate(LocalDate.of(2099, 8, 1))
                .newVersion("V_X")
                .reason("x")
                .operator("admin")
                .build();

        // When / Then
        assertThatThrownBy(() -> sysControlFacade.switchVersion(cmd))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.SYS_CONTROL_VERSION_NOT_FOUND));
        // 锁依然被释放
        verify(lockManager).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("getCurrentVersion / listVersionHistory / initIfAbsent 直接委托 service")
    void readMethods_shouldDelegate() {
        SysControl sc = SysControlTestDataBuilder.buildTest(
                "F02", "EMP", LocalDate.of(2099, 9, 1), "V", 1);
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);
        when(sysControlService.listVersionHistory("EMP", 20)).thenReturn(List.of(sc));
        when(sysControlService.initIfAbsent(eq("EMP"), any(), eq("V_INIT"))).thenReturn(sc);

        assertThat(sysControlFacade.getCurrentVersion("EMP")).isSameAs(sc);
        assertThat(sysControlFacade.listVersionHistory("EMP", 20)).containsExactly(sc);
        assertThat(sysControlFacade.initIfAbsent("EMP", LocalDate.of(2099, 9, 1), "V_INIT"))
                .isSameAs(sc);
    }
}
