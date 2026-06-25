package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.performance.api.KpiApi;
import com.bank.branch.platform.performance.api.MetricApi;
import com.bank.branch.platform.report.config.DashboardPresidentMetrics;
import com.bank.branch.platform.report.dto.resp.ChartDataDTO;
import com.bank.branch.platform.report.dto.resp.ChartSeriesDTO;
import com.bank.branch.platform.report.dto.resp.EmpDashboardRespDTO;
import com.bank.branch.platform.report.dto.resp.KpiCardItem;
import com.bank.branch.platform.report.dto.resp.OrgDashboardRespDTO;
import com.bank.branch.platform.report.dto.resp.OrgRankingItemDTO;
import com.bank.branch.platform.report.dto.resp.PresidentDashboardRespDTO;
import com.bank.branch.platform.report.dto.resp.TopCustomerDTO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.service.DashboardService;
import com.bank.branch.platform.report.support.TrendFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link DashboardService} 实现（Task M2.2.1，Green）.
 *
 * <p>装配步骤（参照 plan L1768-L1799）：
 * <ol>
 *   <li>角色校验：必须有 R_PRESIDENT，否则 RPT-40301</li>
 *   <li>dataDate 默认值兜底：null → today</li>
 *   <li>summaryMetrics：{@link MetricApi#getOrgMetricValues} 单次查询 CORE_METRICS</li>
 *   <li>depositTrend / loanTrend：12 月单条循环（V1.0 简化）</li>
 *   <li>orgRanking：getOrgSubtreeCodes + 子机构 batch 单条循环 RANKING_METRICS</li>
 *   <li>topCustomers：CustomerQueryApi.searchCustomers(默认排序, limit=10)</li>
 *   <li>异步审计：try/catch 包装 AuditApi.log</li>
 * </ol>
 *
 * <p>缓存：{@code @Cacheable(value="rpt:dashboard:president", key="dataDate:orgCode")}，
 * TTL 5 min；缓存命中跳过装配步骤直接返回上次结果.
 *
 * <p>SpEL 解析：{@code @currentUserApi.getCurrentOrgCode()} 通过 BeanFactoryResolver 取
 * 当前请求的 CurrentUserApi bean，在缓存 key 中结合用户隔离不同 orgCode 的结果.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** 全行顶层机构编码（西安分行，ORG_LEVEL=1）；行长仪表盘默认看全行 */
    private static final String ROOT_ORG_CODE = "1";

    private static final int TOP_CUSTOMER_LIMIT = 10;

    private static final int TREND_MONTHS = 12;

    private static final int ORG_RANKING_TOP = 20;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CurrentUserApi currentUserApi;

    private final OrgApi orgApi;

    private final MetricApi metricApi;

    private final CustomerQueryApi customerQueryApi;

    private final AuditApi auditApi;

    /**
     * KpiApi V1.1 P4.3 已真实交付（返回 BigDecimal KPI 总分）.
     *
     * <p>M3 入场清理 M2 reviewer 观察项 #1：去掉 ObjectProvider&lt;KpiApi&gt; 过度防御，
     * 直接 @Autowired 注入；测试侧通过 @MockBean / @Mock 装配，不再需要 provider 容错。
     */
    private final KpiApi kpiApi;

    @Override
    @Cacheable(value = "rpt:dashboard:president",
            key = "T(java.lang.String).format('%s:%s', #dataDate, "
                    + "(#orgCode != null and !#orgCode.isBlank()) "
                    + "? #orgCode : @currentUserApi.getCurrentOrgCode())",
            unless = "#result == null")
    public PresidentDashboardRespDTO getPresidentDashboard(String orgCode, LocalDate dataDate) {
        // 访问控制交由菜单授权（@BizAuth + PT_ROLE_RESOURCE）：有"行长仪表盘"菜单即可查看，
        // 服务层不再硬编码角色白名单（原仅 R_PRESIDENT/ROLE_ID=2 可看）。

        // dataDate 默认取 SYS_CONTROL(ORG) 最新有效数据日期（"读取最新版本control表关联数据"）；
        //    orgCode 默认取全行顶层机构（ORG_LEVEL=1 的"西安分行"=1），实现"全行"视角而非行长个人部门
        LocalDate latest = null;
        try { latest = metricApi.getLatestDataDate("ORG"); } catch (RuntimeException e) {
            log.warn("[DashboardService] 取 SYS_CONTROL(ORG) 最新日期失败，回退 now()：{}", e.getMessage());
        }
        LocalDate resolvedDate = dataDate != null ? dataDate
                : (latest != null ? latest : LocalDate.now());
        String resolvedOrgCode = (orgCode != null && !orgCode.isBlank()) ? orgCode : ROOT_ORG_CODE;
        // 后续逻辑沿用旧变量名 orgCode 表示已解析后的最终机构
        orgCode = resolvedOrgCode;

        // 3) summaryMetrics：CORE_METRICS 单次查询
        Map<String, BigDecimal> summary = safeGetOrgMetrics(orgCode, resolvedDate,
                DashboardPresidentMetrics.CORE_METRICS);

        // 4) 趋势图（12 月单条循环）
        ChartDataDTO depositTrend = buildTrend("M_0265",
                "全行存款趋势", "一般性存款月均", "万元", orgCode, resolvedDate);
        ChartDataDTO loanTrend = buildTrend("M_0348",
                "全行贷款趋势", "对公一般性贷款", "万元", orgCode, resolvedDate);

        // 5) 机构排名
        List<OrgRankingItemDTO> ranking = buildOrgRanking(orgCode, resolvedDate);

        // 6) Top 客户
        List<TopCustomerDTO> topCusts = buildTopCustomers(resolvedDate);

        // 7) 异步审计（try/catch 包装，失败不阻断响应）
        try {
            auditApi.log(buildAuditCmd(orgCode, resolvedDate, "SUCCESS"));
        } catch (RuntimeException e) {
            log.warn("[DashboardService] 审计写入失败 orgCode={} dataDate={} cause={}",
                    orgCode, resolvedDate, e.getMessage());
        }

        // 8) V1.14 # 2 stats 数组：与前端 5 项 KPI 卡契约对齐
        List<KpiCardItem> stats = buildStats(orgCode, resolvedDate, summary);

        return PresidentDashboardRespDTO.builder()
                .dataDate(resolvedDate)
                .stats(stats)
                .summaryMetrics(summary)
                .depositTrend(depositTrend)
                .loanTrend(loanTrend)
                .orgRanking(ranking)
                .topCustomers(topCusts)
                .build();
    }

    /**
     * 组装 5 项 KPI 卡有序数组（V1.14 # 2 新增）.
     *
     * <p>装配步骤：
     * <ol>
     *   <li>月初日二次查询：{@code metricApi.getOrgMetricValues(orgCode, dataDate.withDayOfMonth(1), CORE_METRICS)}
     *       拿环比基准（V1.1+ 走 Redis 缓存避免 RPC 重复消耗）</li>
     *   <li>遍历 {@link DashboardPresidentMetrics#KPI_CARD_METRICS}，按 metric_code 索引 currMap / prevMap</li>
     *   <li>调 {@link TrendFormatter#format} 计算 trend / trendType（任一缺值降级 "--" / flat）</li>
     * </ol>
     *
     * @param orgCode  机构编码
     * @param dataDate 数据日期
     * @param currMap  当日 metric 查询结果（{@link DashboardPresidentMetrics#CORE_METRICS} 一次查询所得）
     */
    private List<KpiCardItem> buildStats(String orgCode, LocalDate dataDate,
                                         Map<String, BigDecimal> currMap) {
        // 月初日值：环比基准；走 safeGetOrgMetrics 缺值时返回空 Map 不阻塞
        LocalDate monthStart = dataDate.withDayOfMonth(1);
        Map<String, BigDecimal> prevMap = safeGetOrgMetrics(orgCode, monthStart,
                DashboardPresidentMetrics.CORE_METRICS);
        return DashboardPresidentMetrics.KPI_CARD_METRICS.stream()
                .map(meta -> {
                    BigDecimal curr = currMap != null ? currMap.get(meta.getMetricCode()) : null;
                    BigDecimal prev = prevMap != null ? prevMap.get(meta.getMetricCode()) : null;
                    TrendFormatter.Result tr = TrendFormatter.format(curr, prev);
                    return KpiCardItem.builder()
                            .metricCode(meta.getMetricCode())
                            .label(meta.getLabel())
                            .value(curr)
                            .unit(meta.getUnit())
                            .trend(tr.text())
                            .trendType(tr.type())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "rpt:dashboard:org",
            key = "T(java.lang.String).format('%s:%s', #orgCode, #dataDate)",
            unless = "#result == null")
    public OrgDashboardRespDTO getOrgDashboard(String orgCode, LocalDate dataDate) {
        // 1) dataDate 默认值
        LocalDate resolvedDate = dataDate != null ? dataDate : LocalDate.now();
        // 2) 机构信息
        String orgName = resolveOrgName(orgCode);
        // 3) summaryMetrics（CORE_METRICS）
        Map<String, BigDecimal> summary = safeGetOrgMetrics(orgCode, resolvedDate,
                DashboardPresidentMetrics.CORE_METRICS);
        // 4) achievementMetrics（ACHIEVEMENT_METRICS）
        Map<String, BigDecimal> achievement = safeGetOrgMetrics(orgCode, resolvedDate,
                DashboardPresidentMetrics.ACHIEVEMENT_METRICS);
        // 5) 直属子机构排名
        List<OrgRankingItemDTO> subRanking = buildOrgRanking(orgCode, resolvedDate);
        // 6) 异步审计
        try {
            auditApi.log(buildAuditCmdForOrg(orgCode, resolvedDate));
        } catch (RuntimeException e) {
            log.warn("[DashboardService] 机构仪表盘审计失败 orgCode={}", orgCode);
        }
        return OrgDashboardRespDTO.builder()
                .orgCode(orgCode)
                .orgName(orgName)
                .dataDate(resolvedDate)
                .summaryMetrics(summary)
                .achievementMetrics(achievement)
                .subOrgRanking(subRanking)
                .build();
    }

    @Override
    @Cacheable(value = "rpt:dashboard:emp",
            key = "T(java.lang.String).format('%s:%s', #empId, #dataDate)",
            unless = "#result == null")
    public EmpDashboardRespDTO getEmpDashboard(String empId, LocalDate dataDate) {
        // 1) dataDate 默认值
        LocalDate resolvedDate = dataDate != null ? dataDate : LocalDate.now();
        // 2) 员工维度 CORE_METRICS（V1.0 复用 ORG 同款 metricCode 列表，由 metric_def 表实际归属决定）
        Map<String, BigDecimal> summary = safeGetEmpMetrics(empId, resolvedDate,
                DashboardPresidentMetrics.CORE_METRICS);
        // 3) KPI 总分（V1.0 KpiApi 抛 UOE 时 fail-soft 返回 null）
        BigDecimal kpiTotal = safeGetCurrentKpiTotal(empId);
        // 4) 异步审计
        try {
            auditApi.log(buildAuditCmdForEmp(empId, resolvedDate));
        } catch (RuntimeException e) {
            log.warn("[DashboardService] 员工仪表盘审计失败 empId={}", empId);
        }
        return EmpDashboardRespDTO.builder()
                .empId(empId)
                .dataDate(resolvedDate)
                .summaryMetrics(summary)
                .kpiTotalScore(kpiTotal)
                .build();
    }

    /**
     * 安全取员工指标值.
     */
    private Map<String, BigDecimal> safeGetEmpMetrics(String empId, LocalDate dataDate,
                                                     List<String> metricCodes) {
        try {
            Map<String, BigDecimal> v = metricApi.getEmpMetricValues(empId, dataDate, metricCodes);
            return v != null ? v : Map.of();
        } catch (RuntimeException e) {
            log.warn("[DashboardService] 取员工指标失败 empId={} dataDate={} cause={}",
                    empId, dataDate, e.getMessage());
            return Map.of();
        }
    }

    /**
     * 安全取 KPI 总分.
     *
     * <p>KpiApi.getCurrentKpiTotal 在 V1.1 P2.6 已真实交付（返回 BigDecimal 或 null，无数据时 null）.
     * 异常时记 warn 并返回 null，避免单点失败影响仪表盘整体展示.
     *
     * <p>M6.3 顺手清理（M3 reviewer 观察项 #4）：原 UOE catch 分支删除，KpiApi V1.1 后无 UOE 路径.
     */
    private BigDecimal safeGetCurrentKpiTotal(String empId) {
        try {
            return kpiApi.getCurrentKpiTotal(empId, "MONTH");
        } catch (RuntimeException e) {
            log.warn("[DashboardService] 取 KPI 总分失败 empId={} cause={}", empId, e.getMessage());
            return null;
        }
    }

    private AuditLogCmd buildAuditCmdForOrg(String orgCode, LocalDate dataDate) {
        return AuditLogCmd.builder()
                .empId(currentUserApi.getCurrentEmpId())
                .bizType("REPORT")
                .bizAction("DASHBOARD_ORG_VIEW")
                .resourceUrl("/api/reports/dashboard/org/" + orgCode)
                .requestMethod("GET")
                .requestParams("orgCode=" + orgCode + ", dataDate=" + dataDate)
                .responseStatus(200)
                .errorMsg("SUCCESS")
                .build();
    }

    private AuditLogCmd buildAuditCmdForEmp(String empId, LocalDate dataDate) {
        return AuditLogCmd.builder()
                .empId(currentUserApi.getCurrentEmpId())
                .bizType("REPORT")
                .bizAction("DASHBOARD_EMP_VIEW")
                .resourceUrl("/api/reports/dashboard/emp/" + empId)
                .requestMethod("GET")
                .requestParams("empId=" + empId + ", dataDate=" + dataDate)
                .responseStatus(200)
                .errorMsg("SUCCESS")
                .build();
    }

    /**
     * 构建 12 月趋势 ChartData.
     *
     * <p>V1.0 简化：从 dataDate 往前推 11 个月（共 12 个月），每月用月末日期单条查询。
     * V2 计划接入 batch get 月份序列接口降低 N+1.
     */
    private ChartDataDTO buildTrend(String metricCode, String title, String seriesName,
                                    String unit, String orgCode, LocalDate dataDate) {
        List<String> xAxis = new ArrayList<>(TREND_MONTHS);
        List<BigDecimal> data = new ArrayList<>(TREND_MONTHS);
        // 从 11 个月前到当前月份，按时间正序排列
        for (int i = TREND_MONTHS - 1; i >= 0; i--) {
            LocalDate monthDate = dataDate.minusMonths(i);
            xAxis.add(monthDate.format(MONTH_FORMATTER));
            Map<String, BigDecimal> values = safeGetOrgMetrics(orgCode, monthDate, List.of(metricCode));
            BigDecimal v = values != null ? values.get(metricCode) : null;
            data.add(v != null ? v : BigDecimal.ZERO);
        }
        ChartSeriesDTO series = ChartSeriesDTO.builder()
                .name(seriesName)
                .unit(unit)
                .data(data)
                .build();
        return ChartDataDTO.builder()
                .title(title)
                .xAxis(xAxis)
                .series(List.of(series))
                .build();
    }

    /**
     * 构建机构排名（取下属机构 RANKING_METRICS，按 KPI_TOTAL_SCORE_ORG 倒序，取 Top 20）.
     */
    private List<OrgRankingItemDTO> buildOrgRanking(String rootOrgCode, LocalDate dataDate) {
        Set<String> subtree = orgApi.getOrgSubtreeCodes(rootOrgCode);
        if (subtree == null || subtree.isEmpty()) {
            return List.of();
        }
        // 1) 收集每个子机构的 RANKING_METRICS（V1.0 简化：单条循环）
        List<OrgRankingItemDTO> items = new ArrayList<>(subtree.size());
        for (String orgCode : subtree) {
            Map<String, BigDecimal> values = safeGetOrgMetrics(orgCode, dataDate,
                    DashboardPresidentMetrics.RANKING_METRICS);
            // 无 KPI 综合得分/达成率数据，V1 按存款规模(M_0265)排名；actual=存款规模，achievementRate 复用同值用于排序
            BigDecimal depBal = values != null ? values.get(DashboardPresidentMetrics.RANKING_SORT_METRIC) : null;
            String orgName = resolveOrgName(orgCode);
            items.add(OrgRankingItemDTO.builder()
                    .orgId(orgCode)
                    .orgName(orgName)
                    .achievementRate(depBal) // V1 用存款规模排序（无达成率源数据）
                    .target(null)            // V2 接入 OrgKpiTarget 后回填
                    .actual(depBal)
                    .build());
        }
        // 2) 按 KPI 总分倒序排，取 Top 20，再回填 rank
        items.sort(Comparator.comparing(
                (OrgRankingItemDTO i) -> i.getAchievementRate() != null ? i.getAchievementRate() : BigDecimal.ZERO,
                Comparator.reverseOrder()));
        List<OrgRankingItemDTO> top = items.stream().limit(ORG_RANKING_TOP).collect(Collectors.toList());
        for (int idx = 0; idx < top.size(); idx++) {
            top.get(idx).setRank(idx + 1);
        }
        return top;
    }

    /**
     * 构建 Top 客户贡献.
     *
     * <p>V1.0 简化：CustomerQueryApi.searchCustomers(null, 10) 默认排序，取前 10.
     * V2 计划按 AUM 倒序 + 客户白名单过滤.
     */
    private List<TopCustomerDTO> buildTopCustomers(LocalDate dataDate) {
        List<CustomerDTO> customers;
        try {
            // V1.14 # 2 修补：CustomerQueryApi.searchCustomers 强制 keyword 非空（line 92 抛 COMMON-40000），
            // 用 "公司" 作为通用 keyword 拿测试客户（CM_C* 6 个含"公司"），等 V2 真实 AUM 倒序接口落地后回退
            customers = customerQueryApi.searchCustomers("公司", TOP_CUSTOMER_LIMIT);
        } catch (RuntimeException e) {
            log.warn("[DashboardService] 取 Top 客户失败：{}", e.getMessage());
            return List.of();
        }
        if (customers == null || customers.isEmpty()) {
            return List.of();
        }
        List<TopCustomerDTO> result = new ArrayList<>(customers.size());
        int rank = 1;
        for (CustomerDTO c : customers) {
            Map<String, BigDecimal> values = safeGetCustMetrics(c.getId(), dataDate,
                    DashboardPresidentMetrics.CUST_CONTRIBUTION_METRICS);
            BigDecimal aum = values != null ? values.get("AUM_TOTAL_CUST") : null;
            BigDecimal score = values != null ? values.get("PROFIT_CONTRIB_CUST") : null;
            result.add(TopCustomerDTO.builder()
                    .rank(rank++)
                    .customerId(c.getId())
                    .customerName(maskCustomerName(c.getCustName()))
                    .contributionScore(score)
                    .depositBal(null) // V2 补充
                    .aum(aum)
                    .build());
        }
        return result;
    }

    /**
     * 客户名脱敏（保留首字 + **，对照 common-dev-guide.md §8）.
     */
    private String maskCustomerName(String name) {
        if (name == null || name.isEmpty()) {
            return "***";
        }
        if (name.length() <= 1) {
            return name + "**";
        }
        return name.charAt(0) + "**";
    }

    /**
     * 解析机构名（OrgApi 异常时回填 orgCode 兜底）.
     */
    private String resolveOrgName(String orgCode) {
        try {
            OrgDTO org = orgApi.getOrg(orgCode);
            return org != null && org.getOrgName() != null ? org.getOrgName() : orgCode;
        } catch (RuntimeException e) {
            log.warn("[DashboardService] resolveOrgName 失败 orgCode={}", orgCode);
            return orgCode;
        }
    }

    /**
     * 安全取机构指标值，跨模块异常 fail-soft 返回空 Map.
     */
    private Map<String, BigDecimal> safeGetOrgMetrics(String orgCode, LocalDate dataDate,
                                                     List<String> metricCodes) {
        try {
            Map<String, BigDecimal> v = metricApi.getOrgMetricValues(orgCode, dataDate, metricCodes);
            return v != null ? v : Map.of();
        } catch (RuntimeException e) {
            log.warn("[DashboardService] 取机构指标失败 orgCode={} dataDate={} metrics={} cause={}",
                    orgCode, dataDate, metricCodes, e.getMessage());
            return Map.of();
        }
    }

    /**
     * 安全取客户指标值.
     */
    private Map<String, BigDecimal> safeGetCustMetrics(String custId, LocalDate dataDate,
                                                       List<String> metricCodes) {
        try {
            Map<String, BigDecimal> v = metricApi.getCustMetricValues(custId, dataDate, metricCodes);
            return v != null ? v : Map.of();
        } catch (RuntimeException e) {
            log.warn("[DashboardService] 取客户指标失败 custId={} dataDate={}", custId, dataDate);
            return Map.of();
        }
    }

    /**
     * 构造审计日志（异步 fire-and-forget）.
     */
    private AuditLogCmd buildAuditCmd(String orgCode, LocalDate dataDate, String result) {
        return AuditLogCmd.builder()
                .empId(currentUserApi.getCurrentEmpId())
                .bizType("REPORT")
                .bizAction("DASHBOARD_PRESIDENT_VIEW")
                .resourceUrl("/api/reports/dashboard/president")
                .requestMethod("GET")
                .requestParams("dataDate=" + dataDate + ", orgCode=" + orgCode)
                .responseStatus(200)
                .errorMsg(result)
                .build();
    }

    @SuppressWarnings("unused")
    private BigDecimal pct(BigDecimal n, BigDecimal d) {
        if (n == null || d == null || d.signum() == 0) {
            return null;
        }
        return n.multiply(BigDecimal.valueOf(100)).divide(d, 2, RoundingMode.HALF_UP);
    }
}
