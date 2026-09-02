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
     * 同步用户映射变更对当前有效任务的报送员分配。
     *
     * <p>仅具备平台 {@code R_RE_REPORT} 角色的用户可获得 REPORTER 待办；全部/指定支部
     * 按新映射所在支部重建或补齐，指定员工任务只匹配显式员工目标。旧支部尚未处理的
     * 待办会取消，但已完成待办和提交版本不删除，以保留历史查看能力。</p>
     *
     * @param employeeId         平台用户 ID
     * @param previousPartyOrgId 变更前党组织 ID，可为空
     * @param currentPartyOrgId  变更后党组织 ID，可为空
     */
    void synchronizeReporterAssignments(String employeeId, Long previousPartyOrgId,
                                        Long currentPartyOrgId);

    /**
     * 分页查询任务实例的支部填报汇总。
     *
     * @param taskId 任务定义 ID
     * @param query  分页和筛选条件
     * @return 支部填报汇总页
     */
    PageResult<ReTaskAssignmentDTO> pageAssignments(Long taskId, ReTaskAssignmentPageQueryDTO query);
}
