package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.RoleRespDTO;

import java.util.List;

/**
 * 角色查询对外 API（供业务模块只读消费，如 KPI 方案的「员工角色范围」下拉）.
 *
 * <p>角色为系统级配置，结果不大，按需实时查询。</p>
 */
public interface RoleApi {

    /**
     * 查询全部「可用」角色（RECORD_STATUS=0），按角色名称（roleChName）升序排序.
     *
     * @return 可用角色列表（按名称排序；无可用角色返回空列表）
     */
    List<RoleRespDTO> listEnabledRoles();
}
