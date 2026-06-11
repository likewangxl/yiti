package com.bank.branch.platform.report.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.performance.api.MetricApi;
import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import com.bank.branch.platform.report.dto.req.DynamicQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.DynamicQueryRespDTO;
import com.bank.branch.platform.report.dto.resp.MetricColumnDTO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.service.DynamicQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * DynamicQueryService 实现（Task M1.2.1，Green）.
 *
 * <p>三层守护实现：
 * <ol>
 *   <li>入参校验：dim 合法性 + subjectIds/metricCodes 个数上限</li>
 *   <li>外层 DataScope：依据 {@link BizScopeApi#buildScopeContext} 返回的 scope 类型，
 *       对照请求的 subjectIds 做范围过滤；命中范围外即 RPT-40005</li>
 *   <li>内层取值：按 dim 调用 {@link MetricApi} 三个分支接口（V1.0 单条循环，batch 优化留 V1.1+）</li>
 * </ol>
 *
 * <p>SubjectName 解析策略：
 * <ul>
 *   <li>EMP：暂取 empId 本身（V1.0 不查 user 表，待 customer-marketing-center / auth 暴露 EmpQueryApi 后切换）</li>
 *   <li>ORG：{@link OrgApi#getOrg(String)} 取 orgName，否则回填 orgCode</li>
 *   <li>CUST：{@link CustomerQueryApi#getCustomer(String)} 取 custName，否则回填 custId</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicQueryServiceImpl implements DynamicQueryService {

    private static final Set<String> VALID_DIMS = Set.of("EMP", "ORG", "CUST");

    private static final String DIM_EMP = "EMP";

    private static final String DIM_ORG = "ORG";

    private static final String DIM_CUST = "CUST";

    private final BizScopeApi bizScopeApi;

    private final CurrentUserApi currentUserApi;

    private final MetricApi metricApi;

    private final OrgApi orgApi;

    private final UserApi userApi;

    private final CustomerQueryApi customerQueryApi;

    @Override
    public DynamicQueryRespDTO execute(DynamicQueryReqDTO req) {
        // 1) 入参校验
        validateRequest(req);

        // 2) 外层 DataScope 过滤
        // 按维度分流到独立的数据范围：员工维度=REPORT_DYN_EMP、机构维度=REPORT_DYN_ORG，
        // 不再共用 REPORT——这样同一角色可以「员工维度只看自己、机构维度按分配看机构」。
        String empId = currentUserApi.getCurrentEmpId();
        DataScopeContext scope = bizScopeApi.buildScopeContext(empId, scopeBizTypeOf(req.getDim()), BizAction.LIST);

        // 对象集合：选了对象=只查所选（含越权报错）；没选=按数据范围枚举"能看到的全部对象"（静默裁剪）
        boolean noSubjectSelected = req.getSubjectIds() == null || req.getSubjectIds().isEmpty();
        List<String> baseSubjects;
        Map<String, UserDTO> empMap;
        if (noSubjectSelected) {
            if (DIM_EMP.equals(req.getDim()) && scope != null && isSelfScope(scope.scopeType())) {
                // SELF 范围默认查本人：直接用本人（不走宽表枚举），即使本人当天无指标数据也显示一行（指标列空）
                String self = scope.empId();
                baseSubjects = (self != null && !self.isEmpty()) ? new ArrayList<>(List.of(self)) : new ArrayList<>();
                empMap = buildEmpMap(baseSubjects);
            } else {
                // 其它范围：枚举当天宽表实际有数据的对象（天然有界，避免返回海量全空行）；EMP 维度把工号转回 USER_ID
                empMap = new HashMap<>();
                baseSubjects = enumerateSubjectsByDim(req.getDim(), req.getDataDate(), empMap);
            }
        } else {
            baseSubjects = req.getSubjectIds();
            // EMP 维度：先批量取用户信息（含主机构 mainOrgCode），用于范围校验 + 结果回填工号/姓名
            empMap = DIM_EMP.equals(req.getDim()) ? buildEmpMap(baseSubjects) : Map.of();
        }

        List<String> allowed = filterBySubjectScope(req.getDim(), baseSubjects, scope, empMap, empId);
        // 用户主动选择了对象却含越权 → 报错；不选（枚举）则静默裁剪到范围内
        if (!noSubjectSelected && allowed.size() < baseSubjects.size()) {
            log.warn("[DynamicQuery] subject 越权过滤：empId={} dim={} 请求 {} 个，命中 {} 个",
                    empId, req.getDim(), baseSubjects.size(), allowed.size());
            throw new RptException(RptErrorCode.SUBJECT_OUT_OF_SCOPE);
        }

        // 3) 分页（可选）：前端传 pageNo/pageSize 才分页，仅对当前页对象取值，对象再多也不拖爆后端/页面；
        //    都不传（如同步导出 exportExcel）则返回全部对象，保持导出全量。
        int total = allowed.size();
        boolean paginate = req.getPageNo() != null || req.getPageSize() != null;
        int pageNo;
        int pageSize;
        List<String> pageIds;
        if (paginate) {
            pageNo = (req.getPageNo() != null && req.getPageNo() > 0) ? req.getPageNo() : 1;
            pageSize = (req.getPageSize() != null && req.getPageSize() > 0) ? Math.min(req.getPageSize(), 100) : 20;
            int from = Math.min((pageNo - 1) * pageSize, total);
            int to = Math.min(from + pageSize, total);
            pageIds = allowed.subList(from, to);
        } else {
            pageNo = 1;
            pageSize = total;
            pageIds = allowed;
        }

        // 4) 跨模块取值（V1.0 单条循环）。empMap 已含工号/姓名/主机构
        List<Map<String, Object>> rows = new ArrayList<>(pageIds.size());
        for (String sid : pageIds) {
            // EMP 维度：前端传入的 subjectId 是 auth 的 USER_ID，而宽表 EMP_INDEX_RESULT.emp_id
            // 存的是工号(PT_USER.username)。需经 empMap(键=USER_ID) 解析出工号后再查指标值/展示，
            // 否则真实员工(USER_ID≠工号)查不到任何指标值（仅 USER_ID 恰好等于工号的账号能命中）。
            UserDTO emp = DIM_EMP.equals(req.getDim()) ? empMap.get(sid) : null;
            String lookupId = (emp != null && emp.getUsername() != null && !emp.getUsername().isEmpty())
                    ? emp.getUsername() : sid;
            Map<String, BigDecimal> values = fetchValuesByDim(req.getDim(), lookupId,
                    req.getDataDate(), req.getMetricCodes());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subjectId", lookupId);
            row.put("subjectName", resolveSubjectName(req.getDim(), sid, empMap));
            // EMP 维度：姓名单独成列，前端在「员工(工号)」后追加「姓名」列
            if (DIM_EMP.equals(req.getDim())) {
                row.put("empName", resolveEmpName(sid, emp));
            }
            // null 值的 metricCode 不入 row（避免 jackson 序列化空字段）
            values.forEach((k, v) -> {
                if (v != null) {
                    row.put(k, v);
                }
            });
            rows.add(row);
        }

        // 4) 列定义
        List<MetricColumnDTO> columns = req.getMetricCodes().stream()
                .map(code -> metricApi.getMetricDef(code).orElse(null))
                .filter(Objects::nonNull)
                .map(this::toColumnDTO)
                .toList();

        return DynamicQueryRespDTO.builder()
                .dim(req.getDim())
                .dataDate(req.getDataDate())
                .columns(columns)
                .rows(rows)
                .rowCount(rows.size())
                .total(total)
                .pageNo(pageNo)
                .pageSize(pageSize)
                .build();
    }

    /**
     * 不选对象时按维度枚举"数据范围内能看到的全部对象"的基础集合（取自当天宽表实际有数据的对象）。
     * <ul>
     *   <li>EMP：宽表 emp_id 是工号，需经 {@code getUsersByUsernames} 转回 USER_ID（与选择/范围口径一致），
     *       顺带把 UserDTO 填进 empMap 供后续范围校验与回填，省一次查库</li>
     *   <li>ORG / CUST：subjectId 与宽表列一致，直接返回</li>
     * </ul>
     * 返回的集合仍会经 filterBySubjectScope 按数据范围裁剪。
     */
    /** 是否「本人」类数据范围（SELF / SELF_CREATED / SELF_ASSIGNED）。 */
    private boolean isSelfScope(DataScopeType type) {
        return type == DataScopeType.SELF
                || type == DataScopeType.SELF_CREATED
                || type == DataScopeType.SELF_ASSIGNED;
    }

    private List<String> enumerateSubjectsByDim(String dim, LocalDate dataDate, Map<String, UserDTO> empMap) {
        switch (dim) {
            case DIM_EMP -> {
                List<String> usernames = metricApi.listEmpIdsWithData(dataDate);
                if (usernames == null || usernames.isEmpty()) {
                    return List.of();
                }
                List<UserDTO> users = userApi.getUsersByUsernames(usernames);
                List<String> userIds = new ArrayList<>();
                for (UserDTO u : (users != null ? users : List.<UserDTO>of())) {
                    if (u != null && u.getEmpId() != null) {
                        empMap.put(u.getEmpId(), u);
                        userIds.add(u.getEmpId());
                    }
                }
                return userIds;
            }
            case DIM_ORG -> {
                List<String> codes = metricApi.listOrgCodesWithData(dataDate);
                return codes != null ? codes : List.of();
            }
            case DIM_CUST -> {
                List<String> custIds = metricApi.listCustIdsWithData(dataDate);
                return custIds != null ? custIds : List.of();
            }
            default -> {
                return List.of();
            }
        }
    }

    /**
     * 同步导出 Excel：复用 execute 查询结果，按"对象 + 各指标列"动态表头生成 xlsx 字节。
     * 不走异步任务表/MinIO，由控制器直接以附件流返回，前端点导出即下载。
     */
    @Override
    public byte[] exportExcel(DynamicQueryReqDTO req) {
        DynamicQueryRespDTO resp = execute(req);
        List<MetricColumnDTO> cols = resp.getColumns() != null ? resp.getColumns() : List.of();

        // 动态表头：EMP 维度为「员工(工号) + 姓名」两列，其它维度为「对象」一列；其后每个指标一列
        boolean isEmp = DIM_EMP.equals(req.getDim());
        List<List<String>> head = new ArrayList<>();
        head.add(List.of(isEmp ? "员工" : "对象"));
        if (isEmp) {
            head.add(List.of("姓名"));
        }
        for (MetricColumnDTO col : cols) {
            String name = col.getMetricName() != null && !col.getMetricName().isBlank()
                    ? col.getMetricName() : col.getMetricCode();
            head.add(List.of(name));
        }

        // 数据行：对象（EMP 为 工号 + 姓名）+ 各指标值（null 输出空串）
        List<List<Object>> data = new ArrayList<>();
        for (Map<String, Object> row : (resp.getRows() != null ? resp.getRows() : List.<Map<String, Object>>of())) {
            List<Object> line = new ArrayList<>();
            Object subjectName = row.get("subjectName");
            line.add(subjectName != null ? subjectName : row.get("subjectId"));
            if (isEmp) {
                Object empName = row.get("empName");
                line.add(empName != null ? empName : "");
            }
            for (MetricColumnDTO col : cols) {
                Object v = row.get(col.getMetricCode());
                line.add(v != null ? v.toString() : "");
            }
            data.add(line);
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // 列宽按内容（含列名表头）自适应：LongestMatchColumnWidthStyleStrategy
            EasyExcel.write(out)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .head(head).sheet("动态指标查询").doWrite(data);
            return out.toByteArray();
        } catch (Exception ex) {
            log.warn("[DynamicQuery] 导出 Excel 生成失败 dim={} err={}", req.getDim(), ex.getMessage());
            throw new RptException(RptErrorCode.EXPORT_START_FAILED, ex);
        }
    }

    /**
     * 入参校验：dim 合法性.
     * <p>对象/指标个数上限已按业务要求取消（想查多少都可以）；注意对象越多查询越慢
     * （V1.0 单条循环逐个取值），如需限流再行加回上限。</p>
     */
    private void validateRequest(DynamicQueryReqDTO req) {
        if (!VALID_DIMS.contains(req.getDim())) {
            throw new RptException(RptErrorCode.METRIC_DIM_MISMATCH);
        }
    }

    /**
     * 按 scope 过滤 subjectIds.
     *
     * <ul>
     *   <li>ALL：全部放行</li>
     *   <li>SELF / SELF_CREATED / SELF_ASSIGNED：仅当 dim=EMP 且 subjectId == ctx.empId 命中</li>
     *   <li>ORG：仅当 dim=ORG 且 subjectId == ctx.orgCode 命中</li>
     *   <li>ORG_SUBTREE：仅当 dim=ORG 且 subjectId ∈ ctx.orgSubtreeCodes 命中</li>
     *   <li>WORKFLOW_PARTICIPANT：V1.0 fail-close 不放行（无 business_key 上下文）</li>
     *   <li>scope 为 null：fail-close（防御性，buildScopeContext 实际返回 null 已属异常）</li>
     * </ul>
     */
    /**
     * 按查询维度选择对应的数据范围 BizType。
     * <p>动态查询的「对象可见范围」按维度独立配置（权限配置页可分别设置）：</p>
     * <ul>
     *   <li>EMP（员工维度）→ {@link BizType#REPORT_DYN_EMP}</li>
     *   <li>ORG（机构维度）→ {@link BizType#REPORT_DYN_ORG}</li>
     *   <li>其它（如 CUST）→ 回退 {@link BizType#REPORT}（界面当前不开放该维度）</li>
     * </ul>
     */
    private BizType scopeBizTypeOf(String dim) {
        if (DIM_EMP.equals(dim)) {
            return BizType.REPORT_DYN_EMP;
        }
        if (DIM_ORG.equals(dim)) {
            return BizType.REPORT_DYN_ORG;
        }
        return BizType.REPORT;
    }

    private List<String> filterBySubjectScope(String dim, List<String> subjectIds, DataScopeContext scope,
                                              Map<String, UserDTO> empMap, String selfEmpId) {
        // 客户维度不做数据范围限制（按产品决策：能进动态查询即可查任意客户），直接放行
        if (DIM_CUST.equals(dim)) {
            return new ArrayList<>(subjectIds);
        }
        if (scope == null || scope.scopeType() == null) {
            log.warn("[DynamicQuery] scope 为空，fail-close");
            return List.of();
        }
        DataScopeType type = scope.scopeType();
        if (type == DataScopeType.ALL) {
            return new ArrayList<>(subjectIds);
        }
        List<String> allowed = new ArrayList<>(subjectIds.size());
        for (String sid : subjectIds) {
            if (isAllowed(dim, sid, type, scope, empMap, selfEmpId)) {
                allowed.add(sid);
            }
        }
        return allowed;
    }

    /**
     * 单个对象是否在数据范围内。
     * <p>关键：ORG / ORG_SUBTREE 在 <b>员工维度</b>下，按「员工主机构是否落在本机构/子树」判定
     * （否则机构负责人在员工维度永远查不到下属，与选择框/搜索口径不一致）。</p>
     * <ul>
     *   <li>ALL：放行</li>
     *   <li>SELF*：EMP 维度且 == 本人</li>
     *   <li>ORG：ORG 维度 == 本机构；EMP 维度 = 员工主机构 == 本机构（或本人）</li>
     *   <li>ORG_SUBTREE：ORG 维度 ∈ 子树；EMP 维度 = 员工主机构 ∈ 子树（或本人）</li>
     * </ul>
     */
    private boolean isAllowed(String dim, String subjectId, DataScopeType type, DataScopeContext scope,
                              Map<String, UserDTO> empMap, String selfEmpId) {
        return switch (type) {
            case ALL -> true;
            case SELF, SELF_CREATED, SELF_ASSIGNED ->
                    DIM_EMP.equals(dim) && Objects.equals(subjectId, scope.empId());
            case ORG -> {
                if (DIM_ORG.equals(dim)) {
                    yield Objects.equals(subjectId, scope.orgCode());
                }
                yield DIM_EMP.equals(dim) && empInOrgScope(subjectId, empMap, selfEmpId,
                        scope.orgCode() != null ? Set.of(scope.orgCode()) : Set.of());
            }
            case ORG_SUBTREE -> {
                Set<String> codes = scope.orgSubtreeCodes() != null ? scope.orgSubtreeCodes() : Set.of();
                if (DIM_ORG.equals(dim)) {
                    yield codes.contains(subjectId);
                }
                yield DIM_EMP.equals(dim) && empInOrgScope(subjectId, empMap, selfEmpId, codes);
            }
            // WORKFLOW_PARTICIPANT 在动态查询上下文无 business_key 概念 → fail-close
            case WORKFLOW_PARTICIPANT -> false;
        };
    }

    /**
     * 员工是否在机构范围内：本人始终放行；否则其主机构(mainOrgCode)需落在 orgCodes 内。
     */
    private boolean empInOrgScope(String empId, Map<String, UserDTO> empMap, String selfEmpId,
                                  Set<String> orgCodes) {
        if (Objects.equals(empId, selfEmpId)) {
            return true;
        }
        UserDTO u = empMap.get(empId);
        if (u == null || u.getMainOrgCode() == null) {
            return false;
        }
        return orgCodes.contains(u.getMainOrgCode());
    }

    /**
     * 按 dim 选择对应的 MetricApi 分支接口取指标值（V1.0 单条循环）。
     */
    private Map<String, BigDecimal> fetchValuesByDim(String dim, String subjectId,
                                                     LocalDate dataDate, List<String> metricCodes) {
        try {
            return switch (dim) {
                case DIM_EMP -> metricApi.getEmpMetricValues(subjectId, dataDate, metricCodes);
                case DIM_ORG -> metricApi.getOrgMetricValues(subjectId, dataDate, metricCodes);
                case DIM_CUST -> metricApi.getCustMetricValues(subjectId, dataDate, metricCodes);
                default -> Map.of();
            };
        } catch (RuntimeException ex) {
            // V1.0 单条失败不影响其他 subject，记 warn 后返回空 Map
            log.warn("[DynamicQuery] 取指标值失败 dim={} subjectId={} dataDate={} cause={}",
                    dim, subjectId, dataDate, ex.getMessage());
            return Map.of();
        }
    }

    /**
     * 批量构建 empId → UserDTO 映射（用于 EMP 维度回填工号+姓名，避免逐行查库）。
     */
    private Map<String, UserDTO> buildEmpMap(List<String> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, UserDTO> map = new HashMap<>();
            for (UserDTO u : userApi.getUserByEmpIds(empIds)) {
                if (u != null && u.getEmpId() != null) {
                    map.put(u.getEmpId(), u);
                }
            }
            return map;
        } catch (RuntimeException ex) {
            log.warn("[DynamicQuery] 批量取员工工号/姓名失败，回退展示 empId，err={}", ex.getMessage());
            return Map.of();
        }
    }

    /**
     * subjectId → subjectName 解析。
     * <ul>
     *   <li>EMP：展示「工号 姓名」（工号=username，姓名=displayName）；查不到则回填 empId</li>
     *   <li>ORG：机构名；CUST：客户名</li>
     * </ul>
     */
    private String resolveSubjectName(String dim, String subjectId, Map<String, UserDTO> empMap) {
        try {
            return switch (dim) {
                case DIM_ORG -> Optional.ofNullable(orgApi.getOrg(subjectId))
                        .map(OrgDTO::getOrgName).orElse(subjectId);
                case DIM_CUST -> customerQueryApi.getCustomer(subjectId)
                        .map(CustomerDTO::getCustName).orElse(subjectId);
                // EMP：工号 + 姓名
                default -> formatEmpLabel(subjectId, empMap.get(subjectId));
            };
        } catch (RuntimeException ex) {
            log.warn("[DynamicQuery] resolveSubjectName 失败 dim={} subjectId={}", dim, subjectId, ex);
            return subjectId;
        }
    }

    /**
     * 员工「工号」列：取 username(工号)，缺失回退 empId。姓名单独成列（empName）。
     */
    private String formatEmpLabel(String empId, UserDTO u) {
        if (u == null) {
            return empId;
        }
        return u.getUsername() != null && !u.getUsername().isBlank() ? u.getUsername() : empId;
    }

    /**
     * 员工「姓名」列：取 displayName，缺失为空串。
     */
    private String resolveEmpName(String empId, UserDTO u) {
        if (u == null || u.getDisplayName() == null) {
            return "";
        }
        return u.getDisplayName().trim();
    }

    private MetricColumnDTO toColumnDTO(MetricDefDTO def) {
        return MetricColumnDTO.builder()
                .metricCode(def.getMetricCode())
                .metricName(def.getMetricName())
                .unit(null)
                .build();
    }
}
