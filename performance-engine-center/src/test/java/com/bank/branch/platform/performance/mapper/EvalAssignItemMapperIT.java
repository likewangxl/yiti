package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalAssignItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignItemMapper;
import com.bank.branch.platform.performance.eval.dto.EvalPendingGroupDTO;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvalAssignItemMapper 汇总/下钻集成测试（分组部门优先，空回退被打分人部门）。
 * <p>测试数据前缀 {@code TEST_GD_*}，@Transactional + @Rollback 自动回滚。</p>
 */
class EvalAssignItemMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private EvalAssignItemMapper itemMapper;
    @Autowired
    private EvalAssignBatchMapper batchMapper;

    /** 建一个进行中(0)、截止未过的批次并返回其自增 ID。 */
    private Long newActiveBatch() {
        EvalAssignBatch b = new EvalAssignBatch();
        b.setTaskType("EVAL");
        b.setBatchName("TEST_GD_批次");
        b.setSource("IMPORT");
        b.setDeadline(LocalDateTime.now().plusDays(7));
        b.setStatus(0);
        b.setCreateBy("TEST_GD_OP");
        b.setCreateTime(LocalDateTime.now());
        batchMapper.insert(b);
        return b.getBatchId();
    }

    private EvalAssignItem item(Long batchId, String evalUserId, String beUserId,
                                String beDept, String groupDept) {
        EvalAssignItem it = new EvalAssignItem();
        it.setBatchId(batchId);
        it.setEvalUserId(evalUserId);
        it.setEvalUserName("评");
        it.setEvalUserTag("t");
        it.setEvalUserDept("管理部");
        it.setBeEvalUserId(beUserId);
        it.setBeEvalUserName("被");
        it.setBeEvalDept(beDept);
        it.setBeEvalTag("t");
        it.setGroupDept(groupDept);
        it.setWeightTag("主要");
        it.setScoreType("NUM");
        it.setSubmitted(0);
        return it;
    }

    @Test
    @DisplayName("selectPendingGroups：group_dept 非空按分组部门汇总(跨被打分人部门合并计数)，空则回退 be_eval_dept")
    void selectPendingGroups_groupByEffectiveDept() {
        Long batchId = newActiveBatch();
        String ev = "TEST_GD_E1";
        itemMapper.batchInsert(List.of(
                item(batchId, ev, "TEST_GD_B1", "信贷部", "零售条线"),
                item(batchId, ev, "TEST_GD_B2", "零售部", "零售条线"),
                item(batchId, ev, "TEST_GD_B3", "对公部", "")));

        List<EvalPendingGroupDTO> groups = itemMapper.selectPendingGroups(ev);

        assertThat(groups).hasSize(2);
        EvalPendingGroupDTO retail = groups.stream()
                .filter(g -> "零售条线".equals(g.getDept())).findFirst().orElseThrow();
        assertThat(retail.getPendingCount()).isEqualTo(2); // B1+B2 跨部门合并
        EvalPendingGroupDTO corp = groups.stream()
                .filter(g -> "对公部".equals(g.getDept())).findFirst().orElseThrow();
        assertThat(corp.getPendingCount()).isEqualTo(1); // group_dept 空 → 回退 be_eval_dept
    }

    @Test
    @DisplayName("selectByScorerBatchDept：按有效分组键下钻(分组部门命中2条、回退部门命中1条)")
    void selectByScorerBatchDept_filterByEffectiveDept() {
        Long batchId = newActiveBatch();
        String ev = "TEST_GD_E2";
        itemMapper.batchInsert(List.of(
                item(batchId, ev, "TEST_GD_C1", "信贷部", "零售条线"),
                item(batchId, ev, "TEST_GD_C2", "零售部", "零售条线"),
                item(batchId, ev, "TEST_GD_C3", "对公部", "")));

        List<EvalAssignItem> retail = itemMapper.selectByScorerBatchDept(ev, batchId, "零售条线");
        assertThat(retail).hasSize(2);
        assertThat(retail).extracting(EvalAssignItem::getGroupDept).containsOnly("零售条线");

        List<EvalAssignItem> corp = itemMapper.selectByScorerBatchDept(ev, batchId, "对公部");
        assertThat(corp).hasSize(1);
        assertThat(corp.get(0).getBeEvalDept()).isEqualTo("对公部");
    }
}
