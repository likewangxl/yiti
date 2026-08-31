package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.redengine.api.dto.ReTaskApproveReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskRejectReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowActionRespDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowAssignmentDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowPageQueryDTO;

/**
 * 任务报送与两级审核服务。
 *
 * <p>任务域自管提交状态，不接入 Flowable。所有方法都接收当前用户 ID，
 * 实现类会再次从 CurrentUserApi 校验身份和角色，避免把 Controller 鉴权当成数据权限。</p>
 */
public interface ReTaskWorkflowService {

    /** 查询当前报送员有待办或已处理过的任务分配。 */
    PageResult<ReTaskWorkflowAssignmentDTO> listMyAssignments(ReTaskWorkflowPageQueryDTO query,
                                                               String operatorId);

    /** 查询单条任务分配详情，并按当前用户做实体归属校验。 */
    ReTaskWorkflowAssignmentDTO getAssignment(Long assignmentId, String operatorId);

    /** 报送员提交或驳回后重新提交任务。 */
    ReTaskWorkflowActionRespDTO submit(ReTaskSubmissionReqDTO request, String operatorId);

    /** 查询当前支部书记可审核的任务。 */
    PageResult<ReTaskWorkflowAssignmentDTO> listBranchReviews(ReTaskWorkflowPageQueryDTO query,
                                                               String operatorId);

    /** 查询当前支部书记可见的任务详情。 */
    ReTaskWorkflowAssignmentDTO getBranchReview(Long assignmentId, String operatorId);

    /** 支部书记审核通过，停留在支部已通过状态。 */
    ReTaskWorkflowActionRespDTO approveBranch(Long assignmentId, ReTaskApproveReqDTO request,
                                              String operatorId);

    /** 支部书记驳回，任务返回报送员。 */
    ReTaskWorkflowActionRespDTO rejectBranch(Long assignmentId, ReTaskRejectReqDTO request,
                                             String operatorId);

    /** 支部书记单独提交至组织审核。 */
    ReTaskWorkflowActionRespDTO submitToOrg(Long assignmentId, ReTaskApproveReqDTO request,
                                             String operatorId);

    /** 查询组织审核员可审核的任务。 */
    PageResult<ReTaskWorkflowAssignmentDTO> listOrgReviews(ReTaskWorkflowPageQueryDTO query,
                                                            String operatorId);

    /** 查询组织审核员可见的任务详情。 */
    ReTaskWorkflowAssignmentDTO getOrgReview(Long assignmentId, String operatorId);

    /** 组织审核通过任务。 */
    ReTaskWorkflowActionRespDTO approveOrg(Long assignmentId, ReTaskApproveReqDTO request,
                                            String operatorId);

    /** 组织审核驳回任务，任务返回报送员。 */
    ReTaskWorkflowActionRespDTO rejectOrg(Long assignmentId, ReTaskRejectReqDTO request,
                                          String operatorId);
}
