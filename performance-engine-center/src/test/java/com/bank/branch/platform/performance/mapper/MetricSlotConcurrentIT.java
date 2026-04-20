package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.service.MetricSlotService;
import com.bank.branch.platform.performance.support.PerformanceConcurrentTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetricSlotService 并发基线测试.
 *
 * <p>该用例不验证最终互斥，只证明 Service 层本身不带锁时，
 * 两个并发请求会基于同一快照计算出相同槽位，真正的互斥由 Facade 层分布式锁保证。
 */
class MetricSlotConcurrentIT extends PerformanceConcurrentTestBase {

    private static final String BASE_DIM = "CONCUR_METRIC_DIM";

    @Autowired
    private MetricSlotService metricSlotService;

    @Test
    @DisplayName("无 Facade 锁时两个并发分配会算出同一槽位")
    void allocSlot_concurrentTwoThreads_bothWouldGetSameSlot_withoutFacadeLock() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Integer> task = () -> metricSlotService.allocSlot(BASE_DIM, 1, null);
        try {
            Future<Integer> first = pool.submit(task);
            Future<Integer> second = pool.submit(task);

            assertThat(first.get()).isEqualTo(1);
            assertThat(second.get()).isEqualTo(1);
        } catch (ExecutionException ex) {
            throw unwrap(ex);
        } finally {
            pool.shutdownNow();
        }
    }

    private Exception unwrap(ExecutionException ex) throws Exception {
        Throwable cause = ex.getCause();
        if (cause instanceof Exception exception) {
            return exception;
        }
        return ex;
    }
}
