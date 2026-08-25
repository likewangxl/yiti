package com.bank.branch.platform.customer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 客户转交记录响应。 */
@Data
public class CustomerTransferRespDTO {
    private String id;
    private String transferNo;
    private String custId;
    private String custName;
    private String fromManagerId;
    private String fromManagerName;
    private String fromOrgId;
    private String fromOrgName;
    private List<Target> targets;
    private Integer accountOpenedSnapshot;
    private String reason;
    private String status;
    private String operatorEmpId;
    private String operatorName;
    private LocalDateTime completedTime;
    private LocalDateTime createdTime;

    /** 接收人员与机构快照。 */
    @Data
    public static class Target {
        private String empId;
        private String empName;
        private String orgId;
        private String orgName;
        private String role;
        private Integer sortNo;
    }
}
