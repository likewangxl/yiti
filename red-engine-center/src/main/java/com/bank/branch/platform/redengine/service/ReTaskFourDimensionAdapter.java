package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.ReTaskDimensionProgressStatus;
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
import java.util.Comparator;
import java.util.List;
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
     * 查询任务 assignment 当前提交版本。
     *
     * <p>材料上报允许在同一支部任务窗口内连续提交多个维度；调用方据此决定是复用当前
     * {@code BRANCH_PENDING} 版本，还是发起一次新的 workflow submit。这里同时复核任务、实例
     * 和 assignment 关系，避免客户端只凭三个 ID 把材料挂到其他任务。</p>
     */
    @Transactional(readOnly = true)
    public ReTaskSubmission findCurrentSubmission(Long taskId, Long taskInstanceId, Long assignmentId) {
        if (taskId == null || taskInstanceId == null || assignmentId == null) {
            throw new BizException("RE-40031", "四维任务关联参数不完整");
        }
        ReTask task = taskMapper.selectById(taskId);
        ReTaskInstance instance = instanceMapper.selectById(taskInstanceId);
        ReTaskBranchAssignment assignment = assignmentMapper.selectById(assignmentId);
        requireContext(task, instance, assignment);
        requireFourDimensionTask(task);
        return currentSubmission(assignment);
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
        linkExistingSubmissionInternal(taskId, taskInstanceId, assignmentId, reSubmitId, null,
                dimensionCode, itemCode, operatorId, true);
    }

    /**
     * 将旧材料关联到指定任务提交版本并累计一次进度。
     *
     * <p>用于当前版本仍为 {@code BRANCH_PENDING} 的后续材料上报。任务提交版本已经存在，
     * 因此不再调用 workflow submit，但本次新材料仍应为对应维度累计一次有效上传。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void linkExistingSubmissionToVersion(ReSubmit request, Long reSubmitId,
                                                 Long taskId, Long taskInstanceId,
                                                 Long assignmentId, Long taskSubmissionId,
                                                 String dimensionCode, String itemCode,
                                                 String operatorId) {
        if (taskSubmissionId == null) {
            throw new BizException("RE-40036", "任务提交版本不能为空");
        }
        linkExistingSubmissionInternal(taskId, taskInstanceId, assignmentId, reSubmitId,
                taskSubmissionId,
                hasText(dimensionCode) ? dimensionCode : request == null ? null : request.getDimension(),
                hasText(itemCode) ? itemCode : request == null ? null : request.getItemCode(),
                operatorId, true);
    }

    /**
     * 执行旧材料与任务版本的关系写入。
     *
     * @param recordProgress 是否由本次桥接负责推进四维进度；workflow 首次提交后再次建桥时必须为 false
     */
    private void linkExistingSubmissionInternal(Long taskId, Long taskInstanceId, Long assignmentId,
                                                 Long reSubmitId, Long taskSubmissionId,
                                                 String dimensionCode, String itemCode,
                                                 String operatorId, boolean recordProgress) {
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
        ReTaskSubmission taskSubmission = requireTaskSubmissionContext(
                taskSubmissionId, taskId, taskInstanceId, assignmentId);

        String resolvedDimension = hasText(dimensionCode) ? dimensionCode.trim() : oldSubmission.getDimension();
        String resolvedItem = hasText(itemCode) ? itemCode.trim() : oldSubmission.getItemCode();
        if (!hasText(resolvedDimension)) {
            throw new BizException("RE-40033", "四维维度不能为空");
        }

        ReTaskReSubmitRel existing = relMapper.selectOne(new LambdaQueryWrapper<ReTaskReSubmitRel>()
                .eq(ReTaskReSubmitRel::getTaskInstanceId, taskInstanceId)
                .eq(ReTaskReSubmitRel::getAssignmentId, assignmentId)
                .eq(ReTaskReSubmitRel::getReSubmitId, reSubmitId));
        boolean relationCreated = false;
        if (existing == null) {
            ReTaskReSubmitRel relation = new ReTaskReSubmitRel();
            relation.setTaskId(taskId);
            relation.setTaskInstanceId(taskInstanceId);
            relation.setAssignmentId(assignmentId);
            relation.setReSubmitId(reSubmitId);
            relation.setTaskSubmissionId(taskSubmissionId);
            relation.setDimensionCode(resolvedDimension);
            relation.setItemCode(resolvedItem);
            relation.setCreatedBy(operatorId);
            relation.setCreateTime(now());
            try {
                relMapper.insert(relation);
                relationCreated = true;
            } catch (DuplicateKeyException duplicate) {
                // 关系表唯一键承担并发幂等；另一节点已创建时当前操作无需重复推进进度。
            }
        }
        if (existing != null && taskSubmissionId != null
                && !Objects.equals(existing.getTaskSubmissionId(), taskSubmissionId)) {
            if (existing.getTaskSubmissionId() != null) {
                throw new BizException("RE-40906", "材料已关联其他任务提交版本");
            }
            existing.setTaskSubmissionId(taskSubmissionId);
            if (relMapper.updateById(existing) != 1) {
                throw new BizException("RE-40901", "材料关联已被其他用户更新");
            }
        }
        if (recordProgress && relationCreated) {
            upsertProgress(task, instance, assignment, resolvedDimension, taskSubmission, now());
        }
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
        linkExistingSubmissionInternal(taskId, taskInstanceId, assignmentId, reSubmitId, null,
                request == null ? null : request.getDimension(),
                request == null ? null : request.getItemCode(), operatorId, true);
    }

    /**
     * 将 workflow 已创建的任务提交版本与旧材料建桥。
     *
     * <p>workflow {@code submit} 已经调用过一次 {@link #recordTaskUpload}；此入口只写关系，
     * 明确禁止再次推进四维进度，避免同一份材料被记为两次上传。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void linkFromLegacyRequest(ReSubmit request, Long reSubmitId, Long taskId,
                                      Long taskInstanceId, Long assignmentId,
                                      Long taskSubmissionId, String operatorId) {
        if (taskId == null || taskInstanceId == null || assignmentId == null) {
            return;
        }
        if (taskSubmissionId == null) {
            throw new BizException("RE-40036", "任务提交版本不能为空");
        }
        linkExistingSubmissionInternal(taskId, taskInstanceId, assignmentId, reSubmitId,
                taskSubmissionId,
                request == null ? null : request.getDimension(),
                request == null ? null : request.getItemCode(), operatorId, false);
    }

    /** 读取 assignment 当前版本，兼容旧数据中 CURRENT_VERSION 缺失的情况。 */
    private ReTaskSubmission currentSubmission(ReTaskBranchAssignment assignment) {
        if (assignment == null || assignment.getId() == null) {
            return null;
        }
        Integer currentVersion = assignment.getCurrentVersion();
        if (currentVersion != null && currentVersion > 0) {
            ReTaskSubmission current = taskSubmissionMapper.selectOne(
                    new LambdaQueryWrapper<ReTaskSubmission>()
                            .eq(ReTaskSubmission::getAssignmentId, assignment.getId())
                            .eq(ReTaskSubmission::getVersionNo, currentVersion));
            if (current != null) {
                return current;
            }
        }
        List<ReTaskSubmission> submissions = taskSubmissionMapper.selectList(
                new LambdaQueryWrapper<ReTaskSubmission>()
                        .eq(ReTaskSubmission::getAssignmentId, assignment.getId())
                        .orderByDesc(ReTaskSubmission::getVersionNo)
                        .orderByDesc(ReTaskSubmission::getId));
        if (submissions != null && !submissions.isEmpty()) {
            return submissions.stream().filter(Objects::nonNull).max(Comparator
                    .comparing(ReTaskSubmission::getVersionNo, Comparator.nullsFirst(Integer::compareTo))
                    .thenComparing(ReTaskSubmission::getId, Comparator.nullsFirst(Long::compareTo))).orElse(null);
        }
        return taskSubmissionMapper.selectOne(new LambdaQueryWrapper<ReTaskSubmission>()
                .eq(ReTaskSubmission::getAssignmentId, assignment.getId())
                .orderByDesc(ReTaskSubmission::getVersionNo)
                .orderByDesc(ReTaskSubmission::getId));
    }

    private ReTaskSubmission requireTaskSubmissionContext(Long taskSubmissionId, Long taskId,
                                                          Long taskInstanceId, Long assignmentId) {
        if (taskSubmissionId == null) {
            return null;
        }
        ReTaskSubmission submission = taskSubmissionMapper.selectById(taskSubmissionId);
        if (submission == null || !Objects.equals(submission.getTaskId(), taskId)
                || !Objects.equals(submission.getTaskInstanceId(), taskInstanceId)
                || !Objects.equals(submission.getAssignmentId(), assignmentId)) {
            throw new BizException("RE-40036", "任务提交版本与任务关联关系不一致");
        }
        return submission;
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
