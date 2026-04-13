package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.portal.adapter.DataScopeAdapter;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.portal.api.dto.ProductListReqDTO;
import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 产品资料库 REST Controller (D.1-D.7)
 *
 * <p>路由顺序约束：/support-available 必须在 /{id} 之前声明，
 * 否则 Spring MVC 会把 "support-available" 匹配为 {id} 路径变量。</p>
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final BizScopeApi bizScopeApi;
    private final CurrentUserApi currentUserApi;

    /**
     * D.1 分页查询产品列表
     *
     * @param req 查询请求参数（含分页 + 筛选条件）
     * @return 分页结果（走 ResponseWrapper.page，JSON 路径 $.page）
     */
    @GetMapping
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.LIST)
    public ResponseWrapper<ProductDTO> listProducts(@Valid ProductListReqDTO req) {
        String empId = currentUserApi.getCurrentEmpId();
        var authScope = bizScopeApi.buildScopeContext(empId, BizType.PRODUCT, BizAction.LIST);
        DataScopeContext scope = DataScopeAdapter.fromAuthRecord(authScope);
        PageResult<ProductDTO> result = productService.listProducts(req, scope);
        return ResponseWrapper.page(result);
    }

    /**
     * D.3 查询支持中场支持的产品
     *
     * @return 支持产品简要列表
     */
    @GetMapping("/support-available")
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.READ)
    public ResponseWrapper<List<ProductSimpleDTO>> getSupportAvailable() {
        return ResponseWrapper.success(productService.listSupportAvailable());
    }

    // D.2 detail / D.4-D.7 will be added in later tasks
    // IMPORTANT: when adding @GetMapping("/{id}"), declare it AFTER /support-available
}
