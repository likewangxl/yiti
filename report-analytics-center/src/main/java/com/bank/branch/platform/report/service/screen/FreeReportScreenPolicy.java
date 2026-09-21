package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FREE_REPORT 大屏数据源的唯一契约入口。
 *
 * <p>自由报表本身是动态列能力，经营大屏只能读取预先准备的 TEST 分支经营批次。
 * 因此这里同时固定配置键、批次元数据、行 JSON 字段、输出列顺序及单位，避免把自由报表
 * 的原始动态列重新变成大屏 SQL/JSON 的任意执行面。</p>
 */
public final class FreeReportScreenPolicy {

    public static final String SOURCE_KIND = "FREE_REPORT";
    public static final String SCOPE_MODE = "NAMED_GROUP";
    public static final String DATA_CLASSIFICATION = "TEST";
    public static final String PROFILE_BRANCH_OVERVIEW = "BRANCH_OVERVIEW";
    public static final String BATCH_ID_PREFIX = "TEST_BRANCH_";
    public static final String REPORT_NAME_PREFIX = "TEST_BRANCH_OPERATING";

    /** 固定输出列顺序：先机构维度，再固定行维度，最后固定度量。 */
    public static final List<String> OUTPUT_COLUMNS = List.of(
            "org_code", "kind", "key", "name", "data_date", "unit", "status", "owner",
            "value", "yoy", "mom", "actual", "target", "rate", "gap", "deposit", "loan",
            "amount", "increase", "count", "pending", "days");

    /** DATA_JSON 允许的固定字段；org_code 只能来自 RPT_FREE_REPORT_ROW.ORG_CODE。 */
    public static final List<String> JSON_FIELDS =
            Collections.unmodifiableList(OUTPUT_COLUMNS.subList(1, OUTPUT_COLUMNS.size()));

    public static final Set<String> ALLOWED_KINDS = Set.of(
            "kpi", "target", "history", "composition", "marketing", "project", "team", "attention");
    public static final Set<String> ALLOWED_UNITS = Set.of("YUAN", "COUNT", "PERCENT");

    private static final Set<String> CONFIG_FIELDS = Set.of(
            "schemaVersion", "scopeMode", "dataClassification", "profile", "batchId");
    private static final Set<String> NUMERIC_FIELDS = Set.of(
            "value", "yoy", "mom", "actual", "target", "rate", "gap", "deposit", "loan",
            "amount", "increase", "count", "pending", "days");
    private static final Set<String> TEXT_FIELDS = Set.of(
            "kind", "key", "name", "data_date", "unit", "status", "owner");
    private static final Map<String, String> FIXED_UNITS = fixedUnits();
    private static final Map<String, String> FIXED_ROLES = fixedRoles();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private FreeReportScreenPolicy() {
    }

    /** 严格判断 FREE_REPORT 配置，不对未知键或缺省字段做兼容推断。 */
    public static boolean isStrictConfig(JsonNode config) {
        if (config == null || !config.isObject() || !hasExactFields(config, CONFIG_FIELDS)) {
            return false;
        }
        JsonNode schemaVersion = config.get("schemaVersion");
        JsonNode scopeMode = config.get("scopeMode");
        JsonNode classification = config.get("dataClassification");
        JsonNode profile = config.get("profile");
        JsonNode batchId = config.get("batchId");
        return schemaVersion != null && schemaVersion.isIntegralNumber()
                && schemaVersion.asInt() == 2
                && textEquals(scopeMode, SCOPE_MODE)
                && textEquals(classification, DATA_CLASSIFICATION)
                && textEquals(profile, PROFILE_BRANCH_OVERVIEW)
                && batchId != null && batchId.isTextual()
                && batchId.asText().equals(batchId.asText().trim())
                && batchId.asText().startsWith(BATCH_ID_PREFIX)
                && batchId.asText().length() > BATCH_ID_PREFIX.length();
    }

    /** 保存、试跑和运行时共用的严格配置校验。 */
    public static void validateConfig(JsonNode config) {
        if (!isStrictConfig(config)) {
            throw invalidConfig();
        }
    }

    /** 保存边界使用的配置+条线校验；FREE_REPORT 仅允许 CORP/COMMON。 */
    public static void validateConfig(JsonNode config, String bizLine) {
        validateConfig(config);
        String line = trimToNull(bizLine);
        if (line == null || !("CORP".equalsIgnoreCase(line) || "COMMON".equalsIgnoreCase(line))) {
            throw new RptException(RptErrorCode.SCREEN_BIZ_LINE_MISMATCH);
        }
    }

    /** JSON 文本入口，使用重复键检测，避免配置旁路覆盖前一个键值。 */
    public static JsonNode parseConfig(String configJson) {
        try {
            JsonNode config = MAPPER.readTree(configJson == null ? "" : configJson);
            validateConfig(config);
            return config;
        } catch (RptException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, ex);
        }
    }

    /** 返回固定批次 ID；调用方不得用请求体中的 batchId 覆盖它。 */
    public static String batchId(JsonNode config) {
        validateConfig(config);
        return config.path("batchId").asText();
    }

    /** 批次状态、报表名称和列定义的只读准入校验。 */
    public static List<ColumnDefinition> validateBatchMetadata(String status, String reportName,
                                                                 String colDefsJson) {
        String rawStatus = status == null ? null : status;
        if (rawStatus == null || !rawStatus.equals(rawStatus.trim()) || !"SUCCESS".equals(rawStatus)) {
            throw invalidConfig();
        }
        String name = reportName;
        if (name == null || !name.equals(name.trim()) || !name.startsWith(REPORT_NAME_PREFIX)) {
            throw invalidConfig();
        }
        return parseColumnDefinitions(colDefsJson);
    }

    /**
     * 校验 COL_DEFS 的精确元数据，并按固定 JSON 字段返回定义。
     * 每个元素只允许 key/label 两个键，key 不得重复，且不能包含 org_code。
     */
    public static List<ColumnDefinition> parseColumnDefinitions(String colDefsJson) {
        try {
            JsonNode root = MAPPER.readTree(colDefsJson == null ? "" : colDefsJson);
            if (root == null || !root.isArray() || root.size() != JSON_FIELDS.size()) {
                throw invalidConfig();
            }
            List<ColumnDefinition> definitions = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (JsonNode item : root) {
                if (item == null || !item.isObject() || !hasExactFields(item, Set.of("key", "label"))) {
                    throw invalidConfig();
                }
                JsonNode keyNode = item.get("key");
                JsonNode labelNode = item.get("label");
                String key = keyNode == null || !keyNode.isTextual()
                        ? null : trimToNull(keyNode.asText());
                String label = labelNode == null || !labelNode.isTextual()
                        ? null : trimToNull(labelNode.asText());
                if (key == null || label == null || !JSON_FIELDS.contains(key) || !seen.add(key)) {
                    throw invalidConfig();
                }
                definitions.add(new ColumnDefinition(key, label));
            }
            if (!seen.equals(new LinkedHashSet<>(JSON_FIELDS))) {
                throw invalidConfig();
            }
            return List.copyOf(definitions);
        } catch (RptException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, ex);
        }
    }

    /**
     * 将一行 JSON 投影为固定列顺序。每个固定字段都必须出现（值可为 null）；未知字段、对象/数组和
     * 非法类型一律拒绝。机构编码始终由 ROW 表列传入，JSON 中出现 org_code 会被视为未知字段。
     */
    public static List<Object> projectRow(String orgCode, String dataJson) {
        String normalizedOrg = trimToNull(orgCode);
        if (normalizedOrg == null) {
            throw invalidConfig();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(dataJson == null ? "" : dataJson);
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, ex);
        }
        if (root == null || !root.isObject()) {
            throw invalidConfig();
        }
        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            if (!JSON_FIELDS.contains(fieldNames.next())) {
                throw invalidConfig();
            }
        }
        for (String field : JSON_FIELDS) {
            if (!root.has(field)) {
                throw invalidConfig();
            }
        }
        List<Object> result = new ArrayList<>(OUTPUT_COLUMNS.size());
        result.add(normalizedOrg);
        for (String field : JSON_FIELDS) {
            JsonNode value = root.get(field);
            result.add(projectValue(field, value));
        }
        // List.copyOf 禁止 null，而固定 JSON 字段明确允许 null。
        return Collections.unmodifiableList(result);
    }

    /** 固定输出列角色，供 CODE 绑定校验复用。 */
    public static Map<String, String> outputRoles() {
        return FIXED_ROLES;
    }

    /** 兼容按配置传参的调用方；FREE_REPORT 输出列不随配置变化。 */
    public static Map<String, String> outputRoles(JsonNode config) {
        validateConfig(config);
        return outputRoles();
    }

    /** 固定输出列单位；value 的单位刻意保持 null，由行 unit 解释。 */
    public static Map<String, String> outputUnits() {
        return FIXED_UNITS;
    }

    /** 固定响应列元数据，顺序与 {@link #OUTPUT_COLUMNS} 一致。 */
    public static List<ScreenDataRespDTO.ColumnMeta> columnsMeta() {
        List<ScreenDataRespDTO.ColumnMeta> result = new ArrayList<>();
        for (String column : OUTPUT_COLUMNS) {
            result.add(new ScreenDataRespDTO.ColumnMeta(column, null, FIXED_ROLES.get(column),
                    FIXED_UNITS.get(column), null));
        }
        return List.copyOf(result);
    }

    public static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Object projectValue(String field, JsonNode value) {
        if (value == null || value.isNull()) {
            if ("kind".equals(field) || "key".equals(field)) {
                throw invalidConfig();
            }
            return null;
        }
        if (!value.isValueNode()) {
            throw invalidConfig();
        }
        if (NUMERIC_FIELDS.contains(field)) {
            if (!value.isNumber()) {
                throw invalidConfig();
            }
            try {
                return value.decimalValue();
            } catch (RuntimeException ex) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, ex);
            }
        }
        if (!TEXT_FIELDS.contains(field) || !value.isTextual()) {
            throw invalidConfig();
        }
        String text = value.asText();
        if ("kind".equals(field)) {
            if (text.isBlank() || !ALLOWED_KINDS.contains(text)) {
                throw invalidConfig();
            }
        } else if ("key".equals(field) && text.isBlank()) {
            throw invalidConfig();
        } else if ("unit".equals(field)) {
            if (!ALLOWED_UNITS.contains(text)) {
                throw invalidConfig();
            }
        } else if ("data_date".equals(field)) {
            try {
                LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ex) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, ex);
            }
        }
        return text;
    }

    private static boolean hasExactFields(JsonNode node, Set<String> expected) {
        Set<String> actual = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        return actual.equals(expected);
    }

    private static boolean textEquals(JsonNode node, String expected) {
        return node != null && node.isTextual() && expected.equals(node.asText());
    }

    private static Map<String, String> fixedRoles() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("org_code", "DIM");
        for (String field : List.of("kind", "key", "name", "data_date", "unit", "status", "owner")) {
            result.put(field, "DIM");
        }
        for (String field : List.of(
                "value", "yoy", "mom", "actual", "target", "rate", "gap", "deposit", "loan",
                "amount", "increase", "count", "pending", "days")) {
            result.put(field, "METRIC");
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> fixedUnits() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("actual", "YUAN");
        result.put("target", "YUAN");
        result.put("gap", "YUAN");
        result.put("deposit", "YUAN");
        result.put("loan", "YUAN");
        result.put("amount", "YUAN");
        result.put("increase", "YUAN");
        result.put("yoy", "PERCENT");
        result.put("mom", "PERCENT");
        result.put("rate", "PERCENT");
        result.put("count", "COUNT");
        result.put("pending", "COUNT");
        result.put("days", "COUNT");
        // value intentionally absent: its unit is the row's unit field.
        return Collections.unmodifiableMap(result);
    }

    private static RptException invalidConfig() {
        return new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
    }

    /** 固定 COL_DEFS 的一项。 */
    public record ColumnDefinition(String key, String label) {
    }
}
