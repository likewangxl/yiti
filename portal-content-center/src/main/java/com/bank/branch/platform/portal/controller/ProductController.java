package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.FileApi;
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
import com.bank.branch.platform.portal.service.UserProductRelationService;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final UserProductRelationService userProductRelationService;
    private final BizScopeApi bizScopeApi;
    private final CurrentUserApi currentUserApi;
    private final OrgApi orgApi;
    private final FileApi fileApi;

    /** D.1 分页查询产品列表 */
    @GetMapping
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.LIST)
    public ResponseWrapper<ProductDTO> listProducts(@Valid ProductQueryReqDTO req) {
        PageResult<ProductInfo> entityPage = productService.listProducts(req);
        List<ProductInfo> entities = entityPage.getRecords();
        Map<String, List<String>> responsibleMap = resolveResponsibleEmpIds(entities);
        Map<String, String> responsibleNameMap = resolveResponsibleEmpNames(responsibleMap);
        List<ProductDTO> base = entities.stream()
                .map(entity -> toProductDTO(entity, responsibleMap, responsibleNameMap))
                .collect(Collectors.toList());
        // 批量解析产品部门机构名称 + 附件文件名（各单次取数，避免逐行 N+1），回填供列表展示
        Map<String, String> orgNameMap = resolveProductDeptOrgNames(base);
        Map<String, String> fileNameMap = resolveAttachmentFileNames(base);
        List<ProductDTO> dtos = base.stream()
                .map(dto -> dto.toBuilder()
                        .productDeptOrgName(dto.getProductDeptOrgCode() == null ? null
                                : orgNameMap.get(dto.getProductDeptOrgCode()))
                        .fileName(dto.getFileObjectId() == null ? null
                                : fileNameMap.get(dto.getFileObjectId()))
                        .build())
                .collect(Collectors.toList());
        return ResponseWrapper.page(PageResult.of(entityPage.getPageNo(), entityPage.getPageSize(), entityPage.getTotal(), dtos));
    }

    /**
     * 批量解析附件 fileObjectId → fileName 映射（单次 FileApi 取数，避免 N+1）。
     * 无附件或 FileApi 返回空时返回空映射。
     */
    private Map<String, String> resolveAttachmentFileNames(List<ProductDTO> dtos) {
        List<String> fileIds = dtos.stream()
                .map(ProductDTO::getFileObjectId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (fileIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Map<String, String> names = fileApi.getFileNames(fileIds);
        return names != null ? names : java.util.Collections.emptyMap();
    }

    /**
     * 批量解析产品部门 orgCode → orgName 映射（单次 OrgApi 取数，避免 N+1）。
     * 入参无有效编码或 OrgApi 返回 null 时返回空映射，调用方按缺失处理（列表列显示空）。
     */
    private Map<String, String> resolveProductDeptOrgNames(List<ProductDTO> dtos) {
        java.util.Set<String> codes = dtos.stream()
                .map(ProductDTO::getProductDeptOrgCode)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());
        if (codes.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        List<OrgDTO> orgs = orgApi.getOrgsByCodes(codes);
        if (orgs == null || orgs.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return orgs.stream().collect(Collectors.toMap(OrgDTO::getOrgCode, OrgDTO::getOrgName, (a, b) -> a));
    }

    /** 批量读取当前页所有产品的负责人关系，避免逐产品查询。 */
    private Map<String, List<String>> resolveResponsibleEmpIds(List<ProductInfo> entities) {
        List<String> productIds = entities.stream()
                .map(ProductInfo::getId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());
        Map<String, List<String>> result = userProductRelationService.mapUserIdsByProductIds(productIds);
        return result != null ? result : Collections.emptyMap();
    }

    /** 批量解析负责人姓名，姓名缺失时由调用方回退显示工号。 */
    private Map<String, String> resolveResponsibleEmpNames(Map<String, List<String>> responsibleMap) {
        if (responsibleMap == null || responsibleMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> empIds = responsibleMap.values().stream()
                .flatMap(List::stream)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (empIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ResponsibleEmpDTO> emps = addrbookQueryService.listResponsibleEmps(List.copyOf(empIds));
        if (emps == null || emps.isEmpty()) {
            return Collections.emptyMap();
        }
        return emps.stream().collect(Collectors.toMap(
                ResponsibleEmpDTO::getEmpId, ResponsibleEmpDTO::getEmpName, (a, b) -> a));
    }

    /** 将关系表负责人和批量姓名映射回单个产品 DTO。 */
    private ProductDTO toProductDTO(ProductInfo entity,
                                    Map<String, List<String>> responsibleMap,
                                    Map<String, String> responsibleNameMap) {
        List<String> empIds = responsibleMap.getOrDefault(entity.getId(), Collections.emptyList());
        String names = empIds.stream()
                .map(id -> responsibleNameMap.getOrDefault(id, id))
                .collect(Collectors.joining("、"));
        return ProductConverter.toDTO(entity, empIds).toBuilder()
                .responsibleEmpNames(names)
                .build();
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
        ProductInfo entity = productService.getProduct(id);
        Map<String, List<String>> responsibleMap = userProductRelationService
                .mapUserIdsByProductIds(Collections.singletonList(entity.getId()));
        Map<String, String> responsibleNameMap = resolveResponsibleEmpNames(responsibleMap);
        return ResponseWrapper.success(toProductDTO(entity, responsibleMap, responsibleNameMap));
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
