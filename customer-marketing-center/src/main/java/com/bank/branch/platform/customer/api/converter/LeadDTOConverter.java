package com.bank.branch.platform.customer.api.converter;

import com.bank.branch.platform.customer.api.dto.LeadDTO;
import com.bank.branch.platform.customer.entity.CustLead;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CustLead → LeadDTO 转换器（纯静态工具类）。
 *
 * <p>转换规则：
 * <ul>
 *   <li>标量字段直接映射，时间字段 createdTime/updatedTime → createdAt/updatedAt</li>
 *   <li>Integer isKeystone/isAccountOpened/touchRestricted/isLatest → Boolean（1=true, 0=false, null=null）</li>
 *   <li>tagIds（JSON 字符串）、leadNo、assignedTo 等非 DTO 字段不映射（DTO 无对应字段）</li>
 * </ul>
 */
public final class LeadDTOConverter {

    private LeadDTOConverter() {
        // 纯静态工具类，禁止实例化
    }

    /**
     * 将单个 CustLead 实体转换为 LeadDTO。
     *
     * @param entity 实体，允许为 null
     * @return DTO；entity 为 null 时返回 null
     */
    public static LeadDTO toDTO(CustLead entity) {
        if (entity == null) {
            return null;
        }
        LeadDTO dto = new LeadDTO();
        dto.setId(entity.getId());
        dto.setCustName(entity.getCustName());
        dto.setUnifiedCreditCode(entity.getUnifiedCreditCode());
        dto.setIndustry(entity.getIndustry());
        dto.setGroupType(entity.getGroupType());
        dto.setCustomerType(entity.getCustomerType());
        dto.setIsKeystone(intToBoolean(entity.getIsKeystone()));
        dto.setEnterpriseType(entity.getEnterpriseType());
        dto.setIsAccountOpened(intToBoolean(entity.getIsAccountOpened()));
        dto.setTouchRestricted(intToBoolean(entity.getTouchRestricted()));
        dto.setCustomerDesc(entity.getCustomerDesc());
        dto.setCreditAmount(entity.getCreditAmount());
        dto.setCreditExposureAmount(entity.getCreditExposureAmount());
        dto.setLeadOp(entity.getLeadOp());
        dto.setSourceCustId(entity.getSourceCustId());
        dto.setPrevLeadId(entity.getPrevLeadId());
        dto.setVersionNo(entity.getVersionNo());
        dto.setIsLatest(intToBoolean(entity.getIsLatest()));
        dto.setLeadStatus(entity.getLeadStatus());
        dto.setBusinessKey(entity.getBusinessKey());
        dto.setImportBatchId(entity.getImportBatchId());
        dto.setOwnerOrgId(entity.getOwnerOrgId());
        dto.setCreatedBy(entity.getCreatedBy());
        // 时间字段重命名映射
        dto.setCreatedAt(entity.getCreatedTime());
        dto.setUpdatedAt(entity.getUpdatedTime());
        return dto;
    }

    /**
     * 将实体列表转换为 DTO 列表，自动过滤 null 元素。
     *
     * @param entities 实体列表，允许为 null
     * @return DTO 列表；entities 为 null 时返回空列表
     */
    public static List<LeadDTO> toDTOList(List<CustLead> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream()
                .filter(Objects::nonNull)
                .map(LeadDTOConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Integer（0/1）转换为 Boolean（false/true），null 返回 null。
     *
     * @param value Integer 值
     * @return Boolean；value 为 null 时返回 null
     */
    private static Boolean intToBoolean(Integer value) {
        if (value == null) {
            return null;
        }
        return value != 0;
    }
}
