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

    /**
     * 原业绩分配（手工录入）。历史可查到时可空，否则提交校验要求至少 1 条。
     *
     * <p>与管理端 {@code AllocAdjustCreateReqDTO.originalAllocList} 同语义：外部渠道（callpu）把
     * isOriginal=1 的明细行拆到此列表，由 perf 落地为 {@code item_kind=ORIGIN} 明细。</p>
     */
    private List<OriginalItem> originalAllocList;

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

    /**
     * 原业绩分配项（手工录入）。字段对齐管理端 / Service {@code SubmitAllocAdjustCmd.OriginalItem}。
     *
     * <p>外部渠道仅采集 工号/姓名/比例，故 {@code acctNo}/{@code orgCode}/{@code orgName} 可空
     * （Service {@code buildOriginalItems} 对缺失机构留空、按工号反查补 username/中文名）。</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OriginalItem {

        /** 账号（选填）。 */
        private String acctNo;

        /** 员工工号（必填，下传 USER_ID）。 */
        private String empId;

        /** 员工登录名/工号快照（可空）。 */
        private String username;

        /** 员工中文姓名快照（可空）。 */
        private String empChnName;

        /** 所属机构号（可空）。 */
        private String orgCode;

        /** 所属机构名称快照（可空）。 */
        private String orgName;

        /** 分配比例。 */
        private BigDecimal ratio;
    }
}
