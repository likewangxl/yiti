package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.api.dto.DataTaskReportResultDTO;
import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DataTaskService V1.3 R0.2 DuplicateKey 降级单元测试.
 *
 * <p>背景：V1.1 Q6 引入 Redis SETNX 幂等，V1.3 R0.2 给 perf_run_task.task_key 加
 * UNIQUE KEY 作为 Redis 宕机时的 DB 兜底。本测试覆盖三条 DuplicateKey 分支：
 * <ul>
 *   <li>insert 抛 DuplicateKeyException → 回查 selectByTaskNo 命中既有行 →
 *       返回 accepted=false 的幂等结果（不再抛异常）</li>
 *   <li>insert 抛 DuplicateKeyException 但 selectByTaskNo 仍返回 null →
 *       抛 PerfException(CALC_JOB_FAILED)（理论不应到达的兜底）</li>
 *   <li>正常路径（先查既有返 null / 锁获取成功 / insert 成功）不受影响，验证无回归</li>
 * </ul>
 *
 * <p><strong>TDD 节奏</strong>：
 * <ul>
 *   <li>Red（R0.2 Step 1）：DataTaskService.report 中尚无 try-catch DuplicateKeyException
 *       → 测试1抛异常不被吞；测试2相同</li>
 *   <li>Green（R0.2 Step 4）：try { insert } catch (DuplicateKeyException) { selectByTaskKey + 降级 } →
 *       全部通过</li>
 * </ul>
 *
 * <p>选择单元测试而非 IT：V1.2 Q8.5c 已将 DataTaskServiceIdempotentIT @Disabled
 * （依赖本地 Redis），本文件用 Mockito 模拟 Mapper / Redis 交互，不需要真实依赖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)  // 快查命中用例不走 Redis 路径，避免 UnnecessaryStubbing
class DataTaskServiceDuplicateKeyTest {

    @Mock
    private PerfRunTaskMapper perfRunTaskMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private DataTaskService dataTaskService;

    /** 幂等锁 key 前缀（与 Service 常量保持一致）. */
    private static final String LOCK_KEY_PREFIX = "perf:data_task:";

    private static final String TASK_ID = "TEST_EXT_R02_TASK_001";

    @BeforeEach
    void setUp() {
        // 默认 Redis SETNX 成功（所有用例都跑到持锁写路径），具体用例可覆盖
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(LOCK_KEY_PREFIX + TASK_ID), anyString(), any(Duration.class)))
            .thenReturn(Boolean.TRUE);
        // 默认释放锁返回 1（成功删）
        when(redisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList(LOCK_KEY_PREFIX + TASK_ID)), anyString()))
            .thenReturn(1L);
    }

    @Test
    @DisplayName("insert 抛 DuplicateKeyException → 回查命中既有行 → 返回 accepted=false")
    void insert_duplicateKey_fallbackReturnsExistingRow() {
        // 1. 初次查 DB：无（进入写路径）
        // 2. 持锁双检：无（进入 insert）
        when(perfRunTaskMapper.selectByTaskNo(TASK_ID))
            .thenReturn(null)  // 第一次（快查）
            .thenReturn(null)  // 第二次（持锁双检）
            .thenReturn(existingRowWithId("EXISTING_RUN_TASK_ID"));  // 第三次（DuplicateKey 降级后回查）

        // 3. insert 抛 DuplicateKeyException（模拟 DB uk_task_key 被并发线程先写入）
        doThrow(new DuplicateKeyException("uk_task_key violated"))
            .when(perfRunTaskMapper).insert(any(PerfRunTask.class));

        DataTaskReportResultDTO result = dataTaskService.report(buildCmd());

        assertThat(result.getTaskId()).isEqualTo(TASK_ID);
        assertThat(result.getAccepted()).as("DuplicateKey 降级返回幂等 accepted=false").isFalse();
        assertThat(result.getPerfRunTaskId()).isEqualTo("EXISTING_RUN_TASK_ID");

        // selectByTaskNo 至少被调用 3 次（快查 + 持锁双检 + DuplicateKey 降级回查）
        verify(perfRunTaskMapper, atLeastOnce()).selectByTaskNo(TASK_ID);
        // insert 被尝试 1 次
        verify(perfRunTaskMapper).insert(any(PerfRunTask.class));
    }

    @Test
    @DisplayName("insert 抛 DuplicateKeyException 但回查仍返回 null → 抛 PerfException(CALC_JOB_FAILED)")
    void insert_duplicateKey_fallbackReturnsNull_throwsPerfException() {
        when(perfRunTaskMapper.selectByTaskNo(TASK_ID))
            .thenReturn(null)  // 快查
            .thenReturn(null)  // 持锁双检
            .thenReturn(null); // DuplicateKey 降级回查仍然 null（理论不应到达）

        doThrow(new DuplicateKeyException("uk_task_key violated"))
            .when(perfRunTaskMapper).insert(any(PerfRunTask.class));

        assertThatThrownBy(() -> dataTaskService.report(buildCmd()))
            .isInstanceOf(PerfException.class)
            .hasFieldOrPropertyWithValue("errorCode", PerfErrorCode.CALC_JOB_FAILED);

        verify(perfRunTaskMapper, atLeastOnce()).selectByTaskNo(TASK_ID);
    }

    @Test
    @DisplayName("正常路径：快查无 + 持锁双检无 + insert 成功 → accepted=true（无回归）")
    void report_happyPath_insertsNewRow() {
        when(perfRunTaskMapper.selectByTaskNo(TASK_ID))
            .thenReturn(null)  // 快查
            .thenReturn(null); // 持锁双检

        // insert 正常（不抛异常）
        DataTaskReportResultDTO result = dataTaskService.report(buildCmd());

        assertThat(result.getAccepted()).isTrue();
        assertThat(result.getTaskId()).isEqualTo(TASK_ID);
        verify(perfRunTaskMapper).insert(any(PerfRunTask.class));
    }

    @Test
    @DisplayName("快查命中：直接返回既有行 accepted=false，不进入写路径（DuplicateKey 分支无关）")
    void report_fastLookupHit_returnsExisting() {
        when(perfRunTaskMapper.selectByTaskNo(TASK_ID))
            .thenReturn(existingRowWithId("ALREADY_EXISTING_ID"));

        DataTaskReportResultDTO result = dataTaskService.report(buildCmd());

        assertThat(result.getAccepted()).isFalse();
        assertThat(result.getPerfRunTaskId()).isEqualTo("ALREADY_EXISTING_ID");
        verify(perfRunTaskMapper, never()).insert(any(PerfRunTask.class));
    }

    // -------------------- helpers --------------------

    private DataTaskStatusCmd buildCmd() {
        return DataTaskStatusCmd.builder()
            .taskId(TASK_ID)
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

    private PerfRunTask existingRowWithId(String runTaskId) {
        PerfRunTask t = new PerfRunTask();
        t.setId(runTaskId);
        t.setTaskKey(TASK_ID);
        t.setTaskType("EXT_DATA");
        t.setStatus("SUCCESS");
        return t;
    }
}
