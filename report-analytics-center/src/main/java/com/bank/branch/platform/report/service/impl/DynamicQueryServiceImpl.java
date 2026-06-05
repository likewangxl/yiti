package com.bank.branch.platform.report.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
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

    private static final int MAX_SUBJECT_IDS = 100;

    private static final int MAX_METRIC_CODES = 20;

    private static final String DIM_EMP = "EMP";

    private static final String DIM_ORG = "ORG";

    private static final String DIM_CUST = "CUST";

    private final BizScopeApi bizScopeApi;

    private final CurrentUserApi currentUserApi;

    private final MetricApi metricApi;

    private final OrgApi orgApi;

    private final CustomerQueryApi customerQueryApi;

    @Override
    public DynamicQueryRespDTO execute(DynamicQueryReqDTO req) {
        // 1) 入参校验
        validateRequest(req);

        // 2) 外层 DataScope 过滤
        String empId = currentUserApi.getCurrentEmpId();
        DataScopeContext scope = bizScopeApi.buildScopeContext(empId, BizType.REPORT, BizAction.LIST);
        List<String> allowed = filterBySubjectScope(req.getDim(), req.getSubjectIds(), scope);
        if (allowed.size() < req.getSubjectIds().size()) {
            log.warn("[DynamicQuery] subject 越权过滤：empId={} dim={} 请求 {} 个，命中 {} 个",
                    empId, req.getDim(), req.getSubjectIds().size(), allowed.size());
            throw new RptException(RptErrorCode.SUBJECT_OUT_OF_SCOPE);
        }

        // 3) 跨模块取值（V1.0 单条循环）
        List<Map<String, Object>> rows = new ArrayList<>(allowed.size());
        for (String sid : allowed) {
            Map<String, BigDecimal> values = fetchValuesByDim(req.getDim(), sid,
                    req.getDataDate(), req.getMetricCodes());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subjectId", sid);
            row.put("subjectName", resolveSubjectName(req.getDim(), sid));
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
                .build();
    }

    /**
     * 同步导出 Excel：复用 execute 查询结果，按"对象 + 各指标列"动态表头生成 xlsx 字节。
     * 不走异步任务表/MinIO，由控制器直接以附件流返回，前端点导出即下载。
     */
    @Override
    public byte[] exportExcel(DynamicQueryReqDTO req) {
        DynamicQueryRespDTO resp = execute(req);
        List<MetricColumnDTO> cols = resp.getColumns() != null ? resp.getColumns() : List.of();

        // 动态表头：第 1 列"对象"，其后每个指标一列（用指标名，缺省回退指标编码）
        List<List<String>> head = new ArrayList<>();
        head.add(List.of("对象"));
        for (MetricColumnDTO col : cols) {
            String name = col.getMetricName() != null && !col.getMetricName().isBlank()
                    ? col.getMetricName() : col.getMetricCode();
            head.add(List.of(name));
        }

        // 数据行：对象名 + 各指标值（null 输出空串）
        List<List<Object>> data = new ArrayList<>();
        for (Map<String, Object> row : (resp.getRows() != null ? resp.getRows() : List.<Map<String, Object>>of())) {
            List<Object> line = new ArrayList<>();
            Object subjectName = row.get("subjectName");
            line.add(subjectName != null ? subjectName : row.get("subjectId"));
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
     * 入参校验：dim 合法性 + 个数上限.
     */
    private void validateRequest(DynamicQueryReqDTO req) {
        if (req.getSubjectIds().size() > MAX_SUBJECT_IDS) {
            throw new RptException(RptErrorCode.SUBJECT_SIZE_EXCEEDED);
        }
        if (req.getMetricCodes().size() > MAX_METRIC_CODES) {
            throw new RptException(RptErrorCode.METRIC_SIZE_EXCEEDED);
        }
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
    private List<String> filterBySubjectScope(String dim, List<String> subjectIds, DataScopeContext scope) {
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
            if (isAllowed(dim, sid, type, scope)) {
                allowed.add(sid);
            }
        }
        return allowed;
    }

    private boolean isAllowed(String dim, String subjectId, DataScopeType type, DataScopeContext scope) {
        return switch (type) {
            case ALL -> true;
            case SELF, SELF_CREATED, SELF_ASSIGNED ->
                    DIM_EMP.equals(dim) && Objects.equals(subjectId, scope.empId());
            case ORG ->
                    DIM_ORG.equals(dim) && Objects.equals(subjectId, scope.orgCode());
            case ORG_SUBTREE ->
                    DIM_ORG.equals(dim) && scope.orgSubtreeCodes() != null
                            && scope.orgSubtreeCodes().contains(subjectId);
            // WORKFLOW_PARTICIPANT 在动态查询上下文无 business_key 概念 → fail-close
            case WORKFLOW_PARTICIPANT -> false;
        };
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
     * subjectId → subjectName 解析（V1.0 简化）。
     */
    private String resolveSubjectName(String dim, String subjectId) {
        try {
            return switch (dim) {
                case DIM_ORG -> Optional.ofNullable(orgApi.getOrg(subjectId))
                        .map(OrgDTO::getOrgName).orElse(subjectId);
                case DIM_CUST -> customerQueryApi.getCustomer(subjectId)
                        .map(CustomerDTO::getCustName).orElse(subjectId);
                // EMP V1.0 无 EmpQueryApi，直接回填 empId
                default -> subjectId;
            };
        } catch (RuntimeException ex) {
            log.warn("[DynamicQuery] resolveSubjectName 失败 dim={} subjectId={}", dim, subjectId, ex);
            return subjectId;
        }
    }

    private MetricColumnDTO toColumnDTO(MetricDefDTO def) {
        return MetricColumnDTO.builder()
                .metricCode(def.getMetricCode())
                .metricName(def.getMetricName())
                .unit(null)
                .build();
    }
}
