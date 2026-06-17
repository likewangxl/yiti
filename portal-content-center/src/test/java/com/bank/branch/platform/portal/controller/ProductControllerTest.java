package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.portal.api.dto.ProductCreateReqDTO;
import com.bank.branch.platform.portal.api.dto.ResponsibleEmpDTO;
import com.bank.branch.platform.portal.controller.dto.product.ProductQueryReqDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.service.ProductExportService;
import com.bank.branch.platform.portal.service.ProductService;
import com.bank.branch.platform.portal.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.portal.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.bank.branch.platform.common.web.exception.BizException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProductController 集成测试 -- Service 返回实体，Controller 负责 DTO 转换
 */
class ProductControllerTest extends AbstractControllerIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test @WithMockEmpContext(empId = "E10001", roleCodes = {"R_RM"})
    void getSupportAvailableShouldReturn200WithProductList() throws Exception {
        ProductInfo p1 = new ProductInfo();
        p1.setId("P001"); p1.setProductCode("DEPOSIT_001"); p1.setProductName("活期存款");
        p1.setProductCategory("CAT_DEPOSIT"); p1.setProductDeptOrgCode("ORG_HQ_FIN");
        when(productService.listSupportAvailable()).thenReturn(Arrays.asList(p1));
        mockMvc.perform(get("/api/products/support-available"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].productCode").value("DEPOSIT_001"))
                .andExpect(jsonPath("$.data[0].productName").value("活期存款"));
    }

    @Test @WithMockEmpContext
    void getSupportAvailableShouldReturn200WithEmptyArrayWhenNoProducts() throws Exception {
        when(productService.listSupportAvailable()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/products/support-available"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test @WithMockEmpContext(empId = "E10001", dataScope = "ORG_SUBTREE", orgSubtree = {"ORG_SZ_001", "ORG_SZ_002"})
    void listProductsShouldReturn200WithPagedResult() throws Exception {
        ProductInfo entity = new ProductInfo();
        entity.setId("P001"); entity.setProductCode("DEPOSIT_001"); entity.setProductName("活期存款");
        entity.setProductCategory("CAT_DEPOSIT"); entity.setProductDeptOrgCode("ORG_SZ_001");
        entity.setSupportForSupportRequest(true); entity.setStatus("ACTIVE");
        PageResult<ProductInfo> page = PageResult.of(1, 20, 1L, List.of(entity));
        when(productService.listProducts(any(ProductQueryReqDTO.class))).thenReturn(page);
        mockMvc.perform(get("/api/products").param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].productCode").value("DEPOSIT_001"));
    }

    @Test @WithMockEmpContext(empId = "E10001", dataScope = "ORG_SUBTREE", orgSubtree = {"ORG_SZ_001"})
    void listProductsShouldResolveResponsibleEmpNames() throws Exception {
        ProductInfo entity = new ProductInfo();
        entity.setId("P001"); entity.setProductCode("DEPOSIT_001"); entity.setProductName("活期存款");
        entity.setProductCategory("CAT_DEPOSIT"); entity.setProductDeptOrgCode("ORG_SZ_001");
        entity.setStatus("ACTIVE");
        entity.setResponsibleEmpIds(List.of("E001", "E002"));
        when(productService.listProducts(any(ProductQueryReqDTO.class)))
                .thenReturn(PageResult.of(1, 20, 1L, List.of(entity)));
        ResponsibleEmpDTO e1 = new ResponsibleEmpDTO(); e1.setEmpId("E001"); e1.setEmpName("张三");
        ResponsibleEmpDTO e2 = new ResponsibleEmpDTO(); e2.setEmpId("E002"); e2.setEmpName("李四");
        when(addrbookQueryService.listResponsibleEmps(List.of("E001", "E002")))
                .thenReturn(List.of(e1, e2));
        mockMvc.perform(get("/api/products").param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.records[0].responsibleEmpNames").value("张三、李四"));
    }

    @Test @WithMockEmpContext(empId = "E10001", dataScope = "ORG", orgSubtree = {"ORG_SZ_001"})
    void listProductsShouldReturn200WithEmptyPageWhenNoResults() throws Exception {
        PageResult<ProductInfo> emptyPage = PageResult.of(1, 20, 0L, Collections.emptyList());
        when(productService.listProducts(any(ProductQueryReqDTO.class))).thenReturn(emptyPage);
        mockMvc.perform(get("/api/products").param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(0))
                .andExpect(jsonPath("$.page.records").isArray()).andExpect(jsonPath("$.page.records").isEmpty());
    }

    @Test @WithMockEmpContext(empId = "E10001")
    void getProductShouldReturn200WithProductDTO() throws Exception {
        ProductInfo entity = new ProductInfo();
        entity.setId("P001"); entity.setProductCode("DEPOSIT_001"); entity.setProductName("活期存款");
        entity.setProductCategory("CAT_DEPOSIT");
        when(productService.getProduct("P001")).thenReturn(entity);
        mockMvc.perform(get("/api/products/P001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("P001"))
                .andExpect(jsonPath("$.data.productCode").value("DEPOSIT_001"));
    }

    @Test @WithMockEmpContext
    void supportAvailableEndpointShouldNotBeMatchedAsIdPathVariable() throws Exception {
        when(productService.listSupportAvailable()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/products/support-available")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        verify(productService, never()).getProduct("support-available");
    }

    @Test @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void createProductShouldReturn200WithProductId() throws Exception {
        ProductInfo resultEntity = new ProductInfo(); resultEntity.setId("NEW_P_001"); resultEntity.setProductCode("DEPOSIT_001");
        when(productService.createProduct(any(ProductCreateReqDTO.class))).thenReturn(resultEntity);
        String body = "{\"productCode\":\"DEPOSIT_001\",\"productName\":\"活期存款\",\"productCategory\":\"CAT_DEPOSIT\",\"supportForSupportRequest\":true,\"productDeptOrgCode\":\"ORG_SZ_001\"}";
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").value("NEW_P_001"));
    }

    @Test @WithMockEmpContext(empId = "E10001")
    void createProductShouldReturn400WhenProductCodeMissing() throws Exception {
        String body = "{\"productName\":\"活期存款\",\"supportForSupportRequest\":true,\"productDeptOrgCode\":\"ORG_SZ_001\"}";
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    // ========== D.5 updateProduct 测试 ==========

    @Test @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void updateProductShouldReturn200() throws Exception {
        ProductInfo updated = new ProductInfo();
        updated.setId("P001"); updated.setProductCode("DEPOSIT_001"); updated.setProductName("更新后名称");
        when(productService.updateProduct(eq("P001"), any(com.bank.branch.platform.portal.controller.dto.product.ProductUpdateReqDTO.class))).thenReturn(updated);
        String body = "{\"productName\":\"更新后名称\",\"description\":\"更新描述\"}";
        mockMvc.perform(put("/api/products/P001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"));
    }

    // ========== D.6 deleteProduct 测试 ==========

    @Test @WithMockEmpContext(empId = "E10001")
    void deleteProductShouldReturn200() throws Exception {
        doNothing().when(productService).deleteProduct(eq("P001"));
        mockMvc.perform(delete("/api/products/P001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"));
    }

    // ========== D.2 getProduct 异常测试 ==========

    @Test @WithMockEmpContext(empId = "E10001")
    void getProductShouldReturnErrorCodeWhenNotFound() throws Exception {
        when(productService.getProduct("P_NONE"))
                .thenThrow(new BizException("PORTAL-40003", "产品不存在"));
        // GlobalExceptionHandler 对 BizException 返回 HTTP 200 + 业务错误码
        mockMvc.perform(get("/api/products/P_NONE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("PORTAL-40003"));
    }

    // ========== D.7 exportProducts 测试 ==========

    @Test @WithMockEmpContext(empId = "E10001", dataScope = "ORG_SUBTREE", orgSubtree = {"ORG_SZ_001"})
    void exportProductsShouldReturn200() throws Exception {
        // ProductExportService 已被 @MockBean，doNothing 是默认行为
        mockMvc.perform(get("/api/products/export"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
