package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProductConverter 单元测试
 * <p>纯 POJO 转换，不需要 Spring 上下文。</p>
 */
class ProductConverterTest {

    @Test
    @DisplayName("toSimple: null 输入 -> 返回 null")
    void toSimpleShouldReturnNullOnNullInput() {
        assertNull(ProductConverter.toSimple(null));
    }

    @Test
    @DisplayName("toSimple: 5 个字段全量映射验证")
    void toSimpleShouldMapAllFields() {
        // given
        ProductInfo entity = new ProductInfo();
        entity.setId("prod-001");
        entity.setProductCode("P2024-001");
        entity.setProductName("理财宝A款");
        entity.setProductCategory("WEALTH");
        entity.setProductDeptOrgCode("ORG-DEPT-001");

        // when
        ProductSimpleDTO dto = ProductConverter.toSimple(entity);

        // then
        assertNotNull(dto);
        assertEquals("prod-001", dto.getId());
        assertEquals("P2024-001", dto.getProductCode());
        assertEquals("理财宝A款", dto.getProductName());
        assertEquals("WEALTH", dto.getProductCategory());
        assertEquals("ORG-DEPT-001", dto.getProductDeptOrgCode());
    }

    @Test
    @DisplayName("toDTO: null 输入 -> 返回 null")
    void toDTOShouldReturnNullOnNullInput() {
        assertNull(ProductConverter.toDTO(null));
    }

    @Test
    @DisplayName("toDTO: 全量字段映射验证（14 个字段）")
    void toDTOShouldMapAllFields() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 4, 13, 10, 0, 0);
        ProductInfo entity = new ProductInfo();
        entity.setId("prod-001");
        entity.setProductCode("P2024-001");
        entity.setProductName("理财宝A款");
        entity.setProductCategory("WEALTH");
        entity.setDescription("安全稳健的理财产品");
        entity.setSupportForSupportRequest(true);
        entity.setProductDeptOrgCode("ORG-DEPT-001");
        entity.setFileObjectId("file-001");
        entity.setResponsibleEmpIds(List.of("emp-001", "emp-002"));
        entity.setStatus("ACTIVE");
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now);

        // when
        ProductDTO dto = ProductConverter.toDTO(entity);

        // then
        assertNotNull(dto);
        assertEquals("prod-001", dto.getId());
        assertEquals("P2024-001", dto.getProductCode());
        assertEquals("理财宝A款", dto.getProductName());
        assertEquals("WEALTH", dto.getProductCategory());
        assertNull(dto.getProductCategoryDesc(), "categoryDesc 应由 Service 层填充");
        assertEquals("安全稳健的理财产品", dto.getDescription());
        assertTrue(dto.getSupportForSupportRequest());
        assertEquals("ORG-DEPT-001", dto.getProductDeptOrgCode());
        assertNull(dto.getProductDeptOrgName(), "orgName 应由 Service 层填充");
        assertEquals("file-001", dto.getFileObjectId());
        assertEquals(List.of("emp-001", "emp-002"), dto.getResponsibleEmpIds());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals(now, dto.getCreatedTime());
        assertEquals(now, dto.getUpdatedTime());
    }

    @Test
    @DisplayName("toListItem: 与 toDTO 行为一致（别名方法）")
    void toListItemShouldDelegateToDTO() {
        // given
        ProductInfo entity = new ProductInfo();
        entity.setId("prod-003");
        entity.setProductCode("P2024-003");
        entity.setProductName("稳健存款B款");
        entity.setStatus("ACTIVE");

        // when
        ProductDTO dto = ProductConverter.toListItem(entity);

        // then
        assertNotNull(dto);
        assertEquals("prod-003", dto.getId());
        assertEquals("P2024-003", dto.getProductCode());
    }
}
