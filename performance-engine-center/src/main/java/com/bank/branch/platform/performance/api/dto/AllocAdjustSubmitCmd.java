package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分配关系调整申请「对外渠道」提交命令。
 *
 * <p>由 {@link com.bank.branch.platform.performance.api.PerfApprovalCmdApi} 消费，
 * 供 callpu 网关等外部渠道按员工号提交申请。字段语义与管理端
 * {@code AllocAdjustCreateReqDTO} 一致，差异点：</p>
 * <ul>
 *   <li>{@code applicant} 由报文显式传入（外部渠道无平台登录态）；</li>
 *   <li>{@code ownerOrgId} 可空——为空时由 Facade 按 {@code applicant} 反查主机构补齐。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocAdjustSubmitCmd {

    /** 客户类型：CORP / RETAIL（决定审批流路由）。 */
    private String custType;

    /** 客户编号（存入 PERF_ALLOC_ADJUST_APPLY.cust_id）。 */
    private String custId;

    /** 分配维度：RULE / ACCOUNT。 */
    private String allocDim;

    /** 业务种类（如 CORP_DEPOSIT / RETAIL_LOAN）。 */
    private String bizKind;

    /** 账号（ACCOUNT 维度必填）。 */
    private String accountNo;

    /** 归属机构（可空；空则由 Facade 按 applicant 反查主机构）。 */
    private String ownerOrgId;

    /** 申请原因。 */
    private String reason;

    /** 申请人工号（外部渠道传入，上游已认证）。 */
    private String applicant;

    /** 调整后明细。 */
    private List<Item> items;

    /** 调整明细项。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        /** 调整后归属员工工号。 */
        private String empId;

        /** 调整后分配比例（0-100）。 */
        private BigDecimal ratio;

        /** 说明（可空）。 */
        private String remark;
    }
}
