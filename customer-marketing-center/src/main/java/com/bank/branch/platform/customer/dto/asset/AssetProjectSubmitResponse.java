package com.bank.branch.platform.customer.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AssetProjectSubmitResponse {
    private String processInstanceId;
    private String businessKey;
    private String status;
}
