package com.bank.branch.platform.portal.facade;

import com.bank.branch.platform.portal.api.NavApi;
import com.bank.branch.platform.portal.api.dto.NavDTO;
import com.bank.branch.platform.portal.convert.NavConverter;
import com.bank.branch.platform.portal.entity.PortalNav;
import com.bank.branch.platform.portal.mapper.PortalNavMapper;
import com.bank.branch.platform.portal.service.NavService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 网址导航 Facade 实现
 *
 * <p>实现 {@link NavApi} 接口，负责将 NavService 返回的实体
 * 转换为跨模块 DTO。所有方法均为只读查询，不提供写操作。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NavFacade implements NavApi {

    private final NavService navService;
    private final PortalNavMapper portalNavMapper;

    /**
     * 查询所有启用状态的导航列表。
     * 委托 NavService 查询后转换为 NavDTO。
     *
     * @return 启用导航 DTO 列表
     */
    @Override
    public List<NavDTO> listActiveNavs() {
        List<PortalNav> entities = navService.listActiveNavs();
        return entities.stream()
                .map(NavConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按分类查询启用状态的导航列表。
     * 直接通过 Mapper 按分类查询后转换为 NavDTO。
     *
     * @param category 导航分类
     * @return 匹配分类的启用导航 DTO 列表
     */
    @Override
    public List<NavDTO> listActiveNavsByCategory(String category) {
        List<PortalNav> entities = portalNavMapper.listByCategory(category);
        return entities.stream()
                .map(NavConverter::toDTO)
                .collect(Collectors.toList());
    }
}
