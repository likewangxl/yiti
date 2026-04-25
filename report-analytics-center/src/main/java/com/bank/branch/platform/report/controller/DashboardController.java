package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.resp.PresidentDashboardRespDTO;
import com.bank.branch.platform.report.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 仪表盘 REST 控制器（C 章 3 接口，M2 阶段）.
 *
 * <p>M2.2：C.1 GET /api/reports/dashboard/president — 分行行长仪表盘
 * <p>M2.3：C.2 GET /api/reports/dashboard/org/{orgCode} + C.3 /dashboard/emp/{empId}
 *
 * <p>鉴权：所有 3 个接口 {@code @BizAuth(REPORT, READ)} + PT_RESOURCE ID（M2.4 注册）.
 */
@Slf4j
@RestController
@RequestMapping("/api/reports/dashboard")
@Tag(name = "报表-仪表盘", description = "C 章 3 接口（president/org/emp）")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * C.1 分行行长仪表盘.
     *
     * @param dataDate 数据日期，可选；为空时回填 today
     */
    @GetMapping("/president")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "C.1 分行行长仪表盘")
    public ResponseWrapper<PresidentDashboardRespDTO> getPresidentDashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDate) {
        return ResponseWrapper.success(dashboardService.getPresidentDashboard(dataDate));
    }
}
