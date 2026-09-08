package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 地址解析预览请求；预览本身不写机构位置表。 */
@Data
public class OrgLocationGeocodePreviewReqDTO {

    @NotBlank
    @Size(max = 255)
    private String address;

    @NotBlank
    @Size(max = 12)
    private String cityCode;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
