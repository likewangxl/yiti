package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.eval.dto.EvalRewardPendingGroupDTO;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvalRewardItemMapper 汇总/下钻集成测试（奖励分配按部门聚合）。
 * <p>测试数据前缀 {@code TEST_RW_*}，@Transactional + @Rollback 自动回滚。</p>
 */
class EvalRewardItemMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private EvalRewardItemMapper itemMapper;
    @Autowired
    private EvalAssignBatchMapper batchMapper;

    /** 建一个进行中(0)、截止未过的 REWARD 批次并返回其自增 ID。 */
    private Long newActiveBatch() {
        EvalAssignBatch b = new EvalAssignBatch();
        b.setTaskType("REWARD");
        b.setBatchName("TEST_RW_批次");
        b.setSource("IMPORT");
        b.setDeadline(LocalDateTime.now().plusDays(7));
        b.setStatus(0);
        b.setCreateBy("TEST_RW_OP");
        b.setCreateTime(LocalDateTime.now());
        batchMapper.insert(b);
        return b.getBatchId();
    }

    private EvalRewardItem item(Long batchId, String assignUserId, String beId, String dept, String total) {
        EvalRewardItem it = new EvalRewardItem();
        it.setBatchId(batchId);
        it.setAssignUserId(assignUserId);
        it.setBeAssignedUserId(beId);
        it.setBeAssignedUserName("被");
        it.setDeptName(dept);
        it.setOriginalValue(new BigDecimal("76.5"));
        it.setCashValue(new BigDecimal("80"));
        it.setAssignTotal(new BigDecimal(total));
        it.setSubmitted(0);
        return it;
    }

    @Test
    @DisplayName("selectRewardPendingGroups：按部门聚合未提交明细，含 pendingCount 与 assignTotal")
    void selectRewardPendingGroups_groupByDept() {
        Long batchId = newActiveBatch();
        String a = "TEST_RW_A1";
        itemMapper.batchInsert(List.of(
                item(batchId, a, "TEST_RW_B1", "信贷部", "100"),
                item(batchId, a, "TEST_RW_B2", "信贷部", "100"),
                item(batchId, a, "TEST_RW_B3", "零售部", "200")));

        List<EvalRewardPendingGroupDTO> groups = itemMapper.selectRewardPendingGroups(a);

        assertThat(groups).hasSize(2);
        EvalRewardPendingGroupDTO credit = groups.stream()
                .filter(g -> "信贷部".equals(g.getDept())).findFirst().orElseThrow();
        assertThat(credit.getPendingCount()).isEqualTo(2);
        assertThat(credit.getAssignTotal()).isEqualByComparingTo("100");
        assertThat(credit.getTaskType()).isEqualTo("REWARD");
        EvalRewardPendingGroupDTO retail = groups.stream()
                .filter(g -> "零售部".equals(g.getDept())).findFirst().orElseThrow();
        assertThat(retail.getPendingCount()).isEqualTo(1);
        assertThat(retail.getAssignTotal()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("selectByAssignerBatchDept：按分配人+批次+部门下钻全部明细")
    void selectByAssignerBatchDept_filterByDept() {
        Long batchId = newActiveBatch();
        String a = "TEST_RW_A2";
        itemMapper.batchInsert(List.of(
                item(batchId, a, "TEST_RW_C1", "信贷部", "100"),
                item(batchId, a, "TEST_RW_C2", "信贷部", "100"),
                item(batchId, a, "TEST_RW_C3", "零售部", "200")));

        List<EvalRewardItem> credit = itemMapper.selectByAssignerBatchDept(a, batchId, "信贷部");
        assertThat(credit).hasSize(2);
        assertThat(credit).extracting(EvalRewardItem::getDeptName).containsOnly("信贷部");

        List<EvalRewardItem> retail = itemMapper.selectByAssignerBatchDept(a, batchId, "零售部");
        assertThat(retail).hasSize(1);
        assertThat(retail.get(0).getBeAssignedUserId()).isEqualTo("TEST_RW_C3");
    }

    @Test
    @DisplayName("markAssigned：带 submitted=0 乐观条件，写入分配值并置已提交")
    void markAssigned_writesValueAndFlag() {
        Long batchId = newActiveBatch();
        String a = "TEST_RW_A3";
        itemMapper.batchInsert(List.of(item(batchId, a, "TEST_RW_D1", "信贷部", "100")));
        EvalRewardItem before = itemMapper.selectByAssignerBatchDept(a, batchId, "信贷部").get(0);

        int updated = itemMapper.markAssigned(before.getItemId(), new BigDecimal("100"), LocalDateTime.now());

        assertThat(updated).isEqualTo(1);
        EvalRewardItem after = itemMapper.selectById(before.getItemId());
        assertThat(after.getSubmitted()).isEqualTo(1);
        assertThat(after.getAssignValue()).isEqualByComparingTo("100");
        // 二次执行乐观条件不再命中
        assertThat(itemMapper.markAssigned(before.getItemId(), new BigDecimal("50"), LocalDateTime.now())).isEqualTo(0);
    }
}
