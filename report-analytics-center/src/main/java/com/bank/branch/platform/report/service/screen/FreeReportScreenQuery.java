package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * FREE_REPORT 的只读 JDBC 查询器。
 *
 * <p>查询模板只读取批次状态/名称/列定义和行的 ID、ORG_CODE、DATA_JSON 三列；固定 batchId
 * 来自已校验配置，机构范围来自调用方完成屏级授权后的服务端集合。请求中的 batchId 不进入此类，
 * 因而不能覆盖配置批次或形成任意批次读取旁路。</p>
 */
@Slf4j
final class FreeReportScreenQuery {

    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final int MAX_LIMIT = 100_000;
    private static final String BATCH_SQL =
            "SELECT b.STATUS, b.REPORT_NAME, b.COL_DEFS "
                    + "FROM RPT_FREE_REPORT_BATCH b WHERE b.ID = ?";

    private final DataSource dataSource;

    FreeReportScreenQuery(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 执行固定分支经营批次查询。
     *
     * @param config 严格 FREE_REPORT 配置
     * @param authorizedOrgCodes 屏级完整授权机构范围
     * @param outputOrgCodes 服务端输出机构子集
     * @param requestedOrgCode 服务端额外下钻机构条件，可为空
     * @param limit 正式/试跑输出上限；数据库实际读取 limit+1 用于拒绝超限
     */
    ScreenDataRespDTO query(JsonNode config, Collection<String> authorizedOrgCodes,
                            Collection<String> outputOrgCodes, String requestedOrgCode, int limit) {
        FreeReportScreenPolicy.validateConfig(config);
        List<String> authorized = requiredCodes(authorizedOrgCodes);
        List<String> output = requiredOutputCodes(authorized, outputOrgCodes, requestedOrgCode);
        int safeLimit = checkedLimit(limit);
        String batchId = FreeReportScreenPolicy.batchId(config);
        if (dataSource == null) {
            throw queryFailure(new IllegalStateException("FREE_REPORT read-only datasource unavailable"));
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            readAndValidateBatch(connection, batchId);
            ScreenQueryPlan plan = buildRowsPlan(batchId, output, safeLimit);
            List<List<Object>> rows = new ArrayList<>();
            Set<String> received = new LinkedHashSet<>();
            LocalDate maxDate = null;
            try (PreparedStatement statement = connection.prepareStatement(plan.sql())) {
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                bind(statement, plan.params());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        // 只读取 r.ID/r.ORG_CODE/r.DATA_JSON；禁止退化为 SELECT * 或访问冻结列。
                        long rowId = result.getLong(1);
                        if (result.wasNull()) {
                            throw invalidRow("FREE_REPORT row ID is null");
                        }
                        if (rows.size() >= safeLimit) {
                            throw overflow(rowId, safeLimit);
                        }
                        String orgCode = result.getString(2);
                        String dataJson = result.getString(3);
                        List<Object> projected = FreeReportScreenPolicy.projectRow(orgCode, dataJson);
                        if (projected.size() != FreeReportScreenPolicy.OUTPUT_COLUMNS.size()) {
                            throw invalidRow("FREE_REPORT fixed projection size mismatch");
                        }
                        rows.add(projected);
                        String normalizedOrg = FreeReportScreenPolicy.trimToNull(orgCode);
                        if (!output.contains(normalizedOrg)) {
                            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
                        }
                        received.add(normalizedOrg);
                        Object date = projected.get(4);
                        if (date != null) {
                            LocalDate parsed = LocalDate.parse(String.valueOf(date));
                            if (maxDate == null || parsed.isAfter(maxDate)) {
                                maxDate = parsed;
                            }
                        }
                    }
                }
            }
            return response(batchId, rows, output, received, maxDate);
        } catch (RptException ex) {
            throw ex;
        } catch (SQLException ex) {
            log.warn("[FreeReportScreenQuery] fixed batch query failed batchId={} cause={}", batchId,
                    ex.getMessage());
            throw queryFailure(ex);
        } catch (RuntimeException ex) {
            log.warn("[FreeReportScreenQuery] fixed batch query runtime failure batchId={} cause={}", batchId,
                    ex.getMessage());
            throw queryFailure(ex);
        }
    }

    /** 保存数据源时的只读批次/元数据校验，不读取任何行，不修改数据库。 */
    void validateConfiguredSource(JsonNode config) {
        FreeReportScreenPolicy.validateConfig(config);
        if (dataSource == null) {
            throw queryFailure(new IllegalStateException("FREE_REPORT read-only datasource unavailable"));
        }
        String batchId = FreeReportScreenPolicy.batchId(config);
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            readAndValidateBatch(connection, batchId);
        } catch (RptException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw queryFailure(ex);
        } catch (RuntimeException ex) {
            throw queryFailure(ex);
        }
    }

    /**
     * 构造可审查的固定 SQL 计划。该入口不打开数据库，便于单元测试锁定参数绑定和 LIMIT+1。
     */
    ScreenQueryPlan previewPlan(JsonNode config, Collection<String> authorizedOrgCodes,
                                Collection<String> outputOrgCodes, String requestedOrgCode, int limit) {
        FreeReportScreenPolicy.validateConfig(config);
        List<String> authorized = requiredCodes(authorizedOrgCodes);
        List<String> output = requiredOutputCodes(authorized, outputOrgCodes, requestedOrgCode);
        return buildRowsPlan(FreeReportScreenPolicy.batchId(config), output, checkedLimit(limit));
    }

    private ScreenQueryPlan buildRowsPlan(String batchId, List<String> outputOrgCodes, int limit) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(outputOrgCodes.size(), "?"));
        String sql = "SELECT r.ID, r.ORG_CODE, r.DATA_JSON "
                + "FROM RPT_FREE_REPORT_ROW r "
                + "JOIN RPT_FREE_REPORT_BATCH b ON b.ID = r.BATCH_ID "
                + "AND b.STATUS = 'SUCCESS' AND b.REPORT_NAME LIKE 'TEST_BRANCH_OPERATING%' "
                + "WHERE r.BATCH_ID = ? AND r.ORG_CODE IN (" + placeholders + ") "
                + "ORDER BY r.ID LIMIT " + (limit + 1);
        List<Object> params = new ArrayList<>();
        params.add(batchId);
        params.addAll(outputOrgCodes);
        return new ScreenQueryPlan(sql, List.copyOf(params));
    }

    private void readAndValidateBatch(Connection connection, String batchId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(BATCH_SQL)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setString(1, batchId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                FreeReportScreenPolicy.validateBatchMetadata(
                        result.getString(1), result.getString(2), result.getString(3));
            }
        }
    }

    private ScreenDataRespDTO response(String batchId, List<List<Object>> rows, List<String> output,
                                       Set<String> received, LocalDate maxDate) {
        String status = received.isEmpty() ? "NO_SOURCE"
                : received.size() == output.size() ? "COMPLETE" : "PARTIAL";
        List<String> missing = output.stream().filter(code -> !received.contains(code)).toList();
        String message = switch (status) {
            case "COMPLETE" -> null;
            case "PARTIAL" -> "所选输出机构存在批次行缺口";
            default -> "所选输出机构没有批次行";
        };
        ScreenDataRespDTO response = new ScreenDataRespDTO(
                FreeReportScreenPolicy.OUTPUT_COLUMNS, rows,
                FreeReportScreenPolicy.columnsMeta());
        ScreenDataRespDTO.Quality quality = new ScreenDataRespDTO.Quality(
                batchId,
                maxDate == null ? null : maxDate.toString(),
                batchId,
                status,
                output.size(),
                received.size(),
                null,
                null,
                message);
        quality.setDataClassification(FreeReportScreenPolicy.DATA_CLASSIFICATION);
        quality.setSelectedComplete("COMPLETE".equals(status));
        quality.setMixedPeriod(false);
        quality.setMissing(List.of());
        quality.setMissingSubjects(missing);
        quality.setNewerIncomplete(List.of());
        quality.setExpected(output.size());
        quality.setReceived(received.size());
        response.setQuality(quality);
        return response;
    }

    private List<String> requiredOutputCodes(List<String> authorized, Collection<String> source,
                                             String requestedOrgCode) {
        List<String> output = requiredCodes(source);
        if (!new LinkedHashSet<>(authorized).containsAll(output)) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        String requested = FreeReportScreenPolicy.trimToNull(requestedOrgCode);
        if (requested != null) {
            if (!authorized.contains(requested) || !output.contains(requested)) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            // 服务端下钻只能继续收窄，不能把请求机构并入范围或替换授权集合。
            return List.of(requested);
        }
        return output;
    }

    private List<String> requiredCodes(Collection<String> source) {
        if (source == null || source.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : source) {
            String normalized = FreeReportScreenPolicy.trimToNull(value);
            if (normalized == null) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            result.add(normalized);
        }
        if (result.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        return new ArrayList<>(result);
    }

    private int checkedLimit(int limit) {
        if (limit <= 0 || limit >= MAX_LIMIT) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        return limit;
    }

    private void bind(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value instanceof String text) {
                statement.setString(i + 1, text);
            } else {
                statement.setObject(i + 1, value);
            }
        }
    }

    private RptException overflow(long rowId, int limit) {
        return new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED,
                new IllegalStateException("FREE_REPORT result exceeds limit=" + limit
                        + "; first overflow row id=" + rowId));
    }

    private RptException invalidRow(String message) {
        return new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, new IllegalStateException(message));
    }

    private RptException queryFailure(Throwable cause) {
        return new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, cause);
    }

    record ScreenQueryPlan(String sql, List<Object> params) {
    }
}
