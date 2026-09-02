package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.redengine.api.dto.ReTaskEligibleUserDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskEligibleUserPageQueryDTO;

/** 任务指定员工候选项查询服务。 */
public interface ReTaskEligibleUserService {

    /**
     * 查询当前管理端数据范围内可用于任务对象的已启用员工。
     *
     * @param query      分页、关键字和党支部过滤条件
     * @param operatorId 当前登录人平台用户 ID
     * @return 候选员工分页
     */
    PageResult<ReTaskEligibleUserDTO> page(ReTaskEligibleUserPageQueryDTO query, String operatorId);
}
