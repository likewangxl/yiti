package com.bank.branch.platform.common.db.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MybatisPlusConfigTest {

    @Test
    void shouldRegisterOptimisticLockerBeforePagination() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();
        List<?> innerInterceptors = interceptor.getInterceptors();

        assertEquals(2, innerInterceptors.size());
        assertTrue(innerInterceptors.get(0) instanceof OptimisticLockerInnerInterceptor);
        assertTrue(innerInterceptors.get(1) instanceof PaginationInnerInterceptor);
    }
}
