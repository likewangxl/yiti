package com.bank.branch.platform.bizapp.controller;

import com.bank.branch.platform.bizapp.dto.req.TransferReq;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 高危端点 @AuditLog 注解存在性验证测试。
 * <p>
 * 采用反射断言，覆盖 3 个 Controller 共 14 个高危端点：
 * - LoanController: create / update / delete / submit / cancel / export (6 个)
 * - SupportController: create / submit / delete / cancel / export (5 个)
 * - SupportDeptController: dispatch / transfer / complete (3 个)
 * </p>
 *
 * <p><b>未覆盖的端点(待实现):</b> 文档 07-审计要求 §1 共 18 条,本测试覆盖 14 个已实现端点。
 * 以下 4 个端点尚未在 Controller 中实现,待后续实现时需同步补齐 @AuditLog:
 * <ul>
 *   <li>§1 序号 7:POST /api/loans/\{id}/tasks/\{taskId}/complete (节点审批)</li>
 *   <li>§1 序号 9:PUT /api/support-requests/\{id} (支持申请更新)</li>
 *   <li>§1 序号 17:POST /api/support-dept/requests/\{id}/reject (承接侧驳回)</li>
 *   <li>§1 序号 18:GET /api/support-dept/requests/export (承接侧导出)</li>
 * </ul>
 *
 * @since Task 1.6
 */
class HighRiskEndpointAuditTest {

    /**
     * 辅助方法：按方法名查找方法，存在重载时抛出异常以避免假绿。
     * 若存在多个同名重载，请改用 {@link Class#getDeclaredMethod(String, Class[])} 精确匹配。
     */
    private Method findMethod(Class<?> cls, String methodName) {
        List<Method> matches = Arrays.stream(cls.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .collect(Collectors.toList());
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "在 " + cls.getSimpleName() + " 中方法名 " + methodName + " 存在 " + matches.size()
                            + " 个重载,请改用 getDeclaredMethod(name, paramTypes) 精确查找");
        }
        return matches.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "在 " + cls.getSimpleName() + " 中未找到方法: " + methodName));
    }

    // ==================== LoanController (6 个高危端点) ====================

    @Test
    @DisplayName("LoanController 的 6 个高危端点必须都有 @AuditLog")
    void loanController_allHighRiskEndpoints_haveAuditLog() {
        Class<?> cls = LoanController.class;
        String[] methods = {"create", "update", "delete", "submit", "cancel", "export"};
        for (String name : methods) {
            Method m = findMethod(cls, name);
            assertThat(m.getAnnotation(AuditLog.class))
                    .as("LoanController.%s 必须有 @AuditLog", name)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("LoanController cancel 和 export 的 @AuditLog reasonRequired 必须为 true")
    void loanController_cancelAndExport_reasonRequired() {
        Class<?> cls = LoanController.class;

        AuditLog cancelAudit = findMethod(cls, "cancel").getAnnotation(AuditLog.class);
        assertThat(cancelAudit).as("LoanController.cancel 必须有 @AuditLog").isNotNull();
        assertThat(cancelAudit.reasonRequired())
                .as("LoanController.cancel 的 @AuditLog.reasonRequired 必须为 true")
                .isTrue();

        AuditLog exportAudit = findMethod(cls, "export").getAnnotation(AuditLog.class);
        assertThat(exportAudit).as("LoanController.export 必须有 @AuditLog").isNotNull();
        assertThat(exportAudit.reasonRequired())
                .as("LoanController.export 的 @AuditLog.reasonRequired 必须为 true")
                .isTrue();
    }

    // ==================== SupportController (5 个高危端点) ====================

    @Test
    @DisplayName("SupportController 的 5 个高危端点必须都有 @AuditLog")
    void supportController_allHighRiskEndpoints_haveAuditLog() {
        Class<?> cls = SupportController.class;
        String[] methods = {"create", "submit", "delete", "cancel", "export"};
        for (String name : methods) {
            Method m = findMethod(cls, name);
            assertThat(m.getAnnotation(AuditLog.class))
                    .as("SupportController.%s 必须有 @AuditLog", name)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("SupportController cancel 和 export 的 @AuditLog reasonRequired 必须为 true")
    void supportController_cancelAndExport_reasonRequired() {
        Class<?> cls = SupportController.class;

        AuditLog cancelAudit = findMethod(cls, "cancel").getAnnotation(AuditLog.class);
        assertThat(cancelAudit).as("SupportController.cancel 必须有 @AuditLog").isNotNull();
        assertThat(cancelAudit.reasonRequired())
                .as("SupportController.cancel 的 @AuditLog.reasonRequired 必须为 true")
                .isTrue();

        AuditLog exportAudit = findMethod(cls, "export").getAnnotation(AuditLog.class);
        assertThat(exportAudit).as("SupportController.export 必须有 @AuditLog").isNotNull();
        assertThat(exportAudit.reasonRequired())
                .as("SupportController.export 的 @AuditLog.reasonRequired 必须为 true")
                .isTrue();
    }

    // ==================== SupportDeptController (3 个高危端点) ====================

    @Test
    @DisplayName("SupportDeptController 的 3 个高危端点必须都有 @AuditLog")
    void supportDeptController_allHighRiskEndpoints_haveAuditLog() {
        Class<?> cls = SupportDeptController.class;
        String[] methods = {"dispatch", "transfer", "complete"};
        for (String name : methods) {
            Method m = findMethod(cls, name);
            assertThat(m.getAnnotation(AuditLog.class))
                    .as("SupportDeptController.%s 必须有 @AuditLog", name)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("SupportDeptController.transfer 的 @AuditLog reasonRequired 必须为 true（文档 07-审计要求 §1.3 明确要求）")
    void supportDeptController_transfer_reasonRequired() throws Exception {
        Method transfer = SupportDeptController.class.getDeclaredMethod(
                "transfer", String.class, TransferReq.class);
        AuditLog audit = transfer.getAnnotation(AuditLog.class);
        assertThat(audit).as("SupportDeptController.transfer 必须有 @AuditLog").isNotNull();
        assertThat(audit.reasonRequired())
                .as("transfer 操作 reasonRequired 必须为 true（转交必须填写原因）")
                .isTrue();
    }
}
