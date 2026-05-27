package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 资源更新请求DTO
 * 所有字段均为可选，仅更新传入的字段
 */
@Data
public class ResourceUpdateReqDTO {

    @Size(max = 256)
    private String resourceUrl;

    @Pattern(regexp = "^(GET|POST|PUT|DELETE|\\*|MENU)$")
    private String resourceMethod;

    @Size(max = 256)
    private String menuName;

    private Integer isMenu;

    @Pattern(regexp = "^[01]$")
    private String menuEndFlag;

    @Min(0)
    private Integer menuRankNo;

    @Size(max = 60)
    private String parentResourceId;

    private Integer status;

    @Size(max = 256)
    private String menuIconUrl;
}
