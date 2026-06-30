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

    // 路由路径：父节点/目录菜单（含一级菜单）只做分组、不跳转，允许为空；叶子资源由前端保证非空。
    // 空 URL 不参与 url+method+sysCode 三元唯一校验（见 ResourceService.createResource）。
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
