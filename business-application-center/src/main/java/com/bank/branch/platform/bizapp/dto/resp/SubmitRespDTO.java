package com.bank.branch.platform.bizapp.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务申请提交响应 DTO。
 * <p>
 * 当 Loan/Support 申请成功提交进入审批流时，返回此对象，
 * 前端可据此跳转到对应的流程详情页面。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitRespDTO {

    /** Flowable 流程实例ID，对应 WorkflowLaunchResp.processInstanceId */
    private String processInstanceId;

    /** 业务键，格式：LOAN:{id} 或 SUPPORT:{id} */
    private String businessKey;

    /** 提交后状态，固定值 "IN_APPROVAL" */
    private String status;
}
