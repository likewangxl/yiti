package com.bank.branch.platform.performance.facade.assembler;

import com.bank.branch.platform.performance.api.dto.CustAllocRelationDTO;
import com.bank.branch.platform.performance.entity.CustAllocRelation;

import java.util.Collections;
import java.util.List;

/**
 * 客户业绩分配关系 DTO 装配器.
 *
 * <p>职责仅做 Entity → DTO 字段映射, 不查 DB / 不调 Service, 以便 Facade 可单元测试隔离。
 *
 * <p>对外字段对齐 {@link CustAllocRelationDTO} 的 11 个字段:
 * <ul>
 *   <li>核心映射字段: id / custId / allocDim / bizKind / accountNo / empId / ratio /
 *       effectiveDate / endDate</li>
 *   <li>可选冗余展示字段: custName / empName, 来源 Mapper 暂不 join 客户 / 员工表,
 *       V1.0 统一映射为 null (调用方按需二次补齐)</li>
 * </ul>
 *
 * <p>Entity 的审计字段 (createdBy / createdTime / updatedBy / updatedTime) 与来源批次字段
 * (sourceBatchId / sourceProcessDate) 不对外暴露。
 *
 * <p>v1.2: id 为 String (对齐生产 DDL varchar(32))。
 */
public final class AllocAssembler {

    private AllocAssembler() {
    }

    /**
     * 将分配关系实体装配为对外 DTO.
     *
     * @param entity 实体, 允许为 null (返回 null)
     * @return DTO 或 null
     */
    public static CustAllocRelationDTO toDto(CustAllocRelation entity) {
        if (entity == null) {
            return null;
        }
        return CustAllocRelationDTO.builder()
                .id(entity.getId())
                .custId(entity.getCustId())
                // custName 对外可选, Mapper 未 join 客户表, V1.0 统一 null
                .custName(null)
                .allocDim(entity.getAllocDim())
                .bizKind(entity.getBizKind())
                .accountNo(entity.getAccountNo())
                .empId(entity.getEmpId())
                // empName 对外可选, Mapper 未 join 员工表, V1.0 统一 null
                .empName(null)
                .ratio(entity.getRatio())
                .effectiveDate(entity.getEffectiveDate())
                .endDate(entity.getEndDate())
                .build();
    }

    /**
     * 将分配关系实体列表装配为 DTO 列表.
     *
     * @param entities 实体列表, 允许为 null / 空 (返回空列表)
     * @return DTO 列表, 不会返回 null
     */
    public static List<CustAllocRelationDTO> toDtoList(List<CustAllocRelation> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(AllocAssembler::toDto)
                .toList();
    }
}
