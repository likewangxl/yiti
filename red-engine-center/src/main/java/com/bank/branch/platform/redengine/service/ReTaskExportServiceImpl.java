package com.bank.branch.platform.redengine.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskBusinessType;
import com.bank.branch.platform.redengine.api.dto.ReTaskDetailDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskExportReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskExportRespDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskExportRowDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskExportStatus;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.entity.ReSubmitFile;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskExportTask;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskReSubmitRel;
import com.bank.branch.platform.redengine.entity.ReTaskSubmission;
import com.bank.branch.platform.redengine.entity.ReTaskSubmissionFile;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReSubmitFileMapper;
import com.bank.branch.platform.redengine.mapper.ReSubmitMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskExportTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskReSubmitRelMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionFileMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 任务异步导出实现。
 *
 * <p>导出作业先落库再由执行器异步抢占；worker 只读取红色引擎本域表，并通过治理中心
 * {@link FileApi} 读取附件、上传最终 ZIP。任务管理服务在创建、查询和下载入口重新执行
 * 实体级权限/数据范围校验，异步阶段不依赖请求线程中的会话上下文。</p>
 *
 * <p>当前隔离库的导出状态列仍采用既有 {@code PENDING/SUCCESS} 值，本实现在 REST DTO
 * 映射为契约要求的 {@code QUEUED/SUCCEEDED}，避免在本阶段绕过 DBA 直接改表约束。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReTaskExportServiceImpl implements ReTaskExportService {

    static final int SHEET_ROW_LIMIT = 5000;
    private static final int EXPORT_ID_LENGTH = 60;
    private static final int EXPORT_EXPIRE_DAYS = 7;
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String FOUR_DIMENSION = ReTaskBusinessType.FOUR_DIMENSION.name();
    private static final String ITEM_CODE_DICT_TYPE = "RE_ITEM_CODE";
    private static final String FILE_CATEGORY = "re_task_export";
    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final String GENERIC_FAILURE = "导出失败，请稍后重试";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ReTaskExportTaskMapper exportTaskMapper;
    private final ReTaskMapper taskMapper;
    private final ReTaskInstanceMapper instanceMapper;
    private final ReTaskBranchAssignmentMapper assignmentMapper;
    private final ReTaskSubmissionMapper submissionMapper;
    private final ReTaskSubmissionFileMapper submissionFileMapper;
    private final ReTaskReSubmitRelMapper taskReSubmitRelMapper;
    private final ReSubmitMapper reSubmitMapper;
    private final ReSubmitFileMapper reSubmitFileMapper;
    private final RePartyOrgMapper partyOrgMapper;
    private final ReTaskManagementService taskManagementService;
    private final DictApi dictApi;
    private final FileApi fileApi;
    private final ReTaskExportAsyncExecutor asyncExecutor;
    private final ReTaskExportWorker exportWorker;

    /**
     * 创建异步导出作业。
     *
     * <p>作业 ID 由任务、操作者和规范化明细项计算而来，数据库主键同时承担跨节点幂等
     * 边界；重复请求直接复用已有作业，不重复提交 worker。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReTaskExportRespDTO createExport(Long taskId, ReTaskExportReqDTO request, String operatorId) {
        requireOperator(operatorId);
        ReTaskDetailDTO detail = taskManagementService.getDetail(taskId, operatorId);
        if (detail == null) {
            throw new BizException("RE-40010", "任务不存在");
        }
        ReTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("RE-40010", "任务不存在");
        }

        List<String> itemCodes = normalizeItemCodes(request == null ? null : request.getItemCodes());
        validateItemCodes(task, detail, itemCodes);
        String exportId = buildExportId(taskId, operatorId, itemCodes);
        ReTaskExportTask existing = exportTaskMapper.selectById(exportId);
        if (existing != null) {
            if (existing.getStatus() == ReTaskExportStatus.FAILED
                    || existing.getStatus() == ReTaskExportStatus.EXPIRED) {
                int requeued = exportTaskMapper.update(null, new LambdaUpdateWrapper<ReTaskExportTask>()
                        .eq(ReTaskExportTask::getId, exportId)
                        .in(ReTaskExportTask::getStatus,
                                ReTaskExportStatus.FAILED, ReTaskExportStatus.EXPIRED)
                        .set(ReTaskExportTask::getStatus, ReTaskExportStatus.PENDING)
                        .set(ReTaskExportTask::getRowCount, null)
                        .set(ReTaskExportTask::getSheetCount, null)
                        .set(ReTaskExportTask::getFileObjectId, null)
                        .set(ReTaskExportTask::getFileSize, null)
                        .set(ReTaskExportTask::getErrorMessage, null)
                        .set(ReTaskExportTask::getStartedAt, null)
                        .set(ReTaskExportTask::getFinishedAt, null)
                        .set(ReTaskExportTask::getUpdatedTime, now()));
                if (requeued == 1) {
                    submitAfterCommit(exportId);
                    existing.setStatus(ReTaskExportStatus.PENDING);
                    existing.setRowCount(null);
                    existing.setSheetCount(null);
                    existing.setFileObjectId(null);
                    existing.setFileSize(null);
                    existing.setErrorMessage(null);
                    existing.setStartedAt(null);
                    existing.setFinishedAt(null);
                    existing.setUpdatedTime(now());
                } else {
                    ReTaskExportTask winner = exportTaskMapper.selectById(exportId);
                    return toResponse(winner == null ? existing : winner);
                }
            }
            return toResponse(existing);
        }

        LocalDateTime now = now();
        ReTaskExportTask exportTask = new ReTaskExportTask();
        exportTask.setId(exportId);
        exportTask.setTaskId(taskId);
        exportTask.setOperatorId(operatorId.trim());
        exportTask.setDetailItemCodesJson(toJson(itemCodes));
        exportTask.setStatus(ReTaskExportStatus.PENDING);
        exportTask.setSheetRowLimit(SHEET_ROW_LIMIT);
        exportTask.setExpireAt(now.plusDays(EXPORT_EXPIRE_DAYS));
        exportTask.setCreatedTime(now);
        exportTask.setUpdatedTime(now);
        try {
            exportTaskMapper.insert(exportTask);
        } catch (DuplicateKeyException duplicate) {
            // 同一请求可能由其他节点先插入；回读赢家，仍只提交一个 worker。
            ReTaskExportTask winner = exportTaskMapper.selectById(exportId);
            if (winner == null) {
                throw duplicate;
            }
            return toResponse(winner);
        }
        submitAfterCommit(exportId);
        return toResponse(exportTask);
    }

    /** 查询导出作业；先通过任务管理服务执行当前用户的实体级数据范围校验。 */
    @Override
    @Transactional(readOnly = true)
    public ReTaskExportRespDTO getStatus(String exportId, String operatorId) {
        ReTaskExportTask exportTask = loadAuthorizedExport(exportId, operatorId);
        return toResponse(exportTask);
    }

    /** 读取已完成的 ZIP 产物；不将内部文件对象 ID 暴露给前端。 */
    @Override
    @Transactional(readOnly = true)
    public byte[] download(String exportId, String operatorId) {
        ReTaskExportTask exportTask = loadAuthorizedExport(exportId, operatorId);
        if (exportTask.getStatus() != ReTaskExportStatus.SUCCESS) {
            throw new BizException("RE-40901", "导出尚未完成");
        }
        if (exportTask.getExpireAt() != null && now().isAfter(exportTask.getExpireAt())) {
            throw new BizException("RE-41001", "导出文件已过期");
        }
        if (!hasText(exportTask.getFileObjectId())) {
            throw new BizException("RE-41002", "导出文件不可用");
        }
        byte[] bytes = fileApi.getFileContent(exportTask.getFileObjectId());
        if (bytes == null) {
            throw new BizException("RE-41002", "导出文件不可用");
        }
        return bytes;
    }

    /**
     * 异步 worker 入口，包可见便于定向单元测试；状态条件更新保证多节点只有一个执行者。
     */
    void executeExport(String exportId) {
        executeExportNow(exportId);
    }

    /**
     * 在 {@link ReTaskExportWorker} 已开启的事务中执行导出；不作为异步提交入口使用。
     */
    void executeExportNow(String exportId) {
        ReTaskExportTask queued = exportTaskMapper.selectById(exportId);
        if (queued == null || queued.getStatus() != ReTaskExportStatus.PENDING) {
            return;
        }

        LocalDateTime startedAt = now();
        ReTaskExportTask running = new ReTaskExportTask();
        running.setId(exportId);
        running.setStatus(ReTaskExportStatus.RUNNING);
        running.setStartedAt(startedAt);
        running.setUpdatedTime(startedAt);
        int claimed = exportTaskMapper.update(running, new LambdaUpdateWrapper<ReTaskExportTask>()
                .eq(ReTaskExportTask::getId, exportId)
                .eq(ReTaskExportTask::getStatus, ReTaskExportStatus.PENDING));
        if (claimed != 1) {
            return;
        }

        FileObjectDTO uploadedArtifact = null;
        try {
            ReTask task = taskMapper.selectById(queued.getTaskId());
            if (task == null) {
                throw new IllegalStateException("task missing");
            }
            ExportPayload payload = loadPayload(task, queued);
            byte[] archive = buildArchive(payload.rows(), payload.attachments());
            uploadedArtifact = fileApi.upload(archive,
                    "red-engine-task-export-" + exportId + ".zip",
                    ZIP_CONTENT_TYPE,
                    queued.getOperatorId(),
                    FILE_CATEGORY);
            if (uploadedArtifact == null || !hasText(uploadedArtifact.getId())) {
                throw new IllegalStateException("file object unavailable");
            }

            LocalDateTime finishedAt = now();
            ReTaskExportTask succeeded = new ReTaskExportTask();
            succeeded.setId(exportId);
            succeeded.setStatus(ReTaskExportStatus.SUCCESS);
            succeeded.setRowCount(payload.rows().size());
            succeeded.setSheetCount(sheetCount(payload.rows().size()));
            succeeded.setSheetRowLimit(SHEET_ROW_LIMIT);
            succeeded.setFileObjectId(uploadedArtifact.getId());
            succeeded.setFileSize((long) archive.length);
            succeeded.setFinishedAt(finishedAt);
            succeeded.setUpdatedTime(finishedAt);
            int updated = exportTaskMapper.update(succeeded, new LambdaUpdateWrapper<ReTaskExportTask>()
                    .eq(ReTaskExportTask::getId, exportId)
                    .eq(ReTaskExportTask::getStatus, ReTaskExportStatus.RUNNING));
            if (updated != 1) {
                log.warn("任务导出完成状态未写入 exportId={}", exportId);
                // 条件更新失败意味着当前节点没有取得产物所有权；删除已上传对象，避免
                // 状态仍为 RUNNING/被其他节点接管时留下不可达 ZIP。
                deleteUploadedArtifact(uploadedArtifact, exportId);
                uploadedArtifact = null;
                markFailed(exportId);
            }
        } catch (Exception failure) {
            log.error("任务异步导出失败 exportId={}", exportId, failure);
            deleteUploadedArtifact(uploadedArtifact, exportId);
            markFailed(exportId);
        }
    }

    /** 上传成功但后续状态落库失败时回收对象；清理失败不能掩盖原始导出失败。 */
    private void deleteUploadedArtifact(FileObjectDTO artifact, String exportId) {
        if (artifact == null || !hasText(artifact.getId())) {
            return;
        }
        try {
            fileApi.deleteFile(artifact.getId());
        } catch (RuntimeException cleanupFailure) {
            log.warn("任务导出产物清理失败 exportId={}", exportId, cleanupFailure);
        }
    }

    private void submitAfterCommit(String exportId) {
        Runnable worker = () -> exportWorker.execute(exportId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            asyncExecutor.submit(worker);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    asyncExecutor.submit(worker);
                } catch (RuntimeException rejected) {
                    log.error("任务导出 worker 提交失败 exportId={}", exportId, rejected);
                    markFailed(exportId);
                }
            }
        });
    }

    private void markFailed(String exportId) {
        LocalDateTime finishedAt = now();
        ReTaskExportTask failed = new ReTaskExportTask();
        failed.setId(exportId);
        failed.setStatus(ReTaskExportStatus.FAILED);
        failed.setErrorMessage(GENERIC_FAILURE);
        failed.setFinishedAt(finishedAt);
        failed.setUpdatedTime(finishedAt);
        exportTaskMapper.update(failed, new LambdaUpdateWrapper<ReTaskExportTask>()
                .eq(ReTaskExportTask::getId, exportId)
                .in(ReTaskExportTask::getStatus,
                        ReTaskExportStatus.PENDING, ReTaskExportStatus.RUNNING));
    }

    private ExportPayload loadPayload(ReTask task, ReTaskExportTask exportTask) {
        List<ReTaskInstance> instances = instanceMapper.selectList(
                new LambdaQueryWrapper<ReTaskInstance>().eq(ReTaskInstance::getTaskId, task.getId())
                        .orderByDesc(ReTaskInstance::getWindowStartAt)
                        .orderByDesc(ReTaskInstance::getId));
        if (instances == null || instances.isEmpty()) {
            return new ExportPayload(List.of(), List.of());
        }
        Map<Long, ReTaskInstance> instanceById = instances.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ReTaskInstance::getId, Function.identity(), (first, ignored) -> first));
        List<Long> instanceIds = new ArrayList<>(instanceById.keySet());
        if (instanceIds.isEmpty()) {
            return new ExportPayload(List.of(), List.of());
        }
        List<ReTaskBranchAssignment> assignments = assignmentMapper.selectList(
                new LambdaQueryWrapper<ReTaskBranchAssignment>()
                        .in(ReTaskBranchAssignment::getTaskInstanceId, instanceIds)
                        .orderByAsc(ReTaskBranchAssignment::getBranchId)
                        .orderByAsc(ReTaskBranchAssignment::getId));
        if (assignments == null || assignments.isEmpty()) {
            return new ExportPayload(List.of(), List.of());
        }

        List<Long> assignmentIds = assignments.stream().map(ReTaskBranchAssignment::getId)
                .filter(Objects::nonNull).toList();
        Map<Long, List<ReTaskSubmission>> submissionsByAssignment = submissionsByAssignment(assignmentIds);
        List<Long> submissionIds = submissionsByAssignment.values().stream().flatMap(Collection::stream)
                .map(ReTaskSubmission::getId).filter(Objects::nonNull).toList();
        Map<Long, List<ReTaskSubmissionFile>> filesBySubmission = filesBySubmission(submissionIds);
        boolean fourDimension = isFourDimension(task);
        Map<Long, List<ReTaskReSubmitRel>> legacyRelationsByAssignment = fourDimension
                ? legacyRelationsByAssignment(assignmentIds) : Map.of();
        List<Long> legacySubmissionIds = legacyRelationsByAssignment.values().stream()
                .flatMap(Collection::stream).map(ReTaskReSubmitRel::getReSubmitId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, ReSubmit> legacySubmissionsById = fourDimension
                ? legacySubmissionsById(legacySubmissionIds) : Map.of();
        Map<Long, List<ReSubmitFile>> legacyFilesBySubmission = fourDimension
                ? legacyFilesBySubmission(legacySubmissionIds) : Map.of();
        Map<Long, String> branchNameById = branchNames(assignments);
        Set<String> selectedCodes = new LinkedHashSet<>(readItemCodes(exportTask.getDetailItemCodesJson()));

        List<ReTaskExportRowDTO> rows = new ArrayList<>();
        List<ExportAttachment> attachments = new ArrayList<>();
        for (ReTaskBranchAssignment assignment : assignments) {
            ReTaskInstance instance = instanceById.get(assignment.getTaskInstanceId());
            if (instance == null) {
                continue;
            }
            List<ReTaskSubmission> selectedSubmissions = selectSubmissions(
                    submissionsByAssignment.getOrDefault(assignment.getId(), List.of()),
                    fourDimension, selectedCodes);
            List<LegacyExportSubmission> selectedLegacySubmissions = fourDimension
                    ? selectLegacySubmissions(legacyRelationsByAssignment.getOrDefault(assignment.getId(), List.of()),
                    legacySubmissionsById, selectedCodes) : List.of();
            if (!selectedLegacySubmissions.isEmpty() && !selectedSubmissions.isEmpty()) {
                Set<String> taskSubmissionCodes = selectedSubmissions.stream()
                        .map(item -> normalizeCode(item.getItemCode())).filter(this::hasText)
                        .collect(Collectors.toSet());
                selectedLegacySubmissions = selectedLegacySubmissions.stream()
                        .filter(item -> !taskSubmissionCodes.contains(normalizeCode(item.itemCode())))
                        .toList();
            }
            String branchName = branchNameById.getOrDefault(assignment.getBranchId(),
                    assignment.getBranchId() == null ? "未知党支部" : "支部-" + assignment.getBranchId());
            String branchFolder = branchFolder(assignment.getBranchId(), branchName);
            if (selectedSubmissions.isEmpty() && selectedLegacySubmissions.isEmpty()) {
                rows.add(toRow(task, instance, branchName, (ReTaskSubmission) null));
                continue;
            }
            for (ReTaskSubmission submission : selectedSubmissions) {
                rows.add(toRow(task, instance, branchName, submission));
                for (ReTaskSubmissionFile file : filesBySubmission.getOrDefault(submission.getId(), List.of())) {
                    if (!hasText(file.getFileObjectId())) {
                        throw new IllegalStateException("attachment id missing");
                    }
                    attachments.add(new ExportAttachment(branchFolder, file.getFileName(), file.getFileObjectId()));
                }
            }
            for (LegacyExportSubmission selectedLegacy : selectedLegacySubmissions) {
                rows.add(toRow(task, instance, branchName, selectedLegacy));
                for (ReSubmitFile file : legacyFilesBySubmission.getOrDefault(selectedLegacy.submission().getId(), List.of())) {
                    if (!hasText(file.getFileObjectId())) {
                        throw new IllegalStateException("legacy attachment id missing");
                    }
                    attachments.add(new ExportAttachment(branchFolder, file.getFileName(), file.getFileObjectId()));
                }
            }
        }
        return new ExportPayload(rows, attachments);
    }

    private Map<Long, List<ReTaskReSubmitRel>> legacyRelationsByAssignment(List<Long> assignmentIds) {
        if (assignmentIds.isEmpty()) {
            return Map.of();
        }
        List<ReTaskReSubmitRel> relations = taskReSubmitRelMapper.selectList(
                new LambdaQueryWrapper<ReTaskReSubmitRel>()
                        .in(ReTaskReSubmitRel::getAssignmentId, assignmentIds)
                        .orderByDesc(ReTaskReSubmitRel::getId));
        if (relations == null || relations.isEmpty()) {
            return Map.of();
        }
        return relations.stream().filter(item -> item.getAssignmentId() != null)
                .collect(Collectors.groupingBy(ReTaskReSubmitRel::getAssignmentId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, ReSubmit> legacySubmissionsById(List<Long> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        List<ReSubmit> submissions = reSubmitMapper.selectList(
                new LambdaQueryWrapper<ReSubmit>().in(ReSubmit::getId, submissionIds));
        if (submissions == null || submissions.isEmpty()) {
            return Map.of();
        }
        return submissions.stream().filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ReSubmit::getId, Function.identity(), (first, ignored) -> first));
    }

    private Map<Long, List<ReSubmitFile>> legacyFilesBySubmission(List<Long> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        List<ReSubmitFile> files = reSubmitFileMapper.selectList(
                new LambdaQueryWrapper<ReSubmitFile>().in(ReSubmitFile::getSubmitId, submissionIds)
                        .orderByAsc(ReSubmitFile::getId));
        if (files == null || files.isEmpty()) {
            return Map.of();
        }
        return files.stream().filter(item -> item.getSubmitId() != null)
                .collect(Collectors.groupingBy(ReSubmitFile::getSubmitId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    private List<LegacyExportSubmission> selectLegacySubmissions(List<ReTaskReSubmitRel> relations,
                                                                  Map<Long, ReSubmit> submissionsById,
                                                                  Set<String> selectedCodes) {
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        Map<String, LegacyExportSubmission> latestByItem = new LinkedHashMap<>();
        for (ReTaskReSubmitRel relation : relations) {
            if (relation.getTaskSubmissionId() != null || relation.getReSubmitId() == null) {
                continue;
            }
            ReSubmit submission = submissionsById.get(relation.getReSubmitId());
            if (submission == null) {
                continue;
            }
            String itemCode = hasText(relation.getItemCode())
                    ? relation.getItemCode() : submission.getItemCode();
            String normalizedItemCode = normalizeCode(itemCode);
            if (!hasText(normalizedItemCode) || !selectedCodes.contains(normalizedItemCode)) {
                continue;
            }
            latestByItem.putIfAbsent(normalizedItemCode,
                    new LegacyExportSubmission(submission, normalizedItemCode));
        }
        return latestByItem.values().stream()
                .sorted(Comparator.comparing(LegacyExportSubmission::itemCode))
                .toList();
    }

    private Map<Long, List<ReTaskSubmission>> submissionsByAssignment(List<Long> assignmentIds) {
        if (assignmentIds.isEmpty()) {
            return Map.of();
        }
        List<ReTaskSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<ReTaskSubmission>().in(ReTaskSubmission::getAssignmentId, assignmentIds)
                        .orderByDesc(ReTaskSubmission::getVersionNo)
                        .orderByDesc(ReTaskSubmission::getId));
        if (submissions == null || submissions.isEmpty()) {
            return Map.of();
        }
        return submissions.stream().filter(item -> item.getAssignmentId() != null)
                .collect(Collectors.groupingBy(ReTaskSubmission::getAssignmentId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, List<ReTaskSubmissionFile>> filesBySubmission(List<Long> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        List<ReTaskSubmissionFile> files = submissionFileMapper.selectList(
                new LambdaQueryWrapper<ReTaskSubmissionFile>().in(ReTaskSubmissionFile::getSubmissionId, submissionIds)
                        .orderByAsc(ReTaskSubmissionFile::getSortNo)
                        .orderByAsc(ReTaskSubmissionFile::getId));
        if (files == null || files.isEmpty()) {
            return Map.of();
        }
        return files.stream().filter(item -> item.getSubmissionId() != null)
                .collect(Collectors.groupingBy(ReTaskSubmissionFile::getSubmissionId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    private List<ReTaskSubmission> selectSubmissions(List<ReTaskSubmission> source,
                                                       boolean fourDimension,
                                                       Set<String> selectedCodes) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<ReTaskSubmission> candidates = source.stream()
                .filter(Objects::nonNull)
                .filter(item -> !fourDimension || selectedCodes.contains(normalizeCode(item.getItemCode())))
                .sorted(Comparator.comparingInt(this::version).reversed()
                        .thenComparing(ReTaskSubmission::getId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (!fourDimension) {
            return candidates.isEmpty() ? List.of() : List.of(candidates.get(0));
        }
        Map<String, ReTaskSubmission> latestByItem = new LinkedHashMap<>();
        for (ReTaskSubmission submission : candidates) {
            String itemCode = normalizeCode(submission.getItemCode());
            if (hasText(itemCode)) {
                latestByItem.putIfAbsent(itemCode, submission);
            }
        }
        return latestByItem.values().stream()
                .sorted(Comparator.comparing(ReTaskSubmission::getItemCode,
                        Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private ReTaskExportRowDTO toRow(ReTask task, ReTaskInstance instance,
                                      String branchName, ReTaskSubmission submission) {
        ReTaskExportRowDTO row = new ReTaskExportRowDTO();
        row.setTaskTitle(excelSafe(task.getTitle()));
        row.setPublishedAt(task.getPublishedAt());
        row.setTaskEndAt(instance.getWindowEndAt());
        row.setBranchName(excelSafe(branchName));
        if (submission != null) {
            row.setSubmitterId(excelSafe(submission.getSubmitterId()));
            row.setSubmittedAt(submission.getSubmittedAt());
            row.setContent(excelSafe(hasText(submission.getContentText())
                    ? submission.getContentText() : submission.getFormData()));
            row.setItemCode(excelSafe(submission.getItemCode()));
        }
        return row;
    }

    private ReTaskExportRowDTO toRow(ReTask task, ReTaskInstance instance,
                                      String branchName, LegacyExportSubmission selected) {
        ReSubmit submission = selected.submission();
        ReTaskExportRowDTO row = new ReTaskExportRowDTO();
        row.setTaskTitle(excelSafe(task.getTitle()));
        row.setPublishedAt(task.getPublishedAt());
        row.setTaskEndAt(instance.getWindowEndAt());
        row.setBranchName(excelSafe(branchName));
        row.setSubmitterId(excelSafe(submission.getSubmitterId()));
        row.setSubmittedAt(submission.getUpdateTime() != null
                ? submission.getUpdateTime()
                : submission.getSubmitDate() == null ? null : submission.getSubmitDate().atStartOfDay());
        row.setContent(excelSafe(submission.getFormData()));
        row.setItemCode(excelSafe(selected.itemCode()));
        return row;
    }

    private Map<Long, String> branchNames(List<ReTaskBranchAssignment> assignments) {
        Map<Long, String> result = new HashMap<>();
        for (ReTaskBranchAssignment assignment : assignments) {
            Long branchId = assignment.getBranchId();
            if (branchId == null || result.containsKey(branchId)) {
                continue;
            }
            RePartyOrg branch = partyOrgMapper.selectById(branchId);
            if (branch != null && hasText(branch.getOrgName())) {
                result.put(branchId, branch.getOrgName());
            }
        }
        return result;
    }

    private byte[] buildArchive(List<ReTaskExportRowDTO> rows, List<ExportAttachment> attachments) {
        byte[] excel = buildExcel(rows);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            putEntry(zip, "task-export.xlsx", excel);
            Map<String, Set<String>> usedNamesByFolder = new HashMap<>();
            for (ExportAttachment attachment : attachments) {
                byte[] bytes = fileApi.getFileContent(attachment.fileObjectId());
                if (bytes == null) {
                    throw new IllegalStateException("attachment content unavailable");
                }
                String folder = safeZipSegment(attachment.branchFolder(), "branch-unknown");
                Set<String> usedNames = usedNamesByFolder.computeIfAbsent(folder, ignored -> new HashSet<>());
                String fileName = uniqueFileName(attachment.fileName(), usedNames);
                putEntry(zip, folder + "/" + fileName, bytes);
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("archive creation failed", ex);
        }
    }

    private byte[] buildExcel(List<ReTaskExportRowDTO> rows) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ExcelWriterBuilder builder = EasyExcel.write(output, ReTaskExportRowDTO.class);
            try (ExcelWriter writer = builder.build()) {
                int totalSheets = sheetCount(rows.size());
                for (int sheetIndex = 0; sheetIndex < totalSheets; sheetIndex++) {
                    int from = sheetIndex * SHEET_ROW_LIMIT;
                    int to = Math.min(from + SHEET_ROW_LIMIT, rows.size());
                    List<ReTaskExportRowDTO> sheetRows = rows.subList(from, to);
                    WriteSheet sheet = EasyExcel.writerSheet(sheetIndex, "Sheet" + (sheetIndex + 1)).build();
                    writer.write(sheetRows, sheet);
                }
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("excel creation failed", ex);
        }
    }

    private void putEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private String uniqueFileName(String originalName, Set<String> usedNames) {
        String base = safeZipSegment(originalName, "attachment");
        String candidate = base;
        int sequence = 2;
        while (!usedNames.add(candidate.toLowerCase(Locale.ROOT))) {
            int dot = base.lastIndexOf('.');
            String stem = dot > 0 ? base.substring(0, dot) : base;
            String extension = dot > 0 ? base.substring(dot) : "";
            candidate = stem + " (" + sequence++ + ")" + extension;
        }
        return candidate;
    }

    private String safeZipSegment(String value, String fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String basename = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        basename = basename.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (basename.isEmpty() || ".".equals(basename) || "..".equals(basename)) {
            return fallback;
        }
        return basename.replace("..", "_");
    }

    private String branchFolder(Long branchId, String branchName) {
        String id = branchId == null ? "unknown" : String.valueOf(branchId);
        return "branch-" + id + "-" + safeZipSegment(branchName, "party");
    }

    private ReTaskExportTask loadAuthorizedExport(String exportId, String operatorId) {
        requireOperator(operatorId);
        if (!hasText(exportId)) {
            throw new BizException("RE-40030", "导出任务不存在");
        }
        ReTaskExportTask exportTask = exportTaskMapper.selectById(exportId.trim());
        if (exportTask == null || exportTask.getTaskId() == null) {
            throw new BizException("RE-40030", "导出任务不存在");
        }
        // 任务管理服务的角色、当前用户及实体范围守卫不能由导出接口绕过。
        if (taskManagementService.getDetail(exportTask.getTaskId(), operatorId) == null) {
            throw new BizException("RE-40304", "无权访问该导出任务");
        }
        return exportTask;
    }

    private void validateItemCodes(ReTask task, ReTaskDetailDTO detail, List<String> itemCodes) {
        boolean fourDimension = isFourDimension(task)
                || (detail != null && detail.getBusinessType() == ReTaskBusinessType.FOUR_DIMENSION);
        if (fourDimension && itemCodes.isEmpty()) {
            throw new BizException("RE-40031", "四大维度任务必须选择明细项");
        }
        if (!fourDimension && !itemCodes.isEmpty()) {
            throw new BizException("RE-40031", "非四大维度任务不能选择明细项");
        }
        if (fourDimension) {
            validateActiveItemCodes(itemCodes);
        }
    }

    private void validateActiveItemCodes(List<String> itemCodes) {
        if (dictApi == null) {
            // 治理中心字典 API 缺失时 fail-close，不能放行前端自带编码。
            throw new BizException("RE-50013", "四维明细项字典不可用");
        }
        // DictApi 只返回启用字典项；红色引擎不直接投影治理中心 SYS_DICT，避免跨模块
        // 依赖私有表结构，也使明细项配置可以由字典维护后立即生效。
        List<DictItemDTO> activeItems = dictApi.getDictItems(ITEM_CODE_DICT_TYPE);
        Set<String> activeCodes = activeItems == null ? Set.of() : activeItems.stream()
                .filter(Objects::nonNull)
                .map(DictItemDTO::getDictCode)
                .filter(this::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());
        if (!activeCodes.containsAll(itemCodes)) {
            throw new BizException("RE-40031", "明细项不存在或已停用");
        }
    }

    private List<String> normalizeItemCodes(List<String> itemCodes) {
        if (itemCodes == null || itemCodes.isEmpty()) {
            return List.of();
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String itemCode : itemCodes) {
            if (!hasText(itemCode)) {
                throw new BizException("RE-40031", "明细项不能为空");
            }
            String normalized = normalizeCode(itemCode);
            if (!distinct.add(normalized)) {
                throw new BizException("RE-40031", "明细项不能重复");
            }
        }
        return List.copyOf(distinct);
    }

    private String buildExportId(Long taskId, String operatorId, List<String> itemCodes) {
        String canonical = taskId + "|" + operatorId.trim() + "|" + String.join(",", itemCodes);
        return "RTE_" + sha256(canonical).substring(0, EXPORT_ID_LENGTH);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("export id generation failed", ex);
        }
    }

    private String toJson(List<String> itemCodes) {
        try {
            return OBJECT_MAPPER.writeValueAsString(itemCodes);
        } catch (IOException ex) {
            throw new IllegalStateException("export options serialization failed", ex);
        }
    }

    private List<String> readItemCodes(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            List<String> values = OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
            return values == null ? List.of() : values.stream()
                    .filter(this::hasText).map(this::normalizeCode).distinct().toList();
        } catch (IOException ex) {
            throw new IllegalStateException("export options corrupted", ex);
        }
    }

    private ReTaskExportRespDTO toResponse(ReTaskExportTask source) {
        ReTaskExportRespDTO target = new ReTaskExportRespDTO();
        target.setExportId(source.getId());
        target.setTaskId(source.getTaskId());
        target.setStatus(externalStatus(source.getStatus()));
        target.setTotalRows(source.getRowCount());
        target.setRowCount(source.getRowCount());
        target.setSheetCount(source.getSheetCount());
        target.setSheetRowLimit(source.getSheetRowLimit() == null ? SHEET_ROW_LIMIT : source.getSheetRowLimit());
        target.setFileSize(source.getFileSize());
        target.setExpireAt(source.getExpireAt());
        target.setErrorMessage(source.getStatus() == null || source.getStatus() == ReTaskExportStatus.FAILED
                ? GENERIC_FAILURE : null);
        target.setDownloadable(source.getStatus() == ReTaskExportStatus.SUCCESS
                && (source.getExpireAt() == null || !now().isAfter(source.getExpireAt())));
        return target;
    }

    private String externalStatus(ReTaskExportStatus status) {
        if (status == null) {
            return "FAILED";
        }
        return switch (status) {
            case PENDING -> "QUEUED";
            case SUCCESS -> "SUCCEEDED";
            case RUNNING -> "RUNNING";
            case FAILED -> "FAILED";
            case CANCELLED -> "CANCELLED";
            case EXPIRED -> "EXPIRED";
        };
    }

    private int sheetCount(int rowCount) {
        return Math.max(1, (rowCount + SHEET_ROW_LIMIT - 1) / SHEET_ROW_LIMIT);
    }

    private int version(ReTaskSubmission submission) {
        return submission.getVersionNo() == null ? 0 : submission.getVersionNo();
    }

    private boolean isFourDimension(ReTask task) {
        return task != null && FOUR_DIMENSION.equalsIgnoreCase(task.getTypeCode());
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim();
    }

    private String excelSafe(String value) {
        if (!hasText(value)) {
            return value;
        }
        String normalized = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ");
        if (normalized.startsWith("=") || normalized.startsWith("+")
                || normalized.startsWith("-") || normalized.startsWith("@")) {
            return "'" + normalized;
        }
        return normalized;
    }

    private void requireOperator(String operatorId) {
        if (!hasText(operatorId)) {
            throw new BizException("RE-40301", "当前用户上下文缺失");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BEIJING_ZONE);
    }

    private record ExportPayload(List<ReTaskExportRowDTO> rows, List<ExportAttachment> attachments) {
    }

    private record ExportAttachment(String branchFolder, String fileName, String fileObjectId) {
    }

    private record LegacyExportSubmission(ReSubmit submission, String itemCode) {
    }
}
