package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.portal.api.dto.ProductDetailDTO;
import com.bank.branch.platform.portal.api.dto.ProductListReqDTO;
import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.service.ProductService;
import com.bank.branch.platform.portal.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.portal.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bank.branch.platform.portal.service.dto.ProductCreateCmd;
import org.springframework.http.MediaType;

/**
 * ProductController 集成测试
 * <p>TDD RED-GREEN 闭环：先写测试，再实现 Controller</p>
 * <p>继承 AbstractControllerIntegrationTest 获取 MockMvc + 跨模块 Api 的 Mock</p>
 */
class ProductControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ProductService productService;

    /**
     * D.3 查询支持中场支持的产品 - 有数据时返回 200 + 产品列表
     */
    @Test
    @WithMockEmpContext(empId = "E10001", roleCodes = {"R_RM"})
    void getSupportAvailableShouldReturn200WithProductList() throws Exception {
        ProductSimpleDTO p1 = new ProductSimpleDTO();
        p1.setId("P001");
        p1.setProductCode("DEPOSIT_001");
        p1.setProductName("活期存款");
        p1.setProductCategory("CAT_DEPOSIT");
        p1.setProductDeptOrgCode("ORG_HQ_FIN");
        when(productService.listSupportAvailable()).thenReturn(Arrays.asList(p1));

        mockMvc.perform(get("/api/products/support-available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].productCode").value("DEPOSIT_001"))
                .andExpect(jsonPath("$.data[0].productName").value("活期存款"));
    }

    /**
     * D.3 查询支持中场支持的产品 - 无数据时返回 200 + 空数组
     */
    @Test
    @WithMockEmpContext
    void getSupportAvailableShouldReturn200WithEmptyArrayWhenNoProducts() throws Exception {
        when(productService.listSupportAvailable()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/products/support-available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ========== D.1 listProducts 测试 ==========

    /**
     * D.1 分页查询产品列表 - 正常返回分页数据（$.page 路径）
     */
    @Test
    @WithMockEmpContext(empId = "E10001", dataScope = "ORG_SUBTREE", orgSubtree = {"ORG_SZ_001", "ORG_SZ_002"})
    void listProductsShouldReturn200WithPagedResult() throws Exception {
        // Arrange: mock BizScopeApi.buildScopeContext
        DataScopeContext authRecord = new DataScopeContext(
                DataScopeType.ORG_SUBTREE, "E10001", "ORG_SZ_001",
                Set.of("ORG_SZ_001", "ORG_SZ_002"),
                BizType.PRODUCT, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(eq("E10001"), eq(BizType.PRODUCT), eq(BizAction.LIST)))
                .thenReturn(authRecord);

        ProductDTO dto = ProductDTO.builder()
                .id("P001")
                .productCode("DEPOSIT_001")
                .productName("活期存款")
                .build();
        PageResult<ProductDTO> page = PageResult.of(1, 20, 1L, List.of(dto));
        when(productService.listProducts(any(ProductListReqDTO.class),
                any(com.bank.branch.platform.common.security.context.DataScopeContext.class)))
                .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/products")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].productCode").value("DEPOSIT_001"));
    }

    /**
     * D.1 分页查询产品列表 - 空结果时返回空分页
     */
    @Test
    @WithMockEmpContext(empId = "E10001", dataScope = "ORG", orgSubtree = {"ORG_SZ_001"})
    void listProductsShouldReturn200WithEmptyPageWhenNoResults() throws Exception {
        // Arrange
        DataScopeContext authRecord = new DataScopeContext(
                DataScopeType.ORG, "E10001", "ORG_SZ_001",
                Set.of("ORG_SZ_001"),
                BizType.PRODUCT, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(eq("E10001"), eq(BizType.PRODUCT), eq(BizAction.LIST)))
                .thenReturn(authRecord);

        PageResult<ProductDTO> emptyPage = PageResult.of(1, 20, 0L, Collections.emptyList());
        when(productService.listProducts(any(ProductListReqDTO.class),
                any(com.bank.branch.platform.common.security.context.DataScopeContext.class)))
                .thenReturn(emptyPage);

        // Act & Assert
        mockMvc.perform(get("/api/products")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(0))
                .andExpect(jsonPath("$.page.records").isArray())
                .andExpect(jsonPath("$.page.records").isEmpty());
    }

    // ========== D.2 getProduct 测试 ==========

    /**
     * D.2 产品详情 - 正常返回 200 + 详情 DTO
     */
    @Test
    @WithMockEmpContext(empId = "E10001")
    void getProductShouldReturn200WithDetailDTO() throws Exception {
        ProductDetailDTO dto = new ProductDetailDTO();
        dto.setId("P001");
        dto.setProductCode("DEPOSIT_001");
        when(productService.getProduct("P001")).thenReturn(dto);

        mockMvc.perform(get("/api/products/P001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("P001"))
                .andExpect(jsonPath("$.data.productCode").value("DEPOSIT_001"));
    }

    /**
     * /support-available 路由不应被 /{id} 路径变量匹配
     */
    @Test
    @WithMockEmpContext
    void supportAvailableEndpointShouldNotBeMatchedAsIdPathVariable() throws Exception {
        when(productService.listSupportAvailable()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/products/support-available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        verify(productService, never()).getProduct("support-available");
    }

    // ========== D.4 createProduct 测试 ==========

    /**
     * D.4 新增产品 - 正常返回 200 + 新产品ID
     */
    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void createProductShouldReturn200WithProductId() throws Exception {
        when(productService.createProduct(any(), eq("E10001"))).thenReturn("NEW_P_001");

        String body = """
            {"productCode":"DEPOSIT_001","productName":"活期存款","productCategory":"CAT_DEPOSIT","supportForSupportRequest":true,"productDeptOrgCode":"ORG_SZ_001"}
            """;
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").value("NEW_P_001"));
    }

    /**
     * D.4 新增产品 - productCode 缺失时返回 400
     */
    @Test
    @WithMockEmpContext(empId = "E10001")
    void createProductShouldReturn400WhenProductCodeMissing() throws Exception {
        String body = """
            {"productName":"活期存款","supportForSupportRequest":true,"productDeptOrgCode":"ORG_SZ_001"}
            """;
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }
}
