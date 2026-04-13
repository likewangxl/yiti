package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.api.dto.ProductCreateReqDTO;
import com.bank.branch.platform.portal.controller.dto.product.ProductUpdateReqDTO;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.event.ProductResponsibleUpdatedEvent;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ProductService 单元测试 —— 纯 JUnit 5 + Mockito，无需 Spring 上下文
 *
 * <p>TDD RED-GREEN 闭环：先写测试（Red），再实现 Service（Green）。
 * 涵盖 CRUD、双向同步、缓存、事件发布等核心场景。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductInfoMapper productInfoMapper;

    @Mock
    AddrbookEmployeeMapper addrbookEmployeeMapper;

    @Mock
    CurrentUserApi currentUserApi;

    @Mock
    BizScopeApi bizScopeApi;

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ProductService productService;

    // ===== 1. createProduct_success =====

    /**
     * 正常创建产品：校验 insert 被调用、事件被发布、返回实体包含正确字段
     */
    @Test
    void createProduct_success() {
        // given
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("PRD001");
        req.setProductName("测试产品");
        req.setProductCategory("LOAN");
        req.setDescription("测试描述");
        req.setSupportForSupportRequest(true);
        req.setProductDeptOrgCode("ORG001");
        req.setFileObjectId("file-001");
        req.setResponsibleEmpIds(Arrays.asList("E001", "E002"));

        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(productInfoMapper.selectByProductCode("PRD001")).thenReturn(null);
        when(addrbookEmployeeMapper.countActiveByEmpIds(Arrays.asList("E001", "E002"))).thenReturn(2);

        // 为 addrbook 同步准备 mock（按字典序 E001, E002 处理）
        AddrbookEmployee emp1 = buildEmployee("E001", new ArrayList<>());
        AddrbookEmployee emp2 = buildEmployee("E002", new ArrayList<>());
        when(addrbookEmployeeMapper.selectByEmpId("E001")).thenReturn(emp1);
        when(addrbookEmployeeMapper.selectByEmpId("E002")).thenReturn(emp2);
        when(addrbookEmployeeMapper.updateResponsibleProductsWithOptimisticLock(
                anyString(), any(), any(), anyString())).thenReturn(1);

        // when
        ProductInfo result = productService.createProduct(req);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getProductCode()).isEqualTo("PRD001");
        assertThat(result.getProductName()).isEqualTo("测试产品");
        assertThat(result.getCreatedBy()).isEqualTo("OPERATOR01");
        assertThat(result.getId()).isNotBlank();
        assertThat(result.getStatus()).isEqualTo("ACTIVE");

        verify(productInfoMapper).insert(any(ProductInfo.class));
        verify(eventPublisher).publishEvent(any(ProductResponsibleUpdatedEvent.class));
    }

    // ===== 2. createProduct_duplicateCode =====

    /**
     * 产品代码已存在时应抛出 PRODUCT_CODE_DUPLICATE 异常
     */
    @Test
    void createProduct_duplicateCode() {
        // given
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("EXISTING_CODE");
        req.setProductName("重复产品");
        req.setProductCategory("LOAN");
        req.setSupportForSupportRequest(false);
        req.setProductDeptOrgCode("ORG001");

        when(productInfoMapper.selectByProductCode("EXISTING_CODE")).thenReturn(new ProductInfo());

        // when & then
        assertThatThrownBy(() -> productService.createProduct(req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo(PortalErrorCode.PRODUCT_CODE_DUPLICATE.getCode());
                });

        verify(productInfoMapper, never()).insert(any());
    }

    // ===== 3. createProduct_resignedEmployee =====

    /**
     * 传入的负责人中有离职员工时应抛出 EMPLOYEE_RESIGNED 异常
     */
    @Test
    void createProduct_resignedEmployee() {
        // given
        ProductCreateReqDTO req = new ProductCreateReqDTO();
        req.setProductCode("PRD002");
        req.setProductName("产品2");
        req.setProductCategory("DEPOSIT");
        req.setSupportForSupportRequest(true);
        req.setProductDeptOrgCode("ORG002");
        req.setResponsibleEmpIds(Arrays.asList("E001", "E002", "E003"));

        when(productInfoMapper.selectByProductCode("PRD002")).thenReturn(null);
        // 只有2人在职，但传入了3个empId
        when(addrbookEmployeeMapper.countActiveByEmpIds(Arrays.asList("E001", "E002", "E003"))).thenReturn(2);

        // when & then
        assertThatThrownBy(() -> productService.createProduct(req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo(PortalErrorCode.EMPLOYEE_RESIGNED.getCode());
                });

        verify(productInfoMapper, never()).insert(any());
    }

    // ===== 4. updateProduct_success =====

    /**
     * 正常更新产品名称和描述（不变更负责人），验证 updateById 被调用
     */
    @Test
    void updateProduct_success() {
        // given
        String productId = "prod-001";
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setProductName("更新后名称");
        req.setDescription("更新后描述");

        ProductInfo existing = buildProduct(productId, "PRD001", Arrays.asList("E001"));
        when(productInfoMapper.selectByIdForUpdate(productId)).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");

        // when
        ProductInfo result = productService.updateProduct(productId, req);

        // then
        assertThat(result).isNotNull();
        verify(productInfoMapper).updateById(any(ProductInfo.class));
    }

    // ===== 5. updateProduct_notFound =====

    /**
     * 更新不存在的产品时应抛出 PRODUCT_NOT_FOUND 异常
     */
    @Test
    void updateProduct_notFound() {
        // given
        String productId = "nonexistent";
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setProductName("不存在的产品");

        when(productInfoMapper.selectByIdForUpdate(productId)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> productService.updateProduct(productId, req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo(PortalErrorCode.PRODUCT_NOT_FOUND.getCode());
                });

        verify(productInfoMapper, never()).updateById(any());
    }

    // ===== 6. updateProduct_responsibleSync =====

    /**
     * 更新产品负责人时验证 addrbook 双向同步：
     * 原 [E001, E002] -> 新 [E002, E003]，应移除 E001、添加 E003
     */
    @Test
    void updateProduct_responsibleSync() {
        // given
        String productId = "prod-002";
        ProductUpdateReqDTO req = new ProductUpdateReqDTO();
        req.setResponsibleEmpIds(Arrays.asList("E002", "E003"));

        ProductInfo existing = buildProduct(productId, "PRD002", Arrays.asList("E001", "E002"));
        when(productInfoMapper.selectByIdForUpdate(productId)).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");
        when(addrbookEmployeeMapper.countActiveByEmpIds(Arrays.asList("E002", "E003"))).thenReturn(2);

        // removed: E001（原有，新列表无）, added: E003（原无，新列表有）
        AddrbookEmployee emp1 = buildEmployee("E001", new ArrayList<>(Collections.singletonList(productId)));
        AddrbookEmployee emp3 = buildEmployee("E003", new ArrayList<>());
        when(addrbookEmployeeMapper.selectByEmpId("E001")).thenReturn(emp1);
        when(addrbookEmployeeMapper.selectByEmpId("E003")).thenReturn(emp3);
        when(addrbookEmployeeMapper.updateResponsibleProductsWithOptimisticLock(
                anyString(), any(), any(), anyString())).thenReturn(1);

        // when
        ProductInfo result = productService.updateProduct(productId, req);

        // then: 验证 addrbook 同步调用
        verify(addrbookEmployeeMapper).selectByEmpId("E001");
        verify(addrbookEmployeeMapper).selectByEmpId("E003");
        verify(addrbookEmployeeMapper, times(2)).updateResponsibleProductsWithOptimisticLock(
                anyString(), any(), any(), anyString());

        // 验证发布事件
        verify(eventPublisher).publishEvent(any(ProductResponsibleUpdatedEvent.class));
    }

    // ===== 7. deleteProduct_success_cleansReferences =====

    /**
     * 删除产品时验证软删除 + 清理所有员工引用 + 发布事件（before 有值，after 为空）
     */
    @Test
    void deleteProduct_success_cleansReferences() {
        // given
        String productId = "prod-003";
        ProductInfo existing = buildProduct(productId, "PRD003", Arrays.asList("E001", "E002"));
        when(productInfoMapper.selectById(productId)).thenReturn(existing);
        when(currentUserApi.getCurrentEmpId()).thenReturn("OPERATOR01");

        AddrbookEmployee emp1 = buildEmployee("E001", new ArrayList<>(Collections.singletonList(productId)));
        AddrbookEmployee emp2 = buildEmployee("E002", new ArrayList<>(Collections.singletonList(productId)));
        when(addrbookEmployeeMapper.selectByEmpId("E001")).thenReturn(emp1);
        when(addrbookEmployeeMapper.selectByEmpId("E002")).thenReturn(emp2);
        when(addrbookEmployeeMapper.updateResponsibleProductsWithOptimisticLock(
                anyString(), any(), any(), anyString())).thenReturn(1);

        // when
        productService.deleteProduct(productId);

        // then
        verify(productInfoMapper).softDeleteById(productId, "OPERATOR01");
        // 验证清理 E001 和 E002 的引用
        verify(addrbookEmployeeMapper, times(2)).updateResponsibleProductsWithOptimisticLock(
                anyString(), any(), any(), anyString());
        // 验证发布事件（before=[E001,E002], after=[]）
        ArgumentCaptor<ProductResponsibleUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(ProductResponsibleUpdatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ProductResponsibleUpdatedEvent event = eventCaptor.getValue();
        assertThat(event.getAfterEmpIds()).isEmpty();
        assertThat(event.getBeforeEmpIds()).containsExactlyInAnyOrder("E001", "E002");
    }

    // ===== 8. deleteProduct_notFound =====

    /**
     * 删除不存在的产品时应抛出 PRODUCT_NOT_FOUND 异常
     */
    @Test
    void deleteProduct_notFound() {
        // given
        when(productInfoMapper.selectById("nonexistent")).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> productService.deleteProduct("nonexistent"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo(PortalErrorCode.PRODUCT_NOT_FOUND.getCode());
                });

        verify(productInfoMapper, never()).softDeleteById(anyString(), anyString());
    }

    // ===== 9. listSupportAvailable_cached =====

    /**
     * 缓存命中时不应查询数据库，缓存未命中时查询数据库并回填缓存
     */
    @Test
    @SuppressWarnings("unchecked")
    void listSupportAvailable_cached() {
        // given
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        List<ProductInfo> dbResult = Arrays.asList(
                buildProduct("p1", "PRD001", Collections.emptyList()),
                buildProduct("p2", "PRD002", Collections.emptyList())
        );

        // 第一次调用：缓存为 null，查询数据库
        when(valueOps.get("portal:product:support-available")).thenReturn(null, (Object) dbResult);
        when(productInfoMapper.listSupportAvailable()).thenReturn(dbResult);

        // when: 第一次调用
        List<ProductInfo> result1 = productService.listSupportAvailable();

        // then: 应查询数据库并回填缓存
        assertThat(result1).hasSize(2);
        verify(productInfoMapper, times(1)).listSupportAvailable();
        verify(valueOps).set(eq("portal:product:support-available"), eq(dbResult), any());

        // when: 第二次调用（缓存命中）
        List<ProductInfo> result2 = productService.listSupportAvailable();

        // then: 不应再查询数据库（仍然只调用了1次）
        assertThat(result2).hasSize(2);
        verify(productInfoMapper, times(1)).listSupportAvailable();
    }

    // ===== 10. getProduct_notFound =====

    /**
     * 按 ID 查询不存在的产品时应抛出 PRODUCT_NOT_FOUND 异常
     */
    @Test
    void getProduct_notFound() {
        // given
        when(productInfoMapper.selectById("nonexistent")).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> productService.getProduct("nonexistent"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo(PortalErrorCode.PRODUCT_NOT_FOUND.getCode());
                });
    }

    // ===== 辅助方法 =====

    /**
     * 构建测试用 ProductInfo 实体
     */
    private ProductInfo buildProduct(String id, String productCode, List<String> responsibleEmpIds) {
        ProductInfo product = new ProductInfo();
        product.setId(id);
        product.setProductCode(productCode);
        product.setProductName("产品-" + productCode);
        product.setProductCategory("LOAN");
        product.setStatus("ACTIVE");
        product.setResponsibleEmpIds(responsibleEmpIds);
        product.setProductDeptOrgCode("ORG001");
        product.setCreatedBy("CREATOR01");
        product.setCreatedTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        product.setUpdatedTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        product.setDeleted(0);
        return product;
    }

    /**
     * 构建测试用 AddrbookEmployee 实体
     */
    private AddrbookEmployee buildEmployee(String empId, List<String> responsibleProductIds) {
        AddrbookEmployee emp = new AddrbookEmployee();
        emp.setEmpId(empId);
        emp.setEmpName("员工-" + empId);
        emp.setStatus("ACTIVE");
        emp.setResponsibleProductIds(responsibleProductIds != null ? responsibleProductIds : new ArrayList<>());
        emp.setUpdatedTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        emp.setDeleted(0);
        return emp;
    }
}
