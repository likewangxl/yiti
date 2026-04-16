package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.SysControlService;
import com.bank.branch.platform.performance.service.cmd.SwitchVersionCmd;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import com.bank.branch.platform.performance.support.SysControlTestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysControlFacade 纯单元测试.
 * <p>Facade 层负责 Redis 分布式锁的申请/释放 (Lua 脚本 compare-and-del), 事务外.
 */
class SysControlFacadeTest extends PerformanceServiceTestBase {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SysControlService sysControlService;

    @InjectMocks
    private SysControlFacade sysControlFacade;

    @BeforeEach
    void setupRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("switchVersion 获取锁失败时抛 PERF-40904")
    void switchVersion_whenLockAcquireFailed_shouldThrow40904() {
        // Given: setIfAbsent 返回 false (锁已被占)
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

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
                        .isEqualTo(PerfErrorCode.SYS_CONTROL_CONFLICT));

        // 验证: 未调 service, 未释放锁 (因为根本未获锁)
        verify(sysControlService, never()).doSwitchVersion(any());
    }

    @Test
    @DisplayName("switchVersion 成功时应 Lua 脚本释放锁")
    void switchVersion_whenSuccess_shouldReleaseLock() {
        // Given
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
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
        // Lua compare-and-del 脚本被调用
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("perf:sys_control:switch:EMP")),
                any());
    }

    @Test
    @DisplayName("switchVersion service 抛异常时依然释放锁")
    void switchVersion_whenServiceThrows_shouldStillReleaseLock() {
        // Given
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(sysControlService.doSwitchVersion(any()))
                .thenThrow(new PerfException(PerfErrorCode.SYS_CONTROL_NOT_FOUND, "EMP"));

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
                        .isEqualTo(PerfErrorCode.SYS_CONTROL_NOT_FOUND));
        // 锁依然被释放
        verify(redisTemplate).execute(any(RedisScript.class), any(List.class), any());
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
