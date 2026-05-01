package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.portal.api.dto.NavDTO;
import com.bank.branch.platform.portal.config.PortalCacheConfig;
import com.bank.branch.platform.portal.controller.dto.nav.NavCreateReqDTO;
import com.bank.branch.platform.portal.controller.dto.nav.NavGroupItem;
import com.bank.branch.platform.portal.controller.dto.nav.NavGroupRespDTO;
import com.bank.branch.platform.portal.controller.dto.nav.NavSortItemReqDTO;
import com.bank.branch.platform.portal.controller.dto.nav.NavUpdateReqDTO;
import com.bank.branch.platform.portal.entity.PortalNav;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.PortalNavMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NavService 单元测试 -- 纯 JUnit 5 + Mockito，无需 Spring 上下文
 *
 * <p>TDD RED-GREEN 闭环：先写测试（Red），再实现 Service（Green）。
 * 涵盖分组查询、新增、更新、删除、批量排序等核心场景。</p>
 */
@ExtendWith(MockitoExtension.class)
class NavServiceTest {

    @Mock PortalNavMapper portalNavMapper;
    @Mock CurrentUserApi currentUserApi;
    @Mock AuditApi auditApi;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @InjectMocks NavService navService;

    // ========== listGrouped ==========

    @Test
    void listGrouped_groupsByCategory() {
        // 模拟两个分类、各含一条导航
        PortalNav nav1 = buildNav("id1", "核心系统", "https://core.bank.com", "业务系统", 1);
        PortalNav nav2 = buildNav("id2", "信贷系统", "https://loan.bank.com", "业务系统", 2);
        PortalNav nav3 = buildNav("id3", "百度", "https://baidu.com", "常用工具", 1);
        when(portalNavMapper.listActive()).thenReturn(Arrays.asList(nav1, nav2, nav3));

        NavGroupRespDTO result = navService.listGrouped(null, null);

        assertThat(result).isNotNull();
        assertThat(result.getGroups()).hasSize(2);
        // 验证分组内容
        NavGroupItem bizGroup = result.getGroups().stream()
                .filter(g -> "业务系统".equals(g.getCategory()))
                .findFirst().orElse(null);
        assertThat(bizGroup).isNotNull();
        assertThat(bizGroup.getNavs()).hasSize(2);
        NavGroupItem toolGroup = result.getGroups().stream()
                .filter(g -> "常用工具".equals(g.getCategory()))
                .findFirst().orElse(null);
        assertThat(toolGroup).isNotNull();
        assertThat(toolGroup.getNavs()).hasSize(1);
    }

    @Test
    void listGrouped_withCategoryFilter() {
        PortalNav nav1 = buildNav("id1", "核心系统", "https://core.bank.com", "业务系统", 1);
        when(portalNavMapper.listByCategory("业务系统")).thenReturn(Collections.singletonList(nav1));

        NavGroupRespDTO result = navService.listGrouped("业务系统", null);

        assertThat(result.getGroups()).hasSize(1);
        assertThat(result.getGroups().get(0).getCategory()).isEqualTo("业务系统");
    }

    @Test
    void listGrouped_emptyResult() {
        when(portalNavMapper.listActive()).thenReturn(Collections.emptyList());

        NavGroupRespDTO result = navService.listGrouped(null, null);

        assertThat(result).isNotNull();
        assertThat(result.getGroups()).isEmpty();
    }

    // ========== createNav ==========

    @Test
    void createNav_success() {
        NavCreateReqDTO req = new NavCreateReqDTO();
        req.setNavName("核心系统");
        req.setNavUrl("https://core.bank.com");
        req.setNavIcon("icon-core");
        req.setNavCategory("业务系统");
        req.setSortOrder(1);

        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(portalNavMapper.countByNameAndCategory("核心系统", "业务系统")).thenReturn(0);

        PortalNav result = navService.createNav(req);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotBlank();
        assertThat(result.getNavName()).isEqualTo("核心系统");
        assertThat(result.getNavUrl()).isEqualTo("https://core.bank.com");
        assertThat(result.getNavCategory()).isEqualTo("业务系统");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getCreatedBy()).isEqualTo("OPERATOR01");

        ArgumentCaptor<PortalNav> captor = ArgumentCaptor.forClass(PortalNav.class);
        verify(portalNavMapper).insert(captor.capture());
        assertThat(captor.getValue().getId()).isNotBlank();
    }

    @Test
    void createNav_duplicateName() {
        NavCreateReqDTO req = new NavCreateReqDTO();
        req.setNavName("核心系统");
        req.setNavUrl("https://core.bank.com");
        req.setNavCategory("业务系统");

        when(portalNavMapper.countByNameAndCategory("核心系统", "业务系统")).thenReturn(1);

        assertThatThrownBy(() -> navService.createNav(req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo(PortalErrorCode.NAV_NAME_DUPLICATE.getCode());
                });

        verify(portalNavMapper, never()).insert(any(PortalNav.class));
    }

    // ========== updateNav ==========

    @Test
    void updateNav_success() {
        PortalNav existing = buildNav("nav-001", "旧名称", "https://old.com", "业务系统", 1);
        when(portalNavMapper.selectById("nav-001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");

        NavUpdateReqDTO req = new NavUpdateReqDTO();
        req.setNavName("新名称");
        req.setNavUrl("https://new.com");

        navService.updateNav("nav-001", req);

        ArgumentCaptor<PortalNav> captor = ArgumentCaptor.forClass(PortalNav.class);
        verify(portalNavMapper).updateById(captor.capture());
        PortalNav patch = captor.getValue();
        assertThat(patch.getId()).isEqualTo("nav-001");
        assertThat(patch.getNavName()).isEqualTo("新名称");
        assertThat(patch.getNavUrl()).isEqualTo("https://new.com");
        assertThat(patch.getUpdatedBy()).isEqualTo("OPERATOR01");
    }

    @Test
    void updateNav_notFound() {
        when(portalNavMapper.selectById("nav-nonexist")).thenReturn(null);

        NavUpdateReqDTO req = new NavUpdateReqDTO();
        req.setNavName("新名称");

        assertThatThrownBy(() -> navService.updateNav("nav-nonexist", req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo(PortalErrorCode.NAV_NOT_FOUND.getCode());
                });

        verify(portalNavMapper, never()).updateById(any(PortalNav.class));
    }

    // ========== deleteNav ==========

    @Test
    void deleteNav_success() {
        PortalNav existing = buildNav("nav-001", "核心系统", "https://core.bank.com", "业务系统", 1);
        when(portalNavMapper.selectById("nav-001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");

        navService.deleteNav("nav-001");

        verify(portalNavMapper).softDeleteById("nav-001", "OPERATOR01");
        verify(auditApi).log(any());
    }

    @Test
    void deleteNav_notFound() {
        when(portalNavMapper.selectById("nav-nonexist")).thenReturn(null);

        assertThatThrownBy(() -> navService.deleteNav("nav-nonexist"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo(PortalErrorCode.NAV_NOT_FOUND.getCode());
                });

        verify(portalNavMapper, never()).softDeleteById(anyString(), anyString());
    }

    // ========== batchSort ==========

    @Test
    void batchSort_success() {
        NavSortItemReqDTO item1 = new NavSortItemReqDTO();
        item1.setId("nav-001"); item1.setSortOrder(2);
        NavSortItemReqDTO item2 = new NavSortItemReqDTO();
        item2.setId("nav-002"); item2.setSortOrder(1);

        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");

        navService.batchSort(Arrays.asList(item1, item2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PortalNav>> captor = ArgumentCaptor.forClass(List.class);
        verify(portalNavMapper).updateSortOrderBatch(captor.capture());
        List<PortalNav> items = captor.getValue();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getId()).isEqualTo("nav-001");
        assertThat(items.get(0).getSortOrder()).isEqualTo(2);
        assertThat(items.get(1).getId()).isEqualTo("nav-002");
        assertThat(items.get(1).getSortOrder()).isEqualTo(1);
    }

    // ========== listActiveNavs (Cache-Aside) ==========

    @Test
    @SuppressWarnings("unchecked")
    void listActiveNavs_cacheMiss_queriesDbAndCaches() {
        // 模拟 Redis 缓存未命中
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(PortalCacheConfig.NAV_ACTIVE_KEY)).thenReturn(null);

        PortalNav nav1 = buildNav("id1", "核心系统", "https://core.bank.com", "业务系统", 1);
        PortalNav nav2 = buildNav("id2", "百度", "https://baidu.com", "常用工具", 1);
        when(portalNavMapper.listActive()).thenReturn(Arrays.asList(nav1, nav2));

        List<PortalNav> result = navService.listActiveNavs();

        assertThat(result).hasSize(2);
        // 缓存未命中时应查询数据库
        verify(portalNavMapper, times(1)).listActive();
        // 并将结果写入缓存（TTL 带抖动）
        verify(valueOps).set(eq(PortalCacheConfig.NAV_ACTIVE_KEY), eq(Arrays.asList(nav1, nav2)), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listActiveNavs_cacheHit_skipsDb() {
        // 模拟 Redis 缓存命中
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        PortalNav nav1 = buildNav("id1", "核心系统", "https://core.bank.com", "业务系统", 1);
        List<PortalNav> cachedData = Collections.singletonList(nav1);
        when(valueOps.get(PortalCacheConfig.NAV_ACTIVE_KEY)).thenReturn(cachedData);

        List<PortalNav> result = navService.listActiveNavs();

        assertThat(result).hasSize(1);
        // 缓存命中时不应查询数据库
        verify(portalNavMapper, never()).listActive();
    }

    @Test
    void createNav_clearsNavCache() {
        NavCreateReqDTO req = new NavCreateReqDTO();
        req.setNavName("新导航");
        req.setNavUrl("https://new.bank.com");
        req.setNavIcon("icon-new");
        req.setNavCategory("业务系统");
        req.setSortOrder(1);

        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(portalNavMapper.countByNameAndCategory("新导航", "业务系统")).thenReturn(0);

        navService.createNav(req);

        // 写操作完成后应清除导航缓存
        verify(redisTemplate).delete(PortalCacheConfig.NAV_ACTIVE_KEY);
    }

    @Test
    void updateNav_clearsNavCache() {
        PortalNav existing = buildNav("nav-001", "旧名称", "https://old.com", "业务系统", 1);
        when(portalNavMapper.selectById("nav-001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");

        NavUpdateReqDTO req = new NavUpdateReqDTO();
        req.setNavName("更新名称");

        navService.updateNav("nav-001", req);

        // 更新操作完成后应清除导航缓存
        verify(redisTemplate).delete(PortalCacheConfig.NAV_ACTIVE_KEY);
    }

    @Test
    void deleteNav_clearsNavCache() {
        PortalNav existing = buildNav("nav-001", "核心系统", "https://core.bank.com", "业务系统", 1);
        when(portalNavMapper.selectById("nav-001")).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");

        navService.deleteNav("nav-001");

        // 删除操作完成后应清除导航缓存
        verify(redisTemplate).delete(PortalCacheConfig.NAV_ACTIVE_KEY);
    }

    @Test
    void batchSort_clearsNavCache() {
        NavSortItemReqDTO item1 = new NavSortItemReqDTO();
        item1.setId("nav-001"); item1.setSortOrder(2);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");

        navService.batchSort(Collections.singletonList(item1));

        // 排序操作完成后应清除导航缓存
        verify(redisTemplate).delete(PortalCacheConfig.NAV_ACTIVE_KEY);
    }

    // ========== 辅助方法 ==========

    private PortalNav buildNav(String id, String name, String url, String category, int sortOrder) {
        PortalNav nav = new PortalNav();
        nav.setId(id);
        nav.setNavName(name);
        nav.setNavUrl(url);
        nav.setNavCategory(category);
        nav.setSortOrder(sortOrder);
        nav.setStatus("ACTIVE");
        return nav;
    }
}
