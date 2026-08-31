package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeSelfUpdateReqDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.service.AddressBookService;
import com.bank.branch.platform.portal.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.portal.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 通讯录 REST 新契约测试。 */
class AddressBookControllerTest extends AbstractControllerIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockEmpContext(empId = "U1")
    void listUsesBatchRelationAndProductLookup() throws Exception {
        UserDirectoryDTO user = user("U1", "张三");
        when(addressBookService.listEmployees(any(EmployeeQueryReqDTO.class)))
                .thenReturn(PageResult.of(1, 20, 1L, List.of(user)));
        when(addressBookService.mapProductIdsByUserIds(any())).thenReturn(Map.of("U1", List.of("P1")));
        ProductInfo product = new ProductInfo();
        product.setId("P1");
        product.setProductCode("P-001");
        product.setProductName("活期存款");
        when(addressBookService.listProductsByIds(List.of("P1"))).thenReturn(List.of(product));

        mockMvc.perform(get("/api/employees").param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].empId").value("U1"))
                .andExpect(jsonPath("$.page.records[0].mobile").value("13800000000"))
                .andExpect(jsonPath("$.page.records[0].responsibleProducts[0].id").value("P1"));
    }

    @Test
    @WithMockEmpContext(empId = "U1")
    void selfUpdateHasIndependentEndpoint() throws Exception {
        when(addressBookService.updateCurrentUser(any(EmployeeSelfUpdateReqDTO.class))).thenReturn(null);

        mockMvc.perform(put("/api/employees/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"13900000000\",\"email\":\"me@example.com\",\"responsibleProductIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mockMvc.perform(put("/api/employees/U1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"13900000000\"}"))
                .andExpect(status().isMethodNotAllowed());
    }

    private UserDirectoryDTO user(String id, String name) {
        UserDirectoryDTO dto = new UserDirectoryDTO();
        dto.setEmpId(id);
        dto.setEmpName(name);
        dto.setMobile("13800000000");
        dto.setEmail("user@example.com");
        dto.setOrgCode("ORG001");
        dto.setOrgName("深圳分行");
        dto.setStatus("ACTIVE");
        return dto;
    }
}
