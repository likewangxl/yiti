package com.bank.branch.platform.soap.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SidecarRegistrationChecker#register 单测：边车注册检查的轮询与判定逻辑。
 * <p>
 * 用 0ms 重试间隔避免测试变慢；probe 被 mock，按序返回模拟边车响应。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SidecarRegistrationCheckerTest {

    @Mock
    private SidecarProbe probe;

    private SidecarRegistrationChecker checker;

    @BeforeEach
    void setUp() {
        checker = new SidecarRegistrationChecker(probe, 0L);
    }

    @Test
    void register_isReadyEventuallyZero_thenUpZero_returnsSuccess() {
        // /isready 先返回 "1"（未就绪）再返回 "0"（就绪）；/up 返回 "0"
        when(probe.get("/isready")).thenReturn("1", "0");
        when(probe.get("/up")).thenReturn("0");

        SidecarRegistrationChecker.Result result = checker.register();

        assertThat(result).isEqualTo(SidecarRegistrationChecker.Result.SUCCESS);
        verify(probe, times(2)).get("/isready");
        verify(probe).get("/up");
    }

    @Test
    void register_isReadyTimeoutThenZero_returnsSuccess() {
        // 超时（probe 返回 null）也应继续轮询，直到 "0"
        when(probe.get("/isready")).thenReturn(null, "0");
        when(probe.get("/up")).thenReturn("0");

        SidecarRegistrationChecker.Result result = checker.register();

        assertThat(result).isEqualTo(SidecarRegistrationChecker.Result.SUCCESS);
        verify(probe, times(2)).get("/isready");
    }

    @Test
    void register_upReturnsOneThenZero_retriesUntilSuccess() {
        // /up 先返回 "1"（注册未成功）再返回 "0"（成功）——按间隔重试直到 "0"
        when(probe.get("/isready")).thenReturn("0");
        when(probe.get("/up")).thenReturn("1", "0");

        SidecarRegistrationChecker.Result result = checker.register();

        assertThat(result).isEqualTo(SidecarRegistrationChecker.Result.SUCCESS);
        verify(probe, times(2)).get("/up");
    }

    @Test
    void register_upTimeoutThenZero_retriesUntilSuccess() {
        // /up 超时（probe 返回 null）也应继续重试，直到 "0"
        when(probe.get("/isready")).thenReturn("0");
        when(probe.get("/up")).thenReturn(null, "0");

        SidecarRegistrationChecker.Result result = checker.register();

        assertThat(result).isEqualTo(SidecarRegistrationChecker.Result.SUCCESS);
        verify(probe, times(2)).get("/up");
    }

    @Test
    void deregister_callsDownEndpoint() {
        checker.deregister();

        verify(probe).get("/down");
    }
}
