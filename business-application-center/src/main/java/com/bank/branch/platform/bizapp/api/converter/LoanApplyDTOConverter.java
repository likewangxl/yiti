package com.bank.branch.platform.bizapp.api.converter;

import com.bank.branch.platform.bizapp.api.dto.LoanApplyDTO;
import com.bank.branch.platform.bizapp.api.dto.LoanApplyListItemDTO;
import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
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
 * 贷款申请 DTO 转换器。
 * <p>
 * 职责：将 {@code LoanApply} 实体转换为对外 API 的 {@code LoanApplyDTO}，
 * 同时通过 {@link CustomerQueryApi} 补充消费方所需的 {@code custName} 冗余字段。
 * 注意：不映射 {@code deleted} 字段，避免内部运维字段泄露到 API 层。
 * </p>
 * <p>
 * N+1 防护：批量 {@link #toDTOList} 使用 {@code customerQueryApi.listCustomers()} 一次性获取客户名称 Map，
 * 不在循环内逐条调用 {@code getCustomer}。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanApplyDTOConverter {

    private final CustomerQueryApi customerQueryApi;

    // -----------------------------------------------------------------------
    // 公开方法
    // -----------------------------------------------------------------------

    /**
     * 单条实体转 DTO，同时调用 CustomerQueryApi 填充 custName。
     *
     * @param entity 贷款申请实体，为 null 时返回 null
     * @return 填充完毕的 DTO
     */
    public LoanApplyDTO toDTO(LoanApply entity) {
        if (entity == null) {
            return null;
        }
        LoanApplyDTO dto = toDTOWithoutCustName(entity);
        dto.setCustName(resolveCustName(entity.getCustId()));
        return dto;
    }

    /**
     * 批量实体转 DTO，通过批量 API 一次性查询客户名称，避免 N+1。
     * <p>
     * 业务说明：当前分页 pageSize 上限为 100，不超过 CustomerQueryApi.listCustomers 的 500 上限。
     * </p>
     *
     * @param entities 贷款申请实体列表，为 null 或空时返回空列表
     * @return 填充完毕的 DTO 列表
     */
    public List<LoanApplyDTO> toDTOList(List<LoanApply> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> custNameMap = buildCustNameMap(entities);
        return entities.stream().map(entity -> {
            LoanApplyDTO dto = toDTOWithoutCustName(entity);
            dto.setCustName(custNameMap.getOrDefault(entity.getCustId() != null ? entity.getCustId() : "", ""));
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 单条实体转列表条目 DTO，同时填充 custName 冗余字段。
     *
     * @param entity 贷款申请实体，为 null 时返回 null
     * @return 填充完毕的列表条目 DTO
     */
    public LoanApplyListItemDTO toListItem(LoanApply entity) {
        if (entity == null) {
            return null;
        }
        LoanApplyListItemDTO dto = toListItemWithoutCustName(entity);
        dto.setCustName(resolveCustName(entity.getCustId()));
        return dto;
    }

    /**
     * 批量实体转列表条目 DTO，批量查询 custName 避免 N+1。
     * <p>
     * 业务说明：当前分页 pageSize 上限为 100，不超过 CustomerQueryApi.listCustomers 的 500 上限。
     * </p>
     *
     * @param entities 贷款申请实体列表，为 null 或空时返回空列表
     * @return 填充完毕的列表条目 DTO 列表
     */
    public List<LoanApplyListItemDTO> toListItems(List<LoanApply> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> custNameMap = buildCustNameMap(entities);
        return entities.stream().map(entity -> {
            LoanApplyListItemDTO dto = toListItemWithoutCustName(entity);
            dto.setCustName(custNameMap.getOrDefault(entity.getCustId() != null ? entity.getCustId() : "", ""));
            return dto;
        }).collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // 私有辅助方法
    // -----------------------------------------------------------------------

    /**
     * 纯字段复制，不含 custName 和 deleted。
     * 供单条和批量路径共用，避免代码重复。
     *
     * @param entity 贷款申请实体
     * @return 未填充 custName 的 DTO（custName 为 null）
     */
    private LoanApplyDTO toDTOWithoutCustName(LoanApply entity) {
        LoanApplyDTO dto = new LoanApplyDTO();
        dto.setId(entity.getId());
        dto.setApplyNo(entity.getApplyNo());
        dto.setCustId(entity.getCustId());
        dto.setSourceTouchTaskId(entity.getSourceTouchTaskId());
        dto.setProjectType(entity.getProjectType());
        dto.setBizType(entity.getBizType());
        dto.setGuaranteeType(entity.getGuaranteeType());
        dto.setCreditAmount(entity.getCreditAmount());
        dto.setCreditExposureAmount(entity.getCreditExposureAmount());
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

    /**
     * 纯字段复制到列表条目 DTO，不含 custName 和敏感/内部字段。
     *
     * @param entity 贷款申请实体
     * @return 未填充 custName 的列表条目 DTO
     */
    private LoanApplyListItemDTO toListItemWithoutCustName(LoanApply entity) {
        LoanApplyListItemDTO dto = new LoanApplyListItemDTO();
        dto.setId(entity.getId());
        dto.setApplyNo(entity.getApplyNo());
        dto.setCustId(entity.getCustId());
        dto.setCreditAmount(entity.getCreditAmount());
        dto.setStatus(entity.getStatus());
        dto.setOwnerOrgId(entity.getOwnerOrgId());
        dto.setCreatedTime(entity.getCreatedTime());
        // 注意：不复制 deleted、businessKey、processInstanceId、updatedBy、updatedTime
        return dto;
    }

    /**
     * 批量查询客户名称，合并为 Map。
     * <p>
     * CustomerDTO.id 字段存储的即是 custId（与 CustMaster 主键对应），
     * 可直接作为 custId 查找的 Map key；若将来 CustomerDTO 引入独立业务 id 字段，
     * 此处必须同步更新，否则 custName 全部变空。
     * </p>
     *
     * @param entities 贷款申请实体列表
     * @return custId → custName Map；查询失败时返回空 Map
     */
    private Map<String, String> buildCustNameMap(List<LoanApply> entities) {
        Set<String> custIds = entities.stream()
                .map(LoanApply::getCustId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (custIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return customerQueryApi.listCustomers(new ArrayList<>(custIds)).stream()
                    .filter(c -> c.getId() != null)
                    .collect(Collectors.toMap(
                            CustomerDTO::getId,  // ← CustomerDTO.id == custId,非通用业务 id
                            c -> Optional.ofNullable(c.getCustName()).orElse(""),
                            (a, b) -> a));
        } catch (Exception e) {
            log.warn("[LoanApplyDTOConverter.buildCustNameMap] 批量查询客户名称失败", e);
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
            log.warn("[LoanApplyDTOConverter.resolveCustName] 查询客户名称失败 custId={}", custId, e);
            return "";
        }
    }
}
