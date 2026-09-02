package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserDirectoryApi;
import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeSelfUpdateReqDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 通讯录新数据源与自助更新服务测试。 */
@ExtendWith(MockitoExtension.class)
class AddressBookServiceTest {

    @Mock UserDirectoryApi userDirectoryApi;
    @Mock UserProductRelationService userProductRelationService;
    @Mock ProductInfoMapper productInfoMapper;
    @Mock CurrentUserApi currentUserApi;
    @Mock AuditApi auditApi;
    @InjectMocks AddressBookService addressBookService;

    @Test
    void listEmployeesDelegatesToUserDirectoryApi() {
        EmployeeQueryReqDTO req = new EmployeeQueryReqDTO();
        req.setKeyword("张");
        req.setOrgCode("ORG001");
        req.setPageNo(2);
        req.setPageSize(30);
        PageResult<UserDirectoryDTO> page = PageResult.of(2, 30, 1L, List.of(employee("U1", "张三")));
        when(userDirectoryApi.pageActiveUsers("张", "ORG001", 2, 30)).thenReturn(page);

        assertThat(addressBookService.listEmployees(req)).isSameAs(page);
        verify(userDirectoryApi).pageActiveUsers("张", "ORG001", 2, 30);
    }

    @Test
    void updateCurrentUserPreservesOmittedContactsAndReplacesRelations() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("U1");
        UserDirectoryDTO current = employee("U1", "张三");
        current.setMobile("13800000000");
        current.setEmail("old@example.com");
        when(userDirectoryApi.getEmployee("U1")).thenReturn(current);
        when(userDirectoryApi.updateCurrentUserContact("13800000000", "old@example.com")).thenReturn(current);
        ProductInfo product = new ProductInfo();
        product.setId("P1");
        product.setProductCode("P-001");
        product.setStatus("ACTIVE");
        product.setSupportForSupportRequest(true);
        when(productInfoMapper.listByIds(List.of("P1"))).thenReturn(List.of(product));

        EmployeeSelfUpdateReqDTO req = new EmployeeSelfUpdateReqDTO();
        req.setResponsibleProductIds(List.of(" P1", "P1"));
        addressBookService.updateCurrentUser(req);

        verify(userDirectoryApi).updateCurrentUserContact("13800000000", "old@example.com");
        verify(userProductRelationService).replaceProductsForUser("U1", List.of("P1"), "U1");
    }

    @Test
    void updateCurrentUserRejectsMoreThanTwentyProductsBeforeWriting() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("U1");
        when(userDirectoryApi.getEmployee("U1")).thenReturn(employee("U1", "张三"));
        EmployeeSelfUpdateReqDTO req = new EmployeeSelfUpdateReqDTO();
        req.setResponsibleProductIds(java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> "P" + i).toList());

        assertThatThrownBy(() -> addressBookService.updateCurrentUser(req))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(PortalErrorCode.PRODUCT_RESPONSIBLE_LIMIT.getCode());
        verify(userDirectoryApi, never()).updateCurrentUserContact(any(), any());
        verify(userProductRelationService, never()).replaceProductsForUser(any(), any(), any());
    }

    private UserDirectoryDTO employee(String id, String name) {
        UserDirectoryDTO dto = new UserDirectoryDTO();
        dto.setEmpId(id);
        dto.setEmpName(name);
        dto.setStatus("ACTIVE");
        return dto;
    }
}
