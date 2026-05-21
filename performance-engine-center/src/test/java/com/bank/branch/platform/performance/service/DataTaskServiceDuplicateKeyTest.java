package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.common.web.lock.LockManager;
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

import java.time.Instant;
import java.time.LocalDate;

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
 * DataTaskService V1.3 R0.2 DuplicateKey 降级单元测试（去 Redis 后改 mock LockManager）.
 *
 * <p>分布式锁从 Redis SETNX 改为 PT_LOCK + LockManager，DB 唯一键兜底语义不变。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataTaskServiceDuplicateKeyTest {

    @Mock
    private PerfRunTaskMapper perfRunTaskMapper;

    @Mock
    private LockManager lockManager;

    @InjectMocks
    private DataTaskService dataTaskService;

    /** 幂等锁 key 前缀（与 Service 常量保持一致）. */
    private static final String LOCK_KEY_PREFIX = "perf:data_task:";

    private static final String TASK_ID = "TEST_EXT_R02_TASK_001";

    @BeforeEach
    void setUp() {
        // 默认 tryLock 成功（所有用例都跑到持锁写路径），具体用例可覆盖
        when(lockManager.tryLock(eq(LOCK_KEY_PREFIX + TASK_ID), anyString(), anyLong()))
            .thenReturn(true);
        when(lockManager.unlock(eq(LOCK_KEY_PREFIX + TASK_ID), anyString()))
            .thenReturn(true);
    }

    @Test
    @DisplayName("insert 抛 DuplicateKeyException → 回查命中既有行 → 返回 accepted=false")
    void insert_duplicateKey_fallbackReturnsExistingRow() {
        when(perfRunTaskMapper.selectByTaskNo(TASK_ID))
            .thenReturn(null)
            .thenReturn(null)
            .thenReturn(existingRowWithId("EXISTING_RUN_TASK_ID"));

        doThrow(new DuplicateKeyException("uk_task_key violated"))
            .when(perfRunTaskMapper).insert(any(PerfRunTask.class));

        DataTaskReportResultDTO result = dataTaskService.report(buildCmd());

        assertThat(result.getTaskId()).isEqualTo(TASK_ID);
        assertThat(result.getAccepted()).as("DuplicateKey 降级返回幂等 accepted=false").isFalse();
        assertThat(result.getPerfRunTaskId()).isEqualTo("EXISTING_RUN_TASK_ID");

        verify(perfRunTaskMapper, atLeastOnce()).selectByTaskNo(TASK_ID);
        verify(perfRunTaskMapper).insert(any(PerfRunTask.class));
    }

    @Test
    @DisplayName("insert 抛 DuplicateKeyException 但回查仍返回 null → 抛 PerfException(CALC_JOB_FAILED)")
    void insert_duplicateKey_fallbackReturnsNull_throwsPerfException() {
        when(perfRunTaskMapper.selectByTaskNo(TASK_ID))
            .thenReturn(null)
            .thenReturn(null)
            .thenReturn(null);

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
            .thenReturn(null)
            .thenReturn(null);

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
