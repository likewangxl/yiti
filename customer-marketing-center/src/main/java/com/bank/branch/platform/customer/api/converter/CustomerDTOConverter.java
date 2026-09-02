package com.bank.branch.platform.customer.api.converter;

import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.M98CustMaster;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CustMaster → CustomerDTO 转换器（纯静态工具类）。
 *
 * <p>转换规则：
 * <ul>
 *   <li>标量字段直接映射，时间字段 createdTime/updatedTime → createdAt/updatedAt</li>
 *   <li>Integer isKeystone/isAccountOpened/touchRestricted → Boolean（1=true, 0=false, null=null）</li>
 *   <li>ownerOrgName、industryName、tagIds 需二次查询，转换器层暂置 null，由上层 Service 补充</li>
 * </ul>
 */
public final class CustomerDTOConverter {

    private CustomerDTOConverter() {
        // 纯静态工具类，禁止实例化
    }

    /**
     * 将单个 CustMaster 实体转换为 CustomerDTO。
     *
     * @param entity 实体，允许为 null
     * @return DTO；entity 为 null 时返回 null
     */
    public static CustomerDTO toDTO(CustMaster entity) {
        if (entity == null) {
            return null;
        }
        CustomerDTO dto = new CustomerDTO();
        dto.setId(entity.getId());
        dto.setCustNo(entity.getCustNo());
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
        dto.setOwnerOrgId(entity.getOwnerOrgId());
        dto.setLeadId(entity.getLeadId());
        dto.setCurrentLeadId(entity.getCurrentLeadId());
        dto.setMainManagerId(entity.getMainManagerId());
        dto.setMainOrgId(entity.getMainOrgId());
        dto.setOwnershipStatus(entity.getOwnershipStatus());
        dto.setLastTouchTime(entity.getLastTouchTime());
        dto.setSourceSystem(entity.getSourceSystem());
        dto.setSourceUpdatedTime(entity.getSourceUpdatedTime());
        dto.setStatus(entity.getStatus());
        // 时间字段重命名映射
        dto.setCreatedAt(entity.getCreatedTime());
        dto.setUpdatedAt(entity.getUpdatedTime());
        // 以下字段需上层 Service 查询后补充，转换器层暂置 null
        dto.setOwnerOrgName(null);   // 需 OrgApi 查询
        dto.setIndustryName(null);   // 需 DictApi 查询
        dto.setTagIds(null);          // 需查 cust_tag_rel 表
        return dto;
    }

    /**
     * 将存量 M98 客户主档转换为最小客户 DTO。
     *
     * <p>M98 主档只维护客户号、客户名称和统计日期，不伪造客户营销字段。</p>
     *
     * @param entity M98 客户主档，允许为 null
     * @return 最小客户 DTO；entity 为 null 时返回 null
     */
    public static CustomerDTO toDTOFromM98(M98CustMaster entity) {
        if (entity == null) {
            return null;
        }
        CustomerDTO dto = new CustomerDTO();
        dto.setId(entity.getId());
        dto.setCustNo(entity.getCustNo());
        dto.setCustName(entity.getCustName());
        dto.setStatus(entity.getStatus());
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
    public static List<CustomerDTO> toDTOList(List<CustMaster> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream()
                .filter(Objects::nonNull)
                .map(CustomerDTOConverter::toDTO)
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
