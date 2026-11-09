package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.UniqueUserOrgDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpMetricValueRow;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 计算由员工指标汇总得到的机构 SUMMARY 指标。
 *
 * <p>员工归属只能通过 auth 的公开 OrgApi 获得；本类不跨模块查询认证私有表，也不写入数据库。</p>
 */
@Component
public class OrgMetricSummaryCalculator {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MetricDefService metricDefService;
    private final OrgApi orgApi;
    private final EmpIndexResultMapper empIndexResultMapper;
    private final OrgIndexResultMapper orgIndexResultMapper;

    public OrgMetricSummaryCalculator(MetricDefService metricDefService,
                                      OrgApi orgApi,
                                      EmpIndexResultMapper empIndexResultMapper,
                                      OrgIndexResultMapper orgIndexResultMapper) {
        this.metricDefService = metricDefService;
        this.orgApi = orgApi;
        this.empIndexResultMapper = empIndexResultMapper;
        this.orgIndexResultMapper = orgIndexResultMapper;
    }

    /** 按指标定义、日期和版本计算机构值；缺少任一员工引用值时该机构返回 null。 */
    public Map<String, BigDecimal> calculate(PerfMetricDef definition, LocalDate dataDate, String version) {
        validateDefinition(definition);
        List<String> refs = parseRefs(definition.getRefMetricCodes());
        List<PerfMetricDef> refDefs = refs.stream().map(metricDefService::getByCodeOrNull).toList();
        String unit = definition.getUnit();
        if (blank(unit)) throw invalid("机构与员工指标单位不能为空");
        for (PerfMetricDef ref : refDefs) {
            if (ref == null || ref.getDeleted() != null && ref.getDeleted() != 0
                    || !"ACTIVE".equalsIgnoreCase(ref.getStatus())
                    || !"EMP".equalsIgnoreCase(ref.getBaseDim())
                    || ref.getValSlot() == null || ref.getValSlot() < 1 || ref.getValSlot() > 400) {
                throw invalid("员工引用指标定义非法");
            }
            if (blank(ref.getUnit()) || !unit.equals(ref.getUnit())) throw invalid("员工引用指标单位不一致");
        }
        String scope = summaryScope(definition);
        List<String> orgCodes = orgIndexResultMapper.selectDistinctOrgCodes(dataDate, version);
        if (orgCodes == null || orgCodes.isEmpty()) {
            return Map.of();
        }
        verifyOrgCodes(orgCodes, orgApi.getOrgsByCodes(orgCodes));
        Map<String, Set<String>> scopes = new LinkedHashMap<>();
        for (String orgCode : orgCodes) scopes.put(orgCode, Set.of(orgCode));
        Set<String> allowedOrgCodes = new HashSet<>(orgCodes);
        if ("ORG_TREE".equals(scope)) {
            allowedOrgCodes.clear();
            for (String orgCode : orgCodes) {
                Set<String> subtree = orgApi.getOrgSubtreeCodes(orgCode);
                if (subtree == null || subtree.isEmpty() || !subtree.contains(orgCode)) throw invalid("机构子树非法: " + orgCode);
                scopes.put(orgCode, Set.copyOf(subtree));
                allowedOrgCodes.addAll(subtree);
            }
            verifyOrgCodes(allowedOrgCodes, orgApi.getOrgsByCodes(allowedOrgCodes));
        }
        List<UniqueUserOrgDTO> mappings = orgApi.listUniqueUserOrgs(allowedOrgCodes);
        Map<String, String> empOrg = uniqueMappings(mappings, allowedOrgCodes);
        List<String> empIds = new ArrayList<>(empOrg.keySet());
        Set<String> existingEmps = new HashSet<>(empIndexResultMapper.selectDistinctEmpIds(dataDate, version));
        empIds.removeIf(id -> !existingEmps.contains(id));
        Map<String, Map<String, BigDecimal>> values = new HashMap<>();
        for (int i = 0; i < refs.size(); i++) {
            PerfMetricDef ref = refDefs.get(i);
            Map<String, BigDecimal> byEmp = readValues(empIds, dataDate, version, ref.getValSlot());
            values.put(refs.get(i), byEmp);
        }
        Map<String, BigDecimal> output = new LinkedHashMap<>();
        for (String orgCode : orgCodes) {
            Set<String> selected = new HashSet<>();
            for (Map.Entry<String, String> entry : empOrg.entrySet()) {
                if (scopes.getOrDefault(orgCode, Set.of()).contains(entry.getValue())) {
                    if (!selected.add(entry.getKey())) throw invalid("员工重复计入: " + entry.getKey());
                }
            }
            BigDecimal total = BigDecimal.ZERO;
            boolean complete = !selected.isEmpty();
            for (String empId : selected) {
                BigDecimal employeeTotal = BigDecimal.ZERO;
                for (String ref : refs) {
                    BigDecimal value = values.get(ref).get(empId);
                    if (value == null) { complete = false; break; }
                    employeeTotal = employeeTotal.add(value);
                }
                if (!complete) break;
                total = total.add(employeeTotal);
            }
            output.put(orgCode, complete ? total : null);
        }
        return output;
    }

    private Map<String, String> uniqueMappings(List<UniqueUserOrgDTO> mappings, Set<String> orgCodes) {
        Map<String, String> result = new LinkedHashMap<>();
        if (mappings == null) throw invalid("员工机构映射查询失败");
        for (UniqueUserOrgDTO mapping : mappings) {
            if (mapping == null || blank(mapping.getEmpId()) || blank(mapping.getOrgCode())) throw invalid("员工归属缺失");
            if (!orgCodes.contains(mapping.getOrgCode())) throw invalid("员工归属超出机构范围");
            if (result.putIfAbsent(mapping.getEmpId(), mapping.getOrgCode()) != null) throw invalid("员工存在多个机构归属");
        }
        return result;
    }

    private void verifyOrgCodes(java.util.Collection<String> expected, List<com.bank.branch.platform.auth.api.dto.OrgDTO> actual) {
        Set<String> expectedSet = new HashSet<>(expected);
        Set<String> actualSet = new HashSet<>();
        if (actual == null) throw invalid("机构查询失败");
        for (var dto : actual) {
            if (dto == null || blank(dto.getOrgCode()) || !actualSet.add(dto.getOrgCode())) throw invalid("机构查询结果重复或缺失");
        }
        if (!expectedSet.equals(actualSet) || expectedSet.size() != expected.size()) throw invalid("机构集合未完整核验");
    }

    private Map<String, BigDecimal> readValues(List<String> empIds, LocalDate date, String version, Integer slot) {
        if (slot == null || slot < 1 || slot > 400) throw invalid("员工引用指标槽位非法");
        if (empIds.isEmpty()) return Map.of();
        List<EmpMetricValueRow> rows = empIndexResultMapper.selectSlotValuesByEmps(empIds, date, version, slot);
        Map<String, BigDecimal> result = new HashMap<>();
        Set<String> allowed = new HashSet<>(empIds);
        for (EmpMetricValueRow row : rows == null ? List.<EmpMetricValueRow>of() : rows) {
            if (row == null || blank(row.getEmpId()) || !allowed.contains(row.getEmpId()) || result.containsKey(row.getEmpId())) throw invalid("员工指标结果重复或越界");
            result.put(row.getEmpId(), row.getMetricValue());
        }
        return result;
    }

    private void validateDefinition(PerfMetricDef def) {
        if (def == null || def.getDeleted() != null && def.getDeleted() != 0 || !"ACTIVE".equalsIgnoreCase(def.getStatus())
                || !"ORG".equalsIgnoreCase(def.getBaseDim()) || !"SUM".equalsIgnoreCase(def.getSummaryRule())
                || !"SUMMARY".equalsIgnoreCase(def.getCalcLogicType())) throw invalid("机构 SUMMARY 定义非法");
        if (def.getValSlot() == null || def.getValSlot() < 1 || def.getValSlot() > 400) throw invalid("机构指标槽位非法");
        if (parseRefs(def.getRefMetricCodes()).isEmpty()) throw invalid("机构 SUMMARY 缺少引用");
        if (summaryScope(def) == null) throw invalid("机构 SUMMARY 范围非法");
    }

    private List<String> parseRefs(String json) {
        try {
            JsonNode node = MAPPER.readTree(json == null ? "null" : json);
            if (node == null || !node.isArray()) throw new IllegalArgumentException();
            List<String> refs = new ArrayList<>();
            node.forEach(n -> { if (!n.isTextual() || blank(n.asText()) || refs.contains(n.asText())) throw invalid("引用指标重复或非法"); refs.add(n.asText()); });
            return refs;
        } catch (PerfException ex) { throw ex; } catch (Exception ex) { throw invalid("引用指标 JSON 非法"); }
    }

    private String summaryScope(PerfMetricDef def) {
        try { JsonNode node = MAPPER.readTree(def.getMetricDesc() == null ? "{}" : def.getMetricDesc()).path("_summaryOrgScope");
            return node.isTextual() && ("ORG_DIRECT".equals(node.asText()) || "ORG_TREE".equals(node.asText())) ? node.asText() : null;
        } catch (Exception ex) { return null; }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static PerfException invalid(String message) { return new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, message); }
}
