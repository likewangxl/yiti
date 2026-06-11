package com.bank.branch.platform.performance.facade;

import java.util.List;

/**
 * 分配关系调整审批「下一节点」静态推算器。
 *
 * <p>callPu 手机端「同意」不带网关路由选择，{@code PerfApprovalCmdFacade} 将
 * {@code corpRouteTo=OWNER} / {@code finRouteTo=LEADER} 硬编码下发，故
 * perf_alloc_adjust_corp_v1 / perf_alloc_adjust_retail_v1 两条流程在手机端口径下
 * 是<b>确定的线性链路</b>，可按 nodeKey 顺序表静态推算下一节点，无需 BPMN 图遍历。</p>
 *
 * <p>「当前节点」由调用方取 Flowable 活动 userTask 的真实节点名，本类只负责「下一节点」。
 * 末节点返回 {@link #FLOW_END}；未知 nodeKey（BPMN 漂移）安全返回空串。</p>
 *
 * <p><b>维护红线</b>：若 corp/retail BPMN 节点链路调整，必须同步更新本类的顺序表。</p>
 */
final class AllocAdjustNodeProgress {

    /** 末节点之后的下一节点文案。 */
    static final String FLOW_END = "流程结束";

    /**
     * 公司链路（custType=CORP，corpRouteTo=OWNER）：
     * 机构负责人 → 公司部经办 → 原业绩所属人会签 → 公司部负责人 → 资财部经办 → 资财部负责人 → end。
     */
    private static final List<String[]> CORP_SEQ = List.of(
            new String[]{"branch_approve", "机构负责人审批"},
            new String[]{"biz_dept_review", "公司部经办审批"},
            new String[]{"original_owner_approve", "原业绩所属人审批"},
            new String[]{"biz_dept_leader_approve", "公司部负责人审批"},
            new String[]{"finance_review", "资财部经办审批"},
            new String[]{"finance_leader_approve", "资财部负责人审批"});

    /**
     * 零售链路（custType=RETAIL，corpRouteTo=OWNER）：
     * 机构负责人 → 零售部经办 → 原业绩所属人会签 → 资财部经办 → 资财部负责人 → end。
     * 注意：零售 OWNER 路径会签后<b>直接汇合</b>，不过「零售部负责人审批」。
     */
    private static final List<String[]> RETAIL_SEQ = List.of(
            new String[]{"branch_approve", "机构负责人审批"},
            new String[]{"biz_dept_review", "零售部经办审批"},
            new String[]{"original_owner_approve", "原业绩所属人审批"},
            new String[]{"finance_review", "资财部经办审批"},
            new String[]{"finance_leader_approve", "资财部负责人审批"});

    private AllocAdjustNodeProgress() {
    }

    /**
     * 按当前节点 nodeKey 推算下一节点中文名。
     *
     * @param custType      CORP / RETAIL（其余值按 CORP 兜底）
     * @param currentNodeKey 当前活动节点 nodeKey
     * @return 下一节点中文名；末节点返回 {@link #FLOW_END}；未知/空 nodeKey 返回空串
     */
    static String nextNodeName(String custType, String currentNodeKey) {
        if (currentNodeKey == null || currentNodeKey.isBlank()) {
            return "";
        }
        List<String[]> seq = "RETAIL".equals(custType) ? RETAIL_SEQ : CORP_SEQ;
        for (int i = 0; i < seq.size(); i++) {
            if (seq.get(i)[0].equals(currentNodeKey)) {
                return i + 1 < seq.size() ? seq.get(i + 1)[1] : FLOW_END;
            }
        }
        return "";
    }
}
