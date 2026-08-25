package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PerfAllocAdjustItemMapper 集成测试 (V1.2 Q2.1).
 *
 * <p>对齐生产 DDL：
 * <ul>
 *   <li>字段：id / apply_id / item_kind / emp_id / ratio / created_time</li>
 *   <li>UK：(apply_id, emp_id) 防止同一申请同员工重复登记</li>
 *   <li>NEW/ORIGIN 分开保存，避免审批落地把 ORIGIN 快照当作新分配</li>
 * </ul>
 *
 * <p>覆盖方法：batchInsert / selectByApplyId / deleteByApplyId.
 */
class PerfAllocAdjustItemMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfAllocAdjustItemMapper mapper;

    private PerfAllocAdjustItem item(String applyId, String itemKind, String empId, String ratio) {
        PerfAllocAdjustItem i = new PerfAllocAdjustItem();
        i.setId("TEST_AI_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        i.setApplyId(applyId);
        i.setItemKind(itemKind);
        i.setEmpId(empId);
        i.setRatio(new BigDecimal(ratio));
        return i;
    }

    @Test
    @DisplayName("batchInsert + selectByApplyId 顺序 / 比例回读一致")
    void batchInsert_and_selectByApplyId_ok() {
        String applyId = "TEST_AI_APP_" + UUID.randomUUID().toString().substring(0, 8);
        PerfAllocAdjustItem it1 = item(applyId, "NEW", "EMP_A", "60.00");
        PerfAllocAdjustItem it2 = item(applyId, "ORIGIN", "EMP_B", "40.00");
        int rows = mapper.batchInsert(Arrays.asList(it1, it2));
        assertThat(rows).isEqualTo(2);

        List<PerfAllocAdjustItem> items = mapper.selectByApplyId(applyId);
        assertThat(items).hasSize(2);
        assertThat(items).extracting(PerfAllocAdjustItem::getEmpId)
                .containsExactlyInAnyOrder("EMP_A", "EMP_B");
        assertThat(items).extracting(PerfAllocAdjustItem::getItemKind)
                .containsExactlyInAnyOrder("NEW", "ORIGIN");

        BigDecimal total = BigDecimal.ZERO;
        for (PerfAllocAdjustItem it : items) {
            total = total.add(it.getRatio());
        }
        assertThat(total).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("deleteByApplyId 清空明细（用于更新场景）")
    void deleteByApplyId_ok() {
        String applyId = "TEST_AI_DEL_" + UUID.randomUUID().toString().substring(0, 8);
        mapper.batchInsert(Arrays.asList(
                item(applyId, "NEW", "EMP_D1", "50.00"),
                item(applyId, "NEW", "EMP_D2", "50.00")));

        int deleted = mapper.deleteByApplyId(applyId);
        assertThat(deleted).isEqualTo(2);
        assertThat(mapper.selectByApplyId(applyId)).isEmpty();
    }

    @Test
    @DisplayName("selectByApplyId 不存在时返回空列表")
    void selectByApplyId_emptyWhenNotFound() {
        assertThat(mapper.selectByApplyId("NO_SUCH_APPLY")).isEmpty();
    }
}
