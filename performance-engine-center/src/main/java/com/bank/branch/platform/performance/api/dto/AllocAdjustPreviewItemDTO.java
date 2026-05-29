package com.bank.branch.platform.performance.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 原业绩分配预览项 DTO（取分配关系调整申请「审批通过的最后一条」的明细）.
 *
 * <p>数据来源：{@code PERF_ALLOC_ADJUST_APPLY}（按客户编号 + 分配维度取 status=APPROVED
 * 且 created_time 最新的一条）关联 {@code PERF_ALLOC_ADJUST_ITEM}，再按 {@code emp_id}（员工工号）
 * 经 {@code UserApi} 补全员工名称与所属机构。
 *
 * <p>与 {@code cust_alloc_relation}（生产分配关系）不同：本视图反映的是「最近一次审批通过的调整申请」
 * 快照，供审批/新增调整申请页面的「原业绩分配」模块展示。
 */
@Data
public class AllocAdjustPreviewItemDTO {

    /** 分配维度：RULE（按规则分配）/ ACCOUNT（按账号分配）. */
    private String allocDim;

    /** 账号（ACCOUNT 维度取 apply.account_no；RULE 维度为空）. */
    private String accountNo;

    /** 员工工号（item.emp_id）. */
    private String empId;

    /** 员工登录名（PT_USER.USERNAME；解析不到时回退为工号）. */
    private String username;

    /** 员工中文姓名（PT_USER.USERCHNNAME）. */
    private String empChnName;

    /** 所属机构号（员工主机构 ORG_CODE）. */
    private String orgCode;

    /** 所属机构名称（员工主机构 ORG_NAME）. */
    private String orgName;

    /** 分配比例（0-100）. */
    private BigDecimal ratio;
}
