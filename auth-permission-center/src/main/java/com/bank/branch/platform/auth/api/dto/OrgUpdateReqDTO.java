package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 机构更新请求 DTO（不支持改 ORG_CODE 与 P_ID，避免树结构破坏） */
@Data
public class OrgUpdateReqDTO {

    @Size(max = 100)
    private String orgName;
}
