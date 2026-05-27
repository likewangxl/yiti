package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 资源创建请求DTO
 */
@Data
public class ResourceCreateReqDTO {

    @NotBlank
    @Size(max = 256)
    private String resourceUrl;

    @NotBlank
    @Pattern(regexp = "^(GET|POST|PUT|DELETE|\\*|MENU)$")
    private String resourceMethod;

    @NotBlank
    @Size(max = 256)
    private String menuName;

    @NotNull
    private Integer isMenu;

    @Pattern(regexp = "^[01]$")
    private String menuEndFlag;

    @Min(0)
    private Integer menuRankNo;

    @Size(max = 60)
    private String parentResourceId;

    @Size(max = 10)
    private String sysCode;

    @Size(max = 256)
    private String menuIconUrl;
}
