package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bank.branch.platform.redengine.api.dto.ReTaskDimensionProgressStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionStatus;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskDimensionProgress;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskReSubmitRel;
import com.bank.branch.platform.redengine.entity.ReTaskSubmission;
import com.bank.branch.platform.redengine.mapper.ReSubmitMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskDimensionProgressMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskReSubmitRelMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 四维旧材料与新任务关系、季度维度进度适配测试。 */
@ExtendWith(MockitoExtension.class)
class ReTaskFourDimensionAdapterTest {

    @Mock private ReTaskReSubmitRelMapper relMapper;
    @Mock private ReTaskDimensionProgressMapper progressMapper;
    @Mock private ReTaskSubmissionMapper taskSubmissionMapper;
    @Mock private ReSubmitMapper reSubmitMapper;
    @Mock private ReTaskMapper taskMapper;
    @Mock private ReTaskInstanceMapper instanceMapper;
    @Mock private ReTaskBranchAssignmentMapper assignmentMapper;

    @InjectMocks private ReTaskFourDimensionAdapter adapter;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ReTask.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskInstance.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskBranchAssignment.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskReSubmitRel.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskDimensionProgress.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskSubmission.class);
        TableInfoHelper.initTableInfo(assistant, ReSubmit.class);
    }

    @Test
    void linkExistingSubmission_createsRelationAndMarksDimensionCompleted() {
        ReTask task = task(10L);
        ReTaskInstance instance = instance(20L, 10L);
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L);
        ReSubmit old = new ReSubmit();
        old.setId(99L);
        old.setOrgId(40L);
        old.setDimension("DIM_1");
        old.setItemCode("ITEM_1");
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(reSubmitMapper.selectById(99L)).thenReturn(old);
        when(relMapper.selectOne(any())).thenReturn(null);
        when(progressMapper.selectOne(any())).thenReturn(null);
        when(relMapper.insert(any(ReTaskReSubmitRel.class))).thenAnswer(invocation -> {
            ReTaskReSubmitRel relation = invocation.getArgument(0);
            relation.setId(100L);
            return 1;
        });
        when(progressMapper.insert(any(ReTaskDimensionProgress.class))).thenReturn(1);

        adapter.linkExistingSubmission(10L, 20L, 30L, 99L, null, null, "REPORTER-1");

        verify(relMapper).insert(org.mockito.ArgumentMatchers.<ReTaskReSubmitRel>argThat(relation ->
                Long.valueOf(10L).equals(relation.getTaskId())
                        && Long.valueOf(20L).equals(relation.getTaskInstanceId())
                        && Long.valueOf(30L).equals(relation.getAssignmentId())
                        && Long.valueOf(99L).equals(relation.getReSubmitId())
                        && "DIM_1".equals(relation.getDimensionCode())
                        && "ITEM_1".equals(relation.getItemCode())));
        verify(progressMapper).insert(org.mockito.ArgumentMatchers.<ReTaskDimensionProgress>argThat(progress ->
                progress.getStatus() == ReTaskDimensionProgressStatus.COMPLETED
                        && progress.getUploadCount().equals(1)
                        && "DIM_1".equals(progress.getDimensionCode())));
    }

    @Test
    void recordTaskUpload_repeatedUploadIncrementsCountWithoutResettingFirstTime() {
        ReTask task = task(10L);
        ReTaskInstance instance = instance(20L, 10L);
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L);
        ReTaskDimensionProgress existing = new ReTaskDimensionProgress();
        existing.setId(101L);
        existing.setUploadCount(1);
        existing.setDimensionCode("DIM_1");
        existing.setStatus(ReTaskDimensionProgressStatus.COMPLETED);
        ReTaskSubmission submission = new ReTaskSubmission();
        submission.setId(102L);
        when(progressMapper.selectOne(any())).thenReturn(existing);
        when(progressMapper.update(any(), any())).thenReturn(1);

        adapter.recordTaskUpload(task, instance, assignment, submission, "DIM_1", "ITEM_1", "REPORTER-1");

        verify(progressMapper).update(isNull(), any());
    }

    @Test
    void recordTaskUpload_repeatedUploadUsesAtomicCounterIncrement() {
        ReTask task = task(10L);
        ReTaskInstance instance = instance(20L, 10L);
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L);
        ReTaskDimensionProgress existing = new ReTaskDimensionProgress();
        existing.setId(101L);
        existing.setUploadCount(7);
        existing.setDimensionCode("DIM_1");
        existing.setStatus(ReTaskDimensionProgressStatus.COMPLETED);
        when(progressMapper.selectOne(any())).thenReturn(existing);
        when(progressMapper.update(any(), any())).thenReturn(1);

        adapter.recordTaskUpload(task, instance, assignment, null,
                "DIM_1", "ITEM_1", "REPORTER-1");

        verify(progressMapper).update(isNull(), org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<ReTaskDimensionProgress>>argThat(
                wrapper -> wrapper.getSqlSet().contains("upload_count = COALESCE(upload_count, 0) + 1")));
    }

    @Test
    void linkFromLegacyRequest_withWorkflowVersionOnlyCreatesBridgeWithoutProgressIncrement() {
        ReTask task = task(10L);
        ReTaskInstance instance = instance(20L, 10L);
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L);
        ReSubmit old = new ReSubmit();
        old.setId(99L);
        old.setOrgId(40L);
        old.setDimension("DIM_1");
        old.setItemCode("ITEM_1");
        ReTaskSubmission taskSubmission = new ReTaskSubmission();
        taskSubmission.setId(700L);
        taskSubmission.setTaskId(10L);
        taskSubmission.setTaskInstanceId(20L);
        taskSubmission.setAssignmentId(30L);
        when(taskSubmissionMapper.selectById(700L)).thenReturn(taskSubmission);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(reSubmitMapper.selectById(99L)).thenReturn(old);
        when(relMapper.selectOne(any())).thenReturn(null);
        when(relMapper.insert(any(ReTaskReSubmitRel.class))).thenAnswer(invocation -> {
            ReTaskReSubmitRel relation = invocation.getArgument(0);
            relation.setId(100L);
            return 1;
        });

        adapter.linkFromLegacyRequest(old, 99L, 10L, 20L, 30L, 700L, "REPORTER-1");

        verify(relMapper).insert(org.mockito.ArgumentMatchers.<ReTaskReSubmitRel>argThat(relation ->
                Long.valueOf(700L).equals(relation.getTaskSubmissionId())
                        && Long.valueOf(99L).equals(relation.getReSubmitId())
                        && "DIM_1".equals(relation.getDimensionCode())
                        && "ITEM_1".equals(relation.getItemCode())));
        verify(progressMapper, never()).selectOne(any());
        verify(progressMapper, never()).insert(any(ReTaskDimensionProgress.class));
        verify(progressMapper, never()).update(any(), any());
    }

    @Test
    void linkExistingSubmissionToVersion_incrementsDimensionProgressOnceAndReferencesVersion() {
        ReTask task = task(10L);
        ReTaskInstance instance = instance(20L, 10L);
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L);
        ReSubmit old = new ReSubmit();
        old.setId(99L);
        old.setOrgId(40L);
        old.setDimension("DIM_1");
        old.setItemCode("ITEM_1");
        ReTaskSubmission taskSubmission = taskSubmission(700L, 10L, 20L, 30L);
        when(taskSubmissionMapper.selectById(700L)).thenReturn(taskSubmission);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(reSubmitMapper.selectById(99L)).thenReturn(old);
        when(relMapper.selectOne(any())).thenReturn(null);
        when(relMapper.insert(any(ReTaskReSubmitRel.class))).thenAnswer(invocation -> {
            ReTaskReSubmitRel relation = invocation.getArgument(0);
            relation.setId(100L);
            return 1;
        });
        when(progressMapper.selectOne(any())).thenReturn(null);
        when(progressMapper.insert(any(ReTaskDimensionProgress.class))).thenReturn(1);

        adapter.linkExistingSubmissionToVersion(old, 99L, 10L, 20L, 30L,
                700L, "DIM_1", "ITEM_1", "REPORTER-1");

        verify(relMapper).insert(org.mockito.ArgumentMatchers.<ReTaskReSubmitRel>argThat(relation ->
                Long.valueOf(700L).equals(relation.getTaskSubmissionId())));
        verify(progressMapper).insert(org.mockito.ArgumentMatchers.<ReTaskDimensionProgress>argThat(progress ->
                Integer.valueOf(1).equals(progress.getUploadCount())
                        && Long.valueOf(700L).equals(progress.getCompletedSubmissionId())
                        && Long.valueOf(700L).equals(progress.getLastSubmissionId())));
    }

    @Test
    void findCurrentSubmission_returnsCurrentAssignmentVersion() {
        ReTask task = task(10L);
        ReTaskInstance instance = instance(20L, 10L);
        ReTaskBranchAssignment assignment = assignment(30L, 20L, 40L);
        assignment.setCurrentVersion(2);
        ReTaskSubmission current = new ReTaskSubmission();
        current.setId(700L);
        current.setAssignmentId(30L);
        current.setVersionNo(2);
        current.setStatus(ReTaskSubmissionStatus.BRANCH_PENDING);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(instanceMapper.selectById(20L)).thenReturn(instance);
        when(assignmentMapper.selectById(30L)).thenReturn(assignment);
        when(taskSubmissionMapper.selectOne(any())).thenReturn(current);

        ReTaskSubmission result = adapter.findCurrentSubmission(10L, 20L, 30L);

        assertThat(result).isSameAs(current);
        verify(taskSubmissionMapper).selectOne(any());
    }

    private static ReTask task(Long id) {
        ReTask task = new ReTask();
        task.setId(id);
        task.setTypeCode("FOUR_DIMENSION");
        return task;
    }

    private static ReTaskInstance instance(Long id, Long taskId) {
        ReTaskInstance instance = new ReTaskInstance();
        instance.setId(id);
        instance.setTaskId(taskId);
        return instance;
    }

    private static ReTaskBranchAssignment assignment(Long id, Long instanceId, Long branchId) {
        ReTaskBranchAssignment assignment = new ReTaskBranchAssignment();
        assignment.setId(id);
        assignment.setTaskInstanceId(instanceId);
        assignment.setBranchId(branchId);
        return assignment;
    }

    private static ReTaskSubmission taskSubmission(Long id, Long taskId, Long instanceId,
                                                   Long assignmentId) {
        ReTaskSubmission submission = new ReTaskSubmission();
        submission.setId(id);
        submission.setTaskId(taskId);
        submission.setTaskInstanceId(instanceId);
        submission.setAssignmentId(assignmentId);
        submission.setStatus(ReTaskSubmissionStatus.BRANCH_PENDING);
        return submission;
    }
}
