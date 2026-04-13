package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.api.dto.NavDTO;
import com.bank.branch.platform.portal.config.PortalCacheConfig;
import com.bank.branch.platform.portal.controller.dto.nav.NavCreateReqDTO;
import com.bank.branch.platform.portal.controller.dto.nav.NavGroupItem;
import com.bank.branch.platform.portal.controller.dto.nav.NavGroupRespDTO;
import com.bank.branch.platform.portal.controller.dto.nav.NavSortItemReqDTO;
import com.bank.branch.platform.portal.controller.dto.nav.NavUpdateReqDTO;
import com.bank.branch.platform.portal.convert.NavConverter;
import com.bank.branch.platform.portal.entity.PortalNav;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.PortalNavMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 网址导航业务逻辑服务
 *
 * <p>提供导航的分组查询、新增、更新、逻辑删除和批量排序功能。
 * 导航名称在同一分类下必须唯一（PORTAL-40903），删除采用逻辑删除（status=DISABLED）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NavService {

    private final PortalNavMapper portalNavMapper;
    private final CurrentUserApi currentUserApi;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 按分类分组查询导航列表。
     *
     * @param category 分类过滤条件（null 时查询全部分类）
     * @param status   状态过滤（null 或空时默认 ACTIVE；"ALL" 查全部状态；其他值精确匹配）
     * @return 分组后的导航列表
     */
    public NavGroupRespDTO listGrouped(String category, String status) {
        List<PortalNav> navs;
        if (category != null && !category.isBlank()) {
            // 按分类查询（仅查 ACTIVE）
            navs = portalNavMapper.listByCategory(category);
        } else if ("ALL".equalsIgnoreCase(status)) {
            // 查询全部状态的导航
            navs = portalNavMapper.listAll();
        } else if (status != null && !status.isBlank()) {
            // 按指定状态查询
            navs = portalNavMapper.listByStatus(status);
        } else {
            // 默认查询启用导航
            navs = portalNavMapper.listActive();
        }

        // 按 navCategory 分组，保持插入顺序
        Map<String, List<PortalNav>> grouped = navs.stream()
                .collect(Collectors.groupingBy(
                        PortalNav::getNavCategory,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<NavGroupItem> groups = new ArrayList<>();
        for (Map.Entry<String, List<PortalNav>> entry : grouped.entrySet()) {
            NavGroupItem item = new NavGroupItem();
            item.setCategory(entry.getKey());
            item.setNavs(entry.getValue().stream()
                    .map(NavConverter::toDTO)
                    .collect(Collectors.toList()));
            groups.add(item);
        }

        NavGroupRespDTO resp = new NavGroupRespDTO();
        resp.setGroups(groups);
        return resp;
    }

    /**
     * 新增导航。
     * 校验同分类下名称唯一性，生成 UUID 主键后插入数据库。
     *
     * @param req 新增请求
     * @return 新创建的导航实体
     * @throws BizException 导航名称在同一分类下已存在（PORTAL-40903）
     */
    public PortalNav createNav(NavCreateReqDTO req) {
        // 同分类下名称唯一性校验
        String category = req.getNavCategory() != null ? req.getNavCategory() : "";
        int count = portalNavMapper.countByNameAndCategory(req.getNavName(), category);
        if (count > 0) {
            throw new BizException(
                    PortalErrorCode.NAV_NAME_DUPLICATE.getCode(),
                    PortalErrorCode.NAV_NAME_DUPLICATE.getMessage()
            );
        }

        String currentEmpId = currentUserApi.getCurrentEmpId();
        String navId = UUID.randomUUID().toString().replace("-", "");

        PortalNav entity = new PortalNav();
        entity.setId(navId);
        entity.setNavName(req.getNavName());
        entity.setNavUrl(req.getNavUrl());
        entity.setNavIcon(req.getNavIcon());
        entity.setNavCategory(req.getNavCategory());
        entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(currentEmpId);
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedBy(currentEmpId);
        entity.setUpdatedTime(LocalDateTime.now());

        portalNavMapper.insert(entity);
        clearNavCache();
        log.info("[NavService.createNav] 新增导航成功, id={}, name={}", navId, req.getNavName());
        return entity;
    }

    /**
     * 更新导航。
     * 查询导航是否存在，不存在则抛异常；名称或分类变更时校验同分类下名称唯一性；存在则动态更新非 null 字段。
     *
     * @param id  导航ID
     * @param req 更新请求
     * @throws BizException 导航不存在（PORTAL-40001）或名称重复（PORTAL-40903）
     */
    public void updateNav(String id, NavUpdateReqDTO req) {
        PortalNav existing = portalNavMapper.selectById(id);
        if (existing == null) {
            throw new BizException(
                    PortalErrorCode.NAV_NOT_FOUND.getCode(),
                    PortalErrorCode.NAV_NOT_FOUND.getMessage()
            );
        }

        // 同分类下名称唯一性校验（名称或分类变更时触发）
        String newName = req.getNavName() != null ? req.getNavName() : existing.getNavName();
        String newCategory = req.getNavCategory() != null ? req.getNavCategory() : existing.getNavCategory();
        boolean nameOrCategoryChanged = !newName.equals(existing.getNavName())
                || !newCategory.equals(existing.getNavCategory());
        if (nameOrCategoryChanged) {
            int count = portalNavMapper.countByNameAndCategoryExcludeId(newName, newCategory, id);
            if (count > 0) {
                throw new BizException(
                        PortalErrorCode.NAV_NAME_DUPLICATE.getCode(),
                        PortalErrorCode.NAV_NAME_DUPLICATE.getMessage()
                );
            }
        }

        String currentEmpId = currentUserApi.getCurrentEmpId();

        PortalNav patch = new PortalNav();
        patch.setId(id);
        patch.setNavName(req.getNavName());
        patch.setNavUrl(req.getNavUrl());
        patch.setNavIcon(req.getNavIcon());
        patch.setNavCategory(req.getNavCategory());
        patch.setSortOrder(req.getSortOrder());
        patch.setStatus(req.getStatus());
        patch.setUpdatedBy(currentEmpId);

        portalNavMapper.updateById(patch);
        clearNavCache();
        log.info("[NavService.updateNav] 更新导航成功, id={}", id);
    }

    /**
     * 逻辑删除导航（设置 status=DISABLED）。
     *
     * @param id 导航ID
     * @throws BizException 导航不存在（PORTAL-40001）
     */
    public void deleteNav(String id) {
        PortalNav existing = portalNavMapper.selectById(id);
        if (existing == null) {
            throw new BizException(
                    PortalErrorCode.NAV_NOT_FOUND.getCode(),
                    PortalErrorCode.NAV_NOT_FOUND.getMessage()
            );
        }

        String currentEmpId = currentUserApi.getCurrentEmpId();
        portalNavMapper.softDeleteById(id, currentEmpId);
        clearNavCache();
        log.info("[NavService.deleteNav] 逻辑删除导航成功, id={}", id);
    }

    /**
     * 批量更新导航排序号，在单个事务内完成。
     *
     * @param items 排序项列表（id + sortOrder）
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchSort(List<NavSortItemReqDTO> items) {
        String currentEmpId = currentUserApi.getCurrentEmpId();

        List<PortalNav> entities = items.stream().map(item -> {
            PortalNav nav = new PortalNav();
            nav.setId(item.getId());
            nav.setSortOrder(item.getSortOrder());
            nav.setUpdatedBy(currentEmpId);
            return nav;
        }).collect(Collectors.toList());

        portalNavMapper.updateSortOrderBatch(entities);
        clearNavCache();
        log.info("[NavService.batchSort] 批量排序完成, count={}", items.size());
    }

    /**
     * 查询所有启用状态的导航列表（Cache-Aside 模式）。
     *
     * <p>优先从 Redis 缓存读取，缓存未命中时查询数据库并回填缓存。
     * TTL 使用 PortalCacheConfig.jitteredTtl() 添加 +-10% 抖动，防止缓存雪崩。</p>
     *
     * @return 启用导航列表（按 sort_order 升序）
     */
    @SuppressWarnings("unchecked")
    public List<PortalNav> listActiveNavs() {
        Object cached = redisTemplate.opsForValue().get(PortalCacheConfig.NAV_ACTIVE_KEY);
        if (cached != null) {
            log.debug("[NavService.listActiveNavs] cache hit");
            return (List<PortalNav>) cached;
        }
        log.debug("[NavService.listActiveNavs] cache miss, querying DB");
        List<PortalNav> items = portalNavMapper.listActive();
        redisTemplate.opsForValue().set(
                PortalCacheConfig.NAV_ACTIVE_KEY,
                items,
                PortalCacheConfig.jitteredTtl(PortalCacheConfig.DEFAULT_TTL)
        );
        return items;
    }

    /**
     * 清除导航列表缓存（吞没异常，缓存删除失败不影响主流程）
     */
    private void clearNavCache() {
        try {
            redisTemplate.delete(PortalCacheConfig.NAV_ACTIVE_KEY);
        } catch (Exception e) {
            log.warn("[NavService] clearNavCache failed", e);
        }
    }
}
