package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bank.branch.platform.redengine.api.dto.ReTaskDeductionStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskDimensionProgressStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskExportStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskInstanceStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务域实体与已实施 yit_test 表的静态契约测试。
 * <p>该测试只检查 Java 元数据，不连接数据库；表结构的只读事实由仓库验收脚本提供。</p>
 */
class ReTaskEntitySchemaContractTest {

    @Test
    void taskDefinitionUsesTheImplementedColumnShape() throws Exception {
        assertTable(ReTask.class, "RE_TASK");
        assertFieldType(ReTask.class, "taskNo", String.class);
        assertFieldType(ReTask.class, "title", String.class);
        assertFieldType(ReTask.class, "description", String.class);
        assertFieldType(ReTask.class, "nature", String.class);
        assertFieldType(ReTask.class, "typeCode", String.class);
        assertFieldType(ReTask.class, "audienceType", String.class);
        assertFieldType(ReTask.class, "cycle", String.class);
        assertFieldType(ReTask.class, "startAt", LocalDateTime.class);
        assertFieldType(ReTask.class, "endAt", LocalDateTime.class);
        assertFieldType(ReTask.class, "effectiveFrom", java.time.LocalDate.class);
        assertFieldType(ReTask.class, "effectiveTo", java.time.LocalDate.class);
        assertFieldType(ReTask.class, "requiresFile", Integer.class);
        assertFieldType(ReTask.class, "versionNo", Integer.class);
        assertFieldType(ReTask.class, "publishedBy", String.class);
        assertVersioned(ReTask.class, "versionNo");
        assertLogicalDelete(ReTask.class, "deleted");
    }

    @Test
    void taskInstanceAndAssignmentUseNaturalDatetimeWindowsAndLatestVersionColumns() throws Exception {
        assertTable(ReTaskInstance.class, "RE_TASK_INSTANCE");
        assertFieldType(ReTaskInstance.class, "windowStartAt", LocalDateTime.class);
        assertFieldType(ReTaskInstance.class, "windowEndAt", LocalDateTime.class);
        assertFieldType(ReTaskInstance.class, "status", ReTaskInstanceStatus.class);
        assertFieldType(ReTaskInstance.class, "versionNo", Integer.class);
        assertVersioned(ReTaskInstance.class, "versionNo");

        assertTable(ReTaskBranchAssignment.class, "RE_TASK_BRANCH_ASSIGNMENT");
        assertFieldType(ReTaskBranchAssignment.class, "branchId", Long.class);
        assertFieldType(ReTaskBranchAssignment.class, "status", String.class);
        assertFieldType(ReTaskBranchAssignment.class, "currentVersion", Integer.class);
        assertFieldType(ReTaskBranchAssignment.class, "lastSubmittedAt", LocalDateTime.class);
        assertFieldType(ReTaskBranchAssignment.class, "lastSubmitterId", String.class);
    }

    @Test
    void submissionAndFileModelsMatchVersionedSubmissionColumns() throws Exception {
        assertTable(ReTaskSubmission.class, "RE_TASK_SUBMISSION");
        assertFieldType(ReTaskSubmission.class, "taskId", Long.class);
        assertFieldType(ReTaskSubmission.class, "taskInstanceId", Long.class);
        assertFieldType(ReTaskSubmission.class, "branchId", Long.class);
        assertFieldType(ReTaskSubmission.class, "versionNo", Integer.class);
        assertFieldType(ReTaskSubmission.class, "status", ReTaskSubmissionStatus.class);
        assertFieldType(ReTaskSubmission.class, "contentText", String.class);
        assertFieldType(ReTaskSubmission.class, "submitterId", String.class);
        assertLogicalDelete(ReTaskSubmission.class, "deleted");

        assertTable(ReTaskSubmissionFile.class, "RE_TASK_SUBMISSION_FILE");
        assertFieldType(ReTaskSubmissionFile.class, "fileType", String.class);
        assertFieldType(ReTaskSubmissionFile.class, "createdBy", String.class);
        assertLogicalDelete(ReTaskSubmissionFile.class, "deleted");
    }

    @Test
    void progressDeductionAndExportModelsAreRepresented() throws Exception {
        assertTable(ReTaskReSubmitRel.class, "RE_TASK_RE_SUBMIT_REL");
        assertFieldType(ReTaskReSubmitRel.class, "taskId", Long.class);
        assertFieldType(ReTaskReSubmitRel.class, "taskInstanceId", Long.class);
        assertFieldType(ReTaskReSubmitRel.class, "reSubmitId", Long.class);

        assertTable(ReTaskDimensionProgress.class, "RE_TASK_DIMENSION_PROGRESS");
        assertFieldType(ReTaskDimensionProgress.class, "dimensionCode", String.class);
        assertFieldType(ReTaskDimensionProgress.class, "uploadCount", Integer.class);
        assertFieldType(ReTaskDimensionProgress.class, "completedSubmissionId", Long.class);

        assertTable(ReTaskDeduction.class, "RE_TASK_DEDUCTION");
        assertFieldType(ReTaskDeduction.class, "taskId", Long.class);
        assertFieldType(ReTaskDeduction.class, "branchId", Long.class);
        assertFieldType(ReTaskDeduction.class, "deductionPoints", BigDecimal.class);
        assertFieldType(ReTaskDeduction.class, "executedBy", String.class);

        assertTable(ReTaskExportTask.class, "RE_TASK_EXPORT_TASK");
        assertFieldType(ReTaskExportTask.class, "id", String.class);
        assertFieldType(ReTaskExportTask.class, "sheetRowLimit", Integer.class);
        assertFieldType(ReTaskExportTask.class, "createdTime", LocalDateTime.class);
        assertFieldType(ReTaskExportTask.class, "updatedTime", LocalDateTime.class);
        assertEquals(TableId.class, ReTaskExportTask.class.getDeclaredField("id")
                .getAnnotation(TableId.class).annotationType());
    }

    @Test
    void everyImplementedTaskTableHasOnlyItsDocumentedColumns() {
        assertDeclaredFields(ReTask.class, "id", "taskNo", "title", "description", "nature", "typeCode",
                "audienceType", "cycle", "durationDays", "startAt", "endAt", "effectiveFrom", "effectiveTo",
                "requiresFile", "status", "publishedAt", "publishedBy", "createdBy", "versionNo", "updatedBy",
                "deleted", "createTime", "updateTime");
        assertDeclaredFields(ReTaskFileType.class, "id", "taskId", "fileTypeCode", "fileTypeName", "fileExtension",
                "mimeType", "maxSizeBytes", "sortNo", "enabled", "createdBy", "createTime", "updatedBy", "updateTime");
        assertDeclaredFields(ReTaskTarget.class, "id", "taskId", "targetType", "branchId", "employeeId", "targetKey",
                "targetLabel", "createdBy", "createTime");
        assertDeclaredFields(ReTaskInstance.class, "id", "taskId", "periodKey", "windowStartAt", "windowEndAt", "status",
                "generatedAt", "closedAt", "versionNo", "createTime", "updateTime");
        assertDeclaredFields(ReTaskBranchAssignment.class, "id", "taskInstanceId", "branchId", "status", "currentVersion",
                "lastSubmittedAt", "lastSubmitterId", "completedAt", "createTime", "updateTime");
        assertDeclaredFields(ReTaskTodo.class, "id", "assignmentId", "employeeId", "roleCode", "status", "availableAt",
                "completedAt", "createTime", "updateTime");
        assertDeclaredFields(ReTaskSubmission.class, "id", "taskId", "taskInstanceId", "assignmentId", "branchId", "versionNo",
                "status", "dimensionCode", "itemCode", "contentText", "formData", "submitterId", "submittedAt",
                "branchReviewerId", "branchReviewedAt", "orgReviewerId", "orgReviewedAt", "reviewOpinion", "deleted",
                "createTime", "updateTime");
        assertDeclaredFields(ReTaskSubmissionFile.class, "id", "submissionId", "fileObjectId", "fileName", "fileSize",
                "fileType", "sortNo", "createdBy", "createTime", "deleted");
        assertDeclaredFields(ReTaskReSubmitRel.class, "id", "taskId", "taskInstanceId", "assignmentId", "reSubmitId",
                "taskSubmissionId", "dimensionCode", "itemCode", "createdBy", "createTime");
        assertDeclaredFields(ReTaskStatusHistory.class, "id", "taskId", "taskInstanceId", "assignmentId", "submissionId",
                "actionCode", "fromStatus", "toStatus", "opinion", "operatorId", "occurredAt", "createTime");
        assertDeclaredFields(ReTaskDimensionProgress.class, "id", "taskId", "taskInstanceId", "assignmentId", "dimensionCode",
                "status", "uploadCount", "firstUploadedAt", "completedAt", "completedSubmissionId", "lastSubmissionId",
                "createTime", "updateTime");
        assertDeclaredFields(ReTaskDeduction.class, "id", "taskId", "taskInstanceId", "assignmentId", "branchId",
                "deductionPoints", "deductionReason", "status", "executedBy", "executedAt", "createTime", "updateTime");
        assertDeclaredFields(ReTaskExportTask.class, "id", "taskId", "operatorId", "detailItemCodesJson", "status", "rowCount",
                "sheetCount", "sheetRowLimit", "fileObjectId", "fileSize", "expireAt", "errorMessage", "startedAt", "finishedAt",
                "createdTime", "updatedTime");
    }

    @Test
    void everyDocumentedColumnUsesStorageCompatibleJavaType() throws Exception {
        assertFieldType(ReTask.class, "id", Long.class);
        assertFieldType(ReTask.class, "durationDays", Integer.class);
        assertFieldType(ReTask.class, "effectiveFrom", LocalDate.class);
        assertFieldType(ReTask.class, "effectiveTo", LocalDate.class);
        assertFieldType(ReTask.class, "status", ReTaskStatus.class);
        assertFieldType(ReTask.class, "createTime", LocalDateTime.class);

        assertFieldType(ReTaskFileType.class, "id", Long.class);
        assertFieldType(ReTaskFileType.class, "maxSizeBytes", Long.class);
        assertFieldType(ReTaskFileType.class, "sortNo", Integer.class);
        assertFieldType(ReTaskFileType.class, "enabled", Integer.class);

        assertFieldType(ReTaskTarget.class, "branchId", Long.class);
        assertFieldType(ReTaskTarget.class, "employeeId", String.class);

        assertFieldType(ReTaskInstance.class, "status", ReTaskInstanceStatus.class);
        assertFieldType(ReTaskInstance.class, "windowStartAt", LocalDateTime.class);
        assertFieldType(ReTaskInstance.class, "versionNo", Integer.class);

        assertFieldType(ReTaskBranchAssignment.class, "branchId", Long.class);
        assertFieldType(ReTaskBranchAssignment.class, "currentVersion", Integer.class);
        assertFieldType(ReTaskBranchAssignment.class, "lastSubmittedAt", LocalDateTime.class);

        assertFieldType(ReTaskTodo.class, "availableAt", LocalDateTime.class);
        assertFieldType(ReTaskTodo.class, "completedAt", LocalDateTime.class);

        assertFieldType(ReTaskSubmission.class, "versionNo", Integer.class);
        assertFieldType(ReTaskSubmission.class, "status", ReTaskSubmissionStatus.class);
        assertFieldType(ReTaskSubmission.class, "submittedAt", LocalDateTime.class);

        assertFieldType(ReTaskSubmissionFile.class, "fileSize", Long.class);
        assertFieldType(ReTaskSubmissionFile.class, "sortNo", Integer.class);

        assertFieldType(ReTaskReSubmitRel.class, "reSubmitId", Long.class);
        assertFieldType(ReTaskReSubmitRel.class, "taskSubmissionId", Long.class);

        assertFieldType(ReTaskStatusHistory.class, "submissionId", Long.class);
        assertFieldType(ReTaskStatusHistory.class, "occurredAt", LocalDateTime.class);

        assertFieldType(ReTaskDimensionProgress.class, "status", ReTaskDimensionProgressStatus.class);
        assertFieldType(ReTaskDimensionProgress.class, "uploadCount", Integer.class);
        assertFieldType(ReTaskDimensionProgress.class, "lastSubmissionId", Long.class);

        assertFieldType(ReTaskDeduction.class, "deductionPoints", BigDecimal.class);
        assertFieldType(ReTaskDeduction.class, "status", ReTaskDeductionStatus.class);
        assertFieldType(ReTaskDeduction.class, "executedAt", LocalDateTime.class);

        assertFieldType(ReTaskExportTask.class, "id", String.class);
        assertFieldType(ReTaskExportTask.class, "status", ReTaskExportStatus.class);
        assertFieldType(ReTaskExportTask.class, "sheetRowLimit", Integer.class);
        assertFieldType(ReTaskExportTask.class, "fileSize", Long.class);
        assertFieldType(ReTaskExportTask.class, "createdTime", LocalDateTime.class);
    }

    private static void assertDeclaredFields(Class<?> type, String... expected) {
        Set<String> actual = new HashSet<>(Arrays.stream(type.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList());
        assertEquals(Set.of(expected), actual, type.getSimpleName() + " declared columns");
    }

    private static void assertTable(Class<?> type, String expected) {
        TableName annotation = type.getAnnotation(TableName.class);
        assertNotNull(annotation, type.getSimpleName() + " must declare @TableName");
        assertEquals(expected, annotation.value());
    }

    private static void assertFieldType(Class<?> type, String fieldName, Class<?> expected) throws Exception {
        assertEquals(expected, type.getDeclaredField(fieldName).getType(),
                type.getSimpleName() + "." + fieldName);
    }

    private static void assertVersioned(Class<?> type, String fieldName) throws Exception {
        assertNotNull(type.getDeclaredField(fieldName).getAnnotation(Version.class),
                type.getSimpleName() + "." + fieldName + " must be @Version");
    }

    private static void assertLogicalDelete(Class<?> type, String fieldName) throws Exception {
        assertTrue(type.getDeclaredField(fieldName).isAnnotationPresent(TableLogic.class),
                type.getSimpleName() + "." + fieldName + " must be @TableLogic");
    }
}
