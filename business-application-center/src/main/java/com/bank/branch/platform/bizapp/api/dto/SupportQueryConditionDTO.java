package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;

/**
 * 中台支持申请分页查询条件 DTO。
 * <p>
 * 用于跨模块调用 {@code SupportQueryApi.pageQuery()} 时传递查询参数。
 * </p>
 */
@Data
public class SupportQueryConditionDTO {

    /** 关键字（模糊匹配申请编号、客户ID等） */
    private String keyword;

    /** 状态过滤：DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED */
    private String status;

    /** 归属机构过滤（发起侧ORG_CODE） */
    private String ownerOrgId;

    /** 承接部门过滤（ORG_CODE） */
    private String supportDeptId;

    /** 当前页码（从1开始） */
    private int pageNo = 1;

    /** 每页大小（默认20，最大100） */
    private int pageSize = 20;
}
