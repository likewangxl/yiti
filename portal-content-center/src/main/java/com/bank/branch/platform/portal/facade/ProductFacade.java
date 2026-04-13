package com.bank.branch.platform.portal.facade;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.api.ProductApi;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.portal.convert.ProductConverter;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import com.bank.branch.platform.portal.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 产品资料 Facade 实现
 *
 * <p>实现 {@link ProductApi} 接口，负责将 ProductService / ProductInfoMapper 返回的实体
 * 转换为跨模块 DTO。所有方法均为只读查询，不提供写操作。</p>
 *
 * <p>异常处理约定：
 * <ul>
 *   <li>单条查询不存在时返回 {@code Optional.empty()}，不抛异常</li>
 *   <li>批量查询不存在的项不包含在返回列表中，不报错</li>
 *   <li>系统异常直接抛出 RuntimeException，由调用方降级</li>
 * </ul></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductFacade implements ProductApi {

    private final ProductService productService;
    private final ProductInfoMapper productInfoMapper;

    /**
     * 获取产品详情。
     * 捕获 BizException（产品不存在）返回 Optional.empty()，符合 API 契约。
     *
     * @param productId 产品ID
     * @return 产品详情 DTO，不存在时返回 Optional.empty()
     */
    @Override
    public Optional<ProductDTO> getProduct(String productId) {
        try {
            ProductInfo entity = productService.getProduct(productId);
            return Optional.ofNullable(ProductConverter.toDTO(entity));
        } catch (BizException e) {
            log.debug("[ProductFacade.getProduct] 产品不存在, productId={}", productId);
            return Optional.empty();
        }
    }

    /**
     * 批量获取产品信息。
     * 使用 Mapper 直接批量查询，避免 N+1。
     *
     * @param productIds 产品ID列表
     * @return 产品DTO列表（不存在的产品不返回）
     */
    @Override
    public List<ProductDTO> getProducts(List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProductInfo> entities = productInfoMapper.listByIds(productIds);
        return entities.stream()
                .map(ProductConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 查询支持中场支持的产品列表。
     * 委托 ProductService（含 Cache-Aside 缓存策略）。
     *
     * @return 所有支持中场支持的产品 DTO 列表
     */
    @Override
    public List<ProductDTO> listSupportAvailableProducts() {
        List<ProductInfo> entities = productService.listSupportAvailable();
        return entities.stream()
                .map(ProductConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 按部门查询产品。
     * 直接通过 Mapper 按 product_dept_org_code 查询。
     *
     * @param productDeptOrgCode 产品部门机构编码
     * @return 该部门维护的全部产品列表
     */
    @Override
    public List<ProductDTO> listProductsByDept(String productDeptOrgCode) {
        List<ProductInfo> entities = productInfoMapper.listByProductDeptOrgCode(productDeptOrgCode);
        return entities.stream()
                .map(ProductConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 查询产品负责人工号列表。
     * 捕获 BizException（产品不存在）返回空列表。
     *
     * @param productId 产品ID
     * @return 负责人工号列表（可能为空）
     */
    @Override
    public List<String> getProductResponsibleEmpIds(String productId) {
        try {
            ProductInfo entity = productService.getProduct(productId);
            List<String> empIds = entity.getResponsibleEmpIds();
            return empIds != null ? empIds : Collections.emptyList();
        } catch (BizException e) {
            log.debug("[ProductFacade.getProductResponsibleEmpIds] 产品不存在, productId={}", productId);
            return Collections.emptyList();
        }
    }
}
