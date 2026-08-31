package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.ReTaskAttachmentDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskFileDownloadDTO;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskSubmission;
import com.bank.branch.platform.redengine.entity.ReTaskSubmissionFile;
import com.bank.branch.platform.redengine.entity.ReTaskTodo;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionFileMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTodoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLConnection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 任务附件服务。
 *
 * <p>下载顺序固定为：校验 assignment、任务、提交版本和当前用户红色引擎归属，
 * 再调用治理中心 FileApi 读取对象；因此拿不到 assignment 归属的用户不会触发
 * FileApi，避免把治理文件 ID 当作公开下载口令。</p>
 */
@Service
@RequiredArgsConstructor
public class ReTaskFileServiceImpl implements ReTaskFileService {

    static final String REPORTER_TODO_ROLE = "REPORTER";
    static final String BRANCH_SECRETARY_ROLE = "R_RE_SECR";
    static final String ORG_REVIEWER_ROLE = "R_RE_ORGREV";

    private final ReTaskMapper taskMapper;
    private final ReTaskInstanceMapper instanceMapper;
    private final ReTaskBranchAssignmentMapper assignmentMapper;
    private final ReTaskSubmissionMapper submissionMapper;
    private final ReTaskSubmissionFileMapper submissionFileMapper;
    private final ReTaskTodoMapper todoMapper;
    private final RePartyOrgMapper partyOrgMapper;
    private final CurrentUserApi currentUserApi;
    private final FileApi fileApi;

    /** 查询当前提交版本附件，并先执行红色引擎实体授权。 */
    @Override
    @Transactional(readOnly = true)
    public List<ReTaskAttachmentDTO> listAttachments(Long assignmentId, String operatorId) {
        requireAuthorizedContext(null, assignmentId, operatorId);
        ReTaskSubmission submission = latestSubmission(assignmentId);
        if (submission == null) {
            return List.of();
        }
        return loadFiles(submission.getId());
    }

    /** 校验红色引擎归属后，通过治理中心读取附件。 */
    @Override
    @Transactional(readOnly = true)
    public ReTaskFileDownloadDTO download(Long taskId, Long assignmentId, String fileId,
                                          String operatorId) {
        if (!hasText(fileId)) {
            throw new BizException("RE-40040", "附件不存在");
        }
        requireAuthorizedContext(taskId, assignmentId, operatorId);
        ReTaskSubmission submission = latestSubmission(assignmentId);
        if (submission == null || submission.getId() == null) {
            throw new BizException("RE-40040", "附件不存在");
        }
        ReTaskSubmissionFile file = submissionFileMapper.selectOne(
                new LambdaQueryWrapper<ReTaskSubmissionFile>()
                        .eq(ReTaskSubmissionFile::getSubmissionId, submission.getId())
                        .eq(ReTaskSubmissionFile::getFileObjectId, fileId.trim()));
        if (file == null) {
            throw new BizException("RE-40305", "附件不属于当前任务");
        }
        // 归属已确认后才允许访问治理中心，不能先用 FileApi 读取再补鉴权。
        byte[] content = fileApi.getFileContent(file.getFileObjectId());
        if (content == null) {
            throw new BizException("RE-40040", "附件内容不存在");
        }
        ReTaskFileDownloadDTO result = new ReTaskFileDownloadDTO();
        result.setFileName(hasText(file.getFileName()) ? file.getFileName() : fileApi.getFileName(fileId));
        result.setContentType(contentType(result.getFileName()));
        result.setContent(content);
        return result;
    }

    private ReTaskContext requireAuthorizedContext(Long taskId, Long assignmentId, String operatorId) {
        String current = currentUserApi.getCurrentEmpId();
        if (!hasText(operatorId) || !hasText(current) || !operatorId.trim().equals(current.trim())) {
            throw new BizException("RE-40301", "当前用户上下文缺失或不匹配");
        }
        if (assignmentId == null) {
            throw new BizException("RE-40010", "任务分配不存在");
        }
        ReTaskBranchAssignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null || assignment.getTaskInstanceId() == null) {
            throw new BizException("RE-40010", "任务分配不存在");
        }
        ReTaskInstance instance = instanceMapper.selectById(assignment.getTaskInstanceId());
        if (instance == null || instance.getTaskId() == null) {
            throw new BizException("RE-40011", "任务实例不存在");
        }
        if (taskId != null && !Objects.equals(taskId, instance.getTaskId())) {
            throw new BizException("RE-40305", "附件不属于当前任务");
        }
        ReTask task = taskMapper.selectById(instance.getTaskId());
        if (task == null || task.getId() == null) {
            throw new BizException("RE-40010", "任务不存在");
        }
        if (currentUserApi.isSystemAdmin() || hasRole(ORG_REVIEWER_ROLE)) {
            return new ReTaskContext(task, instance, assignment);
        }
        if (hasRole(BRANCH_SECRETARY_ROLE)) {
            RePartyOrg branch = assignment.getBranchId() == null ? null
                    : partyOrgMapper.selectById(assignment.getBranchId());
            if (branch != null && Integer.valueOf(2).equals(branch.getOrgLevel())
                    && operatorId.equals(branch.getSecretaryId())) {
                return new ReTaskContext(task, instance, assignment);
            }
        }
        ReTaskTodo todo = todoMapper.selectOne(new LambdaQueryWrapper<ReTaskTodo>()
                .eq(ReTaskTodo::getAssignmentId, assignmentId)
                .eq(ReTaskTodo::getEmployeeId, operatorId)
                .eq(ReTaskTodo::getRoleCode, REPORTER_TODO_ROLE)
                .ne(ReTaskTodo::getStatus, "CANCELLED"));
        if (todo == null) {
            throw new BizException("RE-40305", "无权访问该任务附件");
        }
        return new ReTaskContext(task, instance, assignment);
    }

    private List<ReTaskAttachmentDTO> loadFiles(Long submissionId) {
        List<ReTaskSubmissionFile> files = submissionFileMapper.selectList(
                new LambdaQueryWrapper<ReTaskSubmissionFile>()
                        .eq(ReTaskSubmissionFile::getSubmissionId, submissionId)
                        .orderByAsc(ReTaskSubmissionFile::getSortNo)
                        .orderByAsc(ReTaskSubmissionFile::getId));
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream().map(file -> {
            ReTaskAttachmentDTO result = new ReTaskAttachmentDTO();
            result.setId(file.getId());
            result.setFileId(file.getFileObjectId());
            result.setFileName(file.getFileName());
            result.setFileSize(file.getFileSize());
            result.setFileType(file.getFileType());
            result.setSortNo(file.getSortNo());
            return result;
        }).toList();
    }

    private ReTaskSubmission latestSubmission(Long assignmentId) {
        List<ReTaskSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<ReTaskSubmission>()
                        .eq(ReTaskSubmission::getAssignmentId, assignmentId)
                        .orderByDesc(ReTaskSubmission::getVersionNo)
                        .orderByDesc(ReTaskSubmission::getId));
        if (submissions != null && !submissions.isEmpty()) {
            return submissions.stream().max(Comparator
                    .comparing(ReTaskSubmission::getVersionNo, Comparator.nullsFirst(Integer::compareTo))
                    .thenComparing(ReTaskSubmission::getId, Comparator.nullsFirst(Long::compareTo))).orElse(null);
        }
        return submissionMapper.selectOne(new LambdaQueryWrapper<ReTaskSubmission>()
                .eq(ReTaskSubmission::getAssignmentId, assignmentId)
                .orderByDesc(ReTaskSubmission::getVersionNo)
                .orderByDesc(ReTaskSubmission::getId));
    }

    private boolean hasRole(String roleCode) {
        Set<String> roles = currentUserApi.getCurrentRoleCodes();
        return roles != null && roles.stream().filter(Objects::nonNull)
                .map(String::trim).anyMatch(roleCode::equalsIgnoreCase);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String contentType(String fileName) {
        String detected = fileName == null ? null : URLConnection.guessContentTypeFromName(fileName);
        return hasText(detected) ? detected : "application/octet-stream";
    }

    private record ReTaskContext(ReTask task, ReTaskInstance instance,
                                 ReTaskBranchAssignment assignment) {
    }
}
