package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.api.dto.PerfEmpOptionDTO;
import com.bank.branch.platform.performance.service.PerfEmployeeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 绩效域员工查询控制器：给目标值等页面提供「按工号选人」的输入建议。
 *
 * <p>路径映射：
 * <ul>
 *   <li>GET /api/perf/employees/search → P_PERF_EMP_SRCH</li>
 * </ul>
 *
 * <p>鉴权用 {@code PERF_CONFIG:LIST}——与目标值/目标方案列表同一权限位。能配绩效的人
 * 本就要给全行员工下达目标，看到员工名单是这项工作的前提；反过来，不给 PERF_CONFIG 的人
 * 也拿不到本接口。选择该权限位而非新开一个，是为了让「能配绩效」与「能选人」保持一致，
 * 避免出现能进目标值页面却选不了人的半残状态。
 *
 * <p>不复用 {@code /api/admin/users}（管理员接口，业务角色无权）、{@code /api/employees}
 * （通讯录覆盖面不足）、{@code /api/reports/employees/search}（返回 USER_ID 而非工号）的
 * 完整理由见 {@link PerfEmployeeQueryService} 类注释。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/perf/employees")
@Tag(name = "绩效-员工查询", description = "目标值等页面按工号选人的输入建议")
public class PerfEmployeeController {

    private final PerfEmployeeQueryService perfEmployeeQueryService;

    /**
     * 按关键字搜索员工（工号/姓名模糊匹配）。
     *
     * @param keyword 关键字，可空
     * @param limit   期望条数，服务层收敛到 [1,50]
     * @return 员工选项列表，元素含工号(username)/姓名/机构名
     */
    @GetMapping("/search")
    @Operation(summary = "员工搜索（绩效选人用，返回工号）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<List<PerfEmpOptionDTO>> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        log.debug("[PerfEmployeeController.search] keyword={}, limit={}", keyword, limit);
        return ResponseWrapper.success(perfEmployeeQueryService.searchEmployees(keyword, limit));
    }
}
