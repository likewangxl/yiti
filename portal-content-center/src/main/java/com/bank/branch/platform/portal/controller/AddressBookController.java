package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeDetailDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeSearchDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeUpdateReqDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.ProductBriefDTO;
import com.bank.branch.platform.portal.convert.EmployeeConverter;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.service.AddressBookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通讯录 REST Controller (C.1-C.4)
 *
 * <p>Service 层返回实体，Controller 层负责 Entity -> DTO 转换。
 * 所有接口都需要通讯录域的业务权限（@BizAuth ADDRBOOK）。</p>
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class AddressBookController {

    private final AddressBookService addressBookService;
    private final CurrentUserApi currentUserApi;

    /**
     * C.1 通讯录列表（分页）
     */
    @GetMapping
    @BizAuth(bizType = BizType.ADDRBOOK, action = BizAction.LIST)
    public ResponseWrapper<EmployeeDetailDTO> listEmployees(@Valid EmployeeQueryReqDTO req) {
        PageResult<AddrbookEmployee> entityPage = addressBookService.listEmployees(req);
        List<EmployeeDetailDTO> dtos = entityPage.getRecords().stream()
                .map(this::toListDTO)
                .collect(Collectors.toList());
        return ResponseWrapper.page(PageResult.of(
                entityPage.getPageNo(), entityPage.getPageSize(),
                entityPage.getTotal(), dtos));
    }

    /**
     * C.4 模糊搜索（员工选择器）
     *
     * <p>注意：此端点必须在 /{empId} 之前声明，避免 /search 被路径变量匹配。</p>
     */
    @GetMapping("/search")
    @BizAuth(bizType = BizType.ADDRBOOK, action = BizAction.READ)
    public ResponseWrapper<List<EmployeeSearchDTO>> searchEmployees(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        List<AddrbookEmployee> entities = addressBookService.searchEmployees(keyword, Math.min(limit, 50));
        List<EmployeeSearchDTO> dtos = entities.stream()
                .map(EmployeeConverter::toSearchDTO)
                .collect(Collectors.toList());
        return ResponseWrapper.success(dtos);
    }

    /**
     * C.2 员工详情
     */
    @GetMapping("/{empId}")
    @BizAuth(bizType = BizType.ADDRBOOK, action = BizAction.READ)
    public ResponseWrapper<EmployeeDetailDTO> getEmployee(@PathVariable String empId) {
        AddrbookEmployee entity = addressBookService.getEmployee(empId);
        EmployeeDetailDTO dto = toDetailDTO(entity);
        return ResponseWrapper.success(dto);
    }

    /**
     * C.3 编辑员工信息
     */
    @PutMapping("/{empId}")
    @BizAuth(bizType = BizType.ADDRBOOK, action = BizAction.WRITE)
    public ResponseWrapper<Void> updateEmployee(@PathVariable String empId,
                                                 @Valid @RequestBody EmployeeUpdateReqDTO req) {
        addressBookService.updateEmployee(empId, req);
        return ResponseWrapper.success(null);
    }

    /**
     * 列表项 DTO 转换（脱敏手机号 + 基本字段）
     */
    private EmployeeDetailDTO toListDTO(AddrbookEmployee entity) {
        EmployeeDetailDTO dto = new EmployeeDetailDTO();
        dto.setEmpId(entity.getEmpId());
        dto.setEmpName(entity.getEmpName());
        dto.setMobile(EmployeeConverter.maskMobile(entity.getMobile()));
        dto.setEmail(entity.getEmail());
        dto.setOrgCode(entity.getOrgCode());
        dto.setOrgName(entity.getOrgName());
        dto.setPosition(entity.getPosition());
        dto.setSelfDesc(entity.getSelfDesc());
        dto.setResponsibleProductIds(entity.getResponsibleProductIds());
        dto.setStatus(entity.getStatus());
        dto.setUpdatedTime(entity.getUpdatedTime());
        return dto;
    }

    /**
     * 详情 DTO 转换（含维护人信息、编辑权限、负责产品列表）
     */
    private EmployeeDetailDTO toDetailDTO(AddrbookEmployee entity) {
        EmployeeDetailDTO dto = new EmployeeDetailDTO();
        dto.setEmpId(entity.getEmpId());
        dto.setEmpName(entity.getEmpName());
        dto.setMobile(EmployeeConverter.maskMobile(entity.getMobile()));
        dto.setEmail(entity.getEmail());
        dto.setOrgCode(entity.getOrgCode());
        dto.setOrgName(entity.getOrgName());
        dto.setPosition(entity.getPosition());
        dto.setSelfDesc(entity.getSelfDesc());
        dto.setResponsibleProductIds(entity.getResponsibleProductIds());
        dto.setStatus(entity.getStatus());
        dto.setUpdatedTime(entity.getUpdatedTime());

        // 维护人信息
        dto.setMaintainerEmpId(entity.getMaintainerEmpId());
        if (entity.getMaintainerEmpId() != null) {
            try {
                AddrbookEmployee maintainer = addressBookService.getEmployee(entity.getMaintainerEmpId());
                dto.setMaintainerEmpName(maintainer.getEmpName());
            } catch (Exception e) {
                dto.setMaintainerEmpName(null);
            }
        }

        // 当前用户是否可编辑
        String currentEmpId = currentUserApi.getCurrentEmpId();
        dto.setCanEdit(currentEmpId != null && currentEmpId.equals(entity.getEmpId()));

        // 负责产品详细列表
        List<String> productIds = entity.getResponsibleProductIds();
        if (productIds != null && !productIds.isEmpty()) {
            List<ProductInfo> products = addressBookService.listProductsByIds(productIds);
            List<ProductBriefDTO> productBriefs = products.stream()
                    .map(this::toProductBrief)
                    .collect(Collectors.toList());
            dto.setResponsibleProducts(productBriefs);
        } else {
            dto.setResponsibleProducts(Collections.emptyList());
        }

        return dto;
    }

    /**
     * ProductInfo -> ProductBriefDTO 转换
     */
    private ProductBriefDTO toProductBrief(ProductInfo product) {
        ProductBriefDTO brief = new ProductBriefDTO();
        brief.setId(product.getId());
        brief.setProductCode(product.getProductCode());
        brief.setProductName(product.getProductName());
        brief.setProductCategory(product.getProductCategory());
        return brief;
    }
}
