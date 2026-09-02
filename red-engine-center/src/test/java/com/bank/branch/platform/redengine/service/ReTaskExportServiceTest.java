package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskBusinessType;
import com.bank.branch.platform.redengine.api.dto.ReTaskDetailDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskExportReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskExportRespDTO;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskExportTask;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskSubmission;
import com.bank.branch.platform.redengine.entity.ReTaskSubmissionFile;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskExportTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionFileMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务导出服务契约测试。
 *
 * <p>先固定四维明细项和请求幂等边界，再由实现补齐异步归档细节；不连接数据库或 OBS。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReTaskExportServiceTest {

    @Mock
    private ReTaskExportTaskMapper exportTaskMapper;
    @Mock
    private ReTaskMapper taskMapper;
    @Mock
    private ReTaskInstanceMapper instanceMapper;
    @Mock
    private ReTaskBranchAssignmentMapper assignmentMapper;
    @Mock
    private ReTaskSubmissionMapper submissionMapper;
    @Mock
    private ReTaskSubmissionFileMapper submissionFileMapper;
    @Mock
    private DictApi dictApi;
    @Mock
    private RePartyOrgMapper partyOrgMapper;
    @Mock
    private ReTaskManagementService taskManagementService;
    @Mock
    private FileApi fileApi;
    @Mock
    private ReTaskExportAsyncExecutor asyncExecutor;
    @Mock
    private ReTaskExportWorker exportWorker;

    @InjectMocks
    private ReTaskExportServiceImpl service;

    @Test
    void fourDimensionTaskRequiresAtLeastOneDetailItem() {
        ReTaskDetailDTO detail = new ReTaskDetailDTO();
        detail.setBusinessType(ReTaskBusinessType.FOUR_DIMENSION);
        when(taskManagementService.getDetail(7L, "E001")).thenReturn(detail);
        ReTask task = new ReTask();
        task.setId(7L);
        task.setTypeCode(ReTaskBusinessType.FOUR_DIMENSION.name());
        when(taskMapper.selectById(7L)).thenReturn(task);

        ReTaskExportReqDTO request = new ReTaskExportReqDTO();
        request.setItemCodes(List.of());

        assertThatThrownBy(() -> service.createExport(7L, request, "E001"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("明细项");
        verify(exportTaskMapper, never()).insert(any(ReTaskExportTask.class));
    }

    @Test
    void sameTaskAndSelectionReturnsExistingExportWithoutSubmittingAgain() {
        ReTaskDetailDTO detail = new ReTaskDetailDTO();
        detail.setBusinessType(ReTaskBusinessType.GENERAL);
        when(taskManagementService.getDetail(7L, "E001")).thenReturn(detail);

        ReTask existing = new ReTask();
        existing.setId(7L);
        existing.setTypeCode(ReTaskBusinessType.GENERAL.name());
        when(taskMapper.selectById(7L)).thenReturn(existing);

        ReTaskExportTask stored = new ReTaskExportTask();
        stored.setId("RTE_existing");
        stored.setTaskId(7L);
        when(exportTaskMapper.selectById(any(String.class))).thenReturn(stored);

        ReTaskExportReqDTO request = new ReTaskExportReqDTO();
        request.setItemCodes(List.of());

        ReTaskExportRespDTO response = service.createExport(7L, request, "E001");

        assertThat(response.getExportId()).isEqualTo("RTE_existing");
        verify(exportTaskMapper, never()).insert(any(ReTaskExportTask.class));
        verify(asyncExecutor, never()).submit(any(Runnable.class));
    }

    @Test
    void blankOrDuplicateDetailItemsAreRejectedBeforePersistence() {
        ReTaskDetailDTO detail = new ReTaskDetailDTO();
        detail.setBusinessType(ReTaskBusinessType.FOUR_DIMENSION);
        when(taskManagementService.getDetail(eq(7L), eq("E001"))).thenReturn(detail);
        ReTask task = new ReTask();
        task.setId(7L);
        task.setTypeCode(ReTaskBusinessType.FOUR_DIMENSION.name());
        when(taskMapper.selectById(7L)).thenReturn(task);

        ReTaskExportReqDTO request = new ReTaskExportReqDTO();
        request.setItemCodes(List.of("1.1", " 1.1 ", ""));

        assertThatThrownBy(() -> service.createExport(7L, request, "E001"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("明细项");
        verify(exportTaskMapper, never()).insert(any(ReTaskExportTask.class));
    }

    @Test
    void unknownOrDisabledDetailItemsAreRejectedByTheActiveDictionary() {
        ReTaskDetailDTO detail = new ReTaskDetailDTO();
        detail.setBusinessType(ReTaskBusinessType.FOUR_DIMENSION);
        when(taskManagementService.getDetail(7L, "E001")).thenReturn(detail);
        ReTask task = new ReTask();
        task.setId(7L);
        task.setTypeCode(ReTaskBusinessType.FOUR_DIMENSION.name());
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(dictApi.getDictItems("RE_ITEM_CODE")).thenReturn(List.of());

        ReTaskExportReqDTO request = new ReTaskExportReqDTO();
        request.setItemCodes(List.of("not-active"));

        assertThatThrownBy(() -> service.createExport(7L, request, "E001"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不存在或已停用");
        verify(exportTaskMapper, never()).insert(any(ReTaskExportTask.class));
    }

    @Test
    void fourDimensionItemValidationUsesGovernanceDictionaryApi() {
        ReTaskDetailDTO detail = new ReTaskDetailDTO();
        detail.setBusinessType(ReTaskBusinessType.FOUR_DIMENSION);
        when(taskManagementService.getDetail(7L, "E001")).thenReturn(detail);
        ReTask task = new ReTask();
        task.setId(7L);
        task.setTypeCode(ReTaskBusinessType.FOUR_DIMENSION.name());
        when(taskMapper.selectById(7L)).thenReturn(task);
        DictItemDTO item = new DictItemDTO();
        item.setDictType("RE_ITEM_CODE");
        item.setDictCode("ITEM_1");
        when(dictApi.getDictItems("RE_ITEM_CODE")).thenReturn(List.of(item));

        ReTaskExportReqDTO request = new ReTaskExportReqDTO();
        request.setItemCodes(List.of("ITEM_1"));

        ReTaskExportRespDTO response = service.createExport(7L, request, "E001");

        assertThat(response.getExportId()).startsWith("RTE_");
        verify(dictApi).getDictItems("RE_ITEM_CODE");
        verify(exportTaskMapper).insert(any(ReTaskExportTask.class));
    }

    @Test
    void fourDimensionSelectionMayUseSubsetOfEnabledDictionaryItems() {
        ReTaskDetailDTO detail = new ReTaskDetailDTO();
        detail.setBusinessType(ReTaskBusinessType.FOUR_DIMENSION);
        when(taskManagementService.getDetail(7L, "E001")).thenReturn(detail);
        ReTask task = new ReTask();
        task.setId(7L);
        task.setTypeCode(ReTaskBusinessType.FOUR_DIMENSION.name());
        when(taskMapper.selectById(7L)).thenReturn(task);

        DictItemDTO first = new DictItemDTO();
        first.setDictCode("ITEM_1");
        DictItemDTO second = new DictItemDTO();
        second.setDictCode("ITEM_2");
        when(dictApi.getDictItems("RE_ITEM_CODE")).thenReturn(List.of(first, second));

        ReTaskExportReqDTO request = new ReTaskExportReqDTO();
        request.setItemCodes(List.of("ITEM_1"));

        ReTaskExportRespDTO response = service.createExport(7L, request, "E001");

        assertThat(response.getExportId()).startsWith("RTE_");
        verify(exportTaskMapper).insert(any(ReTaskExportTask.class));
    }

    @Test
    void workerCreatesOneZipWithExcelAndDeduplicatedBranchAttachments() throws Exception {
        ReTaskExportTask queued = new ReTaskExportTask();
        queued.setId("RTE_worker");
        queued.setTaskId(7L);
        queued.setOperatorId("E001");
        queued.setStatus(com.bank.branch.platform.redengine.api.dto.ReTaskExportStatus.PENDING);
        queued.setDetailItemCodesJson("[]");
        when(exportTaskMapper.selectById("RTE_worker")).thenReturn(queued);
        when(exportTaskMapper.update(any(ReTaskExportTask.class), any())).thenReturn(1);

        ReTask task = new ReTask();
        task.setId(7L);
        task.setTypeCode(ReTaskBusinessType.GENERAL.name());
        task.setTitle("临时任务");
        when(taskMapper.selectById(7L)).thenReturn(task);

        ReTaskInstance instance = new ReTaskInstance();
        instance.setId(70L);
        instance.setTaskId(7L);
        ReTaskBranchAssignment assignment = new ReTaskBranchAssignment();
        assignment.setId(700L);
        assignment.setTaskInstanceId(70L);
        assignment.setBranchId(2L);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));
        when(assignmentMapper.selectList(any())).thenReturn(List.of(assignment));

        ReTaskSubmission submission = new ReTaskSubmission();
        submission.setId(7000L);
        submission.setAssignmentId(700L);
        submission.setVersionNo(1);
        submission.setSubmitterId("E002");
        submission.setContentText("填报内容");
        when(submissionMapper.selectList(any())).thenReturn(List.of(submission));

        ReTaskSubmissionFile first = new ReTaskSubmissionFile();
        first.setId(1L);
        first.setSubmissionId(7000L);
        first.setFileObjectId("F-1");
        first.setFileName("../../说明.txt");
        ReTaskSubmissionFile second = new ReTaskSubmissionFile();
        second.setId(2L);
        second.setSubmissionId(7000L);
        second.setFileObjectId("F-2");
        second.setFileName("说明.txt");
        when(submissionFileMapper.selectList(any())).thenReturn(List.of(first, second));

        RePartyOrg branch = new RePartyOrg();
        branch.setId(2L);
        branch.setOrgName("第一党支部");
        when(partyOrgMapper.selectById(2L)).thenReturn(branch);
        when(fileApi.getFileContent("F-1")).thenReturn(new byte[]{1});
        when(fileApi.getFileContent("F-2")).thenReturn(new byte[]{2});
        com.bank.branch.platform.governance.api.dto.FileObjectDTO artifact =
                new com.bank.branch.platform.governance.api.dto.FileObjectDTO();
        artifact.setId("F-ZIP");
        when(fileApi.upload(any(byte[].class), any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(artifact);

        service.executeExport("RTE_worker");

        org.mockito.ArgumentCaptor<byte[]> archiveCaptor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(fileApi).upload(archiveCaptor.capture(), any(String.class), eq("application/zip"), eq("E001"), any(String.class));
        Set<String> entries = new java.util.LinkedHashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archiveCaptor.getValue()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        assertThat(entries).contains("task-export.xlsx")
                .anyMatch(name -> name.startsWith("branch-2-第一党支部/"))
                .hasSize(3);
        assertThat(entries).noneMatch(name -> name.contains(".."));
    }

    @Test
    void workerDeletesUploadedArchiveWhenSuccessStateUpdateLosesRace() {
        ReTaskExportTask queued = new ReTaskExportTask();
        queued.setId("RTE_cleanup");
        queued.setTaskId(7L);
        queued.setOperatorId("E001");
        queued.setStatus(com.bank.branch.platform.redengine.api.dto.ReTaskExportStatus.PENDING);
        queued.setDetailItemCodesJson("[]");
        when(exportTaskMapper.selectById("RTE_cleanup")).thenReturn(queued);
        // claim succeeds, the conditional SUCCESS update loses the race, then FAILED is recorded.
        when(exportTaskMapper.update(any(ReTaskExportTask.class), any())).thenReturn(1, 0, 1);

        ReTask task = new ReTask();
        task.setId(7L);
        task.setTypeCode(ReTaskBusinessType.GENERAL.name());
        task.setTitle("临时任务");
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(instanceMapper.selectList(any())).thenReturn(List.of());

        com.bank.branch.platform.governance.api.dto.FileObjectDTO artifact =
                new com.bank.branch.platform.governance.api.dto.FileObjectDTO();
        artifact.setId("F-ZIP-RACE");
        when(fileApi.upload(any(byte[].class), any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(artifact);

        service.executeExport("RTE_cleanup");

        verify(fileApi).deleteFile("F-ZIP-RACE");
    }

    @Test
    void asyncWorkerIsSpringManagedAndTransactional() throws Exception {
        Class<?> workerClass = Class.forName(
                "com.bank.branch.platform.redengine.service.ReTaskExportWorker");
        assertThat(workerClass.getAnnotation(org.springframework.stereotype.Component.class)).isNotNull();
        java.lang.reflect.Method execute = workerClass.getDeclaredMethod("execute", String.class);
        assertThat(execute.getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNotNull();
    }

    @Test
    void createExport_submitsSpringManagedWorkerEntryPoint() {
        ReTaskDetailDTO detail = new ReTaskDetailDTO();
        detail.setBusinessType(ReTaskBusinessType.GENERAL);
        when(taskManagementService.getDetail(7L, "E001")).thenReturn(detail);
        ReTask task = new ReTask();
        task.setId(7L);
        task.setTypeCode(ReTaskBusinessType.GENERAL.name());
        when(taskMapper.selectById(7L)).thenReturn(task);

        ReTaskExportReqDTO request = new ReTaskExportReqDTO();
        request.setItemCodes(List.of());
        ReTaskExportRespDTO response = service.createExport(7L, request, "E001");

        ArgumentCaptor<Runnable> workerCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(asyncExecutor).submit(workerCaptor.capture());
        workerCaptor.getValue().run();
        verify(exportWorker).execute(response.getExportId());
    }
}
