package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 资源树节点DTO
 * 用于前端菜单树和资源树展示
 */
@Data
public class ResourceTreeNodeDTO {

    private String resourceId;
    private String resourceUrl;
    private String resourceMethod;
    private String menuName;
    private Integer isMenu;
    private String menuEndFlag;
    private Integer menuRankNo;
    private Integer status;
    /** 子节点列表 */
    private List<ResourceTreeNodeDTO> children;
}
