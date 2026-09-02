package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.enums.TouchTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TouchTaskStateMachineService 单元测试（TDD RED-GREEN 闭环）
 *
 * 覆盖 4x4 状态矩阵（16 格）+ null 处理 + isValidTransition 分支，共 20 个用例。
 * 不需要 Spring 上下文，直接 new 对象即可。
 */
class TouchTaskStateMachineServiceTest {

    private TouchTaskStateMachineService service;

    @BeforeEach
    void setUp() {
        service = new TouchTaskStateMachineService();
    }

    // === 合法转移（4 条）===

    @Test
    void pendingCanTransitionToInProgress() {
        assertThatCode(() -> service.assertTransition(TouchTaskStatus.PENDING, TouchTaskStatus.IN_PROGRESS))
                .doesNotThrowAnyException();
    }

    @Test
    void pendingToSuccess_illegal() {
        assertThatThrownBy(() -> service.assertTransition(TouchTaskStatus.PENDING, TouchTaskStatus.SUCCESS))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "CUST-40010");
    }

    @Test
    void pendingCanTransitionToCancelled() {
        assertThatCode(() -> service.assertTransition(TouchTaskStatus.PENDING, TouchTaskStatus.CANCELLED))
                .doesNotThrowAnyException();
    }

    @Test
    void inProgressCanTransitionToSuccess() {
        assertThatCode(() -> service.assertTransition(TouchTaskStatus.IN_PROGRESS, TouchTaskStatus.SUCCESS))
                .doesNotThrowAnyException();
    }

    @Test
    void inProgressCanTransitionToCancelled() {
        assertThatCode(() -> service.assertTransition(TouchTaskStatus.IN_PROGRESS, TouchTaskStatus.CANCELLED))
                .doesNotThrowAnyException();
    }

    // === 非法转移 — PENDING 起步（PENDING -> PENDING）===

    @Test
    void pendingToPending_illegal() {
        assertThatThrownBy(() -> service.assertTransition(TouchTaskStatus.PENDING, TouchTaskStatus.PENDING))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "CUST-40010");
    }

    // === 非法转移 — IN_PROGRESS 起步（-> PENDING, -> IN_PROGRESS）===

    @Test
    void inProgressToPending_illegal() {
        assertThatThrownBy(() -> service.assertTransition(TouchTaskStatus.IN_PROGRESS, TouchTaskStatus.PENDING))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "CUST-40010");
    }

    @Test
    void inProgressToInProgress_illegal() {
        assertThatThrownBy(() -> service.assertTransition(TouchTaskStatus.IN_PROGRESS, TouchTaskStatus.IN_PROGRESS))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "CUST-40010");
    }

    // === 非法转移 — SUCCESS 为终态（-> 所有状态均非法）===

    @Test
    void successToAnything_illegal() {
        for (TouchTaskStatus to : TouchTaskStatus.values()) {
            assertThatThrownBy(() -> service.assertTransition(TouchTaskStatus.SUCCESS, to))
                    .as("SUCCESS -> %s 应抛 BizException", to)
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", "CUST-40010");
        }
    }

    // === 非法转移 — CANCELLED 为终态（-> 所有状态均非法）===

    @Test
    void cancelledToAnything_illegal() {
        for (TouchTaskStatus to : TouchTaskStatus.values()) {
            assertThatThrownBy(() -> service.assertTransition(TouchTaskStatus.CANCELLED, to))
                    .as("CANCELLED -> %s 应抛 BizException", to)
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", "CUST-40010");
        }
    }

    // === null 处理（2 条）===

    @Test
    void nullFrom_throws() {
        assertThatThrownBy(() -> service.assertTransition(null, TouchTaskStatus.IN_PROGRESS))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "CUST-40010");
    }

    @Test
    void nullTo_throws() {
        assertThatThrownBy(() -> service.assertTransition(TouchTaskStatus.PENDING, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "CUST-40010");
    }

    // === isValidTransition（4 条）===

    @Test
    void isValidTransition_returnsTrueForLegal_pendingToInProgress() {
        assertThat(service.isValidTransition(TouchTaskStatus.PENDING, TouchTaskStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    void isValidTransition_returnsTrueForLegal_inProgressToSuccess() {
        assertThat(service.isValidTransition(TouchTaskStatus.IN_PROGRESS, TouchTaskStatus.SUCCESS)).isTrue();
    }

    @Test
    void isValidTransition_returnsFalseForIllegal_successToPending() {
        assertThat(service.isValidTransition(TouchTaskStatus.SUCCESS, TouchTaskStatus.PENDING)).isFalse();
    }

    @Test
    void isValidTransition_returnsFalseForNull() {
        assertThat(service.isValidTransition(null, TouchTaskStatus.IN_PROGRESS)).isFalse();
        assertThat(service.isValidTransition(TouchTaskStatus.PENDING, null)).isFalse();
    }
}
