package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 命名机构组创建请求。 */
@Data
public class OrgGroupCreateReqDTO {

    @NotBlank
    @Size(max = 64)
    private String groupCode;

    @NotBlank
    @Size(max = 100)
    private String groupName;

    /** 本期固定为 REPORT_SCREEN。 */
    @NotBlank
    @Size(max = 30)
    private String groupPurpose = "REPORT_SCREEN";

    @Size(max = 10)
    private String status = "ACTIVE";

    @Size(max = 500)
    private String remark;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
