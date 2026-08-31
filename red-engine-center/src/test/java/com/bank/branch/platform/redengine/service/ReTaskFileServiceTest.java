package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskSubmission;
import com.bank.branch.platform.redengine.entity.ReTaskSubmissionFile;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionFileMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTodoMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 任务附件访问的实体归属和 FileApi 调用顺序测试。 */
@ExtendWith(MockitoExtension.class)
class ReTaskFileServiceTest {

    @Mock private ReTaskMapper taskMapper;
    @Mock private ReTaskInstanceMapper instanceMapper;
    @Mock private ReTaskBranchAssignmentMapper assignmentMapper;
    @Mock private ReTaskSubmissionMapper submissionMapper;
    @Mock private ReTaskSubmissionFileMapper submissionFileMapper;
    @Mock private ReTaskTodoMapper todoMapper;
    @Mock private RePartyOrgMapper partyOrgMapper;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private FileApi fileApi;

    @InjectMocks private ReTaskFileServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ReTask.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskInstance.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskBranchAssignment.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskSubmission.class);
        TableInfoHelper.initTableInfo(assistant, ReTaskSubmissionFile.class);
    }

    @Test
    void unauthorizedDownload_failsBeforeCallingFileApi() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E-404");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_REPORT"));
        when(assignmentMapper.selectById(30L)).thenReturn(assignment(30L, 20L, 40L));
        when(instanceMapper.selectById(20L)).thenReturn(instance(20L, 10L));
        when(taskMapper.selectById(10L)).thenReturn(task(10L));
        when(todoMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.download(10L, 30L, "FILE-1", "E-404"))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .hasMessage("无权访问该任务附件");
        verify(fileApi, never()).getFileContent(any());
    }

    @Test
    void authorizedDownload_checksLocalFileOwnershipThenReadsFileApi() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("ORG-REVIEWER");
        when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_RE_ORGREV"));
        when(assignmentMapper.selectById(30L)).thenReturn(assignment(30L, 20L, 40L));
        when(instanceMapper.selectById(20L)).thenReturn(instance(20L, 10L));
        when(taskMapper.selectById(10L)).thenReturn(task(10L));
        ReTaskSubmission submission = submission(60L, 30L);
        when(submissionMapper.selectList(any())).thenReturn(List.of(submission));
        ReTaskSubmissionFile file = new ReTaskSubmissionFile();
        file.setId(70L);
        file.setSubmissionId(60L);
        file.setFileObjectId("FILE-1");
        file.setFileName("evidence.pdf");
        when(submissionFileMapper.selectOne(any())).thenReturn(file);
        when(fileApi.getFileContent("FILE-1")).thenReturn(new byte[]{1, 2, 3});

        var result = service.download(10L, 30L, "FILE-1", "ORG-REVIEWER");

        assertThat(result.getFileName()).isEqualTo("evidence.pdf");
        assertThat(result.getContent()).containsExactly(1, 2, 3);
        verify(fileApi).getFileContent("FILE-1");
    }

    private static ReTask task(Long id) {
        ReTask task = new ReTask();
        task.setId(id);
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

    private static ReTaskSubmission submission(Long id, Long assignmentId) {
        ReTaskSubmission submission = new ReTaskSubmission();
        submission.setId(id);
        submission.setAssignmentId(assignmentId);
        submission.setVersionNo(1);
        return submission;
    }
}
