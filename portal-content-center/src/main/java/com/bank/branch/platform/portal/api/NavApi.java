package com.bank.branch.platform.portal.api;

import com.bank.branch.platform.portal.api.dto.NavDTO;

import java.util.List;

/**
 * 网址导航对外接口。
 * 被其他模块依赖时，通过此接口获取导航数据。
 *
 * <p>所有方法均为只读查询，不提供写操作。</p>
 *
 * @author portal-content-center
 * @since V1.0
 */
public interface NavApi {

    /**
     * 查询所有启用状态的导航列表。
     *
     * @return 启用导航列表（按 sort_order 升序）
     */
    List<NavDTO> listActiveNavs();

    /**
     * 按分类查询启用状态的导航列表。
     *
     * @param category 导航分类
     * @return 匹配分类的启用导航列表（按 sort_order 升序）
     */
    List<NavDTO> listActiveNavsByCategory(String category);
}
