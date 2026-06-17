package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.portal.api.dto.ProductCreateReqDTO;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.api.dto.ResponsibleEmpDTO;
import com.bank.branch.platform.portal.controller.dto.product.ProductQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.product.ProductUpdateReqDTO;
import com.bank.branch.platform.portal.convert.ProductConverter;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.service.AddrbookQueryService;
import com.bank.branch.platform.portal.service.ProductExportService;
import com.bank.branch.platform.portal.service.ProductService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 产品资料库 REST Controller (D.1-D.7)
 * <p>Service 层返回实体，Controller 层负责 Entity -> DTO 转换。</p>
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductExportService productExportService;
    private final AddrbookQueryService addrbookQueryService;
    private final BizScopeApi bizScopeApi;
    private final CurrentUserApi currentUserApi;

    /** D.1 分页查询产品列表 */
    @GetMapping
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.LIST)
    public ResponseWrapper<ProductDTO> listProducts(@Valid ProductQueryReqDTO req) {
        PageResult<ProductInfo> entityPage = productService.listProducts(req);
        List<ProductDTO> dtos = entityPage.getRecords().stream()
                .map(ProductConverter::toDTO)
                .map(this::withResponsibleEmpNames)
                .collect(Collectors.toList());
        return ResponseWrapper.page(PageResult.of(entityPage.getPageNo(), entityPage.getPageSize(), entityPage.getTotal(), dtos));
    }

    /**
     * 按 responsibleEmpIds 批量解析负责人姓名，填充 responsibleEmpNames（顿号分隔）。
     * 姓名缺失（如离职/未维护）时回退展示工号，避免空白。
     */
    private ProductDTO withResponsibleEmpNames(ProductDTO dto) {
        List<String> empIds = dto.getResponsibleEmpIds();
        if (empIds == null || empIds.isEmpty()) {
            return dto.toBuilder().responsibleEmpNames("").build();
        }
        Map<String, String> nameMap = addrbookQueryService.listResponsibleEmps(empIds).stream()
                .collect(Collectors.toMap(ResponsibleEmpDTO::getEmpId, ResponsibleEmpDTO::getEmpName, (a, b) -> a));
        String names = empIds.stream()
                .map(id -> nameMap.getOrDefault(id, id))
                .collect(Collectors.joining("、"));
        return dto.toBuilder().responsibleEmpNames(names).build();
    }

    /** D.3 查询支持中场支持的产品 */
    @GetMapping("/support-available")
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.READ)
    public ResponseWrapper<List<ProductSimpleDTO>> getSupportAvailable() {
        List<ProductInfo> entities = productService.listSupportAvailable();
        return ResponseWrapper.success(entities.stream().map(ProductConverter::toSimple).collect(Collectors.toList()));
    }

    /**
     * D.7 产品导出（V1 同步导出 ≤5000 行）
     */
    @GetMapping("/export")
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.EXPORT)
    public void exportProducts(
            @jakarta.annotation.Nullable String keyword,
            @jakarta.annotation.Nullable String category,
            @jakarta.annotation.Nullable String status,
            HttpServletResponse response) throws IOException {
        String empId = currentUserApi.getCurrentEmpId();
        String filename = "product_export_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        productExportService.exportToStream(keyword, category, status, response.getOutputStream(), empId);
    }

    /** D.2 产品详情 */
    @GetMapping("/{id:[A-Za-z0-9_-]{1,64}}")
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.READ)
    public ResponseWrapper<ProductDTO> getProduct(@PathVariable String id) {
        return ResponseWrapper.success(ProductConverter.toDTO(productService.getProduct(id)));
    }

    /** D.4 新增产品 */
    @PostMapping
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.WRITE)
    public ResponseWrapper<String> createProduct(@Valid @RequestBody ProductCreateReqDTO req) {
        return ResponseWrapper.success(productService.createProduct(req).getId());
    }

    /**
     * D.5 编辑产品
     */
    @PutMapping("/{id:[A-Za-z0-9_-]{1,64}}")
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.WRITE)
    public ResponseWrapper<Void> updateProduct(@PathVariable String id, @Valid @RequestBody ProductUpdateReqDTO req) {
        productService.updateProduct(id, req);
        return ResponseWrapper.success(null);
    }

    /**
     * D.6 删除产品（高危操作）
     */
    @DeleteMapping("/{id:[A-Za-z0-9_-]{1,64}}")
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.DELETE)
    public ResponseWrapper<Void> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ResponseWrapper.success(null);
    }

}
