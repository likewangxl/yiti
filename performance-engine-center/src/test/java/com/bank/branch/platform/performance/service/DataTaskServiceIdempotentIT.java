package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.api.dto.DataTaskReportResultDTO;
import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;
import com.bank.branch.platform.performance.support.PerformanceConcurrentRedisTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DataTaskService 幂等 IT（Task P6.3 Red）.
 *
 * <p>目标：N 个线程并发上报 <em>同一</em> {@code taskId}，只应有一条 {@code perf_run_task} 落库；
 * 所有线程返回的 {@code perfRunTaskId} 完全一致；{@code accepted=true} 仅出现一次。
 *
 * <p>不继承 {@code @Transactional} 基类（Spring 事务与多线程不兼容），
 * 用 {@code CONCUR_EXT_} 前缀 + {@link JdbcTemplate} 在 @BeforeEach / @AfterEach 手工清理.
 *
 * <p>P6.2 已实现 "先查再写"的乐观幂等，但两个线程 T1/T2 同时 selectByTaskNo 都拿到 null 时，
 * 会并发双写；P6.3 需通过 Redis SETNX (或双检+互斥) 保证原子幂等，本 IT 即此约束的守护。
 */
/**
 * V1.3 Task R5.1 升级：改继承 {@link PerformanceConcurrentRedisTestBase} 获取
 * Testcontainers 启动的 Redis 容器；{@code @EnabledIfSystemProperty} 保护本 IT
 * 仅在 CI (-Dtestcontainers.enabled=true) 或开发者显式指定时运行，无 Docker
 * 本地环境默认跳过，避免 `mvn verify` 因容器启动失败阻塞构建。
 */
@EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
class DataTaskServiceIdempotentIT extends PerformanceConcurrentRedisTestBase {

    /** 并发上报使用的 taskId（CONCUR_EXT_ 前缀便于清理）. */
    private static final String TASK_ID = "CONCUR_EXT_P63_TASK_001";

    /** Redis 幂等锁 key 前缀（与 Service 实现需保持一致；测试清理用）. */
    private static final String REDIS_KEY_PREFIX = "perf:data_task:";

    @Autowired
    private DataTaskService dataTaskService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void cleanupBefore() {
        // 清理可能遗留的锁 / DB 记录
        redisTemplate.delete(REDIS_KEY_PREFIX + TASK_ID);
        jdbcTemplate.update("DELETE FROM PERF_RUN_TASK WHERE task_key LIKE ?", "CONCUR_EXT_%");
    }

    @AfterEach
    void cleanupAfter() {
        redisTemplate.delete(REDIS_KEY_PREFIX + TASK_ID);
        jdbcTemplate.update("DELETE FROM PERF_RUN_TASK WHERE task_key LIKE ?", "CONCUR_EXT_%");
    }

    @Test
    @DisplayName("并发上报同 taskId: DB 只落一条 + 返回 runTaskId 一致 + accepted=true 仅 1 次")
    void reportSameTaskId_concurrent_onlyOneInsertAndConsistentId() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        CopyOnWriteArrayList<DataTaskReportResultDTO> results = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    DataTaskReportResultDTO r = dataTaskService.report(buildCmd());
                    results.add(r);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // 1. 无任何异常（含 DuplicateKeyException / PerfException）
        assertThat(errors).as("并发上报不应抛任何异常").isEmpty();

        // 2. 所有线程都拿到结果
        assertThat(results).hasSize(threads);

        // 3. DB 仅存在一条记录（同 task_key）
        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PERF_RUN_TASK WHERE task_key = ?", Long.class, TASK_ID);
        assertThat(rowCount).as("同 taskId 并发上报只应落一条 run_task").isEqualTo(1L);

        // 4. 所有返回的 perfRunTaskId 完全一致
        Set<String> distinctIds = results.stream()
                .map(DataTaskReportResultDTO::getPerfRunTaskId)
                .collect(Collectors.toSet());
        assertThat(distinctIds).as("所有并发调用返回同一个 perfRunTaskId").hasSize(1);

        // 5. accepted=true 恰好出现 1 次（胜出者），其余 accepted=false
        long acceptedCount = results.stream()
                .filter(r -> Boolean.TRUE.equals(r.getAccepted()))
                .count();
        assertThat(acceptedCount).as("accepted=true 应仅 1 次（首次胜出）").isEqualTo(1L);
    }

    @Test
    @DisplayName("同步两次上报同 taskId: 第二次返回 accepted=false + 相同 perfRunTaskId")
    void reportSameTaskId_sequential_secondReturnsExistingId() {
        DataTaskStatusCmd cmd = buildCmd();
        DataTaskReportResultDTO first = dataTaskService.report(cmd);
        DataTaskReportResultDTO second = dataTaskService.report(cmd);

        assertThat(first.getAccepted()).isTrue();
        assertThat(second.getAccepted()).isFalse();
        assertThat(second.getPerfRunTaskId()).isEqualTo(first.getPerfRunTaskId());

        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PERF_RUN_TASK WHERE task_key = ?", Long.class, TASK_ID);
        assertThat(rowCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("批量并发：5 个不同 taskId + 每个 4 个并发 = 各自只落 1 条")
    void reportDifferentTaskIds_concurrent_eachInsertedOnce() throws Exception {
        int taskIdCount = 5;
        int threadsPerTaskId = 4;
        ExecutorService pool = Executors.newFixedThreadPool(taskIdCount * threadsPerTaskId);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(taskIdCount * threadsPerTaskId);
        AtomicInteger errorCount = new AtomicInteger();

        for (int k = 0; k < taskIdCount; k++) {
            final String tid = "CONCUR_EXT_BATCH_" + k;
            // 清理
            redisTemplate.delete(REDIS_KEY_PREFIX + tid);
            for (int j = 0; j < threadsPerTaskId; j++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        dataTaskService.report(buildCmd(tid));
                    } catch (Throwable t) {
                        errorCount.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
        }

        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(errorCount.get()).as("批量并发不应有异常").isZero();

        // 每个 taskId 只落 1 条，共 5 条
        Long totalRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PERF_RUN_TASK WHERE task_key LIKE 'CONCUR_EXT_BATCH_%'",
                Long.class);
        assertThat(totalRows).isEqualTo((long) taskIdCount);
    }

    // -------------------- helpers --------------------

    private DataTaskStatusCmd buildCmd() {
        return buildCmd(TASK_ID);
    }

    private DataTaskStatusCmd buildCmd(String taskId) {
        return DataTaskStatusCmd.builder()
                .taskId(taskId)
                .dataType("EMP_INDEX_RESULT")
                .dataDate(LocalDate.of(2026, 4, 10))
                .version("20260410-01")
                .status("SUCCESS")
                .rowCount(1000)
                .errorMsg(null)
                .sourceSystem("CORE_BANK")
                .reportedAt(Instant.now())
                .build();
    }
}
