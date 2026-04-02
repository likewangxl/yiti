package com.bank.branch.platform.common.db;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlowSqlInterceptorTest {
    @Test void defaultThresholdShouldBe5000() {
        SlowSqlInterceptor interceptor = new SlowSqlInterceptor();
        assertEquals(5000L, interceptor.getThresholdMs());
    }
    @Test void shouldAcceptCustomThreshold() {
        SlowSqlInterceptor interceptor = new SlowSqlInterceptor();
        interceptor.setThresholdMs(3000L);
        assertEquals(3000L, interceptor.getThresholdMs());
    }
}
