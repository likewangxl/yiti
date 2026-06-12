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

    /**
     * 审批「分配关系调整」申请（通过 / 驳回，<b>无会话版</b>）。
     *
     * <p>面向手机端等外部渠道（callpu {@code PERF_APPR}）：按 {@code perfAdjustNo} + 审批人
     * {@code empId} 解析当前待办任务 —— <b>候选组 / 角色可见性即权限校验</b>：审批人看不到该待办
     * （查不到 taskId）则拒绝审批。再按 {@code apprStatus} 走通过 / 驳回，委托 workflow 无会话审批，
     * 全程不依赖登录会话。</p>
     *
     * <p><b>下一步路由（{@code routeTo}）</b>：与管理端经办审批一致，仅在「同意」且当前节点是含排他网关的
     * 经办节点时生效——</p>
     * <ul>
     *   <li>{@code biz_dept_review}（公司部/零售部经办）→ 写网关变量 {@code corpRouteTo}：
     *       {@code LEADER}（交部门负责人）/ {@code OWNER}（交原业绩所属人会签）；</li>
     *   <li>{@code finance_review}（资财部经办）→ 写网关变量 {@code finRouteTo}：
     *       {@code LEADER}（交资财部负责人）/ {@code END}（审批结束）。</li>
     * </ul>
     * <p>其余节点忽略 {@code routeTo}；经办节点未传 {@code routeTo} 时按默认走最全链路
     * （corp→OWNER、fin→LEADER），保持老渠道兼容不卡流程。</p>
     *
     * @param perfAdjustNo 申请主键 id（手机端 perfAdjustNo）
     * @param empId        审批人工号（外部渠道认证后透传）
     * @param apprStatus   审批结论："1"=通过 / "2"=驳回
     * @param opinion      审批意见（可空，空则由实现兜底默认文案）
     * @param routeTo      经办节点下一步路由选择（可空；非经办节点 / 驳回时忽略）
     * @throws IllegalStateException    审批人无该待办任务（无权审批 / 已被处理）
     * @throws IllegalArgumentException apprStatus 非 "1"/"2"
     */
    void approveAllocAdjust(String perfAdjustNo, String empId, String apprStatus, String opinion, String routeTo);
}
