package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.api.dto.MetricCardDTO;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.EmpIndexResult;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 个人经营驾驶舱核心指标查询。
 *
 * <p>该服务与 KPI 方案卡片保持独立：候选项来自当前 ACTIVE EMP 指标定义，实际值只读
 * 当前员工最新导入的 EMP 宽表快照，不执行指标逻辑、不读取 ORG/CUST 宽表，也不构造目标值。</p>
 */
@Service
@RequiredArgsConstructor
public class PersonalCoreMetricService {

    private static final int MAX_SLOT = 400;

    /** 核心候选按产品优先级排列；名称匹配前会去掉定义名末尾的「-员工」。 */
    private static final List<String> CORE_CANDIDATE_NAMES = List.of(
            "对公一般性存款余额",
            "一般性存款月均余额",
            "一般性存款年日均余额",
            "一般性存款月均余额较上月",
            "对公一般性贷款余额",
            "对公一般性贷款余额较上月"
    );

    /**
     * 有实际值时用于填满六张卡的有限备选池。总候选为 6 + 9 = 15，保证一次 slot batch 有界。
     */
    private static final List<String> BACKUP_CANDIDATE_NAMES = List.of(
            "零售一般性存款余额",
            "零售一般性存款月均",
            "零售一般性存款年日均",
            "零售一般性存款余额较上月",
            "对公一般性存款月均",
            "对公一般性存款年日均",
            "对公一般性存款余额较上月",
            "同业存款余额",
            "同业存款余额较上月"
    );

    private final MetricDefService metricDefService;
    private final UserApi userApi;
    private final EmpIndexResultMapper empIndexResultMapper;

    /**
     * 查询个人核心指标卡片。
     *
     * @param empId 当前认证用户 USER_ID，由 UserApi 解析为宽表使用的 username 工号
     * @return 最多六张卡片；本人无宽表记录时保留匹配指标的空值卡片，无匹配定义时返回空列表
     */
    public List<MetricCardDTO> getPersonalCoreMetricCards(String empId) {
        if (!StringUtils.hasText(empId)) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "员工号不能为空");
        }
        String userId = empId.trim();
        String employeeId = resolveEmployeeUsername(userId);
        LocalDate today = LocalDate.now();
        EmpIndexResult currentRow = empIndexResultMapper.selectLatestRowForEmployee(employeeId, today);
        if (currentRow != null && (currentRow.getDataDate() == null
                || currentRow.getDataDate().isAfter(today))) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "EMP 最新导入数据日期无效");
        }
        LocalDate currentDate = currentRow == null ? null : currentRow.getDataDate();
        LocalDate previousDate = currentDate == null ? null : previousMonthEnd(currentDate);
        LocalDate yearAgoDate = currentDate == null ? null : currentDate.minusYears(1);
        List<LocalDate> historyDates = new ArrayList<>(2);
        if (previousDate != null) {
            historyDates.add(previousDate);
        }
        if (yearAgoDate != null && !historyDates.contains(yearAgoDate)) {
            historyDates.add(yearAgoDate);
        }
        List<EmpIndexResult> historyRows = historyDates.isEmpty()
                ? List.of()
                : empIndexResultMapper.selectLatestRowsForEmployeeDates(employeeId, historyDates, today);
        Map<LocalDate, EmpIndexResult> rowsByDate = new LinkedHashMap<>();
        if (historyRows != null) {
            for (EmpIndexResult row : historyRows) {
                if (row != null && row.getDataDate() != null) {
                    rowsByDate.put(row.getDataDate(), row);
                }
            }
        }
        EmpIndexResult previousRow = previousDate == null ? null : rowsByDate.get(previousDate);
        EmpIndexResult yearAgoRow = yearAgoDate == null ? null : rowsByDate.get(yearAgoDate);

        List<PerfMetricDef> definitions = metricDefService.listActiveMetrics("EMP", null);
        DefinitionIndex index = indexDefinitions(definitions);
        if (index.byName().isEmpty()) {
            return List.of();
        }

        List<PerfMetricDef> coreDefinitions = definitionsForNames(CORE_CANDIDATE_NAMES, index);
        List<PerfMetricDef> backupDefinitions = definitionsForNames(BACKUP_CANDIDATE_NAMES, index);
        List<PerfMetricDef> queryCandidates = mergeDefinitions(coreDefinitions, backupDefinitions);
        if (queryCandidates.isEmpty()) {
            return List.of();
        }
        List<Integer> slots = queryCandidates.stream()
                .map(PerfMetricDef::getValSlot)
                .distinct()
                .toList();
        List<Long> rowIds = new ArrayList<>(3);
        if (currentRow != null && currentRow.getId() != null) {
            rowIds.add(currentRow.getId());
        }
        if (previousRow != null && previousRow.getId() != null) {
            rowIds.add(previousRow.getId());
        }
        if (yearAgoRow != null && yearAgoRow.getId() != null) {
            rowIds.add(yearAgoRow.getId());
        }
        Map<Long, Map<Integer, BigDecimal>> values = rowIds.isEmpty()
                ? Map.of()
                : empIndexResultMapper.selectSlotValuesByRowIdsAndSlots(
                        employeeId, rowIds, today, slots);
        if (values == null) {
            values = Map.of();
        }
        Map<Integer, BigDecimal> currentValues = rowValues(values, currentRow);

        List<PerfMetricDef> selectedDefinitions = new ArrayList<>(6);
        Set<String> selectedCodes = new HashSet<>();
        appendDefinitionsWithValues(coreDefinitions, currentValues,
                selectedDefinitions, selectedCodes);
        appendDefinitionsWithValues(backupDefinitions, currentValues,
                selectedDefinitions, selectedCodes);
        appendDefinitionsWithoutValues(coreDefinitions, currentValues,
                selectedDefinitions, selectedCodes);

        List<MetricCardDTO> cards = new ArrayList<>(selectedDefinitions.size());
        for (PerfMetricDef def : selectedDefinitions) {
            cards.add(toCard(def, values, currentRow, previousRow, yearAgoRow));
        }
        return cards;
    }

    /** USER_ID 只用于认证身份，EMP_INDEX_RESULT.emp_id 使用其 username 工号。 */
    private String resolveEmployeeUsername(String userId) {
        UserDTO user = userApi.getUserByEmpId(userId);
        if (user == null || !StringUtils.hasText(user.getEmpId())
                || !userId.equals(user.getEmpId().trim())
                || !StringUtils.hasText(user.getUsername())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "员工身份映射无效: " + userId);
        }
        return user.getUsername().trim();
    }

    /** 取前一自然月月末；当前数据日可以是月中，比较口径仍固定为上月末。 */
    private LocalDate previousMonthEnd(LocalDate currentDate) {
        return currentDate.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
    }

    /**
     * 过滤 ACTIVE、未删除、EMP、合法 slot 的定义，并排除同名但 code/slot 不同的歧义定义。
     */
    private DefinitionIndex indexDefinitions(List<PerfMetricDef> definitions) {
        Map<String, List<PerfMetricDef>> grouped = new LinkedHashMap<>();
        if (definitions == null) {
            return new DefinitionIndex(Map.of(), Set.of());
        }
        for (PerfMetricDef def : definitions) {
            if (def == null || !"ACTIVE".equalsIgnoreCase(def.getStatus())
                    || (def.getDeleted() != null && def.getDeleted() != 0)
                    || !"EMP".equalsIgnoreCase(def.getBaseDim())
                    || def.getValSlot() == null || def.getValSlot() < 1
                    || def.getValSlot() > MAX_SLOT
                    || !StringUtils.hasText(def.getMetricCode())
                    || !StringUtils.hasText(def.getMetricName())) {
                continue;
            }
            String normalizedName = normalizeName(def.getMetricName());
            if (!StringUtils.hasText(normalizedName)) {
                continue;
            }
            grouped.computeIfAbsent(normalizedName, ignored -> new ArrayList<>()).add(def);
        }

        Map<String, PerfMetricDef> unique = new LinkedHashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Map.Entry<String, List<PerfMetricDef>> entry : grouped.entrySet()) {
            Set<String> identities = new LinkedHashSet<>();
            for (PerfMetricDef def : entry.getValue()) {
                identities.add(def.getMetricCode() + "#" + def.getValSlot());
            }
            if (identities.size() > 1) {
                ambiguous.add(entry.getKey());
                continue;
            }
            unique.put(entry.getKey(), entry.getValue().get(0));
        }
        return new DefinitionIndex(unique, ambiguous);
    }

    /** 按候选名称取定义，跳过歧义项并保持候选顺序。 */
    private List<PerfMetricDef> definitionsForNames(List<String> names, DefinitionIndex index) {
        Map<String, PerfMetricDef> uniqueByCode = new LinkedHashMap<>();
        for (String name : names) {
            String normalizedName = normalizeName(name);
            PerfMetricDef def = index.byName().get(normalizedName);
            if (def != null && !index.ambiguousNames().contains(normalizedName)) {
                uniqueByCode.putIfAbsent(def.getMetricCode(), def);
            }
        }
        return new ArrayList<>(uniqueByCode.values());
    }

    /** 合并核心和备选定义，按 code 去重并保持稳定顺序。 */
    private List<PerfMetricDef> mergeDefinitions(List<PerfMetricDef> core,
                                                 List<PerfMetricDef> backups) {
        Map<String, PerfMetricDef> byCode = new LinkedHashMap<>();
        for (PerfMetricDef def : core) {
            byCode.putIfAbsent(def.getMetricCode(), def);
        }
        for (PerfMetricDef def : backups) {
            byCode.putIfAbsent(def.getMetricCode(), def);
        }
        return new ArrayList<>(byCode.values());
    }

    /** 先加入当前日期有真实值的定义，0 通过 get()!=null 保留为有效值。 */
    private void appendDefinitionsWithValues(List<PerfMetricDef> definitions,
                                             Map<Integer, BigDecimal> currentValues,
                                             List<PerfMetricDef> selected,
                                             Set<String> selectedCodes) {
        for (PerfMetricDef def : definitions) {
            if (selected.size() >= 6 || selectedCodes.contains(def.getMetricCode())) {
                continue;
            }
            if (currentValues.get(def.getValSlot()) != null) {
                selected.add(def);
                selectedCodes.add(def.getMetricCode());
            }
        }
    }

    /** 有值项不足六张时，最后保留核心定义但当前值为 NULL 的卡片。 */
    private void appendDefinitionsWithoutValues(List<PerfMetricDef> definitions,
                                                Map<Integer, BigDecimal> currentValues,
                                                List<PerfMetricDef> selected,
                                                Set<String> selectedCodes) {
        for (PerfMetricDef def : definitions) {
            if (selected.size() >= 6 || selectedCodes.contains(def.getMetricCode())) {
                continue;
            }
            if (currentValues.get(def.getValSlot()) == null) {
                selected.add(def);
                selectedCodes.add(def.getMetricCode());
            }
        }
    }

    private MetricCardDTO toCard(PerfMetricDef def,
                                 Map<Long, Map<Integer, BigDecimal>> values,
                                 EmpIndexResult currentRow,
                                 EmpIndexResult previousRow,
                                 EmpIndexResult yearAgoRow) {
        BigDecimal current = rowValues(values, currentRow).get(def.getValSlot());
        BigDecimal previous = rowValues(values, previousRow).get(def.getValSlot());
        BigDecimal yearAgo = rowValues(values, yearAgoRow).get(def.getValSlot());
        BigDecimal mom = isIncremental(def) ? null : percentageChange(current, previous);
        BigDecimal yoy = isIncremental(def) ? null : percentageChange(current, yearAgo);
        return MetricCardDTO.builder()
                .metricCode(def.getMetricCode())
                .metricName(def.getMetricName())
                .currentValue(current)
                .previousValue(previous)
                .targetValue(null)
                .baseValue(null)
                .achievementRate(null)
                .unit(def.getUnit())
                .dataDate(currentRow == null ? null : currentRow.getDataDate())
                .mom(mom)
                .yoy(yoy)
                .build();
    }

    private Map<Integer, BigDecimal> rowValues(Map<Long, Map<Integer, BigDecimal>> values,
                                                EmpIndexResult row) {
        if (row == null || row.getId() == null) {
            return Map.of();
        }
        return values.getOrDefault(row.getId(), Map.of());
    }

    private boolean isIncremental(PerfMetricDef def) {
        return def.getMetricName() != null && def.getMetricName().contains("较");
    }

    private BigDecimal percentageChange(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.endsWith("-员工")) {
            return normalized.substring(0, normalized.length() - 3).trim();
        }
        if (normalized.endsWith("－员工")) {
            return normalized.substring(0, normalized.length() - 3).trim();
        }
        return normalized;
    }

    private record DefinitionIndex(Map<String, PerfMetricDef> byName,
                                   Set<String> ambiguousNames) {
    }
}
