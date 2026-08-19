package com.bank.branch.platform.workflow.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前活动任务的可审批员工。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCandidateUserDTO {

    /** PT_USER.USER_ID，工作流办理人标识。 */
    private String empId;

    /** 员工工号（PT_USER.USERNAME），用于界面展示。 */
    private String employeeNo;

    /** 员工姓名（PT_USER.USERCHNNAME）。 */
    private String employeeName;
}
