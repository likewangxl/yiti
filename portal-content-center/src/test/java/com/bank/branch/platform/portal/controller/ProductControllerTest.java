package com.bank.branch.platform.portal.controller;

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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
}
