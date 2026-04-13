package com.bank.branch.platform.portal.controller.dto.nav;

import lombok.Data;

import java.util.List;

/**
 * 导航分组响应 DTO（B.4 按分类分组返回）
 */
@Data
public class NavGroupRespDTO {

    /** 分组列表 */
    private List<NavGroupItem> groups;
}
