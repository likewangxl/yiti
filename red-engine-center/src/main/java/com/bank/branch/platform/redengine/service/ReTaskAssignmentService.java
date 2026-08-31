package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentPageQueryDTO;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;

import java.util.List;

/** 任务实例的支部分配和员工待办服务。 */
public interface ReTaskAssignmentService {

    /**
     * 为一个已发布实例幂等生成支部分配和报送员待办。
     *
     * @param task     已发布任务定义
     * @param instance 任务实例
     * @return 本实例已有及本次生成的支部分配
     */
    List<ReTaskBranchAssignment> ensureAssignments(ReTask task, ReTaskInstance instance);

    /**
     * 分页查询任务实例的支部填报汇总。
     *
     * @param taskId 任务定义 ID
     * @param query  分页和筛选条件
     * @return 支部填报汇总页
     */
    PageResult<ReTaskAssignmentDTO> pageAssignments(Long taskId, ReTaskAssignmentPageQueryDTO query);
}
