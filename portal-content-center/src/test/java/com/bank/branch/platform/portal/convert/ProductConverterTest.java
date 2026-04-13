package com.bank.branch.platform.portal.convert;

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
    @DisplayName("toSimple: null 输入 → 返回 null")
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
    @DisplayName("toSimple: Entity 的额外字段不会出现在 SimpleDTO 中")
    void toSimpleShouldIgnoreExtraEntityFields() {
        // given — 设置 SimpleDTO 不包含的字段
        ProductInfo entity = new ProductInfo();
        entity.setId("prod-002");
        entity.setProductCode("P2024-002");
        entity.setProductName("稳健存款B款");
        entity.setProductCategory("DEPOSIT");
        entity.setProductDeptOrgCode("ORG-DEPT-002");
        entity.setDescription("这是一款稳健的存款产品");
        entity.setStatus("ACTIVE");
        entity.setSupportForSupportRequest(true);
        entity.setFileObjectId("file-123");
        entity.setResponsibleEmpIds(List.of("emp-1", "emp-2"));
        entity.setCreatedBy("admin");
        entity.setUpdatedBy("admin");
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedTime(LocalDateTime.now());
        entity.setDeleted(0);
        entity.setOwnerOrgId("ORG-001");

        // when
        ProductSimpleDTO dto = ProductConverter.toSimple(entity);

        // then — SimpleDTO 只有 5 个字段
        assertNotNull(dto);
        assertEquals("prod-002", dto.getId());
        assertEquals("P2024-002", dto.getProductCode());
        assertEquals("稳健存款B款", dto.getProductName());
        assertEquals("DEPOSIT", dto.getProductCategory());
        assertEquals("ORG-DEPT-002", dto.getProductDeptOrgCode());
    }
}
