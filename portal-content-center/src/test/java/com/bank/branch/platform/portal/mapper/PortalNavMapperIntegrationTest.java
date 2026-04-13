package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.PortalNav;
import com.bank.branch.platform.portal.support.AbstractMapperIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PortalNavMapper 集成测试。
 * <p>
 * 直连本地 MySQL（127.0.0.1:3306/onepl），每个测试方法前清理 TEST_ 前缀数据。
 * 验证导航 CRUD、分类查询、排序批量更新等核心 SQL 的正确性。
 * </p>
 */
@Sql(scripts = "/sql/clean-portal-nav.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PortalNavMapperIntegrationTest extends AbstractMapperIntegrationTest {

    @Autowired
    private PortalNavMapper mapper;

    /**
     * 验证插入和按 ID 查询的完整往返。
     */
    @Test
    void insertAndSelectById() {
        // Arrange
        PortalNav nav = newNav("TEST_导航A", "HQ_SYSTEM", 1);

        // Act
        int rows = mapper.insert(nav);
        PortalNav found = mapper.selectById(nav.getId());

        // Assert
        assertThat(rows).isEqualTo(1);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(nav.getId());
        assertThat(found.getNavName()).isEqualTo("TEST_导航A");
        assertThat(found.getNavUrl()).isEqualTo("https://example.com/导航a");
        assertThat(found.getNavCategory()).isEqualTo("HQ_SYSTEM");
        assertThat(found.getSortOrder()).isEqualTo(1);
        assertThat(found.getStatus()).isEqualTo("ACTIVE");
    }

    /**
     * 验证 listActive 只返回 status=ACTIVE 的导航，且按 sort_order 排序。
     */
    @Test
    void listActive_filtersDisabled() {
        // Arrange
        PortalNav navA = newNav("TEST_导航A", "HQ_SYSTEM", 2);
        navA.setStatus("ACTIVE");
        mapper.insert(navA);

        PortalNav navB = newNav("TEST_导航B", "HQ_SYSTEM", 1);
        navB.setStatus("DISABLED");
        mapper.insert(navB);

        PortalNav navC = newNav("TEST_导航C", "BRANCH_SYSTEM", 3);
        navC.setStatus("ACTIVE");
        mapper.insert(navC);

        // Act
        List<PortalNav> result = mapper.listActive();

        // Assert - navB(DISABLED) 应被排除
        List<String> names = result.stream()
                .map(PortalNav::getNavName)
                .toList();
        assertThat(names).contains("TEST_导航A", "TEST_导航C");
        assertThat(names).doesNotContain("TEST_导航B");

        // 验证排序：TEST_导航A(sort=2) 在 TEST_导航C(sort=3) 之前
        int indexA = names.indexOf("TEST_导航A");
        int indexC = names.indexOf("TEST_导航C");
        assertThat(indexA).isLessThan(indexC);
    }

    /**
     * 验证 listByCategory 只返回指定分类的导航。
     */
    @Test
    void listByCategory() {
        // Arrange
        mapper.insert(newNav("TEST_总行系统", "HQ_SYSTEM", 1));
        mapper.insert(newNav("TEST_分行系统", "BRANCH_SYSTEM", 1));
        mapper.insert(newNav("TEST_总行系统2", "HQ_SYSTEM", 2));

        // Act
        List<PortalNav> result = mapper.listByCategory("HQ_SYSTEM");

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(n -> "HQ_SYSTEM".equals(n.getNavCategory()));
        // 验证排序
        assertThat(result.get(0).getSortOrder()).isLessThanOrEqualTo(result.get(1).getSortOrder());
    }

    /**
     * 验证 softDeleteById 将状态设为 DISABLED。
     */
    @Test
    void softDelete() {
        // Arrange
        PortalNav nav = newNav("TEST_待删除", "HQ_SYSTEM", 1);
        mapper.insert(nav);

        // Act
        int rows = mapper.softDeleteById(nav.getId(), "admin");

        // Assert
        assertThat(rows).isEqualTo(1);
        PortalNav found = mapper.selectById(nav.getId());
        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo("DISABLED");

        // 再次 softDelete 应返回 0（已经是 DISABLED）
        int rows2 = mapper.softDeleteById(nav.getId(), "admin");
        assertThat(rows2).isEqualTo(0);
    }

    /**
     * 验证 updateById 的动态更新功能。
     */
    @Test
    void updateById_dynamicSet() {
        // Arrange
        PortalNav nav = newNav("TEST_原始名称", "HQ_SYSTEM", 1);
        mapper.insert(nav);

        // Act - 只更新 navName 和 navUrl
        PortalNav patch = new PortalNav();
        patch.setId(nav.getId());
        patch.setNavName("TEST_更新后名称");
        patch.setNavUrl("https://updated.com");
        patch.setUpdatedBy("updater");
        int rows = mapper.updateById(patch);

        // Assert
        assertThat(rows).isEqualTo(1);
        PortalNav found = mapper.selectById(nav.getId());
        assertThat(found.getNavName()).isEqualTo("TEST_更新后名称");
        assertThat(found.getNavUrl()).isEqualTo("https://updated.com");
        assertThat(found.getUpdatedBy()).isEqualTo("updater");
        // navCategory 应保持不变
        assertThat(found.getNavCategory()).isEqualTo("HQ_SYSTEM");
    }

    /**
     * 创建测试导航辅助方法。
     *
     * @param name     导航名称
     * @param category 导航分类
     * @param sort     排序号
     * @return 预填充的导航实体
     */
    private PortalNav newNav(String name, String category, int sort) {
        PortalNav nav = new PortalNav();
        nav.setId(UUID.randomUUID().toString().replace("-", ""));
        nav.setNavName(name);
        nav.setNavUrl("https://example.com/" + name.toLowerCase().replace("test_", ""));
        nav.setNavIcon("icon-default");
        nav.setNavCategory(category);
        nav.setSortOrder(sort);
        nav.setStatus("ACTIVE");
        nav.setCreatedBy("tester");
        nav.setCreatedTime(LocalDateTime.now());
        return nav;
    }
}
