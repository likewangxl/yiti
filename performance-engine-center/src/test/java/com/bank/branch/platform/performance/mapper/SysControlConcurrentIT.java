package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.facade.SysControlFacade;
import com.bank.branch.platform.performance.service.cmd.SwitchVersionCmd;
import com.bank.branch.platform.performance.support.PerformanceConcurrentTestBase;
import com.bank.branch.platform.performance.support.TestDbCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SysControlFacade 并发切换 IT.
 *
 * <p>不继承 @Transactional 基类 (Spring 事务与多线程不兼容),
 * 使用 CONCUR_SC_* 前缀, @AfterEach 手工清理.
 *
 * <p>验证: 两个线程并发切同一 scope_dim 的版本, 只有一个成功, 另一个被拒绝 (锁或 DB UK 兜底).
 */
class SysControlConcurrentIT extends PerformanceConcurrentTestBase {

    private static final String SCOPE_DIM = "CONCUR_DIM_EMP_X";

    @Autowired
    private SysControlFacade sysControlFacade;

    @Autowired
    private SysControlMapper sysControlMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void seedBaseline() {
        // 清理可能的遗留锁
        redisTemplate.delete("perf:sys_control:switch:" + SCOPE_DIM);
        // 清理前缀遗留数据 (防止上次失败残留)
        cleanByIdPrefix();
        cleanByScope();

        // 插入一条基线 is_valid=1 的版本, 以便 switchVersion 能找到当前版本
        SysControl baseline = new SysControl();
        baseline.setId("CONCUR_SC_BASE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        baseline.setScopeDim(SCOPE_DIM);
        baseline.setLatestDataDate(LocalDate.of(2099, 1, 1));
        baseline.setCurrentVersion("V_BASE");
        baseline.setIsValid(1);
        LocalDateTime now = LocalDateTime.now();
        baseline.setCreatedTime(now);
        baseline.setUpdatedTime(now);
        sysControlMapper.insert(baseline);
    }

    @AfterEach
    void cleanup() {
        redisTemplate.delete("perf:sys_control:switch:" + SCOPE_DIM);
        cleanByIdPrefix();
        cleanByScope();
    }

    @Test
    @DisplayName("双线程并发切换同一 scopeDim, 只有一个成功")
    void switchVersion_concurrentTwoThreads_onlyOneWins() throws Exception {
        // Given
        LocalDate targetDate = LocalDate.of(2099, 12, 31);
        SwitchVersionCmd cmdA = SwitchVersionCmd.builder()
                .scopeDim(SCOPE_DIM).dataDate(targetDate).newVersion("V_A")
                .reason("threadA").operator("admin").build();
        SwitchVersionCmd cmdB = SwitchVersionCmd.builder()
                .scopeDim(SCOPE_DIM).dataDate(targetDate).newVersion("V_B")
                .reason("threadB").operator("admin").build();

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger otherErrorCount = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Runnable runA = () -> {
            try {
                start.await();
                sysControlFacade.switchVersion(cmdA);
                successCount.incrementAndGet();
            } catch (PerfException e) {
                if (PerfErrorCode.SYS_CONTROL_CONFLICT == e.getErrorCode()) {
                    conflictCount.incrementAndGet();
                } else {
                    otherErrorCount.incrementAndGet();
                }
            } catch (Throwable t) {
                otherErrorCount.incrementAndGet();
            } finally {
                done.countDown();
            }
        };
        Runnable runB = () -> {
            try {
                start.await();
                sysControlFacade.switchVersion(cmdB);
                successCount.incrementAndGet();
            } catch (PerfException e) {
                if (PerfErrorCode.SYS_CONTROL_CONFLICT == e.getErrorCode()) {
                    conflictCount.incrementAndGet();
                } else {
                    otherErrorCount.incrementAndGet();
                }
            } catch (Throwable t) {
                otherErrorCount.incrementAndGet();
            } finally {
                done.countDown();
            }
        };
        pool.submit(runA);
        pool.submit(runB);

        // When: 两线程同时出发
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // Then: 成功 1 次 + 冲突 1 次 (其他错误 0)
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
        assertThat(otherErrorCount.get()).isEqualTo(0);

        // DB 仅保留一条 is_valid=1 记录且是成功的那一方
        SysControl curr = sysControlMapper.selectByScopeAndValid(SCOPE_DIM);
        assertThat(curr).isNotNull();
        assertThat(curr.getCurrentVersion()).isIn("V_A", "V_B");
    }

    private void cleanByIdPrefix() {
        TestDbCleaner.cleanByPrefix(jdbcTemplate, "sys_control", "id", "CONCUR_SC_");
    }

    private void cleanByScope() {
        // 保险: 按 scope_dim 清 CONCUR_DIM_EMP_X 下所有记录
        jdbcTemplate.update("DELETE FROM sys_control WHERE scope_dim = ?", SCOPE_DIM);
    }
}
