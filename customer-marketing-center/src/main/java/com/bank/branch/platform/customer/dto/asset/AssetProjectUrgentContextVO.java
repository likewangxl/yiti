package com.bank.branch.platform.customer.dto.asset;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssetProjectUrgentContextVO {
    private Long assetProjectId;
    private String applyNo;
    private String projectName;
    private String customerName;
    private String currentNodeKey;
    private String currentNodeName;
    private String currentTaskId;
    private boolean allowed;
    private String unavailableReason;
}
