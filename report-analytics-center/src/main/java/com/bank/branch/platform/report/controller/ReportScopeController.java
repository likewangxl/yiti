package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表「对象选择」数据范围解析。
 *
 * <p>范围严格按「权限配置 → 数据范围矩阵(角色 × DataScope)」的配置走，
 * 与查询执行层 {@code DynamicQueryService} 同一个 {@link BizScopeApi#buildScopeContext} 来源，
 * 不再写死角色码。改某角色的可见范围，在权限配置页配 DataScope 即可生效。</p>
 *
 * <p>DataScopeType → 选择界面 mode：</p>
 * <ul>
 *   <li>ALL → ALL（不限）；</li>
 *   <li>ORG_SUBTREE → 本机构子树（orgCodes=子树编码）；</li>
 *   <li>ORG → 仅本机构（orgCodes=本机构）；</li>
 *   <li>SELF / SELF_CREATED / SELF_ASSIGNED / WORKFLOW_PARTICIPANT / 其它 → 仅本人。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/reports/scope")
@Tag(name = "报表-对象选择数据范围")
@RequiredArgsConstructor
public class ReportScopeController {

    private final CurrentUserApi currentUserApi;
    private final BizScopeApi bizScopeApi;

    @GetMapping("/picker")
    @Operation(summary = "对象选择数据范围（按 REPORT 的 DataScope 配置）")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    public ResponseWrapper<Map<String, Object>> picker() {
        String empId = currentUserApi.getCurrentEmpId();
        var ctx = currentUserApi.getCurrentUserContext();
        DataScopeContext scope = bizScopeApi.buildScopeContext(empId, BizType.REPORT, BizAction.LIST);

        String mode;
        List<String> orgCodes = new ArrayList<>();
        DataScopeType type = scope != null ? scope.scopeType() : null;
        if (type == DataScopeType.ALL) {
            mode = "ALL";
        } else if (type == DataScopeType.ORG_SUBTREE) {
            mode = "ORG_SUBTREE";
            if (scope.orgSubtreeCodes() != null) orgCodes.addAll(scope.orgSubtreeCodes());
            if (orgCodes.isEmpty() && scope.orgCode() != null) orgCodes.add(scope.orgCode());
        } else if (type == DataScopeType.ORG) {
            mode = "ORG_SUBTREE";   // 前端按 orgCodes 过滤机构树；ORG 只给本机构一个编码
            if (scope.orgCode() != null) orgCodes.add(scope.orgCode());
        } else {
            // SELF / SELF_CREATED / SELF_ASSIGNED / WORKFLOW_PARTICIPANT / null → 仅本人
            mode = "SELF";
            if (scope != null && scope.orgCode() != null) orgCodes.add(scope.orgCode());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", mode);
        out.put("selfEmpId", empId);
        out.put("selfName", ctx != null ? ctx.displayName() : null);
        out.put("orgCodes", orgCodes);
        log.debug("[ReportScope.picker] empId={} dataScope={} mode={} orgCodes={}",
                empId, type, mode, orgCodes.size());
        return ResponseWrapper.success(out);
    }
}
