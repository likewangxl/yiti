package com.bank.branch.platform.portal.controller.dto.nav;

import com.bank.branch.platform.portal.api.dto.NavDTO;
import lombok.Data;

import java.util.List;

/**
 * 导航分组项（NavGroupRespDTO 的子结构）
 */
@Data
public class NavGroupItem {

    /** 分类名称 */
    private String category;

    /** 该分类下的导航列表 */
    private List<NavDTO> navs;
}
