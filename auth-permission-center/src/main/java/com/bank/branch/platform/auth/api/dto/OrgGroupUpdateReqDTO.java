package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 命名机构组基本信息更新请求。 */
@Data
public class OrgGroupUpdateReqDTO {

    @NotBlank
    @Size(max = 100)
    private String groupName;

    @NotBlank
    @Size(max = 10)
    private String status;

    private Integer version;

    @Size(max = 500)
    private String remark;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
