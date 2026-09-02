package com.bank.branch.platform.customer.dto.asset;

import com.bank.branch.platform.customer.entity.AssetProjectUrgentApply;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.workflow.api.dto.ApprovalLogDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramNodeDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 资产立项列表与详情统一响应。 */
@Data
public class AssetProjectVO {
    private Long id;
    private String applyNo;
    private Long custId;
    private String custNo;
    private String customerName;
    private String unifiedCreditCode;
    private String mainManagerId;
    private String mainOrgId;
    private Long sourceTouchTaskId;
    private String sourceTouchTaskNo;
    private Long sourceWorklogId;
    private String sourceWorklogNo;
    private String projectName;
    private String projectType;
    private String bizType;
    private String guaranteeType;
    private BigDecimal projectTotalInvestment;
    private BigDecimal projectLoanAmount;
    private BigDecimal creditAmount;
    private BigDecimal creditExposureAmount;
    private boolean urgent;
    private boolean keyProject;
    private String urgentSource;
    private String applicantEmpId;
    private String applicantOrgId;
    private String status;
    private String businessKey;
    private String processInstanceId;
    private LocalDateTime submittedTime;
    private LocalDateTime completedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private Integer lockVersion;
    private String currentNode;
    private String currentProcessor;
    private String workflowTaskId;
    private boolean canEdit;
    private boolean canSubmit;
    private boolean canDelete;
    private boolean canCancel;
    private boolean canApplyUrgent;
    private List<FileObjectDTO> attachments;
    private List<ProcessDiagramNodeDTO> processNodes;
    private List<ApprovalLogDTO> approvalLogs;
    private List<AssetProjectUrgentApply> urgentApplies;
}
