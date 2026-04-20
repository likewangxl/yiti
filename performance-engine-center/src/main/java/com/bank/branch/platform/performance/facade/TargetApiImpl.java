package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.TargetApi;
import com.bank.branch.platform.performance.api.dto.TargetPlanDTO;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.facade.assembler.TargetAssembler;
import com.bank.branch.platform.performance.service.TargetPlanService;
import com.bank.branch.platform.performance.service.TargetValueService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 目标查询对外 API 实现.
 *
 * <p>V1.0 契约 (spec §5.2.4): **4 方法全部 V1.0 实现**, 无 UOE 占位.
 * planId 全 String (v1.2 对齐生产 DDL varchar(32))。
 *
 * <p>缓存策略:
 * <ul>
 *   <li>{@link #getTargetPlanById} 使用 {@code perf:target_plan} 缓存, key 为方案 ID;
 *       evict 由 {@link TargetPlanService#create/updateById/disable} 的 afterCommit 回调触发</li>
 *   <li>{@link #getTargetPlan}(code) 不缓存: 按 code 查本质是 code → plan 的二级查询,
 *       加缓存会让写方法的 evict 难以定位 (不知道 code), 故 V1.0 直接穿透到 Service,
 *       与 {@link KpiApiImpl#getKpiScheme(String)} 的决策保持一致</li>
 *   <li>{@link #getTargetValue} / {@link #listTargetValues} 不缓存: 目标值属业务数据,
 *       量大且频繁变动 (批量 upsert 每次可达 500 行), 缓存收益低且一致性成本高</li>
 * </ul>
 *
 * <p>listTargetValues 实现说明:
 * {@link TargetValueService} 当前仅提供 paginated {@link TargetValueService#listByPlan},
 * Facade 层用 pageNo=1 / pageSize=500 (对齐 {@code BATCH_UPPER_LIMIT}) 请求首页;
 * 业务上某主体某周期的目标值数量由 KPI 方案项数上限决定 (通常 &lt; 20), 500 行足够覆盖。
 * V1.1 若出现超过 500 行的极端场景, 再引入非分页查询接口 (需架构师评审)。
 *
 * <p>消费方 (V1.0): portal-content-center (目标展示), report-analytics-center (完成率计算).
 */
@Service
@RequiredArgsConstructor
public class TargetApiImpl implements TargetApi {

    /**
     * listTargetValues 单次请求上限, 对齐 {@code TargetValueService.BATCH_UPPER_LIMIT}.
     * 业务上某主体某周期目标值数量 = KPI 方案项数, 通常 &lt; 20, 500 足够兜底。
     */
    private static final int SUBJECT_CYCLE_LIST_LIMIT = 500;

    private final TargetPlanService targetPlanService;
    private final TargetValueService targetValueService;

    /**
     * 按方案编码查询目标方案 (不缓存).
     *
     * @param planCode 方案编码
     * @return Optional 包装的 DTO
     */
    @Override
    public Optional<TargetPlanDTO> getTargetPlan(String planCode) {
        return targetPlanService.getByCodeOrNull(planCode)
                .map(TargetAssembler::toDto);
    }

    /**
     * 按方案主键查询目标方案, 命中时返回 DTO (带缓存).
     *
     * <p>{@code @Cacheable} 在同一 Spring Bean 内通过自调用不生效,
     * 本方法由外部消费方 (portal-content-center / report-analytics-center)
     * 经 AOP 代理调用, 缓存正常生效。
     *
     * @param planId 方案 ID (varchar 32)
     * @return Optional 包装的 DTO
     */
    @Override
    @Cacheable(cacheNames = "perf:target_plan", key = "#planId")
    public Optional<TargetPlanDTO> getTargetPlanById(String planId) {
        return targetPlanService.getByIdOrNull(planId)
                .map(TargetAssembler::toDto);
    }

    /**
     * 按 UK (planId, subjectType, subjectId, cycleKey, metricCode) 查询目标值 (不缓存).
     *
     * @param planId      方案 ID
     * @param subjectType EMP / ORG
     * @param subjectId   主体 ID
     * @param cycleKey    周期键
     * @param metricCode  指标编码
     * @return Optional 包装的目标值 (target_value 字段); 未命中返回 empty
     */
    @Override
    public Optional<BigDecimal> getTargetValue(String planId, String subjectType, String subjectId,
                                               String cycleKey, String metricCode) {
        return targetValueService.getByUniqueKey(planId, subjectType, subjectId, cycleKey, metricCode)
                .map(PerfTargetValue::getTargetValue);
    }

    /**
     * 批量查询某主体在某周期的所有目标值 (不缓存).
     *
     * <p>委托 {@link TargetValueService#listByPlan} (pageNo=1, pageSize=500), 再装配 DTO 列表。
     * 空 records 时返回 {@link Collections#emptyList()} 保持契约 (不返回 null)。
     *
     * @param planId      方案 ID
     * @param subjectType EMP / ORG
     * @param subjectId   主体 ID
     * @param cycleKey    周期键
     * @return 目标值 DTO 列表
     */
    @Override
    public List<TargetValueDTO> listTargetValues(String planId, String subjectType, String subjectId,
                                                 String cycleKey) {
        PageResult<PerfTargetValue> page = targetValueService.listByPlan(
                planId, subjectType, subjectId, cycleKey, 1, SUBJECT_CYCLE_LIST_LIMIT);
        List<PerfTargetValue> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        return records.stream()
                .map(TargetAssembler::toDto)
                .toList();
    }
}
