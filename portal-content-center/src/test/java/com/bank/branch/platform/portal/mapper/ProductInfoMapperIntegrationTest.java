package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.ProductInfo;
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
 * ProductInfoMapper 集成测试。
 * <p>
 * 直连本地 MySQL 实例，每个测试方法执行前清理 TEST_/AVAIL_ 前缀的测试数据。
 * 重点验证 responsible_emp_ids 的 JSON 序列化/反序列化（TypeHandler 集成）。
 * </p>
 */
@Sql(scripts = "/sql/clean-product-info.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProductInfoMapperIntegrationTest extends AbstractMapperIntegrationTest {

    @Autowired
    ProductInfoMapper mapper;

    /**
     * 验证插入和按 ID 查询的完整往返，重点验证 JSON 字段 responsibleEmpIds 的序列化/反序列化。
     */
    @Test
    void insertAndSelectByIdShouldRoundTripJsonField() {
        // Arrange
        ProductInfo product = newProduct("TEST_JSON_RT");
        product.setResponsibleEmpIds(Arrays.asList("E10001", "E10002"));
        product.setDescription("JSON 往返测试产品");

        // Act
        int rows = mapper.insert(product);
        ProductInfo found = mapper.selectById(product.getId());

        // Assert
        assertThat(rows).isEqualTo(1);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(product.getId());
        assertThat(found.getProductCode()).isEqualTo("TEST_JSON_RT");
        assertThat(found.getProductName()).isEqualTo("产品 TEST_JSON_RT");
        assertThat(found.getProductCategory()).isEqualTo("CAT_DEPOSIT");
        assertThat(found.getDescription()).isEqualTo("JSON 往返测试产品");
        assertThat(found.getSupportForSupportRequest()).isFalse();
        assertThat(found.getStatus()).isEqualTo("ACTIVE");
        assertThat(found.getDeleted()).isEqualTo(0);
        assertThat(found.getResponsibleEmpIds())
                .containsExactly("E10001", "E10002");
    }

    /**
     * 验证逻辑删除后 selectById 返回 null。
     */
    @Test
    void selectByIdShouldReturnNullForDeletedProduct() {
        // Arrange
        ProductInfo product = newProduct("TEST_SOFT_DEL");
        mapper.insert(product);

        // Act
        int deleted = mapper.softDeleteById(product.getId(), "admin");
        ProductInfo found = mapper.selectById(product.getId());

        // Assert
        assertThat(deleted).isEqualTo(1);
        assertThat(found).isNull();
    }

    /**
     * 验证 listSupportAvailable 只返回 support_for_support_request=true 且 status=ACTIVE 且未删除的产品。
     */
    @Test
    void listSupportAvailableShouldReturnOnlyActiveAndSupporting() {
        // Arrange: 3 products
        // A: support=true, ACTIVE -> should be returned
        ProductInfo productA = newProduct("AVAIL_A");
        productA.setSupportForSupportRequest(true);
        productA.setStatus("ACTIVE");
        mapper.insert(productA);

        // B: support=false, ACTIVE -> should NOT be returned
        ProductInfo productB = newProduct("AVAIL_B");
        productB.setSupportForSupportRequest(false);
        productB.setStatus("ACTIVE");
        mapper.insert(productB);

        // C: support=true, DISABLED -> should NOT be returned
        ProductInfo productC = newProduct("AVAIL_C");
        productC.setSupportForSupportRequest(true);
        productC.setStatus("DISABLED");
        mapper.insert(productC);

        // Act
        List<ProductInfo> result = mapper.listSupportAvailable();

        // Assert: only A should be in result
        List<String> codes = result.stream()
                .map(ProductInfo::getProductCode)
                .toList();
        assertThat(codes).contains("AVAIL_A");
        assertThat(codes).doesNotContain("AVAIL_B", "AVAIL_C");
    }

    /**
     * 验证 selectByProductCode 能按产品代码查询。
     */
    @Test
    void selectByProductCodeShouldFindExistingProduct() {
        // Arrange
        ProductInfo product = newProduct("TEST_BY_CODE");
        mapper.insert(product);

        // Act
        ProductInfo found = mapper.selectByProductCode("TEST_BY_CODE");

        // Assert
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(product.getId());
    }

    /**
     * 验证 selectByProductCode 对已删除产品返回 null。
     */
    @Test
    void selectByProductCodeShouldReturnNullForDeletedProduct() {
        // Arrange
        ProductInfo product = newProduct("TEST_CODE_DEL");
        mapper.insert(product);
        mapper.softDeleteById(product.getId(), "admin");

        // Act
        ProductInfo found = mapper.selectByProductCode("TEST_CODE_DEL");

        // Assert
        assertThat(found).isNull();
    }

    /**
     * 验证 updateById 的动态更新功能。
     */
    @Test
    void updateByIdShouldUpdateOnlyNonNullFields() {
        // Arrange
        ProductInfo product = newProduct("TEST_UPDATE");
        product.setDescription("原始描述");
        mapper.insert(product);

        // Act: only update productName and description
        ProductInfo patch = new ProductInfo();
        patch.setId(product.getId());
        patch.setProductName("更新后的名称");
        patch.setDescription("更新后的描述");
        patch.setUpdatedBy("updater");
        int rows = mapper.updateById(patch);

        // Assert
        assertThat(rows).isEqualTo(1);
        ProductInfo found = mapper.selectById(product.getId());
        assertThat(found.getProductName()).isEqualTo("更新后的名称");
        assertThat(found.getDescription()).isEqualTo("更新后的描述");
        assertThat(found.getUpdatedBy()).isEqualTo("updater");
        // productCode should remain unchanged
        assertThat(found.getProductCode()).isEqualTo("TEST_UPDATE");
    }

    /**
     * 验证 listByIds 批量查询。
     */
    @Test
    void listByIdsShouldReturnMatchingProducts() {
        // Arrange
        ProductInfo p1 = newProduct("TEST_BATCH_1");
        ProductInfo p2 = newProduct("TEST_BATCH_2");
        ProductInfo p3 = newProduct("TEST_BATCH_3");
        mapper.insert(p1);
        mapper.insert(p2);
        mapper.insert(p3);

        // Act: query p1 and p3
        List<ProductInfo> result = mapper.listByIds(Arrays.asList(p1.getId(), p3.getId()));

        // Assert
        assertThat(result).hasSize(2);
        List<String> codes = result.stream()
                .map(ProductInfo::getProductCode)
                .toList();
        assertThat(codes).containsExactlyInAnyOrder("TEST_BATCH_1", "TEST_BATCH_3");
    }

    /**
     * 验证 softDeleteById 重复删除应返回 0。
     */
    @Test
    void softDeleteByIdShouldReturnZeroForAlreadyDeleted() {
        // Arrange
        ProductInfo product = newProduct("TEST_DBL_DEL");
        mapper.insert(product);
        mapper.softDeleteById(product.getId(), "admin");

        // Act: second delete
        int rows = mapper.softDeleteById(product.getId(), "admin");

        // Assert
        assertThat(rows).isEqualTo(0);
    }

    /**
     * 验证 responsibleEmpIds 为 null 时的序列化/反序列化。
     */
    @Test
    void insertWithNullResponsibleEmpIdsShouldReturnEmptyList() {
        // Arrange
        ProductInfo product = newProduct("TEST_NULL_JSON");
        product.setResponsibleEmpIds(null);
        mapper.insert(product);

        // Act
        ProductInfo found = mapper.selectById(product.getId());

        // Assert
        assertThat(found).isNotNull();
        assertThat(found.getResponsibleEmpIds()).isEmpty();
    }

    /**
     * 创建测试产品辅助方法。
     *
     * @param code 产品代码
     * @return 预填充的产品实体
     */
    private ProductInfo newProduct(String code) {
        ProductInfo p = new ProductInfo();
        p.setId(UUID.randomUUID().toString().replace("-", ""));
        p.setProductCode(code);
        p.setProductName("产品 " + code);
        p.setProductCategory("CAT_DEPOSIT");
        p.setStatus("ACTIVE");
        p.setProductDeptOrgCode("ORG_SZ_001");
        p.setCreatedBy("tester");
        p.setSupportForSupportRequest(false);
        p.setDeleted(0);
        p.setCreatedTime(LocalDateTime.now());
        return p;
    }
}
