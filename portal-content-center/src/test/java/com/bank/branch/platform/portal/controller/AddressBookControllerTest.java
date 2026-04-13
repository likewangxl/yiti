package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeUpdateReqDTO;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import com.bank.branch.platform.portal.service.AddressBookService;
import com.bank.branch.platform.portal.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.portal.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AddressBookController 集成测试
 *
 * <p>TDD RED-GREEN 闭环：Service 被 MockBean 替换，Controller 负责路由和 DTO 转换。</p>
 */
class AddressBookControllerTest extends AbstractControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @MockBean AddressBookService addressBookService;
    @MockBean ProductInfoMapper productInfoMapper;

    // ===== C.1 GET /api/employees =====

    @Test @WithMockEmpContext(empId = "E10001", dataScope = "ORG_SUBTREE", orgSubtree = {"ORG_SZ_001"})
    void listEmployees_returns200() throws Exception {
        AddrbookEmployee emp = buildEmployee("E001", "张三");
        PageResult<AddrbookEmployee> page = PageResult.of(1, 20, 1L, List.of(emp));
        when(addressBookService.listEmployees(any(EmployeeQueryReqDTO.class))).thenReturn(page);

        mockMvc.perform(get("/api/employees").param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].empId").value("E001"))
                .andExpect(jsonPath("$.page.records[0].empName").value("张三"))
                .andExpect(jsonPath("$.page.records[0].mobile").value("138****5678"));
    }

    // ===== C.2 GET /api/employees/{empId} =====

    @Test @WithMockEmpContext(empId = "E10001")
    void getEmployee_returns200() throws Exception {
        AddrbookEmployee emp = buildEmployee("E001", "张三");
        emp.setResponsibleProductIds(Arrays.asList("P001", "P002"));
        when(addressBookService.getEmployee("E001")).thenReturn(emp);

        ProductInfo p1 = new ProductInfo();
        p1.setId("P001"); p1.setProductCode("DEPOSIT_001"); p1.setProductName("活期存款"); p1.setProductCategory("CAT_DEPOSIT");
        ProductInfo p2 = new ProductInfo();
        p2.setId("P002"); p2.setProductCode("LOAN_001"); p2.setProductName("消费贷"); p2.setProductCategory("CAT_LOAN");
        when(productInfoMapper.listByIds(Arrays.asList("P001", "P002"))).thenReturn(Arrays.asList(p1, p2));

        AddrbookEmployee maintainer = buildEmployee("E999", "维护人");
        when(addressBookService.getEmployee("E999")).thenReturn(maintainer);

        mockMvc.perform(get("/api/employees/E001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.empId").value("E001"))
                .andExpect(jsonPath("$.data.empName").value("张三"))
                .andExpect(jsonPath("$.data.mobile").value("138****5678"))
                .andExpect(jsonPath("$.data.maintainerEmpId").value("E999"))
                .andExpect(jsonPath("$.data.responsibleProducts").isArray())
                .andExpect(jsonPath("$.data.responsibleProducts[0].productCode").value("DEPOSIT_001"));
    }

    @Test @WithMockEmpContext(empId = "E10001")
    void getEmployee_notFound() throws Exception {
        when(addressBookService.getEmployee("E_NONE"))
                .thenThrow(new BizException("PORTAL-40002", "通讯录员工不存在"));

        mockMvc.perform(get("/api/employees/E_NONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PORTAL-40002"));
    }

    // ===== C.3 PUT /api/employees/{empId} =====

    @Test @WithMockEmpContext(empId = "E10001")
    void updateEmployee_returns200() throws Exception {
        doNothing().when(addressBookService).updateEmployee(eq("E10001"), any(EmployeeUpdateReqDTO.class));

        String body = "{\"mobile\":\"13900001111\",\"email\":\"test@bank.com\",\"selfDesc\":\"测试描述\"}";
        mockMvc.perform(put("/api/employees/E10001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test @WithMockEmpContext(empId = "E10001")
    void updateEmployee_noPermission() throws Exception {
        doThrow(new BizException("PORTAL-40301", "无权编辑本人以外的通讯录"))
                .when(addressBookService).updateEmployee(eq("E002"), any(EmployeeUpdateReqDTO.class));

        String body = "{\"mobile\":\"13900002222\"}";
        mockMvc.perform(put("/api/employees/E002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PORTAL-40301"));
    }

    // ===== C.4 GET /api/employees/search =====

    @Test @WithMockEmpContext(empId = "E10001")
    void searchEmployees_returns200() throws Exception {
        List<AddrbookEmployee> results = Arrays.asList(
                buildEmployee("E001", "张三"),
                buildEmployee("E002", "张四")
        );
        when(addressBookService.searchEmployees(eq("张"), eq(20))).thenReturn(results);

        mockMvc.perform(get("/api/employees/search")
                        .param("keyword", "张")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].empId").value("E001"))
                .andExpect(jsonPath("$.data[0].empName").value("张三"))
                .andExpect(jsonPath("$.data[0].orgCode").value("ORG_SZ_001"));
    }

    // ===== helpers =====

    private AddrbookEmployee buildEmployee(String empId, String empName) {
        AddrbookEmployee e = new AddrbookEmployee();
        e.setEmpId(empId);
        e.setEmpName(empName);
        e.setMobile("13812345678");
        e.setEmail(empId.toLowerCase() + "@bank.com");
        e.setOrgCode("ORG_SZ_001");
        e.setOrgName("深圳分行");
        e.setPosition("客户经理");
        e.setSelfDesc("测试员工");
        e.setStatus("ACTIVE");
        e.setResponsibleProductIds(new ArrayList<>());
        e.setMaintainerEmpId("E999");
        e.setCreatedBy("SYSTEM");
        e.setCreatedTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        e.setUpdatedTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        e.setDeleted(0);
        return e;
    }
}
