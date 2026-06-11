package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.report.dto.req.DynamicQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.DynamicQueryRespDTO;
import com.bank.branch.platform.report.service.DynamicQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 动态查询 REST 控制器（A.2 POST /api/reports/dynamic-query，Task M1.2.1）.
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@Tag(name = "报表-动态查询", description = "维度+指标即席查询")
@Validated
@RequiredArgsConstructor
public class DynamicQueryController {

    private final DynamicQueryService dynamicQueryService;
    private final UserApi userApi;
    private final CurrentUserApi currentUserApi;
    private final OrgApi orgApi;
    private final BizScopeApi bizScopeApi;
    private final CustomerQueryApi customerQueryApi;

    /**
     * 执行动态查询：根据 dim + subjectIds + metricCodes + dataDate 返回行列数据.
     */
    @PostMapping("/dynamic-query")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "A.2 执行动态查询")
    public ResponseWrapper<DynamicQueryRespDTO> dynamicQuery(@Valid @RequestBody DynamicQueryReqDTO req) {
        return ResponseWrapper.success(dynamicQueryService.execute(req));
    }

    /**
     * 动态查询「选择对象」客户搜索：按客户名/客户号模糊搜（复用 CustomerQueryApi.searchCustomers）。
     * <p>客户维度按产品决策<b>不做数据范围限制</b>，这里不按机构过滤，直接返回匹配客户。</p>
     * 返回 [{id: 客户内部ID, name: 客户名(缺则客户号), org: 归属机构名}]，供前端对象选择框使用。
     */
    @GetMapping("/customers/search")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "动态查询客户搜索（不限范围，按客户名/客户号）")
    public ResponseWrapper<List<Map<String, String>>> searchCustomers(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
        List<CustomerDTO> custs = customerQueryApi.searchCustomers(keyword, size);
        List<Map<String, String>> out = new ArrayList<>();
        for (CustomerDTO c : custs) {
            String name = c.getCustName() != null && !c.getCustName().isBlank() ? c.getCustName()
                    : (c.getCustNo() != null ? c.getCustNo() : "");
            out.add(Map.of(
                    "id", c.getId() != null ? c.getId() : "",
                    "name", name,
                    "org", c.getOwnerOrgName() != null ? c.getOwnerOrgName() : ""));
        }
        return ResponseWrapper.success(out);
    }

    /**
     * 动态查询「选择对象」用的员工搜索：按工号/姓名模糊搜 PT_USER（与查询同属 REPORT 权限）。
     * 范围按 REPORT 的 DataScope 配置收窄：ALL 不限；ORG/ORG_SUBTREE 限本机构(子树)；其余仅本人。
     * 返回 [{id: 工号, name: 姓名, org: 机构}]，供前端对象选择框使用。
     */
    @GetMapping("/employees/search")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "员工搜索（动态查询选择对象用，按 DataScope 收窄）")
    public ResponseWrapper<List<Map<String, String>>> searchEmployees(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        int size = Math.min(Math.max(limit, 1), 50);

        String selfEmpId = currentUserApi.getCurrentEmpId();
        // 员工搜索属员工维度，按 REPORT_DYN_EMP 取范围，与选择框/执行层口径一致
        DataScopeContext scope = bizScopeApi.buildScopeContext(selfEmpId, BizType.REPORT_DYN_EMP, BizAction.LIST);
        DataScopeType type = scope != null ? scope.scopeType() : null;
        boolean allowAll = type == DataScopeType.ALL;
        boolean selfOnly = !allowAll && type != DataScopeType.ORG && type != DataScopeType.ORG_SUBTREE;
        Set<String> orgCodes = Set.of();
        if (type == DataScopeType.ORG_SUBTREE) {
            orgCodes = scope.orgSubtreeCodes() != null ? scope.orgSubtreeCodes()
                    : (scope.orgCode() != null ? Set.of(scope.orgCode()) : Set.of());
        } else if (type == DataScopeType.ORG && scope.orgCode() != null) {
            orgCodes = Set.of(scope.orgCode());
        }

        List<UserDTO> users = userApi.pageUsers(keyword, 1, size).getRecords();
        List<Map<String, String>> out = new ArrayList<>();
        for (UserDTO u : users) {
            if (!inScope(u.getEmpId(), selfEmpId, allowAll, selfOnly, orgCodes)) continue;
            out.add(Map.of(
                    "id", u.getEmpId() != null ? u.getEmpId() : "",
                    "name", u.getDisplayName() != null ? u.getDisplayName()
                            : (u.getUsername() != null ? u.getUsername() : ""),
                    "org", u.getMainOrgName() != null ? u.getMainOrgName() : ""));
        }
        return ResponseWrapper.success(out);
    }

    /** 某员工是否在范围内：ALL 全放行；selfOnly 仅本人；否则本人或其主机构落在 orgCodes 内。 */
    private boolean inScope(String empId, String selfEmpId, boolean allowAll,
                            boolean selfOnly, Set<String> orgCodes) {
        if (allowAll) return true;
        if (empId == null) return false;
        if (Objects.equals(empId, selfEmpId)) return true;   // 本人始终可见
        if (selfOnly) return false;
        if (orgCodes == null || orgCodes.isEmpty()) return false;
        OrgDTO org = orgApi.getUserMainOrg(empId);
        return org != null && org.getOrgCode() != null && orgCodes.contains(org.getOrgCode());
    }
}
