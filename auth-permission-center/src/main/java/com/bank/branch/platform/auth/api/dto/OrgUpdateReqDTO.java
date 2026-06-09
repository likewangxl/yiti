package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 机构更新请求 DTO（不支持改 ORG_CODE 与 P_ID，避免树结构破坏） */
@Data
public class OrgUpdateReqDTO {

    @Size(max = 100)
    private String orgName;

    /** 机构状态：0-启用，1-禁用。为 null 时不改状态（仅改名）。禁用时机构下有用户则拒绝。 */
    private Integer organState;
}
