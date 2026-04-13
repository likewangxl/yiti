package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.portal.api.dto.ProductListReqDTO;
import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.convert.ProductConverter;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import com.bank.branch.platform.portal.service.dto.ProductListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 产品资料库 Service
 * <p>D.1-D.7 的业务逻辑实现入口</p>
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductInfoMapper productInfoMapper;

    /**
     * D.3 查询所有支持中场支持的产品（active + 未删除）
     * <p>V1 不实现 5min Redis 缓存（spec 附录 B2 登记的 V2 待办）</p>
     *
     * @return 支持中场支持的产品简要列表
     */
    public List<ProductSimpleDTO> listSupportAvailable() {
        return productInfoMapper.listSupportAvailable().stream()
                .map(ProductConverter::toSimple)
                .collect(Collectors.toList());
    }

    /**
     * D.1 分页查询产品列表
     *
     * @param req   查询请求参数
     * @param scope 数据权限范围（common-security POJO）
     * @return 分页结果
     */
    public PageResult<ProductDTO> listProducts(ProductListReqDTO req, DataScopeContext scope) {
        ProductListQuery q = ProductListQuery.builder()
                .keyword(req.getKeyword())
                .category(req.getCategory())
                .status(req.getStatus() != null ? req.getStatus() : "ACTIVE")
                .productDeptOrgCode(req.getProductDeptOrgCode())
                .supportForSupportRequest(req.getSupportForSupportRequest())
                .offset(req.getOffset())
                .limit(req.getPageSize())
                .dataScope(scope)
                .build();

        long total = productInfoMapper.countProducts(q);
        if (total == 0) {
            return PageResult.of(req.getPageNo(), req.getPageSize(), 0L, Collections.emptyList());
        }
        List<ProductInfo> entities = productInfoMapper.listProducts(q);
        return PageResult.of(req.getPageNo(), req.getPageSize(), total,
                entities.stream().map(ProductConverter::toListItem).collect(Collectors.toList()));
    }
}
