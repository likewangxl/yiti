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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 中台支持申请 DTO 转换器。
 * <p>
 * 职责：将 {@code SupportRequest} 实体转换为对外 API 的 {@code SupportRequestDTO}，
 * 并分别通过以下 API 补充消费方所需的冗余展示字段：
 * <ul>
 *   <li>{@code custName} — {@link CustomerQueryApi}</li>
 *   <li>{@code productName} — {@link ProductApi}</li>
 *   <li>{@code supportDeptName} — {@link OrgApi}（可能抛异常，用 try-catch 降级）</li>
 * </ul>
 * 注意：不映射 {@code deleted} 字段，避免内部运维字段泄露到 API 层。
 * </p>
 * <p>
 * N+1 防护：批量 {@link #toDTOList} 对客户和产品均使用批量 API 一次性获取；
 * OrgApi 暂无批量接口，逐条查询时以 try-catch 隔离异常。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportRequestDTOConverter {

    private final CustomerQueryApi customerQueryApi;
    private final ProductApi productApi;
    private final OrgApi orgApi;

    // -----------------------------------------------------------------------
    // 公开方法
    // -----------------------------------------------------------------------

    /**
     * 单条实体转 DTO，同时填充 custName/productName/supportDeptName 冗余字段。
     *
     * @param entity 支持申请实体，为 null 时返回 null
     * @return 填充完毕的 DTO
     */
    public SupportRequestDTO toDTO(SupportRequest entity) {
        if (entity == null) {
            return null;
        }
        SupportRequestDTO dto = toDTOWithoutRedundant(entity);
        dto.setCustName(resolveCustName(entity.getCustId()));
        dto.setProductName(resolveProductName(entity.getProductId()));
        dto.setSupportDeptName(resolveDeptName(entity.getSupportDeptId()));
        return dto;
    }

    /**
     * 批量实体转 DTO，通过批量 API 一次性查询客户和产品名称，避免 N+1。
     * <p>
     * OrgApi 无批量接口，每条记录仍逐一查询，异常时降级为空字符串。
     * 当前业务分页 pageSize 上限为 100，不会超过 CustomerQueryApi 500 条上限。
     * </p>
     *
     * @param entities 支持申请实体列表，为 null 或空时返回空列表
     * @return 填充完毕的 DTO 列表
     */
    public List<SupportRequestDTO> toDTOList(List<SupportRequest> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询客户名称
        Map<String, String> custNameMap = buildCustNameMap(entities);
        // 批量查询产品名称
        Map<String, String> productNameMap = buildProductNameMap(entities);

        return entities.stream().map(entity -> {
            SupportRequestDTO dto = toDTOWithoutRedundant(entity);
            // 客户名称
            dto.setCustName(entity.getCustId() != null
                    ? custNameMap.getOrDefault(entity.getCustId(), "")
                    : "");
            // 产品名称
            dto.setProductName(entity.getProductId() != null
                    ? productNameMap.getOrDefault(entity.getProductId(), "")
                    : "");
            // 承接部门名称（OrgApi 无批量，逐条 + try-catch）
            dto.setSupportDeptName(resolveDeptName(entity.getSupportDeptId()));
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 单条实体转列表条目 DTO，同时填充 custName/productName/supportDeptName 冗余字段。
     *
     * @param entity 支持申请实体，为 null 时返回 null
     * @return 填充完毕的列表条目 DTO
     */
    public SupportRequestListItemDTO toListItem(SupportRequest entity) {
        if (entity == null) {
            return null;
        }
        SupportRequestListItemDTO dto = toListItemWithoutRedundant(entity);
        dto.setCustName(resolveCustName(entity.getCustId()));
        dto.setProductName(resolveProductName(entity.getProductId()));
        dto.setSupportDeptName(resolveDeptName(entity.getSupportDeptId()));
        return dto;
    }

    /**
     * 批量实体转列表条目 DTO，批量查询 custName/productName 避免 N+1。
     * <p>
     * OrgApi 无批量接口，每条记录仍逐一查询，异常时降级为空字符串。
     * 当前业务分页 pageSize 上限为 100，不会超过 CustomerQueryApi 500 条上限。
     * </p>
     *
     * @param entities 支持申请实体列表，为 null 或空时返回空列表
     * @return 填充完毕的列表条目 DTO 列表
     */
    public List<SupportRequestListItemDTO> toListItems(List<SupportRequest> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询客户名称
        Map<String, String> custNameMap = buildCustNameMap(entities);
        // 批量查询产品名称
        Map<String, String> productNameMap = buildProductNameMap(entities);

        return entities.stream().map(entity -> {
            SupportRequestListItemDTO dto = toListItemWithoutRedundant(entity);
            dto.setCustName(entity.getCustId() != null
                    ? custNameMap.getOrDefault(entity.getCustId(), "")
                    : "");
            dto.setProductName(entity.getProductId() != null
                    ? productNameMap.getOrDefault(entity.getProductId(), "")
                    : "");
            // 承接部门名称（OrgApi 无批量，逐条 + try-catch）
            dto.setSupportDeptName(resolveDeptName(entity.getSupportDeptId()));
            return dto;
        }).collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // 私有辅助方法
    // -----------------------------------------------------------------------

    /**
     * 纯字段复制到列表条目 DTO，不含冗余展示字段和敏感/内部字段。
     * <p>
     * ownerOrgId 为内部字段，不暴露到 API 列表层。
     * scenario 由 productId/supportDeptId 组合推断后填入。
     * </p>
     *
     * @param entity 支持申请实体
     * @return 未填充冗余字段的列表条目 DTO
     */
    private SupportRequestListItemDTO toListItemWithoutRedundant(SupportRequest entity) {
        SupportRequestListItemDTO dto = new SupportRequestListItemDTO();
        dto.setId(entity.getId());
        dto.setRequestNo(entity.getRequestNo());
        dto.setSubmitGroupId(entity.getSubmitGroupId());
        dto.setStatus(entity.getStatus());
        dto.setScenario(inferScenario(entity.getProductId(), entity.getSupportDeptId()));
        dto.setCreatedTime(entity.getCreatedTime());
        // 注意：不复制 deleted、businessKey、processInstanceId、updatedBy、updatedTime、ownerOrgId
        return dto;
    }

    /**
     * 根据 productId / supportDeptId 组合推断场景。
     * <ul>
     *   <li>productId 非空 + supportDeptId 为空 → "A"（产品直达）</li>
     *   <li>其他情况 → "B"（部门承接）</li>
     *   <li>productId 和 supportDeptId 均为空 → 降级返回 "B"，并输出 WARN 日志（可能为数据异常）</li>
     * </ul>
     * 规则依据 SupportService.submit 的场景路由逻辑。
     *
     * @param productId     产品ID
     * @param supportDeptId 承接部门ID
     * @return 场景标识 "A" 或 "B"
     */
    private String inferScenario(String productId, String supportDeptId) {
        boolean hasProduct = StringUtils.hasText(productId);
        boolean hasDept = StringUtils.hasText(supportDeptId);
        if (!hasProduct && !hasDept) {
            log.warn("[SupportRequestDTOConverter.inferScenario] productId 和 supportDeptId 均为空，"
                    + "可能为数据异常，降级推断为 B");
        }
        return (hasProduct && !hasDept) ? "A" : "B";
    }

    /**
     * 纯字段复制，不含冗余展示字段和 deleted。
     *
     * @param entity 支持申请实体
     * @return 未填充冗余字段的 DTO
     */
    private SupportRequestDTO toDTOWithoutRedundant(SupportRequest entity) {
        SupportRequestDTO dto = new SupportRequestDTO();
        dto.setId(entity.getId());
        dto.setRequestNo(entity.getRequestNo());
        dto.setSubmitGroupId(entity.getSubmitGroupId());
        dto.setCustId(entity.getCustId());
        dto.setSourceType(resolveSourceType(entity));
        dto.setSourceTouchTaskId(entity.getSourceTouchTaskId());
        dto.setProductId(entity.getProductId());
        dto.setSupportDeptId(entity.getSupportDeptId());
        dto.setOtherDemand(entity.getOtherDemand());
        dto.setDispatchEmpId(entity.getDispatchEmpId());
        dto.setDispatchTime(entity.getDispatchTime());
        dto.setAssignedEmpId(entity.getAssignedEmpId());
        dto.setStatus(entity.getStatus());
        dto.setBusinessKey(entity.getBusinessKey());
        dto.setProcessInstanceId(entity.getProcessInstanceId());
        dto.setOwnerOrgId(entity.getOwnerOrgId());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedTime(entity.getCreatedTime());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedTime(entity.getUpdatedTime());
        // 注意：不复制 deleted 字段，API 层不暴露内部软删标记
        return dto;
    }

    private String resolveSourceType(SupportRequest entity) {
        if (StringUtils.hasText(entity.getSourceType())) {
            return entity.getSourceType();
        }
        return StringUtils.hasText(entity.getSourceTouchTaskId()) ? "TOUCH_TASK" : "EXISTING_CUSTOMER";
    }

    /**
     * 构建 custId → custName 的批量查询 Map。
     *
     * @param entities 实体列表
     * @return Map；查询失败时返回空 Map
     */
    private Map<String, String> buildCustNameMap(List<SupportRequest> entities) {
        Set<String> custIds = entities.stream()
                .map(SupportRequest::getCustId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (custIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return customerQueryApi.listCustomers(new ArrayList<>(custIds))
                    .stream()
                    .filter(c -> c.getId() != null)
                    .collect(Collectors.toMap(
                            // CustomerDTO.id 字段存储的即是 custId(与 CustMaster 主键对应),
                            // 可直接作为 custId 查找的 Map key；
                            // 若将来 CustomerDTO 引入独立业务 id 字段, 此处必须同步更新, 否则 custName 全部变空
                            CustomerDTO::getId,  // ← CustomerDTO.id == custId,非通用业务 id
                            c -> Optional.ofNullable(c.getCustName()).orElse(""),
                            (a, b) -> a));
        } catch (Exception e) {
            log.warn("[SupportRequestDTOConverter.buildCustNameMap] 批量查询客户名称失败", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 构建 productId → productName 的批量查询 Map。
     *
     * @param entities 实体列表
     * @return Map；查询失败时返回空 Map
     */
    private Map<String, String> buildProductNameMap(List<SupportRequest> entities) {
        Set<String> productIds = entities.stream()
                .map(SupportRequest::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return productApi.getProducts(new ArrayList<>(productIds))
                    .stream()
                    .filter(p -> p.getId() != null)
                    .collect(Collectors.toMap(
                            ProductDTO::getId,
                            p -> Optional.ofNullable(p.getProductName()).orElse(""),
                            (a, b) -> a));
        } catch (Exception e) {
            log.warn("[SupportRequestDTOConverter.buildProductNameMap] 批量查询产品名称失败", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 通过 CustomerQueryApi 单条解析客户名称。
     * custId 为空或客户不存在时返回空字符串，不抛异常。
     *
     * @param custId 客户ID
     * @return 客户名称，不可用时为空字符串
     */
    private String resolveCustName(String custId) {
        if (!StringUtils.hasText(custId)) {
            return "";
        }
        try {
            return customerQueryApi.getCustomer(custId)
                    .map(CustomerDTO::getCustName)
                    .filter(Objects::nonNull)
                    .orElse("");
        } catch (Exception e) {
            log.warn("[SupportRequestDTOConverter.resolveCustName] 查询客户名称失败 custId={}", custId, e);
            return "";
        }
    }

    /**
     * 通过 ProductApi 单条解析产品名称。
     * productId 为空或产品不存在时返回空字符串，不抛异常。
     *
     * @param productId 产品ID
     * @return 产品名称，不可用时为空字符串
     */
    private String resolveProductName(String productId) {
        if (!StringUtils.hasText(productId)) {
            return "";
        }
        try {
            return productApi.getProduct(productId)
                    .map(ProductDTO::getProductName)
                    .filter(Objects::nonNull)
                    .orElse("");
        } catch (Exception e) {
            log.warn("[SupportRequestDTOConverter.resolveProductName] 查询产品名称失败 productId={}", productId, e);
            return "";
        }
    }

    /**
     * 通过 OrgApi 解析承接部门名称。
     * OrgApi.getOrg 为非 Optional 返回，不存在时可能抛异常，
     * 此处用 try-catch 隔离，失败时降级为空字符串。
     *
     * @param deptId 承接部门 ORG_CODE
     * @return 部门名称，不可用时为空字符串
     */
    private String resolveDeptName(String deptId) {
        if (!StringUtils.hasText(deptId)) {
            return "";
        }
        try {
            OrgDTO orgDTO = orgApi.getOrg(deptId);
            return orgDTO != null && orgDTO.getOrgName() != null ? orgDTO.getOrgName() : "";
        } catch (Exception e) {
            log.warn("[SupportRequestDTOConverter.resolveDeptName] 查询部门名称失败 deptId={}", deptId, e);
            return "";
        }
    }
}
