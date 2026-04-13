package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.support.AbstractMapperIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AddrbookEmployeeMapper 集成测试。
 * <p>
 * 直连本地 MySQL（127.0.0.1:3306/onepl），每个测试方法前清理 TEST_ 前缀数据。
 * 验证 JSON 字段（responsible_product_ids）的正确序列化/反序列化，
 * 以及 D.6 引用检查、乐观锁更新等关键 SQL 的正确性。
 * </p>
 */
@Sql("/sql/clean-addrbook-employee.sql")
class AddrbookEmployeeMapperIntegrationTest extends AbstractMapperIntegrationTest {

    @Autowired
    private AddrbookEmployeeMapper mapper;

    /**
     * 插入含 JSON 数组字段的员工，再按 empId 查询，验证 List<String> 往返序列化。
     */
    @Test
    void insertAndSelectByEmpIdShouldRoundTripJsonField() {
        // given
        AddrbookEmployee emp = newEmployee("TEST_E001", List.of("P001", "P002"));
        mapper.insert(emp);

        // when
        AddrbookEmployee found = mapper.selectByEmpId("TEST_E001");

        // then
        assertThat(found).isNotNull();
        assertThat(found.getEmpId()).isEqualTo("TEST_E001");
        assertThat(found.getEmpName()).isEqualTo("员工 TEST_E001");
        assertThat(found.getResponsibleProductIds())
                .containsExactlyInAnyOrder("P001", "P002");
        assertThat(found.getStatus()).isEqualTo("ACTIVE");
        assertThat(found.getDeleted()).isEqualTo(0);
    }

    /**
     * 插入 3 名员工，验证 countEmployeesReferringProduct 能统计出引用指定产品的员工数。
     * TEST_E001 负责 P001, P002；TEST_E002 负责 P001；TEST_E003 负责 P003。
     * 查询 P001 应返回 2。
     */
    @Test
    void countEmployeesReferringProductShouldFindAllOwners() {
        // given
        mapper.insert(newEmployee("TEST_E001", List.of("P001", "P002")));
        mapper.insert(newEmployee("TEST_E002", List.of("P001")));
        mapper.insert(newEmployee("TEST_E003", List.of("P003")));

        // when
        int count = mapper.countEmployeesReferringProduct("P001");

        // then
        assertThat(count).isEqualTo(2);
    }

    /**
     * 验证 LIKE 精度：responsible_product_ids 包含 "P0010" 时，
     * 搜索 "P001" 不应匹配，因为 LIKE '%"P001"%' 不会匹配 '"P0010"'。
     * 模式 "P001" 后面跟的是引号，但 "P0010" 在 P001 后面跟的是 0 而非引号。
     */
    @Test
    void countEmployeesReferringProductShouldNotMatchSubstring() {
        // given — 只有 P0010，没有 P001
        mapper.insert(newEmployee("TEST_E001", List.of("P0010")));

        // when
        int count = mapper.countEmployeesReferringProduct("P001");

        // then — "P001" 不是 "P0010" 的精确匹配
        assertThat(count).isEqualTo(0);
    }

    /**
     * 验证乐观锁：当 expectedUpdatedTime 与实际 updated_time 不匹配时，更新应返回 0。
     */
    @Test
    void updateResponsibleProductsWithOptimisticLockShouldReturnZeroOnVersionMismatch() {
        // given
        AddrbookEmployee emp = newEmployee("TEST_E001", List.of("P001"));
        mapper.insert(emp);

        AddrbookEmployee inserted = mapper.selectByEmpId("TEST_E001");
        assertThat(inserted).isNotNull();

        // 用一个偏移 1 天的时间制造乐观锁冲突
        LocalDateTime wrongTime = inserted.getUpdatedTime().minusDays(1);

        // when
        int affected = mapper.updateResponsibleProductsWithOptimisticLock(
                "TEST_E001",
                List.of("P001", "P002"),
                wrongTime,
                "OPERATOR_001");

        // then
        assertThat(affected).isEqualTo(0);

        // 数据应保持不变
        AddrbookEmployee unchanged = mapper.selectByEmpId("TEST_E001");
        assertThat(unchanged.getResponsibleProductIds()).containsExactly("P001");
    }

    /**
     * 验证 listByEmpIds 批量查询：插入 3 名员工，查询其中 2 名，应返回正确数量。
     */
    @Test
    void listByEmpIdsShouldReturnRequestedEmployees() {
        // given
        mapper.insert(newEmployee("TEST_E001", List.of("P001")));
        mapper.insert(newEmployee("TEST_E002", List.of("P002")));
        mapper.insert(newEmployee("TEST_E003", List.of("P003")));

        // when
        List<AddrbookEmployee> result = mapper.listByEmpIds(List.of("TEST_E001", "TEST_E003"));

        // then
        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(AddrbookEmployee::getEmpId)
                .containsExactlyInAnyOrder("TEST_E001", "TEST_E003");
    }

    /**
     * 验证 selectPage 多条件过滤功能。
     */
    @Test
    void selectPage_withFilters() {
        // Arrange
        AddrbookEmployee e1 = newEmployee("TEST_P001", List.of("P001"));
        e1.setMobile("13900001111");
        e1.setOrgCode("ORG_SZ_001");
        mapper.insert(e1);

        AddrbookEmployee e2 = newEmployee("TEST_P002", List.of("P002"));
        e2.setMobile("13900002222");
        e2.setOrgCode("ORG_SZ_002");
        mapper.insert(e2);

        AddrbookEmployee e3 = newEmployee("TEST_P003", List.of("P003"));
        e3.setMobile("13900003333");
        e3.setOrgCode("ORG_SZ_001");
        e3.setStatus("RESIGNED");
        mapper.insert(e3);

        // Act - 按机构代码过滤
        List<AddrbookEmployee> result = mapper.selectPage(null, "ORG_SZ_001", null, 0, 10);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(e -> "ORG_SZ_001".equals(e.getOrgCode()));

        // 按机构 + 状态过滤
        List<AddrbookEmployee> activeOnly = mapper.selectPage(null, "ORG_SZ_001", "ACTIVE", 0, 10);
        assertThat(activeOnly).hasSize(1);

        // countPage 与 selectPage 一致
        long count = mapper.countPage(null, "ORG_SZ_001", "ACTIVE");
        assertThat(count).isEqualTo(1);
    }

    /**
     * 验证 searchByKeyword 按关键词搜索（仅 ACTIVE）。
     */
    @Test
    void searchByKeyword() {
        // Arrange
        AddrbookEmployee e1 = newEmployee("TEST_S001", List.of());
        e1.setEmpName("张三");
        e1.setMobile("13800001111");
        mapper.insert(e1);

        AddrbookEmployee e2 = newEmployee("TEST_S002", List.of());
        e2.setEmpName("李四");
        e2.setMobile("13800002222");
        mapper.insert(e2);

        AddrbookEmployee e3 = newEmployee("TEST_S003", List.of());
        e3.setEmpName("张伟");
        e3.setMobile("13800003333");
        e3.setStatus("RESIGNED");
        mapper.insert(e3);

        // Act - 搜索 "张"
        List<AddrbookEmployee> result = mapper.searchByKeyword("张", 10);

        // Assert - 只返回 ACTIVE 的张三，不返回 RESIGNED 的张伟
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmpName()).isEqualTo("张三");
    }

    /**
     * 验证 updateFields 动态更新可编辑字段。
     */
    @Test
    void updateFields() {
        // Arrange
        AddrbookEmployee emp = newEmployee("TEST_U001", List.of("P001"));
        mapper.insert(emp);

        // Act - 只更新 empName 和 position
        AddrbookEmployee patch = new AddrbookEmployee();
        patch.setEmpId("TEST_U001");
        patch.setEmpName("更新后姓名");
        patch.setPosition("高级客户经理");
        int rows = mapper.updateFields(patch);

        // Assert
        assertThat(rows).isEqualTo(1);
        AddrbookEmployee found = mapper.selectByEmpId("TEST_U001");
        assertThat(found.getEmpName()).isEqualTo("更新后姓名");
        assertThat(found.getPosition()).isEqualTo("高级客户经理");
        // orgCode 应保持不变
        assertThat(found.getOrgCode()).isEqualTo("ORG_SZ_001");
    }

    /**
     * 验证 selectByOrgCode 按机构代码查询。
     */
    @Test
    void selectByOrgCode() {
        // Arrange
        mapper.insert(newEmployee("TEST_O001", List.of()));
        mapper.insert(newEmployee("TEST_O002", List.of()));

        AddrbookEmployee e3 = newEmployee("TEST_O003", List.of());
        e3.setOrgCode("ORG_BJ_001");
        mapper.insert(e3);

        // Act
        List<AddrbookEmployee> result = mapper.selectByOrgCode("ORG_SZ_001");

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(e -> "ORG_SZ_001".equals(e.getOrgCode()));
    }

    /**
     * 构造测试用员工实体的辅助方法。
     *
     * @param empId      员工工号
     * @param productIds 负责产品ID列表
     * @return 填充了默认值的员工实体
     */
    private AddrbookEmployee newEmployee(String empId, List<String> productIds) {
        AddrbookEmployee e = new AddrbookEmployee();
        e.setEmpId(empId);
        e.setEmpName("员工 " + empId);
        e.setMobile("13800000001");
        e.setOrgCode("ORG_SZ_001");
        e.setOrgName("深圳分行");
        e.setPosition("客户经理");
        e.setStatus("ACTIVE");
        e.setResponsibleProductIds(productIds);
        e.setDeleted(0);
        return e;
    }
}
