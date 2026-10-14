package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceProbeReqDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** 设计器列探测必须是独立管理端能力，不能复用运行时 /api/screen/data 的 schema1 后门。 */
class ScreenDatasourceAdminControllerContractTest {

    @Test
    void datasourceProbe_hasDedicatedHighRiskManagementEndpoint() throws Exception {
        Method probe = ScreenDatasourceAdminController.class
                .getMethod("probeColumns", Long.class, ScreenDatasourceProbeReqDTO.class);

        assertThat(probe.getAnnotation(PostMapping.class).value())
                .containsExactly("/datasources/{id}/probe-columns");
        assertThat(probe.getAnnotation(BizAuth.class).action()).isEqualTo(BizAction.EXECUTE_SQL);
    }

    /** 删除高危操作的理由必须显式走 HTTP 参数，不能由服务端写死或从请求体猜测。 */
    @Test
    void datasourceDelete_requiresExplicitReasonParameter() throws Exception {
        Method delete = ScreenDatasourceAdminController.class
                .getMethod("delete", Long.class, String.class);

        assertThat(delete.getAnnotation(DeleteMapping.class).value()).containsExactly("/datasources/{id}");
        assertThat(delete.getParameters()[1].getAnnotation(RequestParam.class)).isNotNull();
        assertThat(delete.getAnnotation(BizAuth.class).action()).isEqualTo(BizAction.DELETE);
    }

    /** 指标目录是报表列表能力，不能复用绩效配置权限，也不能走试跑的高危执行权限。 */
    @Test
    void metricCatalog_usesReportListOnDedicatedPath() throws Exception {
        Method catalog = ScreenDatasourceAdminController.class
                .getMethod("metricCatalog", String.class, String.class);

        assertThat(catalog.getAnnotation(GetMapping.class).value()).containsExactly("/metric-catalog");
        assertThat(catalog.getAnnotation(BizAuth.class).bizType()).isEqualTo(BizType.REPORT);
        assertThat(catalog.getAnnotation(BizAuth.class).action()).isEqualTo(BizAction.LIST);
    }
}
