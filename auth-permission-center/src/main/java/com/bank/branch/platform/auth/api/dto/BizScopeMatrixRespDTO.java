package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 业务范围矩阵响应DTO
 * 以矩阵形式展示所有角色在各业务类型下的数据范围配置
 */
@Data
public class BizScopeMatrixRespDTO {

    private List<RoleSimpleDTO> roles;
    private List<String> bizTypes;
    /** roleId -> (bizType -> dataScope) 的二维映射 */
    private Map<String, Map<String, String>> matrix;
    /** bizType 编码 -> 中文名称（来源 common-security BizType 枚举 description，前端展示单一真相源） */
    private Map<String, String> bizTypeLabels;
}
