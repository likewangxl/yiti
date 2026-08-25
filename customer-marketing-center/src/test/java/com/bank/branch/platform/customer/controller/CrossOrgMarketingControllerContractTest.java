package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.customer.dto.req.CrossOrgReviewReqDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** 跨机构营销高风险操作的 Controller/DTO 契约测试。 */
class CrossOrgMarketingControllerContractTest {

    @Test
    void writeAndReviewActions_shouldHaveRequiredAuditLog() throws Exception {
        assertAudit("create", CrossOrgMarketingController.class
                .getDeclaredMethod("create", com.bank.branch.platform.customer.dto.req.CrossOrgApplyCreateReqDTO.class),
                "CREATE_CROSS_ORG_MARKETING");
        assertAudit("approve", CrossOrgMarketingController.class
                .getDeclaredMethod("approve", String.class, CrossOrgReviewReqDTO.class),
                "APPROVE_CROSS_ORG_MARKETING");
        assertAudit("reject", CrossOrgMarketingController.class
                .getDeclaredMethod("reject", String.class, CrossOrgReviewReqDTO.class),
                "REJECT_CROSS_ORG_MARKETING");
    }

    @Test
    void approve_shouldRequireValidatedReviewBody() throws Exception {
        Method approve = CrossOrgMarketingController.class
                .getDeclaredMethod("approve", String.class, CrossOrgReviewReqDTO.class);
        Annotation[] requestAnnotations = approve.getParameterAnnotations()[1];
        RequestBody requestBody = find(requestAnnotations, RequestBody.class);
        Valid valid = find(requestAnnotations, Valid.class);

        assertThat(requestBody).isNotNull();
        assertThat(requestBody.required()).isTrue();
        assertThat(valid).isNotNull();
    }

    @Test
    void reviewReason_shouldBeRequiredAndLimitedTo500Characters() throws Exception {
        Field reason = CrossOrgReviewReqDTO.class.getDeclaredField("reason");

        assertThat(reason.getAnnotation(NotBlank.class)).isNotNull();
        Size size = reason.getAnnotation(Size.class);
        assertThat(size).isNotNull();
        assertThat(size.max()).isEqualTo(500);
    }

    private void assertAudit(String methodName, Method method, String action) {
        AuditLog audit = method.getAnnotation(AuditLog.class);
        assertThat(audit).as("%s must have @AuditLog", methodName).isNotNull();
        assertThat(audit.action()).isEqualTo(action);
        assertThat(audit.resourceType()).isEqualTo("CROSS_ORG_MARKETING");
        assertThat(audit.reasonRequired()).isTrue();
    }

    private <A extends Annotation> A find(Annotation[] annotations, Class<A> type) {
        for (Annotation annotation : annotations) {
            if (type.isInstance(annotation)) {
                return type.cast(annotation);
            }
        }
        return null;
    }
}
