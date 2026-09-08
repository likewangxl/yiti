package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/** 机构地址存储和地理编码能力状态；不包含任何服务密钥。 */
@Data
public class OrgLocationCapabilitiesDTO {

    private boolean storageEnabled;
    private boolean storageAvailable;
    private String storageReason;
    private boolean geocodingEnabled;
    private boolean geocodingAvailable;
    private String geocodingReason;
}
