package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeDetailDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeSearchDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeSelfUpdateReqDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.ProductBriefDTO;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 通讯录 REST Controller。
 *
 * <p>GET 接口保持原有 /api/employees 兼容路径；自助维护使用独立的
 * PUT /api/employees/me，只允许电话、邮箱和负责产品三个字段。导入、模板、导出及
 * PUT /{empId} 已删除，避免继续暴露旧 ADDRBOOK_EMPLOYEE 写链路。</p>
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class AddressBookController {

    private final AddressBookService addressBookService;
    private final CurrentUserApi currentUserApi;

    /** 分页查询在职通讯录。 */
    @GetMapping
    @BizAuth(bizType = BizType.ADDRBOOK, action = BizAction.LIST)
    public ResponseWrapper<EmployeeDetailDTO> listEmployees(@Valid EmployeeQueryReqDTO req) {
        PageResult<UserDirectoryDTO> userPage = addressBookService.listEmployees(req);
        if (userPage == null) {
            userPage = PageResult.of(1, 20, 0L, Collections.emptyList());
        }
        List<UserDirectoryDTO> users = userPage.getRecords() == null
                ? Collections.emptyList() : userPage.getRecords();
        Map<String, List<String>> productIdsByUser = addressBookService.mapProductIdsByUserIds(
                users.stream().map(UserDirectoryDTO::getEmpId).filter(Objects::nonNull).collect(Collectors.toList()));
        List<String> productIds = productIdsByUser.values().stream()
                .filter(Objects::nonNull).flatMap(List::stream).distinct().collect(Collectors.toList());
        Map<String, ProductBriefDTO> productById = addressBookService.listProductsByIds(productIds).stream()
                .map(this::toProductBrief)
                .collect(Collectors.toMap(ProductBriefDTO::getId, product -> product, (first, ignored) -> first));
        String currentEmpId = currentUserApi.getCurrentEmpId();
        List<EmployeeDetailDTO> records = users.stream()
                .map(user -> toEmployeeDTO(user, productIdsByUser.get(user.getEmpId()), productById))
                .peek(dto -> dto.setCanEdit(currentEmpId != null && currentEmpId.equals(dto.getEmpId())))
                .collect(Collectors.toList());
        return ResponseWrapper.page(PageResult.of(
                userPage.getPageNo(), userPage.getPageSize(), userPage.getTotal(), records));
    }

    /** 员工选择器模糊搜索。 */
    @GetMapping("/search")
    @BizAuth(bizType = BizType.ADDRBOOK, action = BizAction.READ)
    public ResponseWrapper<List<EmployeeSearchDTO>> searchEmployees(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseWrapper.success(addressBookService.searchEmployees(keyword, Math.min(limit, 50)).stream()
                .map(this::toSearchDTO)
                .collect(Collectors.toList()));
    }

    /** 员工详情；负责产品关系由关系表批量模型的单用户查询回填。 */
    @GetMapping("/{empId}")
    @BizAuth(bizType = BizType.ADDRBOOK, action = BizAction.READ)
    public ResponseWrapper<EmployeeDetailDTO> getEmployee(@PathVariable String empId) {
        UserDirectoryDTO user = addressBookService.getEmployee(empId);
        List<String> productIds = addressBookService.listProductIdsByUserId(empId);
        Map<String, ProductBriefDTO> productById = addressBookService.listProductsByIds(productIds).stream()
                .map(this::toProductBrief)
                .collect(Collectors.toMap(ProductBriefDTO::getId, product -> product, (first, ignored) -> first));
        return ResponseWrapper.success(toEmployeeDTO(user, productIds, productById));
    }

    /**
     * 当前用户自助维护电话、邮箱和负责产品。
     *
     * <p>没有目标用户路径变量，越权保护由认证上下文和服务层共同保证。</p>
     */
    @PutMapping("/me")
    @BizAuth(bizType = BizType.ADDRBOOK, action = BizAction.WRITE)
    public ResponseWrapper<Void> updateCurrentUser(@Valid @RequestBody EmployeeSelfUpdateReqDTO req) {
        addressBookService.updateCurrentUser(req);
        return ResponseWrapper.success(null);
    }

    private EmployeeDetailDTO toEmployeeDTO(UserDirectoryDTO user, List<String> productIds,
                                             Map<String, ProductBriefDTO> productById) {
        EmployeeDetailDTO dto = new EmployeeDetailDTO();
        if (user == null) {
            return dto;
        }
        dto.setEmpId(user.getEmpId());
        dto.setEmpName(user.getEmpName());
        // REST 通讯录需要原始手机号，供本人编辑抽屉回填；跨模块 Facade 单独脱敏。
        dto.setMobile(user.getMobile());
        dto.setEmail(user.getEmail());
        dto.setOrgCode(user.getOrgCode());
        dto.setOrgName(user.getOrgName());
        dto.setPosition(user.getPosition());
        dto.setStatus(user.getStatus());
        dto.setUpdatedTime(user.getUpdatedTime());

        List<String> ids = productIds == null ? Collections.emptyList() : productIds;
        dto.setResponsibleProductIds(ids);
        dto.setResponsibleProducts(ids.stream().map(productById::get).filter(Objects::nonNull)
                .collect(Collectors.toList()));
        return dto;
    }

    private EmployeeSearchDTO toSearchDTO(UserDirectoryDTO user) {
        EmployeeSearchDTO dto = new EmployeeSearchDTO();
        if (user == null) {
            return dto;
        }
        dto.setEmpId(user.getEmpId());
        dto.setEmpName(user.getEmpName());
        dto.setOrgCode(user.getOrgCode());
        dto.setOrgName(user.getOrgName());
        dto.setPosition(user.getPosition());
        return dto;
    }

    private ProductBriefDTO toProductBrief(ProductInfo product) {
        ProductBriefDTO brief = new ProductBriefDTO();
        brief.setId(product.getId());
        brief.setProductCode(product.getProductCode());
        brief.setProductName(product.getProductName());
        brief.setProductCategory(product.getProductCategory());
        return brief;
    }
}
