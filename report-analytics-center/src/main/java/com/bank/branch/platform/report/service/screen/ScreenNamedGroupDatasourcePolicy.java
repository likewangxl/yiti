package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 命名机构组数据源的统一安全准入。
 *
 * <p>已有 ORG_INDEX_RESULT 宽表保持原有服务端主体约束；新增 KPI_DETAIL 只允许
 * schema2/ORG/SNAPSHOT/SINGLE；FREE_REPORT 只允许固定 TEST 分支经营批次。
 * KPI 方案是否 ACTIVE 由数据源保存边界的既有 mapper校验负责，本类不重复扩大元数据读取范围。</p>
 */
public final class ScreenNamedGroupDatasourcePolicy {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private ScreenNamedGroupDatasourcePolicy() {
    }

    /** 命名组保存、画布保存/发布和运行时引用扫描共用的精确准入。 */
    public static boolean isSafe(RptScreenDatasource datasource) {
        if (datasource == null) {
            return false;
        }
        if (M98StatPolicy.SOURCE_KIND.equals(datasource.getSourceKind())) {
            return M98StatPolicy.isSafeNamedGroupDatasource(datasource);
        }
        if (FreeReportScreenPolicy.SOURCE_KIND.equals(datasource.getSourceKind())) {
            return isFreeReport(datasource);
        }
        if ("KPI_DETAIL".equals(datasource.getSourceKind())) {
            return isKpiDetailOrgSnapshot(datasource);
        }
        if (!"WIDE_TABLE".equals(datasource.getSourceKind())) {
            return false;
        }
        try {
            JsonNode config = MAPPER.readTree(datasource.getConfigJson() == null
                    ? "{}" : datasource.getConfigJson());
            return "ORG_INDEX_RESULT".equals(config.path("table").asText())
                    && "org_code".equals(config.path("subjectCol").asText());
        } catch (Exception ex) {
            return false;
        }
    }

    /** FREE_REPORT 的命名组准入：固定配置、SINGLE、CORP/COMMON 条线。 */
    public static boolean isFreeReport(RptScreenDatasource datasource) {
        if (datasource == null || !FreeReportScreenPolicy.SOURCE_KIND.equals(datasource.getSourceKind())
                || !"SINGLE".equals(datasource.getDsType())) {
            return false;
        }
        try {
            JsonNode config = MAPPER.readTree(datasource.getConfigJson() == null
                    ? "{}" : datasource.getConfigJson());
            if (!FreeReportScreenPolicy.isStrictConfig(config)) {
                return false;
            }
            String line = datasource.getBizLine() == null
                    ? null : datasource.getBizLine().trim().toUpperCase(java.util.Locale.ROOT);
            return "CORP".equals(line) || "COMMON".equals(line);
        } catch (Exception ex) {
            return false;
        }
    }

    /** KPI_DETAIL 的命名组固定组合；ACTIVE scheme 仍由 ScreenDatasourceServiceImpl 校验。 */
    public static boolean isKpiDetailOrgSnapshot(RptScreenDatasource datasource) {
        if (datasource == null || !"KPI_DETAIL".equals(datasource.getSourceKind())
                || !"SINGLE".equals(datasource.getDsType())) {
            return false;
        }
        try {
            JsonNode config = MAPPER.readTree(datasource.getConfigJson() == null
                    ? "{}" : datasource.getConfigJson());
            return config.path("schemaVersion").isIntegralNumber()
                    && config.path("schemaVersion").asInt() == 2
                    && "NAMED_GROUP".equals(config.path("scopeMode").asText())
                    && "ORG".equals(config.path("subjectType").asText())
                    && "SNAPSHOT".equals(config.path("mode").asText())
                    && !config.path("schemeCode").asText().isBlank();
        } catch (Exception ex) {
            return false;
        }
    }
}
