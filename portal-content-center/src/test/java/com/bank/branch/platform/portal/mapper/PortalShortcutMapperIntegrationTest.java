package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.PortalShortcut;
import com.bank.branch.platform.portal.support.AbstractMapperIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PortalShortcutMapper 集成测试。
 * <p>
 * 直连本地 MySQL 实例，每个测试方法执行前清理 TEST_ 前缀的测试数据。
 * 重点验证系统级/自定义快捷入口的可见性隔离以及物理删除逻辑。
 * </p>
 */
@Sql(scripts = "/sql/clean-portal-shortcut.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PortalShortcutMapperIntegrationTest extends AbstractMapperIntegrationTest {

    @Autowired
    PortalShortcutMapper mapper;

    /**
     * 验证 listByEmpIdOrSystem 返回系统级快捷入口和指定员工的自定义快捷入口，
     * 不返回其他员工的自定义快捷入口。
     */
    @Test
    void insertAndListShouldReturnSystemAndOwnCustom() {
        // Arrange: 1 SYSTEM + 2 CUSTOM (E10001 and E10002)
        PortalShortcut systemShortcut = newShortcut("TEST_SYS_01", "SYSTEM", null);
        PortalShortcut customE10001 = newShortcut("TEST_CUSTOM_E1", "CUSTOM", "E10001");
        PortalShortcut customE10002 = newShortcut("TEST_CUSTOM_E2", "CUSTOM", "E10002");

        mapper.insert(systemShortcut);
        mapper.insert(customE10001);
        mapper.insert(customE10002);

        // Act: query for E10001
        List<PortalShortcut> result = mapper.listByEmpIdOrSystem("E10001");

        // Assert: should return SYSTEM + E10001's CUSTOM only (2 results)
        assertThat(result).hasSize(2);
        List<String> names = result.stream()
                .map(PortalShortcut::getShortcutName)
                .toList();
        assertThat(names).containsExactlyInAnyOrder("TEST_SYS_01", "TEST_CUSTOM_E1");
        assertThat(names).doesNotContain("TEST_CUSTOM_E2");
    }

    /**
     * 验证 deleteCustomByEmpId 只删除指定员工的自定义快捷入口，
     * 不影响系统级快捷入口和其他员工的自定义快捷入口。
     */
    @Test
    void deleteCustomByEmpIdShouldNotTouchSystemOrOthers() {
        // Arrange: 1 SYSTEM + 1 CUSTOM for E10001 + 1 CUSTOM for E10002
        PortalShortcut systemShortcut = newShortcut("TEST_SYS_DEL", "SYSTEM", null);
        PortalShortcut customE10001 = newShortcut("TEST_DEL_E1", "CUSTOM", "E10001");
        PortalShortcut customE10002 = newShortcut("TEST_DEL_E2", "CUSTOM", "E10002");

        mapper.insert(systemShortcut);
        mapper.insert(customE10001);
        mapper.insert(customE10002);

        // Act: delete E10001's custom shortcuts
        int deleted = mapper.deleteCustomByEmpId("E10001");

        // Assert: only E10001's custom is deleted
        assertThat(deleted).isEqualTo(1);

        // SYSTEM still visible to E10001
        List<PortalShortcut> e10001Result = mapper.listByEmpIdOrSystem("E10001");
        assertThat(e10001Result).hasSize(1);
        assertThat(e10001Result.get(0).getShortcutName()).isEqualTo("TEST_SYS_DEL");

        // E10002's custom still exists
        List<PortalShortcut> e10002Result = mapper.listByEmpIdOrSystem("E10002");
        assertThat(e10002Result).hasSize(2);
        List<String> e10002Names = e10002Result.stream()
                .map(PortalShortcut::getShortcutName)
                .toList();
        assertThat(e10002Names).containsExactlyInAnyOrder("TEST_SYS_DEL", "TEST_DEL_E2");
    }

    /**
     * 验证 insertBatch 能够一次性持久化所有行。
     */
    @Test
    void batchInsertShouldPersistAllRows() {
        // Arrange: 3 shortcuts for the same employee
        PortalShortcut s1 = newShortcut("TEST_BATCH_01", "CUSTOM", "E10001");
        PortalShortcut s2 = newShortcut("TEST_BATCH_02", "CUSTOM", "E10001");
        PortalShortcut s3 = newShortcut("TEST_BATCH_03", "CUSTOM", "E10001");

        // Act
        int rows = mapper.insertBatch(Arrays.asList(s1, s2, s3));

        // Assert
        assertThat(rows).isEqualTo(3);

        List<PortalShortcut> result = mapper.listByEmpIdOrSystem("E10001");
        List<String> names = result.stream()
                .map(PortalShortcut::getShortcutName)
                .toList();
        assertThat(names).containsExactlyInAnyOrder("TEST_BATCH_01", "TEST_BATCH_02", "TEST_BATCH_03");
    }

    /**
     * 验证 listByEmpId 只返回指定员工的快捷入口，按 sort_order 排序。
     */
    @Test
    void insertAndListByEmpId() {
        // Arrange
        PortalShortcut s1 = newShortcut("TEST_快捷A", "CUSTOM", "TEST_EMP01");
        s1.setSortOrder(2);
        PortalShortcut s2 = newShortcut("TEST_快捷B", "CUSTOM", "TEST_EMP01");
        s2.setSortOrder(1);
        PortalShortcut s3 = newShortcut("TEST_快捷C", "CUSTOM", "TEST_EMP02");
        mapper.insert(s1);
        mapper.insert(s2);
        mapper.insert(s3);

        // Act
        List<PortalShortcut> result = mapper.listByEmpId("TEST_EMP01");

        // Assert - 只返回 TEST_EMP01 的快捷入口
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(s -> "TEST_EMP01".equals(s.getEmpId()));
        // 验证排序（sort_order 升序）
        assertThat(result.get(0).getSortOrder()).isLessThanOrEqualTo(result.get(1).getSortOrder());
    }

    /**
     * 验证 deleteCustomByEmpId 只删除 CUSTOM 类型，保留 SYSTEM 类型。
     */
    @Test
    void deleteCustomByEmpId_keepsSystem() {
        // Arrange
        PortalShortcut custom1 = newShortcut("TEST_自定义1", "CUSTOM", "TEST_EMP01");
        PortalShortcut custom2 = newShortcut("TEST_自定义2", "CUSTOM", "TEST_EMP01");
        PortalShortcut system1 = newShortcut("TEST_系统1", "SYSTEM", "TEST_EMP01");
        mapper.insert(custom1);
        mapper.insert(custom2);
        mapper.insert(system1);

        // Act
        int deleted = mapper.deleteCustomByEmpId("TEST_EMP01");

        // Assert - 只删除了 2 条 CUSTOM
        assertThat(deleted).isEqualTo(2);

        // SYSTEM 类型应保留
        List<PortalShortcut> remaining = mapper.listByEmpId("TEST_EMP01");
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getShortcutType()).isEqualTo("SYSTEM");
    }

    /**
     * 验证 insertBatch 批量插入。
     */
    @Test
    void insertBatchShouldPersistAllRows() {
        // Arrange
        PortalShortcut s1 = newShortcut("TEST_批量A", "CUSTOM", "TEST_EMP01");
        PortalShortcut s2 = newShortcut("TEST_批量B", "CUSTOM", "TEST_EMP01");
        PortalShortcut s3 = newShortcut("TEST_批量C", "CUSTOM", "TEST_EMP01");

        // Act
        int rows = mapper.insertBatch(Arrays.asList(s1, s2, s3));

        // Assert
        assertThat(rows).isEqualTo(3);
        List<PortalShortcut> result = mapper.listByEmpId("TEST_EMP01");
        assertThat(result).hasSize(3);
    }

    /**
     * 验证统计指定员工的自定义快捷入口数量。
     */
    @Test
    void countCustomByEmpId() {
        // Arrange
        mapper.insert(newShortcut("TEST_自定义A", "CUSTOM", "TEST_EMP01"));
        mapper.insert(newShortcut("TEST_自定义B", "CUSTOM", "TEST_EMP01"));
        mapper.insert(newShortcut("TEST_系统A", "SYSTEM", "TEST_EMP01"));
        mapper.insert(newShortcut("TEST_其他员工", "CUSTOM", "TEST_EMP02"));

        // Act
        int count = mapper.countCustomByEmpId("TEST_EMP01");

        // Assert - 只统计 TEST_EMP01 的 CUSTOM 类型
        assertThat(count).isEqualTo(2);
    }

    /**
     * 创建测试快捷入口辅助方法。
     *
     * @param name  快捷入口名称
     * @param type  类型：SYSTEM 或 CUSTOM
     * @param empId 所属员工工号（SYSTEM 类型可传 null）
     * @return 预填充的快捷入口实体
     */
    private PortalShortcut newShortcut(String name, String type, String empId) {
        PortalShortcut s = new PortalShortcut();
        s.setId(UUID.randomUUID().toString().replace("-", ""));
        s.setShortcutName(name);
        s.setShortcutUrl("https://example.com/" + name);
        s.setShortcutType(type);
        s.setTargetType("INTERNAL");
        s.setEmpId(empId);
        s.setSortOrder(0);
        s.setStatus("ACTIVE");
        s.setCreatedBy("tester");
        return s;
    }
}
