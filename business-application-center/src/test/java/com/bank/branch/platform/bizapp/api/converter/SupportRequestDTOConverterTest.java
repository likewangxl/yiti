package com.bank.branch.platform.bizapp.api.converter;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestListItemDTO;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.portal.api.ProductApi;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SupportRequestDTOConverter} 单元测试。
 * <p>
 * 验证：① custName/productName/supportDeptName 冗余字段正确填充；
 * ② deleted 字段已从 DTO 中移除；
 * ③ 批量转换使用批量 API；
 * ④ 各外部 API 不可用时优雅降级为空字符串。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SupportRequestDTOConverterTest {

    @Mock
    CustomerQueryApi customerQueryApi;

    @Mock
    ProductApi productApi;

    @Mock
    OrgApi orgApi;

    @InjectMocks
    SupportRequestDTOConverter converter;

    // -----------------------------------------------------------------------
    // 测试辅助方法
    // -----------------------------------------------------------------------

    /** 构造测试用 SupportRequest 实体 */
    private SupportRequest buildEntity(String id, String custId, String productId, String supportDeptId) {
        SupportRequest entity = new SupportRequest();
        entity.setId(id);
        entity.setRequestNo("SR202401010001");
        entity.setSubmitGroupId("GROUP001");
        entity.setCustId(custId);
        entity.setSourceTouchTaskId("TASK001");
        entity.setProductId(productId);
        entity.setSupportDeptId(supportDeptId);
        entity.setOtherDemand("补充说明");
        entity.setAssignedEmpId("EMP002");
        entity.setStatus("COMPLETED");
        entity.setBusinessKey("SUPPORT:" + id);
        entity.setProcessInstanceId("PROC002");
        entity.setOwnerOrgId("ORG001");
        entity.setCreatedBy("EMP001");
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedTime(LocalDateTime.now());
        entity.setDeleted(0);
        return entity;
    }

    /** 构造 CustomerDTO */
    private CustomerDTO buildCustomerDTO(String id, String custName) {
        CustomerDTO dto = new CustomerDTO();
        dto.setId(id);
        dto.setCustName(custName);
        return dto;
    }

    /** 构造 ProductDTO（使用 Builder，ProductDTO 是 @Value @Builder 不可变类） */
    private ProductDTO buildProductDTO(String id, String productName) {
        return ProductDTO.builder()
                .id(id)
                .productName(productName)
                .build();
    }

    /** 构造 OrgDTO */
    private OrgDTO buildOrgDTO(String orgCode, String orgName) {
        OrgDTO dto = new OrgDTO();
        dto.setOrgCode(orgCode);
        dto.setOrgName(orgName);
        return dto;
    }

    // -----------------------------------------------------------------------
    // 单条转换测试
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toDTO 应补齐 custName/productName/supportDeptName 且不输出 deleted 字段")
    void toDTO_populatesAllRedundantFields() {
        SupportRequest entity = buildEntity("sr001", "cust001", "prod001", "DEPT001");

        when(customerQueryApi.getCustomer("cust001"))
                .thenReturn(Optional.of(buildCustomerDTO("cust001", "腾讯云计算")));
        when(productApi.getProduct("prod001"))
                .thenReturn(Optional.of(buildProductDTO("prod001", "数字化转型专项贷")));
        when(orgApi.getOrg("DEPT001"))
                .thenReturn(buildOrgDTO("DEPT001", "公司金融部"));

        SupportRequestDTO dto = converter.toDTO(entity);

        assertThat(dto.getCustName()).isEqualTo("腾讯云计算");
        assertThat(dto.getProductName()).isEqualTo("数字化转型专项贷");
        assertThat(dto.getSupportDeptName()).isEqualTo("公司金融部");
        assertThat(dto.getId()).isEqualTo("sr001");
        // deleted 字段已删 — 编译期即不存在 getter，此断言保证 API 不泄露
        assertThat(BeanUtils.getPropertyDescriptors(SupportRequestDTO.class))
                .noneMatch(pd -> pd.getName().equals("deleted"));
    }

    @Test
    @DisplayName("toDTO 当客户不存在时 custName 为空字符串，不抛异常")
    void toDTO_custNotFound_returnsEmptyCustName() {
        SupportRequest entity = buildEntity("sr002", "ghost", "prod001", "DEPT001");

        when(customerQueryApi.getCustomer("ghost")).thenReturn(Optional.empty());
        when(productApi.getProduct("prod001"))
                .thenReturn(Optional.of(buildProductDTO("prod001", "测试产品")));
        when(orgApi.getOrg("DEPT001")).thenReturn(buildOrgDTO("DEPT001", "测试部门"));

        SupportRequestDTO dto = converter.toDTO(entity);

        assertThat(dto.getCustName()).isEmpty();
        assertThat(dto.getProductName()).isEqualTo("测试产品");
    }

    @Test
    @DisplayName("toDTO 当 OrgApi 抛异常时 supportDeptName 为空字符串，不传播异常")
    void toDTO_orgApiThrows_returnEmptyDeptName() {
        SupportRequest entity = buildEntity("sr003", "cust001", "prod001", "DEPT_ERR");

        when(customerQueryApi.getCustomer("cust001"))
                .thenReturn(Optional.of(buildCustomerDTO("cust001", "测试客户")));
        when(productApi.getProduct("prod001"))
                .thenReturn(Optional.of(buildProductDTO("prod001", "测试产品")));
        when(orgApi.getOrg("DEPT_ERR")).thenThrow(new RuntimeException("org not found"));

        SupportRequestDTO dto = converter.toDTO(entity);

        assertThat(dto.getSupportDeptName()).isEmpty();
        assertThat(dto.getCustName()).isEqualTo("测试客户");
    }

    // -----------------------------------------------------------------------
    // 批量转换测试
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toDTOList 批量转换应正确填充所有冗余字段")
    void toDTOList_batchQuery_populatesAllFields() {
        SupportRequest e1 = buildEntity("sr001", "cust001", "prod001", "DEPT001");
        SupportRequest e2 = buildEntity("sr002", "cust002", "prod002", "DEPT002");

        CustomerDTO c1 = buildCustomerDTO("cust001", "腾讯云计算");
        CustomerDTO c2 = buildCustomerDTO("cust002", "阿里巴巴");
        when(customerQueryApi.listCustomers(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Arrays.asList(c1, c2));

        ProductDTO p1 = buildProductDTO("prod001", "数字化专项贷");
        ProductDTO p2 = buildProductDTO("prod002", "科技创新贷");
        when(productApi.getProducts(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Arrays.asList(p1, p2));

        when(orgApi.getOrg("DEPT001")).thenReturn(buildOrgDTO("DEPT001", "公司金融部"));
        when(orgApi.getOrg("DEPT002")).thenReturn(buildOrgDTO("DEPT002", "投行部"));

        List<SupportRequestDTO> result = converter.toDTOList(Arrays.asList(e1, e2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCustName()).isEqualTo("腾讯云计算");
        assertThat(result.get(0).getProductName()).isEqualTo("数字化专项贷");
        assertThat(result.get(0).getSupportDeptName()).isEqualTo("公司金融部");
        assertThat(result.get(1).getCustName()).isEqualTo("阿里巴巴");
        assertThat(result.get(1).getSupportDeptName()).isEqualTo("投行部");
        // 防 N+1 回归：批量 API 必须只调用 1 次，单条查询必须从不调用
        verify(customerQueryApi, times(1)).listCustomers(org.mockito.ArgumentMatchers.anyList());
        verify(productApi, times(1)).getProducts(org.mockito.ArgumentMatchers.anyList());
        verify(customerQueryApi, never()).getCustomer(org.mockito.ArgumentMatchers.anyString());
        verify(productApi, never()).getProduct(org.mockito.ArgumentMatchers.anyString());
        // 注意：OrgApi 因无批量接口，批量路径仍会逐条 getOrg，此处不加 never 断言
    }

    @Test
    @DisplayName("toDTOList 空列表输入时返回空列表，不调用任何 API")
    void toDTOList_emptyInput_returnsEmptyList() {
        List<SupportRequestDTO> result = converter.toDTOList(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("toDTOList null 输入时返回空列表，不抛异常")
    void toDTOList_nullInput_returnsEmptyList() {
        assertThat(converter.toDTOList(null)).isEmpty();
    }

    // -----------------------------------------------------------------------
    // scenario 推断测试
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toListItem scenario 推断 — productId 非空 + supportDeptId 空 → A")
    void toListItem_scenarioA() {
        // custId/supportDeptId 均为 null，resolveCustName/resolveDeptName 直接返回空串，无需 mock
        SupportRequest entity = new SupportRequest();
        entity.setId("sr1");
        entity.setProductId("P001");
        entity.setSupportDeptId(null);
        // productId 非空，resolveProductName 会被调用，mock 使其返回空串
        when(productApi.getProduct("P001")).thenReturn(Optional.empty());

        SupportRequestListItemDTO dto = converter.toListItem(entity);
        assertThat(dto.getScenario()).isEqualTo("A");
    }

    @Test
    @DisplayName("toListItem scenario 推断 — 否则 → B")
    void toListItem_scenarioB() {
        // custId/productId 均为 null，resolveCustName/resolveProductName 直接返回空串，无需 mock
        SupportRequest entity = new SupportRequest();
        entity.setId("sr2");
        entity.setProductId(null);
        entity.setSupportDeptId("DEPT001");
        when(orgApi.getOrg("DEPT001")).thenReturn(buildOrgDTO("DEPT001", "测试部门"));

        SupportRequestListItemDTO dto = converter.toListItem(entity);
        assertThat(dto.getScenario()).isEqualTo("B");
    }

    @Test
    @DisplayName("toListItem 映射前端列表所需实体字段且不暴露内部字段")
    void toListItem_mapsRequiredEntityFields_withoutInternalFields() {
        SupportRequest entity = buildEntity("sr-required-fields", "cust001", "prod001", "DEPT001");

        SupportRequestListItemDTO dto = converter.toListItem(entity);

        assertThat(propertyValue(dto, "custId")).isEqualTo(entity.getCustId());
        assertThat(propertyValue(dto, "productId")).isEqualTo(entity.getProductId());
        assertThat(propertyValue(dto, "supportDeptId")).isEqualTo(entity.getSupportDeptId());
        assertThat(propertyValue(dto, "otherDemand")).isEqualTo(entity.getOtherDemand());
        assertThat(propertyValue(dto, "assignedEmpId")).isEqualTo(entity.getAssignedEmpId());
        assertThat(propertyValue(dto, "createdBy")).isEqualTo(entity.getCreatedBy());

        assertThat(BeanUtils.getPropertyDescriptors(SupportRequestListItemDTO.class))
                .extracting(descriptor -> descriptor.getName())
                .doesNotContain("deleted", "businessKey", "processInstanceId", "updatedBy",
                        "updatedTime", "ownerOrgId");
    }

    private Object propertyValue(SupportRequestListItemDTO dto, String propertyName) {
        return new BeanWrapperImpl(dto).getPropertyValue(propertyName);
    }
}
