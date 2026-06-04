package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 分配关系调整 单据详情（手机端详情页用：含分配明细 + 能力标志）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocAdjustDetailDTO {
    private String perfAdjustNo;
    private String applyNo;
    private String custId;
    private String custName;
    /** CORP / RETAIL */
    private String custType;
    /** ACCOUNT / RULE */
    private String allocDim;
    private String bizKind;
    private String accountNo;
    /** DRAFT/IN_APPROVAL/APPROVED/REJECTED/WITHDRAWN */
    private String status;
    /** 调整理由（apply.remark） */
    private String reason;
    private String createdBy;
    private String applyFullname;
    private LocalDateTime applyTime;
    /** 申请人本人且可撤回（status∈IN_APPROVAL/DRAFT）。 */
    private boolean canDelete;
    /** 当前用户的待办且 IN_APPROVAL。 */
    private boolean canApprove;
    private List<AllocItem> allocaters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocItem {
        private String empId;
        private String username;
        private String fullname;
        private String ratio;
        /** 1=原分配(itemKind=ORIGIN) / 2=调整后(其它)。 */
        private Integer isOriginal;
    }
}
