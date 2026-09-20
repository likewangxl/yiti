package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.UniqueUserOrgDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * XAN_M98_EMP_STAT_SHOW3 固定只读统计模板。
 *
 * <p>该 helper 只消费 auth 的全局唯一员工归属 API，不在 report 侧 JOIN EXT_USER_ORG。
 * 先解析完整授权机构对应的员工并选择一个共同合法统计日期，再读取有限员工明细，由 Java
 * 对 varchar 金额做严格 BigDecimal 汇总。任何缺失、非法值或未知五级分类都保持空值并透出
 * 质量状态，不以 0 填充。</p>
 */
@Slf4j
final class M98StatQuery {

    static final int MAX_EMPLOYEE_IDS = 1000;
    static final int MAX_DETAIL_ROWS = 10_000;
    private static final int QUERY_TIMEOUT_SECONDS = 30;
    private static final String TABLE = "XAN_M98_EMP_STAT_SHOW3";
    private static final Set<String> NPL_CATEGORIES = Set.of("正常", "关注", "次级", "可疑", "损失");
    private static final Set<String> NPL_RISK_CATEGORIES = Set.of("次级", "可疑", "损失");

    private final DataSource dataSource;
    private final OrgApi orgApi;

    M98StatQuery(DataSource dataSource, OrgApi orgApi) {
        this.dataSource = dataSource;
        this.orgApi = orgApi;
    }

    /**
     * 执行固定 M98 SUMMARY profile。
     *
     * @param profile 固定五枚举之一
     * @param authorizedOrgCodes 完整服务端授权机构集合
     * @param outputOrgCodes 服务端校验后的输出子集
     * @param today 统计日期上限
     * @param limit 输出机构行上限；员工明细始终先完整处理再限制输出
     */
    ScreenDataRespDTO query(String profile, Collection<String> authorizedOrgCodes,
                            Collection<String> outputOrgCodes, LocalDate today, int limit) {
        requireProfile(profile);
        List<String> authorized = requiredCodes(authorizedOrgCodes);
        List<String> output = requiredCodes(outputOrgCodes);
        if (!new LinkedHashSet<>(authorized).containsAll(output)) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        Mapping mapping = resolveMapping(authorized);
        if (mapping.employeeIds().isEmpty()) {
            return emptyResponse(profile, authorized, null, "授权机构没有全局唯一员工映射");
        }
        if (mapping.employeeIds().size() > MAX_EMPLOYEE_IDS) {
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED,
                    new IllegalArgumentException("M98 唯一员工映射超过查询上限"));
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            LocalDate dataDate = resolveLatestDate(connection, profile, mapping.employeeIds(), today);
            if (dataDate == null) {
                return emptyResponse(profile, authorized, null, "授权员工没有共同合法统计日期");
            }
            List<StatRow> rows = selectRows(connection, profile, mapping.employeeIds(), dataDate);
            return aggregate(profile, authorized, output, mapping, dataDate, rows, limit);
        } catch (SQLException ex) {
            log.warn("[M98StatQuery] 只读统计查询失败 profile={} cause={}", profile, ex.getMessage());
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, ex);
        }
    }

    /**
     * 为 ScreenQueryEngine.build 提供同一固定 SQL 的结构预览。该方法只解析 auth 映射，
     * 不打开数据库；运行时汇总仍由 query 完成。
     */
    ScreenQueryPlan previewPlan(String profile, Collection<String> authorizedOrgCodes,
                                Collection<String> outputOrgCodes, LocalDate today, int limit) {
        requireProfile(profile);
        List<String> authorized = requiredCodes(authorizedOrgCodes);
        List<String> output = requiredCodes(outputOrgCodes);
        if (!new LinkedHashSet<>(authorized).containsAll(output)) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        Mapping mapping = resolveMapping(authorized);
        if (mapping.employeeIds().isEmpty()) {
            return new ScreenQueryPlan("SELECT EMP_ID, STATIS_DT, IND_TYPE, IND_NAME_1_LEV, "
                    + "IND_NAME_2_LEV, IND_NAME_3_LEV, SUB_PROJ_TYPE, SUB_PROJ_CODE, "
                    + "SUB_PROJ_NAME, CURR_BAL, FTP_REVENUE FROM " + TABLE + " WHERE 1 = 0", List.of());
        }
        if (mapping.employeeIds().size() > MAX_EMPLOYEE_IDS) {
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED,
                    new IllegalArgumentException("M98 唯一员工映射超过查询上限"));
        }
        List<String> outputEmployees = mapping.employeeIdsForOrgs(output);
        if (outputEmployees.isEmpty()) {
            outputEmployees = List.of("__NO_OUTPUT_EMPLOYEE__");
        }
        return buildRowsPlan(profile, mapping.employeeIds(), outputEmployees, today, null, limit);
    }

    private LocalDate resolveLatestDate(Connection connection, String profile, List<String> employeeIds,
                                        LocalDate today) throws SQLException {
        String placeholders = placeholders(employeeIds.size());
        String currency = M98StatPolicy.currencyType(profile);
        String sql = "SELECT MAX(STR_TO_DATE(STATIS_DT, '%Y-%m-%d')) AS data_date FROM " + TABLE
                + " WHERE CURRENCY_TYPE = '" + currency + "' AND EMP_ID IN (" + placeholders + ")"
                + " AND STR_TO_DATE(STATIS_DT, '%Y-%m-%d') IS NOT NULL"
                + " AND STR_TO_DATE(STATIS_DT, '%Y-%m-%d') <= ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int index = bindStrings(statement, employeeIds, 1);
            statement.setDate(index, Date.valueOf(today));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return parseDate(result.getObject(1));
            }
        }
    }

    private List<StatRow> selectRows(Connection connection, String profile, List<String> employeeIds,
                                     LocalDate dataDate) throws SQLException {
        String placeholders = placeholders(employeeIds.size());
        String currency = M98StatPolicy.currencyType(profile);
        String sql = "SELECT EMP_ID, STATIS_DT, IND_TYPE, IND_NAME_1_LEV, IND_NAME_2_LEV, "
                + "IND_NAME_3_LEV, SUB_PROJ_TYPE, SUB_PROJ_CODE, SUB_PROJ_NAME, CURR_BAL, FTP_REVENUE"
                + " FROM " + TABLE + " WHERE CURRENCY_TYPE = '" + currency + "'"
                + " AND STATIS_DT = ? AND EMP_ID IN (" + placeholders + ")"
                + " AND " + profilePredicate(profile)
                + " LIMIT " + (MAX_DETAIL_ROWS + 1);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            int index = 1;
            statement.setString(index++, dataDate.toString());
            index = bindStrings(statement, employeeIds, index);
            try (ResultSet result = statement.executeQuery()) {
                List<StatRow> rows = new ArrayList<>();
                while (result.next()) {
                    rows.add(new StatRow(
                            result.getString(1), result.getString(2), result.getString(3),
                            result.getString(4), result.getString(5), result.getString(6),
                            result.getString(7), result.getString(8), result.getString(9),
                            result.getString(10), result.getString(11)));
                }
                if (rows.size() > MAX_DETAIL_ROWS) {
                    throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED,
                            new IllegalStateException("M98 明细行超过查询上限"));
                }
                return rows;
            }
        }
    }

    private ScreenQueryPlan buildRowsPlan(String profile, List<String> allEmployeeIds,
                                          List<String> outputEmployeeIds, LocalDate today,
                                          LocalDate fixedDate, int limit) {
        String datePredicate;
        List<Object> params = new ArrayList<>();
        String currency = M98StatPolicy.currencyType(profile);
        if (fixedDate == null) {
            String all = placeholders(allEmployeeIds.size());
            datePredicate = "STR_TO_DATE(STATIS_DT, '%Y-%m-%d') = (SELECT MAX(STR_TO_DATE(candidate.STATIS_DT, '%Y-%m-%d'))"
                    + " FROM " + TABLE + " candidate WHERE candidate.CURRENCY_TYPE = '" + currency + "'"
                    + " AND candidate.EMP_ID IN (" + all + ")"
                    + " AND STR_TO_DATE(candidate.STATIS_DT, '%Y-%m-%d') IS NOT NULL"
                    + " AND STR_TO_DATE(candidate.STATIS_DT, '%Y-%m-%d') <= ?)";
            params.addAll(allEmployeeIds);
            params.add(Date.valueOf(today));
        } else {
            datePredicate = "STATIS_DT = ?";
            params.add(fixedDate.toString());
        }
        String output = placeholders(outputEmployeeIds.size());
        String sql = "SELECT EMP_ID, STATIS_DT, IND_TYPE, IND_NAME_1_LEV, IND_NAME_2_LEV, "
                + "IND_NAME_3_LEV, SUB_PROJ_TYPE, SUB_PROJ_CODE, SUB_PROJ_NAME, CURR_BAL, FTP_REVENUE"
                + " FROM " + TABLE + " WHERE CURRENCY_TYPE = '" + currency + "' AND " + datePredicate
                + " AND EMP_ID IN (" + output + ") AND " + profilePredicate(profile)
                + " LIMIT " + (MAX_DETAIL_ROWS + 1);
        params.addAll(outputEmployeeIds);
        return new ScreenQueryPlan(sql, params);
    }

    private ScreenDataRespDTO aggregate(String profile, List<String> authorized, List<String> output,
                                        Mapping mapping, LocalDate dataDate, List<StatRow> rows, int limit) {
        Map<String, AggregateState> states = new LinkedHashMap<>();
        for (StatRow row : rows) {
            String orgCode = mapping.orgByEmployee().get(trimToNull(row.empId()));
            if (orgCode == null) {
                continue;
            }
            AggregateState state = states.computeIfAbsent(orgCode, ignored -> new AggregateState());
            state.hasRows = true;
            if (!dataDate.equals(parseDate(row.statisDt()))) {
                state.invalid = true;
            }
            if (M98StatPolicy.isNplProfile(profile)) {
                state.acceptNpl(row);
            } else {
                state.acceptAmount(valueFor(profile, row));
            }
        }

        boolean invalid = states.values().stream().anyMatch(state -> state.invalid
                || (M98StatPolicy.isNplProfile(profile) && state.nplRatio() == null));
        List<String> receivedCodes = output.stream()
                .filter(code -> states.containsKey(code) && states.get(code).hasRows).toList();
        Object value = M98StatPolicy.isNplProfile(profile)
                ? aggregateNpl(states, receivedCodes, invalid) : aggregateAmount(states, receivedCodes, invalid);
        List<List<Object>> resultRows;
        if (receivedCodes.isEmpty()) {
            resultRows = List.of();
        } else {
            List<Object> summaryRow = new ArrayList<>(List.of(dataDate.toString()));
            summaryRow.add(value);
            resultRows = List.of(summaryRow);
        }
        String status;
        if (receivedCodes.isEmpty()) {
            status = "NO_SOURCE";
        } else if (invalid || receivedCodes.size() != authorized.size()) {
            status = "PARTIAL";
        } else {
            status = "COMPLETE";
        }
        return response(profile, resultRows, authorized, receivedCodes, dataDate, status,
                status.equals("NO_SOURCE") ? "共同统计日期没有匹配的 M98 数据"
                        : status.equals("PARTIAL") ? "授权机构与可解析 M98 数据存在缺口或质量问题" : null);
    }

    private BigDecimal aggregateAmount(Map<String, AggregateState> states, List<String> receivedCodes,
                                       boolean invalid) {
        if (invalid) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (String code : receivedCodes) {
            BigDecimal value = states.get(code).amountValue();
            if (value == null) {
                return null;
            }
            total = total.add(value);
        }
        return total;
    }

    /** 不良率按总不良余额/总贷款余额计算，避免各机构百分比等权平均。 */
    private BigDecimal aggregateNpl(Map<String, AggregateState> states, List<String> receivedCodes,
                                    boolean invalid) {
        if (invalid) {
            return null;
        }
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (String code : receivedCodes) {
            AggregateState state = states.get(code);
            if (state.nplRatio() == null) {
                return null;
            }
            numerator = numerator.add(state.nplNumerator);
            denominator = denominator.add(state.nplDenominator);
        }
        if (denominator.signum() <= 0) {
            return null;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private Object valueFor(String profile, StatRow row) {
        return parseDecimal(M98StatPolicy.CORP_REVENUE.equals(profile)
                || M98StatPolicy.RETAIL_REVENUE.equals(profile) ? row.ftpRevenue() : row.currBal());
    }

    private String profilePredicate(String profile) {
        return switch (profile) {
            case M98StatPolicy.CORP_REVENUE ->
                    "IND_TYPE = '盈利指标' AND IND_NAME_1_LEV = '税前利润' AND IND_NAME_2_LEV = '营业收入'"
                            + " AND SUB_PROJ_TYPE = '板块分类' AND SUB_PROJ_CODE = '1'";
            case M98StatPolicy.RETAIL_REVENUE ->
                    "IND_TYPE = '盈利指标' AND IND_NAME_1_LEV = '税前利润' AND IND_NAME_2_LEV = '营业收入'"
                            + " AND SUB_PROJ_TYPE = '板块分类' AND SUB_PROJ_CODE = '2'";
            case M98StatPolicy.RETAIL_LOAN ->
                    "IND_TYPE = '规模指标' AND IND_NAME_1_LEV = '贷款'"
                            + " AND IND_NAME_2_LEV IN ('个人一般贷款', '信用卡贷款')";
            case M98StatPolicy.CORP_NPL_RATE ->
                    "IND_TYPE = '报表指标' AND SUB_PROJ_TYPE = '五级分类'"
                            + " AND IND_NAME_3_LEV = '对公板块贷款'";
            case M98StatPolicy.RETAIL_NPL_RATE ->
                    "IND_TYPE = '报表指标' AND SUB_PROJ_TYPE = '五级分类'"
                            + " AND IND_NAME_1_LEV = '零售信贷产品（含逾期）'";
            default -> throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        };
    }

    private Mapping resolveMapping(List<String> authorized) {
        List<UniqueUserOrgDTO> rows;
        try {
            rows = orgApi.listUniqueUserOrgs(authorized);
        } catch (RuntimeException ex) {
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, ex);
        }
        if (rows == null || rows.isEmpty()) {
            return new Mapping(Map.of(), List.of(), Map.of());
        }
        Set<String> authorizedSet = new LinkedHashSet<>(authorized);
        Map<String, String> orgByEmployee = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> employeesByOrg = new LinkedHashMap<>();
        for (UniqueUserOrgDTO row : rows) {
            if (row == null) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            String employee = trimToNull(row.getEmpId());
            String org = trimToNull(row.getOrgCode());
            if (employee == null || org == null || !authorizedSet.contains(org)) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            String previous = orgByEmployee.putIfAbsent(employee, org);
            if (previous != null && !previous.equals(org)) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            employeesByOrg.computeIfAbsent(org, ignored -> new LinkedHashSet<>()).add(employee);
        }
        List<String> employeeIds = new ArrayList<>(orgByEmployee.keySet());
        return new Mapping(orgByEmployee, employeeIds, employeesByOrg);
    }

    private ScreenDataRespDTO emptyResponse(String profile, List<String> authorized,
                                             LocalDate dataDate, String message) {
        return response(profile, List.of(), authorized, List.of(), dataDate, "NO_SOURCE", message);
    }

    private ScreenDataRespDTO response(String profile, List<List<Object>> rows, List<String> authorized,
                                       Collection<String> receivedCodes, LocalDate dataDate,
                                       String status, String message) {
        String valueColumn = M98StatPolicy.isNplProfile(profile) ? "ratio" : "amount";
        ScreenDataRespDTO response = new ScreenDataRespDTO(
                List.of("data_date", valueColumn), rows);
        response.setColumnsMeta(List.of(
                new ScreenDataRespDTO.ColumnMeta("data_date", null, "DIM", null, null),
                new ScreenDataRespDTO.ColumnMeta(valueColumn, null, "METRIC",
                        M98StatPolicy.isNplProfile(profile) ? "PERCENT" : null,
                        M98StatPolicy.isNplProfile(profile) ? 2 : null)));
        ScreenDataRespDTO.Quality quality = new ScreenDataRespDTO.Quality(
                null, dataDate == null ? null : dataDate.toString(), null, status,
                authorized.size(), receivedCodes.size(), null, null, message);
        quality.setSelectedComplete("COMPLETE".equals(status));
        quality.setMixedPeriod(false);
        quality.setMissing(List.of());
        quality.setMissingSubjects(authorized.stream()
                .filter(code -> !receivedCodes.contains(code))
                .toList());
        quality.setNewerIncomplete(List.of());
        response.setQuality(quality);
        return response;
    }

    private void requireProfile(String profile) {
        if (!M98StatPolicy.isProfile(profile)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
    }

    private List<String> requiredCodes(Collection<String> source) {
        if (source == null || source.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String code : source) {
            String normalized = trimToNull(code);
            if (normalized == null) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            result.add(normalized);
        }
        return new ArrayList<>(result);
    }

    private static String placeholders(int count) {
        if (count <= 0) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static int bindStrings(PreparedStatement statement, List<String> values, int start)
            throws SQLException {
        int index = start;
        for (String value : values) {
            statement.setString(index++, value);
        }
        return index;
    }

    private static LocalDate parseDate(Object value) {
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value).trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    record ScreenQueryPlan(String sql, List<Object> params) {
    }

    private record StatRow(String empId, String statisDt, String indType, String indName1,
                           String indName2, String indName3, String subProjType,
                           String subProjCode, String subProjName, String currBal,
                           String ftpRevenue) {
    }

    private record Mapping(Map<String, String> orgByEmployee, List<String> employeeIds,
                           Map<String, LinkedHashSet<String>> employeesByOrg) {
        List<String> employeeIdsForOrgs(Collection<String> orgCodes) {
            List<String> result = new ArrayList<>();
            for (String org : orgCodes) {
                result.addAll(employeesByOrg.getOrDefault(org, new LinkedHashSet<>()));
            }
            return result.stream().distinct().toList();
        }
    }

    private static final class AggregateState {
        private boolean hasRows;
        private boolean invalid;
        private BigDecimal amount = BigDecimal.ZERO;
        private BigDecimal nplNumerator = BigDecimal.ZERO;
        private BigDecimal nplDenominator = BigDecimal.ZERO;

        private void acceptAmount(Object raw) {
            if (!(raw instanceof BigDecimal value)) {
                invalid = true;
                return;
            }
            amount = amount.add(value);
        }

        private BigDecimal amountValue() {
            return invalid ? null : amount;
        }

        private void acceptNpl(StatRow row) {
            String category = trimToNull(row.subProjName());
            BigDecimal value = parseDecimal(row.currBal());
            if (!NPL_CATEGORIES.contains(category) || value == null) {
                invalid = true;
                return;
            }
            nplDenominator = nplDenominator.add(value);
            if (NPL_RISK_CATEGORIES.contains(category)) {
                nplNumerator = nplNumerator.add(value);
            }
        }

        private BigDecimal nplRatio() {
            if (invalid || nplDenominator.signum() <= 0) {
                return null;
            }
            return nplNumerator.divide(nplDenominator, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
