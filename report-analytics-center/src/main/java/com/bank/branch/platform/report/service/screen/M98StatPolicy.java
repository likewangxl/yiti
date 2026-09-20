package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * M98 员工统计数据源的单一安全策略。
 *
 * <p>该表属于外部只读统计表，配置只允许固定 profile 和 SUMMARY 模式；所有调用方
 * （数据源保存、查询引擎、画布绑定和 CODE 输出列推导）复用本类，避免各入口形成不同
 * 的白名单。</p>
 */
public final class M98StatPolicy {

    public static final String SOURCE_KIND = "M98_STAT";
    public static final String SCOPE_MODE = "NAMED_GROUP";
    public static final String MODE_SUMMARY = "SUMMARY";

    public static final String CORP_REVENUE = "CORP_REVENUE";
    public static final String RETAIL_REVENUE = "RETAIL_REVENUE";
    public static final String RETAIL_LOAN = "RETAIL_LOAN";
    public static final String CORP_NPL_RATE = "CORP_NPL_RATE";
    public static final String RETAIL_NPL_RATE = "RETAIL_NPL_RATE";

    private static final Set<String> PROFILES = Set.of(
            CORP_REVENUE, RETAIL_REVENUE, RETAIL_LOAN, CORP_NPL_RATE, RETAIL_NPL_RATE);
    private static final Set<String> CONFIG_FIELDS = Set.of(
            "schemaVersion", "scopeMode", "profile", "mode");
    private static final Set<String> FORBIDDEN_SUMMARY_BINDINGS = Set.of(
            "branches", "ranking", "retailRanking", "corpRanking");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private M98StatPolicy() {
    }

    public static boolean isProfile(String profile) {
        return profile != null && PROFILES.contains(profile);
    }

    public static boolean isNplProfile(String profile) {
        return CORP_NPL_RATE.equals(profile) || RETAIL_NPL_RATE.equals(profile);
    }

    /** 固定币种映射：对公 FTP 收入只读全币折人民币，其余 profile 只读人民币。 */
    public static String currencyType(String profile) {
        return CORP_REVENUE.equals(profile) ? "199" : "01";
    }

    public static String requiredBizLine(String profile) {
        if (profile == null) {
            return null;
        }
        return profile.startsWith("CORP_") ? "CORP" : profile.startsWith("RETAIL_") ? "RETAIL" : null;
    }

    /** 严格判断配置对象，供只读 gate 使用；不抛异常以便调用方按 fail-close 处理。 */
    public static boolean isStrictConfig(JsonNode config) {
        if (config == null || !config.isObject()
                || !isExactFields(config)
                || !config.path("schemaVersion").isIntegralNumber()
                || config.path("schemaVersion").asInt() != 2
                || !isText(config, "scopeMode", SCOPE_MODE)
                || !isText(config, "mode", MODE_SUMMARY)) {
            return false;
        }
        return isProfile(config.path("profile").asText());
    }

    /** 保存/试跑边界的严格校验，条线不匹配使用独立错误码。 */
    public static void validateConfig(JsonNode config, String bizLine) {
        if (!isStrictConfig(config)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String expectedLine = requiredBizLine(config.path("profile").asText());
        String actualLine = bizLine == null ? null : bizLine.trim().toUpperCase(Locale.ROOT);
        if (expectedLine == null || !expectedLine.equals(actualLine)) {
            throw new RptException(RptErrorCode.SCREEN_BIZ_LINE_MISMATCH);
        }
    }

    /** 命名组数据源安全准入；WIDE_TABLE 的既有规则由调用方继续保留。 */
    public static boolean isSafeNamedGroupDatasource(RptScreenDatasource datasource) {
        if (datasource == null || !SOURCE_KIND.equals(datasource.getSourceKind())) {
            return false;
        }
        try {
            JsonNode config = MAPPER.readTree(datasource.getConfigJson() == null
                    ? "{}" : datasource.getConfigJson());
            if (!isStrictConfig(config)) {
                return false;
            }
            String expectedLine = requiredBizLine(config.path("profile").asText());
            String actualLine = datasource.getBizLine() == null
                    ? null : datasource.getBizLine().trim().toUpperCase(Locale.ROOT);
            return expectedLine != null && expectedLine.equals(actualLine);
        } catch (Exception ex) {
            return false;
        }
    }

    /** SUMMARY 只允许单指标/趋势槽位，机构列表和排名槽位会改变数据粒度，必须拒绝。 */
    public static boolean allowsCodeBinding(String bindingKey) {
        return bindingKey == null || !FORBIDDEN_SUMMARY_BINDINGS.contains(bindingKey);
    }

    /** 由固定 profile 推导 M98 输出列角色，金额 unit 保持 null 表示原始单位尚未认证。 */
    public static Map<String, String> outputRoles(JsonNode config) {
        if (!isStrictConfig(config)) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put("data_date", "DIM");
        roles.put(isNplProfile(config.path("profile").asText()) ? "ratio" : "amount", "METRIC");
        return roles;
    }

    private static boolean isExactFields(JsonNode config) {
        Set<String> fields = new HashSet<>();
        config.fieldNames().forEachRemaining(fields::add);
        return CONFIG_FIELDS.equals(fields);
    }

    private static boolean isText(JsonNode config, String field, String expected) {
        JsonNode value = config.get(field);
        return value != null && value.isTextual() && expected.equals(value.asText());
    }
}
