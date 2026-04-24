package com.bank.branch.platform.performance.service.scope;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 数据范围 SQL 片段生成 Helper（Task Q7.1 / V1.4 S1.2）.
 *
 * <p>作用：把 {@link BizScopeApi#buildScopeContext} 返回的 {@link DataScopeContext}
 * 转换为可注入 MyBatis {@code ${scopeFragment}} 的 SQL 片段 + 参数对象。
 *
 * <p>Mapper 使用模式：
 * <pre>{@code
 * <if test="scopeFragment != null and scopeFragment != ''">
 *   AND (${scopeFragment})
 * </if>
 * }</pre>
 *
 * <p>Service 调用模式（V1.3 既有 4 参数，WORKFLOW_PARTICIPANT 仍 fail-close）：
 * <pre>{@code
 * PerfScopeHelper.Fragment frag = perfScopeHelper.getFragment(
 *     empId, BizType.PERF_CONFIG, BizAction.LIST,
 *     new PerfScopeHelper.ScopeColumns(
 *         "emp_id",       // ownerEmpCol (SELF)
 *         "emp_id",       // assigneeCol (SELF_ASSIGNED)
 *         "created_by",   // createdByCol (SELF_CREATED)
 *         "org_code",     // ownerOrgCol (ORG / ORG_SUBTREE)
 *         null));         // bizKeyCol (V1.4 WORKFLOW_PARTICIPANT 不使用时传 null)
 * mapper.select(... frag.getSql(), frag.getParams() ...);
 * }</pre>
 *
 * <p>V1.4 新增 5 参调用（支持 WORKFLOW_PARTICIPANT 真实实现，由 AllocAdjust/TargetAdjust 等 Service 使用）：
 * <pre>{@code
 * PerfScopeHelper.Fragment frag = perfScopeHelper.getFragment(
 *     empId, BizType.PERF_CONFIG, BizAction.LIST,
 *     new PerfScopeHelper.ScopeColumns(
 *         "created_by", "created_by", "created_by", "owner_org_id",
 *         "business_key"),          // bizKeyCol 必须非空
 *     "perf_alloc_adjust_");        // processDefKeyPrefix (如 "perf_alloc_adjust_" 或 "perf_target_adjust_")
 * }</pre>
 *
 * <p><b>SQL 注入防护</b>:
 * <ul>
 *   <li>{@link Fragment#sql} 字段通过 {@code ${}} 注入，来源为本 helper 受控生成，
 *       不接收任何用户入参拼接</li>
 *   <li>所有参数值（ownerEmpId/ownerOrgCode/orgCode0..N/bizKey0..N）走
 *       {@code #{scopeParams.xxx}} 预编译，与 sql 片段严格分离</li>
 * </ul>
 *
 * <p><b>Fail-Close 策略</b>（Plan §Task Q7.1 + V1.4 S1.2 要求）:
 * <ul>
 *   <li>{@code buildScopeContext} 返回 null（无权限）→ sql="1=0"</li>
 *   <li>{@code ORG_SUBTREE} 但 subtree 为空 → sql="1=0"</li>
 *   <li>{@code WORKFLOW_PARTICIPANT}（V1.4 S1.2 真实实现）：
 *     <ul>
 *       <li>4 参调用 / 未传 workflowPrefix / bizKeyCol null → sql="1=0"</li>
 *       <li>WorkflowQueryApi 返回空集 → sql="1=0"</li>
 *       <li>正常：{@code <bizKeyCol> IN (#{bizKey0}, #{bizKey1}...)}</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@Slf4j
@Component
public class PerfScopeHelper {

    /** WORKFLOW_PARTICIPANT 时间窗口默认 180 天. */
    private static final int WORKFLOW_PARTICIPATION_WINDOW_DAYS = 180;

    /** WorkflowQueryApi.queryParticipatedBusinessKeys 的 limit 上限（和 API 文档一致）. */
    private static final int WORKFLOW_PARTICIPATION_LIMIT = 5000;

    private final BizScopeApi bizScopeApi;
    private final WorkflowQueryApi workflowQueryApi;

    /**
     * 主构造器（V1.4 S1.2 起注入 WorkflowQueryApi 以支持 WORKFLOW_PARTICIPANT 真实实现）。
     *
     * <p>Spring 自动装配经由此唯一 public 构造器，避免多构造器歧义。
     * 测试类必须显式传 workflowQueryApi（Mock 或传 null），不再支持 {@code new PerfScopeHelper(bizScopeApi)}。
     */
    public PerfScopeHelper(BizScopeApi bizScopeApi, WorkflowQueryApi workflowQueryApi) {
        this.bizScopeApi = bizScopeApi;
        this.workflowQueryApi = workflowQueryApi;
    }

    /**
     * 解析当前请求的数据范围为 SQL 片段（V1.3 既有 4 参 API）.
     *
     * <p>WORKFLOW_PARTICIPANT 场景下退化为 fail-close（不会查询 WorkflowQueryApi）。
     * 若需要真实 WORKFLOW_PARTICIPANT 过滤，请使用 5 参重载。
     *
     * @param empId   当前用户 empId（来自 CurrentUserApi.getCurrentEmpId()）
     * @param bizType 业务类型（绩效模块恒为 PERF_CONFIG）
     * @param action  业务操作（通常 LIST / READ）
     * @param columns 目标业务表的列名映射
     * @return 非 null Fragment；isEmpty() 时表示无过滤（ALL 范围）
     */
    public Fragment getFragment(String empId, BizType bizType, BizAction action, ScopeColumns columns) {
        return getFragment(empId, bizType, action, columns, null);
    }

    /**
     * 解析当前请求的数据范围为 SQL 片段（V1.4 S1.2 新增 5 参 overload）.
     *
     * <p>新增 {@code workflowPrefix} 参数支持 WORKFLOW_PARTICIPANT 真实实现：
     * <ul>
     *   <li>AllocAdjust 调用方传 {@code "perf_alloc_adjust_"} 限定本模块调整流程</li>
     *   <li>TargetAdjust 调用方传 {@code "perf_target_adjust_"}</li>
     *   <li>其他场景（或不需要 WORKFLOW_PARTICIPANT 过滤）可传 null</li>
     * </ul>
     *
     * @param empId          当前用户 empId
     * @param bizType        业务类型
     * @param action         业务操作
     * @param columns        目标业务表的列名映射（含 bizKeyCol）
     * @param workflowPrefix 流程定义 key 前缀；null/空 → WORKFLOW_PARTICIPANT 分支 fail-close
     * @return 非 null Fragment
     */
    public Fragment getFragment(String empId, BizType bizType, BizAction action,
                                ScopeColumns columns, String workflowPrefix) {
        DataScopeContext ctx = bizScopeApi.buildScopeContext(empId, bizType, action);
        if (ctx == null) {
            log.warn("[PerfScopeHelper] buildScopeContext 返回 null, empId={} bizType={} action={} → fail-close",
                    empId, bizType, action);
            return Fragment.failClose();
        }
        DataScopeType scope = ctx.scopeType();
        if (scope == null) {
            log.warn("[PerfScopeHelper] ctx.scopeType 为空, empId={} → fail-close", empId);
            return Fragment.failClose();
        }

        return switch (scope) {
            case ALL -> Fragment.empty();
            case SELF_CREATED -> singleEmpFragment(columns.createdByCol(), ctx.empId());
            case SELF -> singleEmpFragment(columns.ownerEmpCol(), ctx.empId());
            case SELF_ASSIGNED -> singleEmpFragment(columns.assigneeCol(), ctx.empId());
            case ORG -> singleOrgFragment(columns.ownerOrgCol(), ctx.orgCode());
            case ORG_SUBTREE -> subtreeOrgFragment(columns.ownerOrgCol(), ctx.orgSubtreeCodes());
            case WORKFLOW_PARTICIPANT -> workflowParticipantFragment(
                    ctx.empId(), columns.bizKeyCol(), workflowPrefix);
        };
    }

    /**
     * V1.4 S1.2: WORKFLOW_PARTICIPANT 场景生成 businessKey IN 片段.
     *
     * <p>Fail-close 条件（任一）：
     * <ul>
     *   <li>bizKeyCol 为 null/空：表不具备 business_key 列，不能参与 workflow scope 过滤</li>
     *   <li>workflowPrefix 为 null/空：调用方未声明流程定义前缀，默认不承担 Flowable 查询开销</li>
     *   <li>WorkflowQueryApi 未注入（V1.3 兼容构造器场景）</li>
     *   <li>查询返回空集：用户未参与过任何流程</li>
     * </ul>
     */
    private Fragment workflowParticipantFragment(String empId, String bizKeyCol, String workflowPrefix) {
        if (isBlank(bizKeyCol)) {
            log.debug("[PerfScopeHelper] WORKFLOW_PARTICIPANT bizKeyCol 为空, empId={} → fail-close", empId);
            return Fragment.failClose();
        }
        if (isBlank(workflowPrefix)) {
            log.debug("[PerfScopeHelper] WORKFLOW_PARTICIPANT 未传 workflowPrefix, empId={} → fail-close", empId);
            return Fragment.failClose();
        }
        if (workflowQueryApi == null) {
            log.warn("[PerfScopeHelper] WORKFLOW_PARTICIPANT 调用但 WorkflowQueryApi 未注入"
                    + "（可能走了 @Deprecated V1.3 构造器）, empId={} → fail-close", empId);
            return Fragment.failClose();
        }

        Set<String> businessKeys = workflowQueryApi.queryParticipatedBusinessKeys(
                empId, workflowPrefix,
                WORKFLOW_PARTICIPATION_WINDOW_DAYS, WORKFLOW_PARTICIPATION_LIMIT);

        if (businessKeys == null || businessKeys.isEmpty()) {
            log.debug("[PerfScopeHelper] WORKFLOW_PARTICIPANT empId={} prefix={} 无参与记录 → fail-close",
                    empId, workflowPrefix);
            return Fragment.failClose();
        }

        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder(bizKeyCol).append(" IN (");
        int i = 0;
        for (String bk : businessKeys) {
            String key = "bizKey" + i;
            params.put(key, bk);
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("#{scopeParams.").append(key).append("}");
            i++;
        }
        sql.append(")");
        return new Fragment(sql.toString(), params);
    }

    /** 单 empId 比较片段：{@code <col> = #{scopeParams.ownerEmpId}}. */
    private Fragment singleEmpFragment(String col, String ownerEmpId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ownerEmpId", ownerEmpId);
        return new Fragment(col + " = #{scopeParams.ownerEmpId}", params);
    }

    /** 单 orgCode 比较片段：{@code <col> = #{scopeParams.ownerOrgCode}}. */
    private Fragment singleOrgFragment(String col, String ownerOrgCode) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ownerOrgCode", ownerOrgCode);
        return new Fragment(col + " = #{scopeParams.ownerOrgCode}", params);
    }

    /**
     * 子树 IN 列表片段：{@code <col> IN (#{scopeParams.orgCode0}, #{scopeParams.orgCode1}...)}.
     *
     * <p>subtree 为空时 fail-close, 防止 SQL 变成 {@code IN ()} 这类非法语法.
     */
    private Fragment subtreeOrgFragment(String col, Set<String> subtreeCodes) {
        if (subtreeCodes == null || subtreeCodes.isEmpty()) {
            return Fragment.failClose();
        }
        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder(col).append(" IN (");
        int i = 0;
        for (String code : subtreeCodes) {
            String key = "orgCode" + i;
            params.put(key, code);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("#{scopeParams.").append(key).append("}");
            i++;
        }
        sb.append(")");
        return new Fragment(sb.toString(), params);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty() || s.trim().isEmpty();
    }

    /**
     * 目标业务表的 5 列名映射（V1.4 S1.2 从 4 字段扩到 5 字段）.
     *
     * @param ownerEmpCol  SELF 对应列（如 emp_id）
     * @param assigneeCol  SELF_ASSIGNED 对应列（如 assignee_id）
     * @param createdByCol SELF_CREATED 对应列（通常 created_by）
     * @param ownerOrgCol  ORG / ORG_SUBTREE 对应列（如 org_code）
     * @param bizKeyCol    WORKFLOW_PARTICIPANT 对应列（通常 "business_key"；
     *                     若业务表无 business_key 列则传 null，该分支退化为 fail-close）
     */
    public record ScopeColumns(String ownerEmpCol,
                               String assigneeCol,
                               String createdByCol,
                               String ownerOrgCol,
                               String bizKeyCol) {}

    /**
     * 数据范围 SQL 片段容器.
     *
     * <p>{@link #sql}: 通过 MyBatis {@code ${scopeFragment}} 注入的 SQL 字符串。
     *   - {@code ""}：ALL 范围，Mapper 不追加条件
     *   - {@code "1=0"}：fail-close, Mapper 追加后查无结果
     *   - 其他：形如 {@code "emp_id = #{scopeParams.ownerEmpId}"}，Mapper 用 {@code AND (${scopeFragment})} 包装
     *
     * <p>{@link #params}: 预编译参数 Map, 注入路径 {@code #{scopeParams.xxx}}.
     *   Service 层调用 Mapper 时以 {@code @Param("scopeParams")} 包装整个 Map 传入。
     */
    @Getter
    public static class Fragment {
        private final String sql;
        private final Map<String, Object> params;

        public Fragment(String sql, Map<String, Object> params) {
            this.sql = sql == null ? "" : sql;
            this.params = params == null ? Collections.emptyMap() : params;
        }

        /** ALL 范围 → 空 Fragment, Mapper 不追加任何条件. */
        public static Fragment empty() {
            return new Fragment("", Collections.emptyMap());
        }

        /** Fail-Close: 拒绝访问, 任何查询返回 0 行. */
        public static Fragment failClose() {
            return new Fragment("1=0", Collections.emptyMap());
        }

        /** sql 为空视为无过滤 (ALL 范围). */
        public boolean isEmpty() {
            return sql == null || sql.isEmpty();
        }
    }
}
