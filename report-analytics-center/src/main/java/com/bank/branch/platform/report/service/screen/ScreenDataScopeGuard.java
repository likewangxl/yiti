package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.support.ScreenConfigSchema;
import com.bank.branch.platform.report.support.ScreenSqlTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 大屏取数 DATA_SCOPE 行级权限守卫（spec 2026-07-17 §4，common-dev-guide §4/§5）.
 *
 * <p>/api/screen/data 执行前的主体参数约束校验，越权统一抛 {@code RPT-43013}（fail-close）：
 * <ul>
 *   <li>scope 解析走 auth 端 {@link BizScopeApi#resolveScope}（BizType.REPORT，多角色并集取最大边界）；</li>
 *   <li>SELF：contextParams.empId 必须=当前用户，orgCode 必须=主机构（或不传）；</li>
 *   <li>ORG：orgCode 必须=主机构，empId 主机构必须=主机构（EXT_USER_ORG 关系经 OrgApi.getUserMainOrg 查询）；</li>
 *   <li>ORG_SUBTREE：orgCode / empId 所属主机构必须落在主机构子树（OrgApi.getOrgSubtreeCodes，
 *       auth 端按 EXT_ORG_INFO p_id 递归收集，机制同 common-dev-guide §5.1 CTE 模板）；</li>
 *   <li>ALL：放行；</li>
 *   <li>SELF_CREATED / SELF_ASSIGNED / WORKFLOW_PARTICIPANT / 解析失败：fail-close 拒绝；</li>
 *   <li>GLOBAL 口径（config_json scopeMode=GLOBAL，或 SUBJECT 型但不消费任何主体参数——CUSTOM_SQL 无
 *       #{empId}/#{orgCode} 占位、WIDE_TABLE 聚合形态——防绕过）：仅 ALL，或 ORG_SUBTREE 且主机构为
 *       省级顶层节点（EXT_ORG_INFO ORG_LEVEL=1 / P_ID='0'）放行。</li>
 * </ul>
 *
 * <p>主体参数缺失不在本类拦截（属引擎 RPT-43010 入参校验职责）；try-run 试跑端点
 * （独立高危资源 R_RPT_SCR_DS_TRY）维持现状不套本守卫。
 */
@Slf4j
@Component
public class ScreenDataScopeGuard {

    /** GLOBAL 口径下允许放行的顶层机构父标识（EXT_ORG_INFO 根节点 P_ID='0'） */
    private static final Set<String> TOP_PARENT_MARKS = Set.of("", "0");

    private final BizScopeApi bizScopeApi;
    private final CurrentUserApi currentUserApi;
    private final OrgApi orgApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScreenDataScopeGuard(BizScopeApi bizScopeApi,
                                CurrentUserApi currentUserApi,
                                OrgApi orgApi) {
        this.bizScopeApi = bizScopeApi;
        this.currentUserApi = currentUserApi;
        this.orgApi = orgApi;
    }

    /**
     * 取数前数据范围校验：越权统一抛 RPT-43013（fail-close）.
     *
     * @param ds  已加载的数据源（含 sourceKind/configJson）
     * @param req 取数请求（contextParams 携带 empId/orgCode 主体参数）
     */
    public void check(RptScreenDatasource ds, ScreenDataReqDTO req) {
        String curEmpId = currentUserApi.getCurrentEmpId();
        DataScopeType scope = resolveScopeFailClose(curEmpId);
        if (scope == DataScopeType.ALL) {
            // ALL 全量边界：主体口径与 GLOBAL 口径一律放行
            return;
        }
        JsonNode cfg = readConfigLenient(ds.getConfigJson());
        if (requiresGlobalRule(ds, cfg)) {
            checkGlobalRule(scope, curEmpId);
            return;
        }
        String mainOrg = currentUserApi.getCurrentOrgCode();
        String empParam = ctxParam(req, "empId");
        String orgParam = ctxParam(req, "orgCode");
        switch (scope) {
            case SELF -> checkSelf(curEmpId, mainOrg, empParam, orgParam);
            case ORG -> checkOrg(curEmpId, mainOrg, empParam, orgParam);
            case ORG_SUBTREE -> checkOrgSubtree(curEmpId, mainOrg, empParam, orgParam);
            // SELF_CREATED / SELF_ASSIGNED / WORKFLOW_PARTICIPANT 对大屏主体取数无对应谓词语义，fail-close
            default -> deny(curEmpId, scope, "scope 类型不支持大屏取数");
        }
    }

    // ===== scope 分支 =====

    /** SELF：empId 只能查本人；orgCode 只能是本人主机构（或不传） */
    private void checkSelf(String curEmpId, String mainOrg, String empParam, String orgParam) {
        if (empParam != null && !empParam.equals(curEmpId)) {
            deny(curEmpId, DataScopeType.SELF, "empId 非本人");
        }
        if (orgParam != null && !orgParam.equals(mainOrg)) {
            deny(curEmpId, DataScopeType.SELF, "orgCode 非本人主机构");
        }
    }

    /** ORG：orgCode 只能是主机构；empId 的主机构必须=主机构（员工-机构关系经 OrgApi 查询） */
    private void checkOrg(String curEmpId, String mainOrg, String empParam, String orgParam) {
        if (orgParam != null && !orgParam.equals(mainOrg)) {
            deny(curEmpId, DataScopeType.ORG, "orgCode 非主机构");
        }
        if (empParam != null) {
            String targetOrg = safeMainOrgOf(empParam);
            if (targetOrg == null || !targetOrg.equals(mainOrg)) {
                deny(curEmpId, DataScopeType.ORG, "empId 不属于主机构");
            }
        }
    }

    /** ORG_SUBTREE：orgCode / empId 主机构必须落在主机构子树（auth 端按 EXT_ORG_INFO 递归收集） */
    private void checkOrgSubtree(String curEmpId, String mainOrg, String empParam, String orgParam) {
        Set<String> subtree = safeSubtreeOf(mainOrg);
        if (orgParam != null && !subtree.contains(orgParam)) {
            deny(curEmpId, DataScopeType.ORG_SUBTREE, "orgCode 不在主机构子树");
        }
        if (empParam != null) {
            String targetOrg = safeMainOrgOf(empParam);
            if (targetOrg == null || !subtree.contains(targetOrg)) {
                deny(curEmpId, DataScopeType.ORG_SUBTREE, "empId 所属机构不在主机构子树");
            }
        }
    }

    /** GLOBAL 口径（全省聚合/在途流程类）：仅 ALL 或「ORG_SUBTREE 且主机构为省级顶层节点」放行 */
    private void checkGlobalRule(DataScopeType scope, String curEmpId) {
        if (scope == DataScopeType.ORG_SUBTREE && isProvinceTopNode(currentUserApi.getCurrentOrgCode())) {
            return;
        }
        deny(curEmpId, scope, "GLOBAL 数据源要求 ALL 或省级 ORG_SUBTREE");
    }

    // ===== 判定辅助 =====

    /** 解析当前用户 REPORT 数据范围；auth 调用异常或返回 null 一律 fail-close */
    private DataScopeType resolveScopeFailClose(String curEmpId) {
        DataScopeType scope;
        try {
            scope = bizScopeApi.resolveScope(curEmpId, BizType.REPORT);
        } catch (RuntimeException ex) {
            log.warn("[ScreenDataScopeGuard] resolveScope 失败 empId={} cause={}", curEmpId, ex.getMessage());
            throw new RptException(RptErrorCode.SCREEN_DATA_SCOPE_DENIED, ex);
        }
        if (scope == null) {
            deny(curEmpId, null, "scope 未解析");
        }
        return scope;
    }

    /**
     * 是否按 GLOBAL 规则校验：scopeMode=GLOBAL，或 SUBJECT 型但 SQL/配置不消费任何主体参数
     * （此时主体参数约束无从落点，放任即等于全省可见，必须按 GLOBAL 收口防绕过）.
     */
    private boolean requiresGlobalRule(RptScreenDatasource ds, JsonNode cfg) {
        if ("GLOBAL".equals(cfg.path("scopeMode").asText(ScreenConfigSchema.DEFAULT_SCOPE_MODE))) {
            return true;
        }
        return !consumesSubjectParams(ds.getSourceKind(), cfg);
    }

    /** 数据源是否消费主体上下文参数（empId/orgCode/custNo 视为主体锚点） */
    private boolean consumesSubjectParams(String sourceKind, JsonNode cfg) {
        return switch (sourceKind == null ? "" : sourceKind) {
            // 宽表引导式：明细形态 WHERE 主体列=?；聚合形态跨主体不消费
            case "WIDE_TABLE" -> !cfg.path("aggregation").isObject();
            // KPI 结果/细项引导式恒按主体取数
            case "KPI_RESULT", "KPI_DETAIL" -> true;
            case "CUSTOM_SQL" -> customSqlUsesSubjectParams(cfg.path("sql").asText());
            // 未知 sourceKind：按不消费处理（走更严的 GLOBAL 规则，fail-close）
            default -> false;
        };
    }

    /** CUSTOM_SQL 是否携带 #{empId}/#{orgCode} 主体占位；模板解析失败按不消费处理（更严） */
    private boolean customSqlUsesSubjectParams(String sqlTemplate) {
        try {
            for (String name : ScreenSqlTemplate.parse(sqlTemplate).paramNames()) {
                if ("empId".equals(name) || "orgCode".equals(name)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException ex) {
            log.warn("[ScreenDataScopeGuard] CUSTOM_SQL 模板解析失败，按 GLOBAL 规则收口 cause={}", ex.getMessage());
            return false;
        }
    }

    /** 主机构是否省级顶层节点（EXT_ORG_INFO：ORG_LEVEL=1 或 P_ID='0'/空）；查询失败按否（fail-close） */
    private boolean isProvinceTopNode(String orgCode) {
        if (orgCode == null || orgCode.isBlank()) {
            return false;
        }
        OrgDTO org;
        try {
            org = orgApi.getOrg(orgCode);
        } catch (RuntimeException ex) {
            log.warn("[ScreenDataScopeGuard] getOrg 失败 orgCode={} cause={}", orgCode, ex.getMessage());
            return false;
        }
        if (org == null) {
            return false;
        }
        if (org.getOrgLevel() != null && org.getOrgLevel() == 1) {
            return true;
        }
        String parent = org.getParentOrgCode();
        return parent == null || TOP_PARENT_MARKS.contains(parent.trim());
    }

    /** 目标员工主机构（EXT_USER_ORG 唯一有效主机构）；查询异常/无主机构返回 null（调用方 fail-close） */
    private String safeMainOrgOf(String empId) {
        try {
            OrgDTO org = orgApi.getUserMainOrg(empId);
            return org == null ? null : org.getOrgCode();
        } catch (RuntimeException ex) {
            log.warn("[ScreenDataScopeGuard] getUserMainOrg 失败 empId={} cause={}", empId, ex.getMessage());
            return null;
        }
    }

    /** 主机构子树编码集合；查询异常返回空集（后续 contains 判定自然 fail-close） */
    private Set<String> safeSubtreeOf(String mainOrg) {
        if (mainOrg == null || mainOrg.isBlank()) {
            return Set.of();
        }
        try {
            Set<String> codes = orgApi.getOrgSubtreeCodes(mainOrg);
            return codes == null ? Set.of() : codes;
        } catch (RuntimeException ex) {
            log.warn("[ScreenDataScopeGuard] getOrgSubtreeCodes 失败 orgCode={} cause={}", mainOrg, ex.getMessage());
            return Set.of();
        }
    }

    /** config_json 宽松解析：失败回落空对象（scopeMode 默认 SUBJECT + 不消费主体参数 → 走最严 GLOBAL 规则） */
    private JsonNode readConfigLenient(String configJson) {
        try {
            return ScreenConfigSchema.withDefaults(objectMapper.readTree(configJson == null ? "{}" : configJson));
        } catch (Exception ex) {
            log.warn("[ScreenDataScopeGuard] config_json 解析失败，按最严规则收口 cause={}", ex.getMessage());
            return MissingNode.getInstance();
        }
    }

    /** 上下文参数读取（空白视为未传） */
    private String ctxParam(ScreenDataReqDTO req, String name) {
        String v = req.getContextParams() == null ? null : req.getContextParams().get(name);
        return v == null || v.isBlank() ? null : v;
    }

    /** 统一拒绝出口：留审计线索日志后抛 RPT-43013 */
    private void deny(String curEmpId, DataScopeType scope, String reason) {
        log.warn("[ScreenDataScopeGuard] 数据范围拒绝 empId={} scope={} reason={}", curEmpId, scope, reason);
        throw new RptException(RptErrorCode.SCREEN_DATA_SCOPE_DENIED);
    }
}
