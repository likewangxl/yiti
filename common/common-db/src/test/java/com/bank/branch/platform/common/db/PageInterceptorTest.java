package com.bank.branch.platform.common.db;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PageInterceptorTest {
    @Test void safeSortFieldShouldAllowValidField() {
        assertTrue(PageInterceptor.isSafeSortField("created_time"));
        assertTrue(PageInterceptor.isSafeSortField("userName"));
        assertTrue(PageInterceptor.isSafeSortField("id"));
    }
    @Test void safeSortFieldShouldRejectInjection() {
        assertFalse(PageInterceptor.isSafeSortField("1; DROP TABLE--"));
        assertFalse(PageInterceptor.isSafeSortField("field OR 1=1"));
        assertFalse(PageInterceptor.isSafeSortField("name; DELETE"));
    }
    @Test void safeSortFieldShouldRejectNull() {
        assertFalse(PageInterceptor.isSafeSortField(null));
    }
    @Test void safeSortFieldShouldRejectEmpty() {
        assertFalse(PageInterceptor.isSafeSortField(""));
    }
}
