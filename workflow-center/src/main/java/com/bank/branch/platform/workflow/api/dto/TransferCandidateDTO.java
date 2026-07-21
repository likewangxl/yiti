package com.bank.branch.platform.workflow.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 转交接收人候选项 DTO：发起转交时可选的接收人。
 * <p>
 * 由 {@code TaskTransferService#listCandidates} 产出，与 {@code initiate} 的资格校验
 * （{@code isEligibleReceiver}）同源——列表里出现的人必定能通过校验，不会再出现
 * 「弹窗里能选、提交却被 WF-40912 打回」的割裂。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferCandidateDTO {

    /** 员工工号（即 empId，对应 PT_USER.USER_ID） */
    private String empId;

    /** 显示名 */
    private String displayName;

    /** 主机构编码 */
    private String orgCode;

    /** 主机构名称 */
    private String orgName;
}
