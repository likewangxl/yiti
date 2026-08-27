package com.bank.branch.platform.performance.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 原业绩分配预览项 DTO（取客户当前原分配关系的最新来源批次全部明细）.
 *
 * <p>数据来源：{@code CUST_ALLOC_RELATION} 中 {@code is_original='2'} 的最新来源批次；
 * 员工 {@code empId} 保存 PT_USER.USER_ID，{@code username} 经 {@code UserApi} 批量映射为
 * PT_USER.USERNAME，中文姓名和机构字段继续读取分配关系快照。
 *
 * <p>供审批/新增调整申请页面的「原业绩分配」模块展示；用户已删除或无法解析时，
 * {@code username} 回退为原始 {@code empId}。
 */
@Data
public class AllocAdjustPreviewItemDTO {

    /** 分配维度：RULE（按规则分配）/ ACCOUNT（按账号分配）. */
    private String allocDim;

    /** 账号（ACCOUNT 维度取 relation.account_no；RULE 维度为空）. */
    private String accountNo;

    /** 员工 USER_ID（relation.emp_id，展示 username 时经 UserApi 映射）. */
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
