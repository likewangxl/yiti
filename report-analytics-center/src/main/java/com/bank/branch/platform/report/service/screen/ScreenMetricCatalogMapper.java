package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.resp.ScreenMetricCatalogItemDTO;
import com.bank.branch.platform.report.support.ScreenMetricSlotDao.MetricDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 把 PERF_METRIC_DEF 行翻译成大屏绑定目录。
 *
 * <p>单位只接受白名单或已确认的机构金额编码；识别不了就保持空，避免把“箱”或空口径猜成元。
 * 不读取、不返回计算 SQL。
 */
public final class ScreenMetricCatalogMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> CONFIRMED_ORG_AMOUNT_CODES = Set.of("M_0265", "M_0277", "M_0309");
    private static final Map<String, String> UNIT_ALIASES = Map.ofEntries(
            Map.entry("元", "YUAN"),
            Map.entry("人民币", "YUAN"),
            Map.entry("RMB", "YUAN"),
            Map.entry("YUAN", "YUAN"),
            Map.entry("万元", "TEN_THOUSAND"),
            Map.entry("TEN_THOUSAND", "TEN_THOUSAND"),
            Map.entry("TEN_THOUSAND_YUAN", "TEN_THOUSAND"),
            Map.entry("亿元", "HUNDRED_MILLION"),
            Map.entry("HUNDRED_MILLION", "HUNDRED_MILLION"),
            Map.entry("HUNDRED_MILLION_YUAN", "HUNDRED_MILLION"),
            Map.entry("%", "PERCENT"),
            Map.entry("百分数", "PERCENT"),
            Map.entry("百分比", "PERCENT"),
            Map.entry("PERCENT", "PERCENT"),
            Map.entry("比例", "RATIO"),
            Map.entry("RATIO", "RATIO"),
            Map.entry("个", "COUNT"),
            Map.entry("户", "COUNT"),
            Map.entry("笔", "COUNT"),
            Map.entry("个数", "COUNT"),
            Map.entry("COUNT", "COUNT"),
            Map.entry("万户", "TEN_THOUSAND_COUNT"),
            Map.entry("TEN_THOUSAND_COUNT", "TEN_THOUSAND_COUNT")
    );
    private static final Map<String, String> TABLES = Map.of(
            "ORG", "ORG_INDEX_RESULT",
            "EMP", "EMP_INDEX_RESULT",
            "CUST", "CUST_INDEX_RESULT"
    );
    private static final Map<String, String> SUBJECTS = Map.of(
            "ORG", "org_code",
            "EMP", "emp_id",
            "CUST", "cust_no"
    );

    private ScreenMetricCatalogMapper() {
    }

    /**
     * 过滤并翻译目录。baseDim 不是 ORG/EMP/CUST 时返回空列表，未知维度不能被当成全部指标。
     */
    public static List<ScreenMetricCatalogItemDTO> toItems(List<MetricDefinition> rows, String baseDim, String keyword) {
        String dim = normalize(baseDim);
        if (dim != null && !TABLES.containsKey(dim)) {
            return List.of();
        }
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<ScreenMetricCatalogItemDTO> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (MetricDefinition row : rows) {
            if (row == null || isBlank(row.metricCode())) {
                continue;
            }
            ScreenMetricCatalogItemDTO item = toItem(row);
            if (dim != null && !dim.equals(item.getBaseDim())) {
                continue;
            }
            if (!needle.isEmpty() && !matches(item, needle)) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    private static ScreenMetricCatalogItemDTO toItem(MetricDefinition row) {
        String baseDim = normalize(row.baseDim());
        String metricName = row.metricName() == null ? "" : row.metricName().trim();
        String output = metricName.replaceAll("[`'\"\\\\]", "");
        Integer slot = row.valSlot();
        String unitRaw = firstNonBlank(row.unit(), unitFromDescription(row.metricDesc()));
        String unit = normalizeUnit(row.unit());
        String unitSource = "NONE";
        if (unit != null) {
            unitSource = "COLUMN";
        } else {
            String described = normalizeUnit(unitFromDescription(row.metricDesc()));
            if (described != null) {
                unit = described;
                unitSource = "DESCRIPTION";
            } else if (CONFIRMED_ORG_AMOUNT_CODES.contains(row.metricCode()) && "ORG".equals(baseDim)) {
                unit = "YUAN";
                unitSource = "CONFIRMED_CODE";
            }
        }
        boolean slotOk = slot != null && slot >= 1 && slot <= 400;
        boolean tableOk = TABLES.containsKey(baseDim == null ? "" : baseDim);
        boolean nameOk = !output.isBlank();
        boolean active = "ACTIVE".equalsIgnoreCase(row.status() == null ? "" : row.status().trim());
        boolean bindable = slotOk && tableOk && nameOk && active;
        String reason = null;
        if (!bindable) {
            if (!slotOk) {
                reason = "未分配 val_slot，不能映射到宽表列";
            } else if (!tableOk) {
                reason = "基础维度不是机构、员工或客户，不能生成宽表数据源";
            } else if (!nameOk) {
                reason = "指标名称不能作为查询列名";
            } else {
                reason = "指标未启用";
            }
        }
        return new ScreenMetricCatalogItemDTO(
                row.metricCode().trim(),
                metricName,
                baseDim,
                slot,
                unitRaw,
                unit,
                unitSource,
                categoryOf(row),
                normalize(row.summaryRule()),
                row.metricLevel(),
                normalize(row.calcFreq()),
                tableOk ? TABLES.get(baseDim) : null,
                tableOk ? SUBJECTS.get(baseDim) : null,
                output,
                bindable,
                reason
        );
    }

    private static boolean matches(ScreenMetricCatalogItemDTO item, String needle) {
        String hay = (item.getMetricCode() + " " + item.getMetricName() + " " + nullToEmpty(item.getMetricCategory()))
                .toLowerCase(Locale.ROOT);
        return hay.contains(needle);
    }

    private static String categoryOf(MetricDefinition row) {
        String column = row.metricCategory() == null ? "" : row.metricCategory().trim();
        if (!column.isEmpty()) {
            return column;
        }
        String described = textFromDescription(row.metricDesc(), "_category");
        if (described == null) {
            return null;
        }
        int slash = described.indexOf('/');
        return (slash < 0 ? described : described.substring(0, slash)).trim();
    }

    private static String unitFromDescription(String metricDesc) {
        return textFromDescription(metricDesc, "_unit");
    }

    private static String textFromDescription(String metricDesc, String field) {
        if (metricDesc == null || !metricDesc.trim().startsWith("{")) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(metricDesc).path(field);
            if (node.isMissingNode() || node.isNull()) {
                return null;
            }
            String text = node.asText();
            return isBlank(text) ? null : text.trim();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String normalizeUnit(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        return UNIT_ALIASES.get(raw.trim());
    }

    private static String normalize(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String left, String right) {
        if (!isBlank(left)) {
            return left.trim();
        }
        return isBlank(right) ? null : right.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
