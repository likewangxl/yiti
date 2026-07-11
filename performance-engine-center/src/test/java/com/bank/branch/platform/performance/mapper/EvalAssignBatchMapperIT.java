package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvalAssignBatchMapper 过期关闭集成测试（覆盖 EVAL/REWARD 两类导入批次）。
 * <p>测试数据前缀 {@code TEST_EXP_*}，@Transactional + @Rollback 自动回滚。</p>
 */
class EvalAssignBatchMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private EvalAssignBatchMapper batchMapper;

    private Long newBatch(String taskType, int status, LocalDateTime deadline) {
        EvalAssignBatch b = new EvalAssignBatch();
        b.setTaskType(taskType);
        b.setBatchName("TEST_EXP_批次");
        b.setSource("IMPORT");
        b.setDeadline(deadline);
        b.setStatus(status);
        b.setCreateBy("TEST_EXP_OP");
        b.setCreateTime(LocalDateTime.now());
        batchMapper.insert(b);
        return b.getBatchId();
    }

    @Test
    @DisplayName("closeExpiredBatches：仅进行中(0)+已过期批次置已结束(1)；未来/草稿不动，EVAL/REWARD 均覆盖")
    void closeExpiredBatches_onlyActiveExpired() {
        LocalDateTime past = LocalDateTime.now().minusHours(1);
        LocalDateTime future = LocalDateTime.now().plusDays(3);
        Long evalExpired = newBatch("EVAL", 0, past);      // 进行中+过期 → 关闭
        Long rewardExpired = newBatch("REWARD", 0, past);  // 进行中+过期 → 关闭（奖励分配同覆盖）
        Long activeFuture = newBatch("REWARD", 0, future); // 进行中+未过期 → 不动
        Long draftExpired = newBatch("EVAL", 2, past);     // 草稿+过期 → 不动

        int closed = batchMapper.closeExpiredBatches();

        // 至少关闭本测试造的 2 个过期进行中批次（并发/存量数据可能更多，故用 >=）
        assertThat(closed).isGreaterThanOrEqualTo(2);
        assertThat(batchMapper.selectById(evalExpired).getStatus()).isEqualTo(1);
        assertThat(batchMapper.selectById(rewardExpired).getStatus()).isEqualTo(1);
        assertThat(batchMapper.selectById(activeFuture).getStatus()).isEqualTo(0);
        assertThat(batchMapper.selectById(draftExpired).getStatus()).isEqualTo(2);
    }
}
