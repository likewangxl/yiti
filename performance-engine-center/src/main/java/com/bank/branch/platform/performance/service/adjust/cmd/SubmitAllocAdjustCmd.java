package com.bank.branch.platform.performance.service.adjust.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分配关系调整申请提交命令 (V1.2 Q2.2).
 *
 * <p>Service 层 {@code AllocAdjustService.submit(cmd)} 消费：
 * <ol>
 *   <li>校验字段：custNo / allocDim / bizKind / ownerOrgId / items 必填；
 *       items 的 empId 去重、ratio 之和 ≤ 100（RULE 维度）</li>
 *   <li>调用 {@code CustomerQueryApi.getCustomerByCustNo} 按客户编号校验，并将客户主键 id 写入 apply.cust_id</li>
 *   <li>生成 applyNo（AA + yyyyMMdd + UUID 片段）+ 插入主从表</li>
 *   <li>按 {@code bizKind} 前缀路由 corp_v1 / retail_v1 BPMN，启动流程</li>
 *   <li>回写 processInstanceId 到 apply</li>
 * </ol>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitAllocAdjustCmd {

    /** 客户类型：CORP / RETAIL（决定审批流路由）. */
    private String custType;

    /** 客户编号（必填，对应 cust_master.cust_no 业务编号；Service 内部按编号查找客户主键后入库）. */
    private String custNo;

    /** 客户名称（前端反显，提交时快照入库）. */
    private String custName;

    /** 当前余额（前端反显，提交时快照入库）. */
    private BigDecimal currBal;

    /** 月均余额（前端反显，提交时快照入库）. */
    private BigDecimal mAvgBal;

    /** 季日均余额（前端反显，提交时快照入库）. */
    private BigDecimal qAvgBal;

    /** 年日均余额（前端反显，提交时快照入库）. */
    private BigDecimal yAvgBal;

    /** 分配维度：RULE / ACCOUNT（必填）. */
    private String allocDim;

    /** 业务种类（必填，决定流程路由）. */
    private String bizKind;

    /** 账号（ACCOUNT 维度必填，RULE 维度可空）. */
    private String accountNo;

    /** 归属机构（必填，数据范围过滤基准）. */
    private String ownerOrgId;

    /** 申请原因（存 remark 字段，可空）. */
    private String reason;

    /** 申请人工号（即 created_by，必填）. */
    private String applicant;

    /** 调整后的员工 + 比例明细列表（非空，empId 去重）. */
    private List<Item> items;

    /**
     * 调整明细项.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        /** 调整后归属员工工号. */
        private String empId;

        /** 调整后分配比例（0-100）. */
        private BigDecimal ratio;

        /** 说明（可空）. */
        private String remark;
    }
}
