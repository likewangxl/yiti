package com.bank.branch.platform.common.db;

import org.junit.jupiter.api.Test;
import java.util.Properties;
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

    @Test void setProperties_shouldParseThreshold() {
        SlowSqlInterceptor interceptor = new SlowSqlInterceptor();
        Properties props = new Properties();
        props.setProperty("thresholdMs", "3000");
        interceptor.setProperties(props);
        assertEquals(3000L, interceptor.getThresholdMs());
    }

    @Test void setProperties_noThresholdKey_shouldKeepDefault() {
        SlowSqlInterceptor interceptor = new SlowSqlInterceptor();
        Properties props = new Properties();
        interceptor.setProperties(props);
        assertEquals(5000L, interceptor.getThresholdMs());
    }
}
