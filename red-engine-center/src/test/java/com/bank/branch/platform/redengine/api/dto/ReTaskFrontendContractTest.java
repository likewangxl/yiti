package com.bank.branch.platform.redengine.api.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 任务 API 与当前任务原型请求模型的静态契约测试。
 * <p>前端以 taskNature/businessType/cycleType 及 targetType 发送请求，服务层再归一化为数据库值；
 * 该测试防止实体字段名或枚举值悄然偏离接口契约。</p>
 */
class ReTaskFrontendContractTest {

    @Test
    void createRequestKeepsFrontendNamesAndOptionalEffectiveRange() throws Exception {
        assertNotNull(ReTaskCreateReqDTO.class.getDeclaredField("taskNature"));
        assertNotNull(ReTaskCreateReqDTO.class.getDeclaredField("businessType"));
        assertNotNull(ReTaskCreateReqDTO.class.getDeclaredField("cycleType"));
        assertNotNull(ReTaskCreateReqDTO.class.getDeclaredField("temporaryStartTime"));
        assertNotNull(ReTaskCreateReqDTO.class.getDeclaredField("temporaryEndTime"));
        assertEquals(LocalDate.class, ReTaskCreateReqDTO.class.getDeclaredField("effectiveFrom").getType());
        assertEquals(LocalDate.class, ReTaskCreateReqDTO.class.getDeclaredField("effectiveTo").getType());
        assertNotNull(ReTaskTargetDTO.class.getDeclaredField("partyOrgId"));
        assertNotNull(ReTaskTargetDTO.class.getDeclaredField("employeeId"));
    }

    @Test
    void taskDetailAndExportRequestsKeepFrontendPayloadFields() throws Exception {
        assertNotNull(ReTaskAssignmentPageQueryDTO.class.getDeclaredField("keyword"));
        assertNotNull(ReTaskAssignmentPageQueryDTO.class.getDeclaredField("pageNo"));
        assertNotNull(ReTaskAssignmentPageQueryDTO.class.getDeclaredField("pageSize"));
        assertNotNull(ReTaskExportReqDTO.class.getDeclaredField("itemCodes"));
    }

    @Test
    void workflowPayloadExposesTabAndLegacyFourDimensionIds() throws Exception {
        assertNotNull(ReTaskWorkflowPageQueryDTO.class.getDeclaredField("tab"));
        assertNotNull(ReTaskWorkflowAssignmentDTO.class.getDeclaredField("legacyReviewId"));
        assertNotNull(ReTaskWorkflowAssignmentDTO.class.getDeclaredField("submitId"));
        assertNotNull(ReTaskWorkflowAssignmentDTO.class.getDeclaredField("reviewFeedback"));
        assertNotNull(ReTaskWorkflowAssignmentDTO.class.getDeclaredField("reviewHistory"));
        assertNotNull(ReTaskWorkflowActionRespDTO.class.getDeclaredField("legacyReviewId"));
        assertNotNull(ReTaskWorkflowActionRespDTO.class.getDeclaredField("submitId"));
    }

    @Test
    void reviewHistoryExposesAuditLabelsAndOperatorIdentity() throws Exception {
        assertNotNull(ReTaskWorkflowHistoryDTO.class.getDeclaredField("actionCode"));
        assertNotNull(ReTaskWorkflowHistoryDTO.class.getDeclaredField("actionLabel"));
        assertNotNull(ReTaskWorkflowHistoryDTO.class.getDeclaredField("stageLabel"));
        assertNotNull(ReTaskWorkflowHistoryDTO.class.getDeclaredField("operatorId"));
        assertNotNull(ReTaskWorkflowHistoryDTO.class.getDeclaredField("operatorName"));
        assertNotNull(ReTaskWorkflowHistoryDTO.class.getDeclaredField("occurredAt"));
        assertNotNull(ReTaskWorkflowHistoryDTO.class.getDeclaredField("opinion"));
        assertNotNull(ReTaskWorkflowHistoryDTO.class.getDeclaredField("fromStatus"));
        assertNotNull(ReTaskWorkflowHistoryDTO.class.getDeclaredField("toStatus"));
    }

    @Test
    void frontendAliasesNormalizeToImplementedDatabaseValues() {
        assertEquals(ReTaskNature.SCHEDULED, ReTaskNature.fromValue("PERIODIC"));
        assertEquals("SCHEDULED", ReTaskNature.SCHEDULED.getValue());
        assertEquals("ALL_BRANCHES", ReTaskTargetType.ALL_BRANCH.getAudienceType());
        assertEquals("SPECIFIED_BRANCHES", ReTaskTargetType.SPECIFIED_BRANCH.getAudienceType());
        assertEquals("SPECIFIED_EMPLOYEES", ReTaskTargetType.SPECIFIED_EMPLOYEE.getAudienceType());
        assertEquals("BRANCH", ReTaskTargetType.SPECIFIED_BRANCH.getStorageType());
        assertEquals("EMPLOYEE", ReTaskTargetType.SPECIFIED_EMPLOYEE.getStorageType());
        assertEquals(ReTaskTargetType.ALL_BRANCH, ReTaskTargetType.fromValue("ALL_BRANCHES"));
        assertEquals(ReTaskTargetType.SPECIFIED_BRANCH, ReTaskTargetType.fromValue("BRANCH"));
        assertEquals(ReTaskTargetType.SPECIFIED_EMPLOYEE, ReTaskTargetType.fromValue("SPECIFIED_EMPLOYEES"));
    }
}
