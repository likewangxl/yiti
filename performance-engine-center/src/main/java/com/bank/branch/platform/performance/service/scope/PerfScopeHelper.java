package com.bank.branch.platform.performance.service.scope;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 数据范围 SQL 片段生成 Helper（Task Q7.1）.
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
 * <p>Service 调用模式：
 * <pre>{@code
 * PerfScopeHelper.Fragment frag = perfScopeHelper.getFragment(
 *     empId, BizType.PERF_CONFIG, BizAction.LIST,
 *     new PerfScopeHelper.ScopeColumns(
 *         "emp_id",      // ownerEmpCol (SELF)
 *         "emp_id",      // assigneeCol (SELF_ASSIGNED)
 *         "created_by",  // createdByCol (SELF_CREATED)
 *         "org_code"));  // ownerOrgCol (ORG / ORG_SUBTREE)
 * mapper.select(... frag.getSql(), frag.getParams() ...);
 * }</pre>
 *
 * <p><b>SQL 注入防护</b>:
 * <ul>
 *   <li>{@link Fragment#sql} 字段通过 {@code ${}} 注入，来源为本 helper 受控生成，
 *       不接收任何用户入参拼接</li>
 *   <li>所有参数值（ownerEmpId/ownerOrgCode/orgCode0..N）走 {@code #{scopeParams.xxx}}
 *       预编译，与 sql 片段严格分离</li>
 * </ul>
 *
 * <p><b>Fail-Close 策略</b>（Plan §Task Q7.1 要求）:
 * <ul>
 *   <li>{@code buildScopeContext} 返回 null（无权限）→ sql="1=0"</li>
 *   <li>{@code ORG_SUBTREE} 但 subtree 为空 → sql="1=0"</li>
 *   <li>{@code WORKFLOW_PARTICIPANT}（V1.2 暂不支持）→ sql="1=0"</li>
 * </ul>
 *
 * <p><b>V1.3 规划</b>: WORKFLOW_PARTICIPANT 引入 biz_process_map 子查询，
 * 届时仅需在本 helper 增加分支，Mapper/Service 无感知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerfScopeHelper {

    private final BizScopeApi bizScopeApi;

    /**
     * 解析当前请求的数据范围为 SQL 片段.
     *
     * @param empId   当前用户 empId（来自 CurrentUserApi.getCurrentEmpId()）
     * @param bizType 业务类型（绩效模块恒为 PERF_CONFIG）
     * @param action  业务操作（通常 LIST / READ）
     * @param columns 目标业务表的列名映射
     * @return 非 null Fragment；isEmpty() 时表示无过滤（ALL 范围）
     */
    public Fragment getFragment(String empId, BizType bizType, BizAction action, ScopeColumns columns) {
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
            case WORKFLOW_PARTICIPANT -> {
                // V1.2 不实现, 降级为 fail-close. 避免消费方误以为已授权.
                log.warn("[PerfScopeHelper] WORKFLOW_PARTICIPANT 尚未实现, empId={} → fail-close", empId);
                yield Fragment.failClose();
            }
        };
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

    /**
     * 目标业务表的 4 列名映射.
     *
     * @param ownerEmpCol  SELF 对应列（如 emp_id）
     * @param assigneeCol  SELF_ASSIGNED 对应列（如 assignee_id）
     * @param createdByCol SELF_CREATED 对应列（通常 created_by）
     * @param ownerOrgCol  ORG / ORG_SUBTREE 对应列（如 org_code）
     */
    public record ScopeColumns(String ownerEmpCol,
                               String assigneeCol,
                               String createdByCol,
                               String ownerOrgCol) {}

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
