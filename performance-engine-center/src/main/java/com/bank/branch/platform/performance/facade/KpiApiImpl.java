package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.performance.api.KpiApi;
import com.bank.branch.platform.performance.api.dto.KpiResultDTO;
import com.bank.branch.platform.performance.api.dto.KpiSchemeDTO;
import com.bank.branch.platform.performance.entity.KpiResult;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.facade.assembler.KpiAssembler;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import com.bank.branch.platform.performance.service.KpiItemService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * KPI 查询对外 API 实现.
 *
 * <p>V1.0 契约 (spec §5.2.3):
 * <ul>
 *   <li>{@link #getKpiScheme(String)} / {@link #getKpiSchemeById(String)} 返回方案 + 方案项组装 DTO</li>
 * </ul>
 *
 * <p>V1.1 Task P4.3 交付：
 * <ul>
 *   <li>{@link #getCurrentKpiTotal} → 最新一条 {@code kpi_result.kpi_total_score}</li>
 *   <li>{@link #getCurrentKpiResult} → 最新一条完整 DTO</li>
 *   <li>{@link #getKpiHistory} → {@code as_of_date} 落在 {@code [from, to]} 区间的 DTO 列表</li>
 * </ul>
 *
 * <p>缓存策略:
 * <ul>
 *   <li>{@link #getKpiSchemeById} 使用 {@code perf:kpi_scheme} 缓存, key 为方案 ID</li>
 *   <li>{@link #getKpiScheme}(code) 不缓存: 按 code 查本质是 code → scheme 的二级查询,
 *       加缓存会让写方法的 evict 难以定位 (不知道 code), 故 V1.0 直接穿透到 Service。
 *       写方法 evict 仅针对 id 缓存 ({@code KpiSchemeService.updateById/publish/disable})</li>
 *   <li>V1.1 的 3 个结果查询方法不缓存：KPI 结果分钟级变动 + 按员工分散，命中率低。</li>
 * </ul>
 *
 * <p>消费方: portal-content-center, report-analytics-center.
 */
@Service
public class KpiApiImpl implements KpiApi {

    private final KpiSchemeService kpiSchemeService;
    private final KpiItemService kpiItemService;
    private final KpiResultMapper kpiResultMapper;
    /** Q7.3 新增: 当前用户读取（用于数据范围解析）. */
    private final CurrentUserApi currentUserApi;
    /** Q7.3 新增: 数据范围 SQL 片段生成器. */
    private final PerfScopeHelper perfScopeHelper;

    /**
     * 构造器（Q7.3 后手写，取代 @RequiredArgsConstructor 以便明确控制字段顺序）.
     */
    public KpiApiImpl(KpiSchemeService kpiSchemeService,
                      KpiItemService kpiItemService,
                      KpiResultMapper kpiResultMapper,
                      CurrentUserApi currentUserApi,
                      PerfScopeHelper perfScopeHelper) {
        this.kpiSchemeService = kpiSchemeService;
        this.kpiItemService = kpiItemService;
        this.kpiResultMapper = kpiResultMapper;
        this.currentUserApi = currentUserApi;
        this.perfScopeHelper = perfScopeHelper;
    }

    @Override
    public BigDecimal getCurrentKpiTotal(String empId, String cycleType) {
        KpiResult latest = kpiResultMapper.selectLatestByEmpCycle(empId, cycleType);
        return latest == null ? null : latest.getKpiTotalScore();
    }

    @Override
    public Optional<KpiResultDTO> getCurrentKpiResult(String empId, String cycleType) {
        KpiResult latest = kpiResultMapper.selectLatestByEmpCycle(empId, cycleType);
        return Optional.ofNullable(latest).map(this::toResultDto);
    }

    @Override
    public List<KpiResultDTO> getKpiHistory(String empId, String cycleType, LocalDate from, LocalDate to) {
        List<KpiResult> rows = kpiResultMapper.selectByEmpCycleRange(empId, cycleType, from, to);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::toResultDto).toList();
    }

    /**
     * Q7.3 示范方法：基于 {@link PerfScopeHelper} 的 KPI 历史查询.
     *
     * <p>比原 {@link #getKpiHistory} 更安全:
     * <ul>
     *   <li>ALL → 无过滤（管理员全见）</li>
     *   <li>SELF → 强制 "emp_id = 当前用户 empId"（即便入参 empId 为其他人）</li>
     *   <li>ctx=null → fail-close "1=0", 查无结果</li>
     * </ul>
     *
     * <p>ScopeColumns 映射: ownerEmpCol="emp_id" (kpi_result 表的业务主体列).
     *
     * <p>消费方建议: 从 V1.3 起，所有员工 KPI 历史查询统一切换到本方法；
     * V1.2 阶段 getKpiHistory 保留不变以保持 KpiApi 契约稳定。
     *
     * @param empId     员工工号（Mapper 主查询条件）
     * @param cycleType 周期类型
     * @param from      起始日期
     * @param to        截止日期
     * @return KPI 历史列表（已经数据范围过滤）
     */
    public List<KpiResultDTO> getKpiHistoryWithScope(String empId, String cycleType, LocalDate from, LocalDate to) {
        String currentEmpId = currentUserApi.getCurrentEmpId();
        // kpi_result 表仅 emp_id 一个业务主体列, 其他 scope 列降级语义
        PerfScopeHelper.ScopeColumns columns = new PerfScopeHelper.ScopeColumns(
                "emp_id",       // ownerEmpCol (SELF)
                "emp_id",       // assigneeCol (kpi_result 无 assignee 概念)
                "emp_id",       // createdByCol (kpi_result 由 Job 计算生成, 降级为 emp_id)
                "emp_id",       // ownerOrgCol (kpi_result 表无 org_code, 降级为 emp_id)
                null            // bizKeyCol (kpi_result 无 business_key, V1.4 WORKFLOW_PARTICIPANT 退化 fail-close)
        );
        PerfScopeHelper.Fragment frag = perfScopeHelper.getFragment(
                currentEmpId, BizType.PERF_CONFIG, BizAction.LIST, columns);
        List<KpiResult> rows = kpiResultMapper.selectByEmpCycleRangeWithScope(
                empId, cycleType, from, to, frag.getSql(), frag.getParams());
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::toResultDto).toList();
    }

    @Override
    public Optional<KpiSchemeDTO> getKpiScheme(String schemeCode) {
        return kpiSchemeService.getBySchemeCodeOrNull(schemeCode)
                .map(this::assembleWithItems);
    }

    @Override
    @Cacheable(cacheNames = "perf:kpi_scheme", key = "#schemeId")
    public Optional<KpiSchemeDTO> getKpiSchemeById(String schemeId) {
        return kpiSchemeService.getByIdOrNull(schemeId)
                .map(this::assembleWithItems);
    }

    /**
     * 组装方案 DTO (含方案项): 按 id 查方案项后委托 {@link KpiAssembler} 做字段映射.
     *
     * @param scheme 已查到的方案实体
     * @return 含 items 的方案 DTO
     */
    private KpiSchemeDTO assembleWithItems(PerfKpiScheme scheme) {
        List<PerfKpiItem> items = kpiItemService.listBySchemeId(scheme.getId());
        return KpiAssembler.toDto(scheme, items == null ? Collections.emptyList() : items);
    }

    /**
     * {@link KpiResult} → {@link KpiResultDTO} 字段直拷（不查 metricName / schemeName 关联，
     * 这两列需要 Facade 层按需二级查询时再补；当前消费方只用结果字段）.
     *
     * @param r 实体
     * @return DTO
     */
    private KpiResultDTO toResultDto(KpiResult r) {
        return KpiResultDTO.builder()
                .id(r.getId())
                .empId(r.getEmpId())
                .cycleType(r.getCycleType())
                .cycleDate(r.getCycleDate())
                .asOfDate(r.getAsOfDate())
                .dataVersion(r.getDataVersion())
                .kpiTotalScore(r.getKpiTotalScore())
                .detailJson(r.getDetailJson())
                .createTime(r.getCalculatedTime())
                .build();
    }
}
