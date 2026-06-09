package com.bank.branch.platform.soap.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手机端审批列表单条记录，字段对齐 list.vue 模板：
 * applyFullname / custName / applyTime / apprStatus / perfAdjustNo。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfListItem {

    /** 业绩调整审批编号（详情页跳转用）。 */
    private String perfAdjustNo;

    /** 申请人姓名。 */
    private String applyFullname;

    /** 客户名称。 */
    private String custName;

    /** 申请时间（yyyy-MM-dd HH:mm:ss）。 */
    private String applyTime;

    /** 审批状态：0=待审核，1=已同意，2=已拒绝。 */
    private String apprStatus;
}
