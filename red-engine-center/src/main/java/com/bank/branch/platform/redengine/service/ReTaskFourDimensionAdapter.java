package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.ReTaskDimensionProgressStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionReqDTO;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskDimensionProgress;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskReSubmitRel;
import com.bank.branch.platform.redengine.entity.ReTaskSubmission;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReSubmitMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskDimensionProgressMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskReSubmitRelMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 四大维度旧材料上报适配器。
 *
 * <p>四维材料仍由原 {@code RE_SUBMIT} 审核流处理，本适配器只维护新任务实例与旧上报的
 * 关联，以及“本季度该维度一次上传即完成”的进度快照；不改旧上报状态、不迁移历史数据。</p>
 */
@Service
@RequiredArgsConstructor
public class ReTaskFourDimensionAdapter {

    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    private final ReTaskReSubmitRelMapper relMapper;
    private final ReTaskDimensionProgressMapper progressMapper;
    private final ReTaskSubmissionMapper taskSubmissionMapper;
    private final ReSubmitMapper reSubmitMapper;
    private final ReTaskMapper taskMapper;
    private final ReTaskInstanceMapper instanceMapper;
    private final ReTaskBranchAssignmentMapper assignmentMapper;

    /**
     * 记录新任务接口产生的四维上传进度。
     *
     * <p>该入口不创建旧 {@code RE_SUBMIT} 记录，适用于已经由旧材料上报接口创建完毕的
     * 记录或四维任务提交接口只需要记录新域进度的场景。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordTaskUpload(ReTask task, ReTaskInstance instance,
                                 ReTaskBranchAssignment assignment,
                                 ReTaskSubmission taskSubmission,
                                 String dimensionCode, String itemCode,
                                 String operatorId) {
        requireContext(task, instance, assignment);
        requireFourDimensionTask(task);
        if (!hasText(dimensionCode)) {
            return;
        }
        upsertProgress(task, instance, assignment, dimensionCode, taskSubmission, now());
    }

    /**
     * 将旧材料上报记录关联到当前任务 assignment，并推进该维度进度。
     *
     * @param taskId 任务定义 ID
     * @param taskInstanceId 任务实例 ID
     * @param assignmentId 支部分配 ID
     * @param reSubmitId 既有 RE_SUBMIT 主键
     * @param dimensionCode 四维编码（为空时使用旧上报 dimension）
     * @param itemCode 明细项编码（为空时使用旧上报 itemCode）
     * @param operatorId 当前操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void linkExistingSubmission(Long taskId, Long taskInstanceId, Long assignmentId,
                                       Long reSubmitId, String dimensionCode,
                                       String itemCode, String operatorId) {
        if (taskId == null || taskInstanceId == null || assignmentId == null || reSubmitId == null) {
            throw new BizException("RE-40031", "四维任务关联参数不完整");
        }
        ReSubmit oldSubmission = reSubmitMapper.selectById(reSubmitId);
        if (oldSubmission == null) {
            throw new BizException("RE-40032", "材料上报记录不存在");
        }
        ReTask task = taskMapper.selectById(taskId);
        ReTaskInstance instance = instanceMapper.selectById(taskInstanceId);
        ReTaskBranchAssignment assignment = assignmentMapper.selectById(assignmentId);
        requireContext(task, instance, assignment);
        requireFourDimensionTask(task);
        if (!Objects.equals(oldSubmission.getOrgId(), assignment.getBranchId())) {
            throw new BizException("RE-40306", "材料上报不属于该党支部任务");
        }

        String resolvedDimension = hasText(dimensionCode) ? dimensionCode.trim() : oldSubmission.getDimension();
        String resolvedItem = hasText(itemCode) ? itemCode.trim() : oldSubmission.getItemCode();
        if (!hasText(resolvedDimension)) {
            throw new BizException("RE-40033", "四维维度不能为空");
        }

        ReTaskReSubmitRel existing = relMapper.selectOne(new LambdaQueryWrapper<ReTaskReSubmitRel>()
                .eq(ReTaskReSubmitRel::getTaskInstanceId, taskInstanceId)
                .eq(ReTaskReSubmitRel::getAssignmentId, assignmentId)
                .eq(ReTaskReSubmitRel::getReSubmitId, reSubmitId));
        if (existing == null) {
            ReTaskReSubmitRel relation = new ReTaskReSubmitRel();
            relation.setTaskId(taskId);
            relation.setTaskInstanceId(taskInstanceId);
            relation.setAssignmentId(assignmentId);
            relation.setReSubmitId(reSubmitId);
            relation.setDimensionCode(resolvedDimension);
            relation.setItemCode(resolvedItem);
            relation.setCreatedBy(operatorId);
            relation.setCreateTime(now());
            try {
                relMapper.insert(relation);
            } catch (DuplicateKeyException duplicate) {
                // 关系表唯一键承担并发幂等；另一节点已创建时当前操作无需重复写入。
            }
        }
        upsertProgress(task, instance, assignment, resolvedDimension, null, now());
    }

    /**
     * 从扩展材料请求中解析并关联旧上报。
     *
     * <p>此方法仅在请求携带 assignment/task 信息时生效；没有任务字段的旧客户端直接返回，
     * 因而不会改变原材料上报页面的行为。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void linkFromLegacyRequest(ReSubmit request, Long reSubmitId, Long taskId,
                                      Long taskInstanceId, Long assignmentId, String operatorId) {
        if (taskId == null || taskInstanceId == null || assignmentId == null) {
            return;
        }
        linkExistingSubmission(taskId, taskInstanceId, assignmentId, reSubmitId,
                request == null ? null : request.getDimension(),
                request == null ? null : request.getItemCode(), operatorId);
    }

    /** 以 assignment+维度唯一键更新上传次数，首次上传即完成，重复上传只增加次数。 */
    private void upsertProgress(ReTask task, ReTaskInstance instance,
                                ReTaskBranchAssignment assignment,
                                String dimensionCode, ReTaskSubmission submission,
                                LocalDateTime now) {
        ReTaskDimensionProgress existing = progressMapper.selectOne(
                new LambdaQueryWrapper<ReTaskDimensionProgress>()
                        .eq(ReTaskDimensionProgress::getAssignmentId, assignment.getId())
                        .eq(ReTaskDimensionProgress::getDimensionCode, dimensionCode));
        if (existing == null) {
            ReTaskDimensionProgress progress = new ReTaskDimensionProgress();
            progress.setTaskId(task.getId());
            progress.setTaskInstanceId(instance.getId());
            progress.setAssignmentId(assignment.getId());
            progress.setDimensionCode(dimensionCode);
            progress.setStatus(ReTaskDimensionProgressStatus.COMPLETED);
            progress.setUploadCount(1);
            progress.setFirstUploadedAt(now);
            progress.setCompletedAt(now);
            progress.setCompletedSubmissionId(submission == null ? null : submission.getId());
            progress.setLastSubmissionId(submission == null ? null : submission.getId());
            progress.setCreateTime(now);
            progress.setUpdateTime(now);
            try {
                progressMapper.insert(progress);
            } catch (DuplicateKeyException duplicate) {
                // 两个四维页面可能同时首次上传；抢到唯一键的一方保留首条，其余转为累加。
                ReTaskDimensionProgress raced = progressMapper.selectOne(
                        new LambdaQueryWrapper<ReTaskDimensionProgress>()
                                .eq(ReTaskDimensionProgress::getAssignmentId, assignment.getId())
                                .eq(ReTaskDimensionProgress::getDimensionCode, dimensionCode));
                if (raced == null) {
                    throw new BizException("RE-40901", "四维上传进度已被其他用户更新", duplicate);
                }
                incrementProgress(raced, submission, now);
            }
            return;
        }
        incrementProgress(existing, submission, now);
    }

    private void incrementProgress(ReTaskDimensionProgress existing, ReTaskSubmission submission,
                                   LocalDateTime now) {
        LambdaUpdateWrapper<ReTaskDimensionProgress> update = new LambdaUpdateWrapper<ReTaskDimensionProgress>()
                .eq(ReTaskDimensionProgress::getId, existing.getId())
                .set(ReTaskDimensionProgress::getStatus, ReTaskDimensionProgressStatus.COMPLETED)
                // 不能先读 count 再回写：并发上传时两个请求可能都把同一个值加一。
                .setSql("upload_count = COALESCE(upload_count, 0) + 1")
                .set(ReTaskDimensionProgress::getLastSubmissionId,
                        submission == null ? null : submission.getId())
                .set(ReTaskDimensionProgress::getUpdateTime, now);
        if (progressMapper.update(null, update) != 1) {
            throw new BizException("RE-40901", "四维上传进度已被其他用户更新");
        }
    }

    private void requireContext(ReTask task, ReTaskInstance instance,
                                ReTaskBranchAssignment assignment) {
        if (task == null || task.getId() == null || instance == null || instance.getId() == null
                || !Objects.equals(task.getId(), instance.getTaskId()) || assignment == null
                || assignment.getId() == null
                || !Objects.equals(instance.getId(), assignment.getTaskInstanceId())) {
            throw new BizException("RE-40034", "四维任务关联关系不一致");
        }
    }

    private void requireFourDimensionTask(ReTask task) {
        if (task == null || !"FOUR_DIMENSION".equalsIgnoreCase(task.getTypeCode())) {
            throw new BizException("RE-40035", "仅四大维度任务允许关联材料上报");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BEIJING_ZONE);
    }
}
