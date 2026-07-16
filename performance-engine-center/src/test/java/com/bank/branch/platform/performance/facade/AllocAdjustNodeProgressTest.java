package com.bank.branch.platform.performance.facade;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分配调整审批「下一节点」静态推算器单元测试（TDD Red→Green）。
 *
 * <p>序列对齐 callPu 硬编码路由（corpRouteTo=OWNER / finRouteTo=LEADER）下
 * perf_alloc_adjust_corp_v1 / perf_alloc_adjust_retail_v1 的确定链路。</p>
 */
class AllocAdjustNodeProgressTest {

    // ---------- CORP（公司）链路 ----------

    @Test
    void corp_branchApprove_nextIsBizDeptReview() {
        assertThat(AllocAdjustNodeProgress.nextNodeName("CORP", "branch_approve"))
                .isEqualTo("公司部经办审批");
    }

    @Test
    void corp_bizDeptReview_nextIsOriginalOwner() {
        assertThat(AllocAdjustNodeProgress.nextNodeName("CORP", "biz_dept_review"))
                .isEqualTo("原业绩所属机构负责人审批");
    }

    @Test
    void corp_originalOwner_nextIsBizDeptLeader() {
        // 公司链路：会签通过后过「公司部负责人审批」
        assertThat(AllocAdjustNodeProgress.nextNodeName("CORP", "original_owner_approve"))
                .isEqualTo("公司部负责人审批");
    }

    @Test
    void corp_bizDeptLeader_nextIsFinanceReview() {
        assertThat(AllocAdjustNodeProgress.nextNodeName("CORP", "biz_dept_leader_approve"))
                .isEqualTo("资财部经办审批");
    }

    @Test
    void corp_financeReview_nextIsFinanceLeader() {
        assertThat(AllocAdjustNodeProgress.nextNodeName("CORP", "finance_review"))
                .isEqualTo("资财部负责人审批");
    }

    @Test
    void corp_financeLeader_isLast_nextIsFlowEnd() {
        assertThat(AllocAdjustNodeProgress.nextNodeName("CORP", "finance_leader_approve"))
                .isEqualTo("流程结束");
    }

    // ---------- RETAIL（零售）链路 ----------

    @Test
    void retail_bizDeptReview_nameIsRetail() {
        assertThat(AllocAdjustNodeProgress.nextNodeName("RETAIL", "branch_approve"))
                .isEqualTo("零售部经办审批");
    }

    @Test
    void retail_originalOwner_skipsLeader_nextIsFinanceReview() {
        // 零售链路：OWNER 路径会签后直接汇合进资财部，不过「零售部负责人审批」
        assertThat(AllocAdjustNodeProgress.nextNodeName("RETAIL", "original_owner_approve"))
                .isEqualTo("资财部经办审批");
    }

    @Test
    void retail_financeLeader_isLast_nextIsFlowEnd() {
        assertThat(AllocAdjustNodeProgress.nextNodeName("RETAIL", "finance_leader_approve"))
                .isEqualTo("流程结束");
    }

    // ---------- 边界 ----------

    @Test
    void unknownNodeKey_returnsEmpty() {
        assertThat(AllocAdjustNodeProgress.nextNodeName("CORP", "no_such_node")).isEmpty();
    }

    @Test
    void nullNodeKey_returnsEmpty() {
        assertThat(AllocAdjustNodeProgress.nextNodeName("CORP", null)).isEmpty();
    }
}
