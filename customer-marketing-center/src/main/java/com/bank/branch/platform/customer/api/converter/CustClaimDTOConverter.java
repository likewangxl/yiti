package com.bank.branch.platform.customer.api.converter;

import com.bank.branch.platform.customer.api.dto.CustClaimDTO;
import com.bank.branch.platform.customer.entity.CustClaim;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CustClaim → CustClaimDTO 转换器（纯静态工具类）。
 *
 * <p>转换规则：
 * <ul>
 *   <li>标量字段直接映射</li>
 *   <li>时间字段：claimTime → claimedAt，cancelTime → cancelledAt</li>
 *   <li>custName、orgName、maintainerEmpName 需二次查询，转换器层暂置 null，由上层 Service 补充</li>
 * </ul>
 */
public final class CustClaimDTOConverter {

    private CustClaimDTOConverter() {
        // 纯静态工具类，禁止实例化
    }

    /**
     * 将单个 CustClaim 实体转换为 CustClaimDTO。
     *
     * @param entity 实体，允许为 null
     * @return DTO；entity 为 null 时返回 null
     */
    public static CustClaimDTO toDTO(CustClaim entity) {
        if (entity == null) {
            return null;
        }
        CustClaimDTO dto = new CustClaimDTO();
        dto.setId(entity.getId());
        dto.setCustId(entity.getCustId());
        dto.setOrgId(entity.getOrgId());
        dto.setMaintainerEmpId(entity.getMaintainerEmpId());
        dto.setClaimStatus(entity.getClaimStatus());
        dto.setCancelReason(entity.getCancelReason());
        // 时间字段重命名映射
        dto.setClaimedAt(entity.getClaimTime());
        dto.setCancelledAt(entity.getCancelTime());
        // 以下字段需上层 Service 查询后补充，转换器层暂置 null
        dto.setCustName(null);              // 需查 CustMaster
        dto.setOrgName(null);               // 需查 OrgApi
        dto.setMaintainerEmpName(null);     // 需查 EmpApi
        return dto;
    }

    /**
     * 将实体列表转换为 DTO 列表，自动过滤 null 元素。
     *
     * @param entities 实体列表，允许为 null
     * @return DTO 列表；entities 为 null 时返回空列表
     */
    public static List<CustClaimDTO> toDTOList(List<CustClaim> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream()
                .filter(Objects::nonNull)
                .map(CustClaimDTOConverter::toDTO)
                .collect(Collectors.toList());
    }
}
