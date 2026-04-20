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
 * id / custId / custName / allocDim / bizKind / accountNo / empId / empName /
 * ratio / effectiveDate / endDate; 其中 custName / empName 为可选冗余展示字段,
 * 来源 Mapper 暂不 join 客户 / 员工表, V1.0 统一映射为 null (调用方按需二次补齐)。
 *
 * <p>Entity 的审计字段 (createdBy / createdTime / updatedBy / updatedTime) 与来源批次字段
 * (sourceBatchId / sourceProcessDate) 不对外暴露。
 *
 * <p>Step 3 (TDD 红): 本 Assembler 骨架为 {@code toDto(null)} 和 {@code toDtoList(null/empty)}
 * 提供实现, 其余映射路径返回最简默认值使 UT 编译通过但断言失败, 为 Step 4 绿实现作铺垫。
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
        // Step 3 红: 空 DTO 断言将 FAIL, Step 4 绿实现完整映射
        return CustAllocRelationDTO.builder().build();
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
        // Step 3 红: 返回空列表 (映射缺失) 使 UT FAIL, Step 4 绿实现
        return Collections.emptyList();
    }
}
