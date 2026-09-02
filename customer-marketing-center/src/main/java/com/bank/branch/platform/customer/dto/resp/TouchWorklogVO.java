package com.bank.branch.platform.customer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** MARKETING_TOUCH_WORKLOG 页面响应。 */
@Data
public class TouchWorklogVO {
    private String id;
    private String workLogId;
    private String worklogNo;
    private String touchTaskId;
    private String clientUuid;
    private LocalDateTime logTime;
    private String logContent;
    private String touchMethod;
    private List<String> participantEmpIds;
    private List<String> photoUrls;
    private Map<String, List<String>> photoGroups;
    private String operatorLocation;
    private String ownerOrgId;
    private String createdBy;
    private LocalDateTime createdTime;
    private String companyName;
    private String companyUSCI;
    private String accountOpenProgress;
    private String recordStatus;
}
