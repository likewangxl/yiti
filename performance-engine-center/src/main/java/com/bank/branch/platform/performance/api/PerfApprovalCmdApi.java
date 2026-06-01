package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.AllocAdjustSubmitCmd;

/**
 * 绩效（分配关系调整）审批「写」对外 Api。
 *
 * <p>面向手机端等外部渠道（callpu 网关）提交分配关系调整申请，
 * 实现 {@code PerfApprovalCmdFacade} 委托项目内已有的 {@code AllocAdjustService.submit}，
 * 业务规则（校验、客户存在性、主从表落地、对公/零售 BPMN 路由、启动流程）与 Web 管理端
 * {@code POST /api/perf/alloc-adjust/create} 完全一致。</p>
 *
 * <p>跨模块契约：本接口与 {@link PerfApprovalQueryApi} 一起构成 performance-engine-center
 * 暴露给外部渠道的审批读写入口，调用方禁止直接依赖 perf 的 service/mapper/entity。</p>
 */
public interface PerfApprovalCmdApi {

    /**
     * 提交「分配关系调整」申请（启动审批流）。
     *
     * @param cmd 提交命令（applicant 必填；ownerOrgId 可空，空则按 applicant 反查主机构）
     * @return 申请主键 id（手机端 perfAdjustNo）
     */
    String submitAllocAdjust(AllocAdjustSubmitCmd cmd);

    /**
     * 撤回「分配关系调整」申请（带越权校验，同步取消审批流）。
     *
     * <p>面向外部渠道：仅申请创建人本人可撤回（operator 必须等于申请创建人），
     * 委托 {@code AllocAdjustService.withdrawByApplicant}。仅 IN_APPROVAL / DRAFT 状态可撤回。</p>
     *
     * @param perfAdjustNo 申请主键 id（手机端 perfAdjustNo）
     * @param operator     操作人工号（外部渠道传入，必须等于申请创建人）
     * @param reason       撤回原因（可空，空则由实现兜底默认文案）
     */
    void withdrawAllocAdjust(String perfAdjustNo, String operator, String reason);
}
