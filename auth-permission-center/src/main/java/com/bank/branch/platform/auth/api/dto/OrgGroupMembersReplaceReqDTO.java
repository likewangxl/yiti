package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 命名机构组直接成员覆盖保存请求。 */
@Data
public class OrgGroupMembersReplaceReqDTO {

    @NotNull
    @Size(max = 1000)
    private List<@NotBlank @Size(max = 20) String> orgCodes;

    private Integer version;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
