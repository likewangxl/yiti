package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentPageQueryDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskCreateReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskCreateRespDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskDetailDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskListItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskPageQueryDTO;

/** 任务定义管理、发布和当前周期实例协调服务。 */
public interface ReTaskManagementService {

    /**
     * 分页查询已发布任务及其填报进度。
     *
     * @param query      查询条件
     * @param operatorId 当前操作人平台用户 ID
     * @return 任务列表页
     */
    PageResult<ReTaskListItemDTO> page(ReTaskPageQueryDTO query, String operatorId);

    /**
     * 查询任务详情及支部对象、文件限制快照。
     *
     * @param taskId     任务定义 ID
     * @param operatorId 当前操作人平台用户 ID
     * @return 任务详情；任务不存在时返回 null
     */
    ReTaskDetailDTO getDetail(Long taskId, String operatorId);

    /**
     * 校验任务定义并立即发布，生成首个任务实例、支部分配和员工待办。
     *
     * @param request    任务新增请求
     * @param operatorId 当前操作人平台用户 ID
     * @return 发布结果
     */
    ReTaskCreateRespDTO createAndPublish(ReTaskCreateReqDTO request, String operatorId);

    /**
     * 查询任务详情下的支部填报汇总。
     *
     * @param taskId     任务定义 ID
     * @param query      分页和筛选条件
     * @param operatorId 当前操作人平台用户 ID
     * @return 支部填报汇总页
     */
    PageResult<ReTaskAssignmentDTO> pageAssignments(Long taskId,
                                                     ReTaskAssignmentPageQueryDTO query,
                                                     String operatorId);

    /**
     * 定时调度补偿当前有效窗口；仅补当前窗口，不回填已错过的历史窗口。
     */
    void reconcileCurrentWindows();
}
