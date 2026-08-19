package com.bank.branch.platform.customer.dto.resp;

import lombok.Data;

/** 线索指定客户经理范围响应。 */
@Data
public class LeadManagerScopeRespDTO {
    private String managerEmpId;
    private String managerName;
    private String managerOrgId;
    private String managerOrgName;
    private String assignmentType;
    private Boolean primary;
}
