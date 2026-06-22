package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeUpdateReqDTO;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.event.AddrbookUpdatedEvent;
import com.bank.branch.platform.portal.event.ProductResponsibleUpdatedEvent;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AddressBookService 单元测试 -- 纯 JUnit 5 + Mockito，无需 Spring 上下文
 *
 * <p>TDD RED-GREEN 闭环：先写测试（Red），再实现 Service（Green）。
 * 涵盖分页列表、详情查询、编辑校验、双向同步、事件发布等核心场景。</p>
 */
@ExtendWith(MockitoExtension.class)
class AddressBookServiceTest {

    @Mock AddrbookEmployeeMapper addrbookEmployeeMapper;
    @Mock ProductInfoMapper productInfoMapper;
    @Mock CurrentUserApi currentUserApi;
    @Mock BizScopeApi bizScopeApi;
    @Mock AuditApi auditApi;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks AddressBookService addressBookService;

    // ===== listEmployees =====

    @Test
    void listEmployees_withPagination() {
        EmployeeQueryReqDTO req = new EmployeeQueryReqDTO();
        req.setPageNo(1);
        req.setPageSize(10);
        req.setKeyword("张");
        req.setOrgCode("ORG_SZ_001");
        req.setStatus("ACTIVE");

        when(addrbookEmployeeMapper.countPage("张", "ORG_SZ_001", null, "ACTIVE")).thenReturn(2L);
        List<AddrbookEmployee> employees = Arrays.asList(
                buildEmployee("E001", "张三"),
                buildEmployee("E002", "张四")
        );
        when(addrbookEmployeeMapper.selectPage("张", "ORG_SZ_001", null, "ACTIVE", 0, 10))
                .thenReturn(employees);

        PageResult<AddrbookEmployee> result = addressBookService.listEmployees(req);

        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getRecords()).hasSize(2);
    }

    @Test
    void listEmployees_emptyResult() {
        EmployeeQueryReqDTO req = new EmployeeQueryReqDTO();
        req.setPageNo(1);
        req.setPageSize(20);
        when(addrbookEmployeeMapper.countPage(null, null, null, null)).thenReturn(0L);

        PageResult<AddrbookEmployee> result = addressBookService.listEmployees(req);

        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getRecords()).isEmpty();
        verify(addrbookEmployeeMapper, never()).selectPage(any(), any(), any(), any(), anyInt(), anyInt());
    }

    // ===== getEmployee =====

    @Test
    void getEmployee_success() {
        AddrbookEmployee emp = buildEmployee("E001", "张三");
        when(addrbookEmployeeMapper.selectByEmpId("E001")).thenReturn(emp);

        AddrbookEmployee result = addressBookService.getEmployee("E001");

        assertThat(result).isNotNull();
        assertThat(result.getEmpId()).isEqualTo("E001");
        assertThat(result.getEmpName()).isEqualTo("张三");
    }

    @Test
    void getEmployee_notFound() {
        when(addrbookEmployeeMapper.selectByEmpId("E_NONE")).thenReturn(null);

        assertThatThrownBy(() -> addressBookService.getEmployee("E_NONE"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(PortalErrorCode.EMPLOYEE_NOT_FOUND.getCode()));
    }

    // ===== updateEmployee =====

    @Test
    void updateEmployee_self_success() {
        String targetEmpId = "E001";
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        AddrbookEmployee existing = buildEmployee("E001", "张三");
        existing.setOrgCode("ORG_SZ_001");
        when(addrbookEmployeeMapper.selectByEmpId(targetEmpId)).thenReturn(existing);

        EmployeeUpdateReqDTO req = new EmployeeUpdateReqDTO();
        req.setMobile("13900001111");
        req.setEmail("zhangsan@bank.com");

        addressBookService.updateEmployee(targetEmpId, req);

        verify(addrbookEmployeeMapper).updateFields(any(AddrbookEmployee.class));
        verify(eventPublisher).publishEvent(any(AddrbookUpdatedEvent.class));
        verify(auditApi, never()).log(any());
    }

    @Test
    void updateEmployee_otherWithAllScope_success() {
        String targetEmpId = "E002";
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(bizScopeApi.resolveScope("E001", BizType.ADDRBOOK)).thenReturn(DataScopeType.ALL);
        AddrbookEmployee target = buildEmployee("E002", "李四");
        target.setOrgCode("ORG_SZ_001");
        when(addrbookEmployeeMapper.selectByEmpId(targetEmpId)).thenReturn(target);

        EmployeeUpdateReqDTO req = new EmployeeUpdateReqDTO();
        req.setMobile("13900002222");

        addressBookService.updateEmployee(targetEmpId, req);

        verify(addrbookEmployeeMapper).updateFields(any(AddrbookEmployee.class));
    }

    @Test
    void updateEmployee_otherNonAdmin_shouldReject() {
        String targetEmpId = "E002";
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        when(bizScopeApi.resolveScope("E001", BizType.ADDRBOOK)).thenReturn(DataScopeType.SELF);
        AddrbookEmployee target = buildEmployee("E002", "李四");
        target.setOrgCode("ORG_SZ_001");
        when(addrbookEmployeeMapper.selectByEmpId(targetEmpId)).thenReturn(target);

        EmployeeUpdateReqDTO req = new EmployeeUpdateReqDTO();
        req.setMobile("13900002222");

        assertThatThrownBy(() -> addressBookService.updateEmployee(targetEmpId, req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(PortalErrorCode.NO_RIGHT_TO_EDIT_OTHER.getCode()));
        verify(addrbookEmployeeMapper, never()).updateFields(any());
    }

    @Test
    void updateEmployee_resignedEmployee() {
        String targetEmpId = "E003";
        when(currentUserApi.getCurrentEmpId()).thenReturn("E003");
        AddrbookEmployee target = buildEmployee("E003", "王五");
        target.setStatus("RESIGNED");
        when(addrbookEmployeeMapper.selectByEmpId(targetEmpId)).thenReturn(target);

        EmployeeUpdateReqDTO req = new EmployeeUpdateReqDTO();
        req.setMobile("13900003333");

        assertThatThrownBy(() -> addressBookService.updateEmployee(targetEmpId, req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(PortalErrorCode.EMPLOYEE_RESIGNED.getCode()));
        verify(addrbookEmployeeMapper, never()).updateFields(any());
    }

    @Test
    void updateEmployee_productLimitExceeded() {
        String targetEmpId = "E001";
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        AddrbookEmployee existing = buildEmployee("E001", "张三");
        when(addrbookEmployeeMapper.selectByEmpId(targetEmpId)).thenReturn(existing);

        EmployeeUpdateReqDTO req = new EmployeeUpdateReqDTO();
        List<String> productIds = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            productIds.add("P" + String.format("%03d", i));
        }
        req.setResponsibleProductIds(productIds);

        assertThatThrownBy(() -> addressBookService.updateEmployee(targetEmpId, req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(PortalErrorCode.PRODUCT_RESPONSIBLE_LIMIT.getCode()));
        verify(addrbookEmployeeMapper, never()).updateFields(any());
    }

    @Test
    void updateEmployee_syncProductResponsible() {
        String targetEmpId = "E001";
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");

        AddrbookEmployee existing = buildEmployee("E001", "张三");
        existing.setResponsibleProductIds(new ArrayList<>(Arrays.asList("P001", "P002")));
        when(addrbookEmployeeMapper.selectByEmpId(targetEmpId)).thenReturn(existing);

        List<String> newProductIds = Arrays.asList("P002", "P003");
        EmployeeUpdateReqDTO req = new EmployeeUpdateReqDTO();
        req.setResponsibleProductIds(newProductIds);

        ProductInfo p2 = buildProduct("P002", "PRD002");
        p2.setSupportForSupportRequest(true);
        ProductInfo p3 = buildProduct("P003", "PRD003");
        p3.setSupportForSupportRequest(true);
        when(productInfoMapper.listByIds(newProductIds)).thenReturn(Arrays.asList(p2, p3));

        ProductInfo p1ForSync = buildProduct("P001", "PRD001");
        p1ForSync.setResponsibleEmpIds(new ArrayList<>(Arrays.asList("E001", "E005")));
        when(productInfoMapper.selectById("P001")).thenReturn(p1ForSync);

        ProductInfo p3ForSync = buildProduct("P003", "PRD003");
        p3ForSync.setResponsibleEmpIds(new ArrayList<>(Collections.singletonList("E009")));
        when(productInfoMapper.selectById("P003")).thenReturn(p3ForSync);

        addressBookService.updateEmployee(targetEmpId, req);

        verify(addrbookEmployeeMapper).updateFields(any(AddrbookEmployee.class));
        verify(productInfoMapper).updateById(argThat((ProductInfo pi) ->
                pi.getId().equals("P001") && !pi.getResponsibleEmpIds().contains("E001")));
        verify(productInfoMapper).updateById(argThat((ProductInfo pi) ->
                pi.getId().equals("P003") && pi.getResponsibleEmpIds().contains("E001")));
        verify(eventPublisher).publishEvent(any(AddrbookUpdatedEvent.class));
        verify(eventPublisher, atLeastOnce()).publishEvent(any(ProductResponsibleUpdatedEvent.class));
    }

    @Test
    void updateEmployee_noProductChange_noSync() {
        String targetEmpId = "E001";
        when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
        AddrbookEmployee existing = buildEmployee("E001", "张三");
        existing.setResponsibleProductIds(Arrays.asList("P001", "P002"));
        when(addrbookEmployeeMapper.selectByEmpId(targetEmpId)).thenReturn(existing);

        EmployeeUpdateReqDTO req = new EmployeeUpdateReqDTO();
        req.setMobile("13900009999");

        addressBookService.updateEmployee(targetEmpId, req);

        verify(addrbookEmployeeMapper).updateFields(any());
        verify(productInfoMapper, never()).updateById(any(ProductInfo.class));
        verify(eventPublisher).publishEvent(any(AddrbookUpdatedEvent.class));
        verify(eventPublisher, never()).publishEvent(any(ProductResponsibleUpdatedEvent.class));
    }

    // ===== searchEmployees =====

    @Test
    void searchEmployees_delegatesCorrectly() {
        List<AddrbookEmployee> mockResult = Arrays.asList(
                buildEmployee("E001", "张三"),
                buildEmployee("E002", "张四")
        );
        when(addrbookEmployeeMapper.searchByKeyword("张", 20)).thenReturn(mockResult);

        List<AddrbookEmployee> result = addressBookService.searchEmployees("张", 20);

        assertThat(result).hasSize(2);
        verify(addrbookEmployeeMapper).searchByKeyword("张", 20);
    }

    // ===== listByOrg =====

    @Test
    void listByOrg_delegatesCorrectly() {
        List<AddrbookEmployee> mockResult = Collections.singletonList(
                buildEmployee("E001", "张三")
        );
        when(addrbookEmployeeMapper.selectByOrgCode("ORG_SZ_001")).thenReturn(mockResult);

        List<AddrbookEmployee> result = addressBookService.listByOrg("ORG_SZ_001");

        assertThat(result).hasSize(1);
        verify(addrbookEmployeeMapper).selectByOrgCode("ORG_SZ_001");
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
        e.setCreatedTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        e.setUpdatedTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        e.setDeleted(0);
        return e;
    }

    private ProductInfo buildProduct(String id, String productCode) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductCode(productCode);
        p.setProductName("产品-" + productCode);
        p.setProductCategory("LOAN");
        p.setStatus("ACTIVE");
        p.setSupportForSupportRequest(true);
        p.setResponsibleEmpIds(new ArrayList<>());
        p.setProductDeptOrgCode("ORG001");
        p.setCreatedBy("CREATOR01");
        p.setCreatedTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        p.setUpdatedTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        p.setDeleted(0);
        return p;
    }
}
