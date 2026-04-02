package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/**
 * 资源信息DTO
 * 用于跨模块传递资源（菜单/接口）基本信息
 */
@Data
public class ResourceDTO {
    private String resourceId;
    private String resourceUrl;
    private String resourceMethod;
    private String menuName;
    private Integer isMenu;
    private String parentResourceId;
    private Integer status;
}
