package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** 分行大屏不可变批次快照 DTO，不暴露 entity 或 mapper。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDashboardBatchDTO {

    private String batchId;
    private String groupCode;
    private LocalDate dataDate;
    private String version;
    private String status;
    /** 数据分类：TEST 只能用于显式测试 profile，PROD 快照不得混入演示来源。 */
    private String dataClassification;
    private LocalDateTime calculatedAt;
    @Builder.Default
    private List<String> memberOrgCodes = new ArrayList<>();
    private BranchDashboardSourceAsOfDTO sourceAsOf;
    private BranchDashboardQualityDTO quality;
    /** 最新尝试状态；latest 返回旧成功快照时用于提示 FAILED/RUNNING 回退。 */
    private BranchDashboardBatchAttemptDTO latestAttempt;
    /** 指标口径合同，便于下游追溯单位、槽位和描述。 */
    @Builder.Default
    private Map<String, BranchDashboardMetricContractDTO> metricContracts = new LinkedHashMap<>();
    /** 指标合同 JSON 的稳定摘要，便于下游确认口径未漂移。 */
    private String definitionDigest;
    /** 各来源模式；TEST 营收明确为 TEST_MANUAL。 */
    @Builder.Default
    private Map<String, String> sourceModes = new LinkedHashMap<>();
    @Builder.Default
    private List<BranchDashboardBatchRowDTO> rows = new ArrayList<>();
    @Builder.Default
    private List<BranchDashboardBatchRowDTO> historyRows = new ArrayList<>();
    @Builder.Default
    private List<BranchDashboardHistoryCoverageDTO> historyCoverage = new ArrayList<>();
}
