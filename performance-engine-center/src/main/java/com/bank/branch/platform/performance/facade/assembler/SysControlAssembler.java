package com.bank.branch.platform.performance.facade.assembler;

import com.bank.branch.platform.performance.controller.dto.SysControlRespDTO;
import com.bank.branch.platform.performance.entity.SysControl;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SysControl DTO 装配器.
 *
 * <p>将 SysControl entity 转换为 Controller 层响应 DTO，
 * 隐藏内部字段（createdTime / updatedTime / updatedBy / publishSource / publishBy / publishTime）。
 */
public final class SysControlAssembler {

    private SysControlAssembler() {
    }

    /**
     * 将 SysControl entity 转换为 Controller 层响应 DTO.
     *
     * @param entity SysControl 实体
     * @return 响应 DTO，null 入参返回 null
     */
    public static SysControlRespDTO toRespDTO(SysControl entity) {
        if (entity == null) {
            return null;
        }
        SysControlRespDTO dto = new SysControlRespDTO();
        dto.setId(entity.getId());
        dto.setScopeDim(entity.getScopeDim());
        dto.setLatestDataDate(entity.getLatestDataDate());
        dto.setCurrentVersion(entity.getCurrentVersion());
        dto.setIsValid(entity.getIsValid());
        dto.setRemark(entity.getRemark());
        return dto;
    }

    /**
     * 将 SysControl entity 列表转换为 Controller 层响应 DTO 列表.
     *
     * @param entities SysControl 实体列表
     * @return 响应 DTO 列表
     */
    public static List<SysControlRespDTO> toRespDTOList(List<SysControl> entities) {
        if (entities == null) {
            return java.util.Collections.emptyList();
        }
        return entities.stream()
                .map(SysControlAssembler::toRespDTO)
                .collect(Collectors.toList());
    }
}
