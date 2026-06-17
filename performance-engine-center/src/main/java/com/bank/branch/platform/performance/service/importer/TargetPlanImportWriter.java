package com.bank.branch.platform.performance.service.importer;

import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 目标方案导入落库写入器（2026-06-17，TARGET_PLAN 策略专用）.
 *
 * <p>承载「全部行校验通过后」的 all-or-none 落库职责：单个 {@code @Transactional} 方法内
 * 先去重 upsert 目标方案（已存在则复用 id、忽略不改名；不存在则新建），再按 5000/批 chunk
 * 把目标值 {@code upsertBatch} 写入。任一步 DB 异常自动回滚整批。
 *
 * <p>由 {@code TargetPlanImportStrategy} 在所有行校验通过后调用；校验逻辑不在本类内。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TargetPlanImportWriter {

    /** 单批 upsert 的最大行数（避免 50000 行单条 SQL 过大；都在同一事务内仍保证 all-or-none）. */
    private static final int UPSERT_CHUNK = 5000;

    /** 新建目标方案默认状态. */
    private static final String DEFAULT_PLAN_STATUS = "ACTIVE";

    /** 新建目标方案默认目标维度. */
    private static final String DEFAULT_PLAN_TARGET_DIM = "EMP";

    /** 新建目标方案默认目标周期. */
    private static final String DEFAULT_PLAN_TARGET_CYCLE = "YEAR";

    private final PerfTargetPlanMapper targetPlanMapper;
    private final PerfTargetValueMapper targetValueMapper;

    /**
     * 落库目标方案 + 目标值（all-or-none，单事务）.
     *
     * <p>步骤：
     * <ol>
     *   <li>对每个去重的 planCode：{@code selectByPlanCode} 命中 → 复用其 id（忽略，不重建/不改名）；
     *       未命中 → 新建 PerfTargetPlan 并 insert。得到 planCode → planId 映射。</li>
     *   <li>逐行回填 PerfTargetValue 的 id（新 UUID）、planId（映射）、createdBy/createdTime，
     *       cycleKey 已由调用方按 startDate 年份派生。</li>
     *   <li>按 {@link #UPSERT_CHUNK} chunk 调 {@code upsertBatch}。</li>
     * </ol>
     *
     * @param rows     待落库目标值（除 id/planId/createdBy/createdTime 外字段已就绪），与 planCodes 等长
     * @param planCodes 每行对应的目标方案编号（与 rows 一一对应）
     * @param planNames 每行对应的目标方案名称（与 rows 一一对应，仅新建方案时取用）
     * @param operator 操作人（写 created_by）
     */
    @Transactional(rollbackFor = Exception.class)
    public void write(List<PerfTargetValue> rows, List<String> planCodes,
                      List<String> planNames, String operator) {
        LocalDateTime now = LocalDateTime.now();

        // 1) 解析/新建目标方案，得到 planCode → planId 映射
        Map<String, String> codeToPlanId = resolvePlanIds(planCodes, planNames, operator, now);

        // 2) 逐行回填 id / planId / 审计字段
        for (int i = 0; i < rows.size(); i++) {
            PerfTargetValue v = rows.get(i);
            v.setId(uuid32());
            v.setPlanId(codeToPlanId.get(planCodes.get(i)));
            v.setCreatedBy(operator);
            v.setCreatedTime(now);
        }

        // 3) 按 chunk upsert（同一事务内，all-or-none）
        for (int from = 0; from < rows.size(); from += UPSERT_CHUNK) {
            int to = Math.min(from + UPSERT_CHUNK, rows.size());
            targetValueMapper.upsertBatch(rows.subList(from, to));
        }
        log.info("[TargetPlanImportWriter] 落库完成 plans={}, values={}",
                codeToPlanId.size(), rows.size());
    }

    /** 去重 planCode → 复用既有 id 或新建方案，返回 planCode → planId 映射. */
    private Map<String, String> resolvePlanIds(List<String> planCodes, List<String> planNames,
                                               String operator, LocalDateTime now) {
        // LinkedHashSet 保持首次出现顺序，便于日志稳定
        LinkedHashSet<String> distinctCodes = new LinkedHashSet<>(planCodes);
        Map<String, String> codeToPlanId = new HashMap<>(distinctCodes.size());
        for (String code : distinctCodes) {
            PerfTargetPlan exist = targetPlanMapper.selectByPlanCode(code);
            if (exist != null) {
                // 已存在 → 复用 id，不重建、不改名
                codeToPlanId.put(code, exist.getId());
                continue;
            }
            // 新建：取该 code 首次出现的名称
            String planName = firstNameForCode(planCodes, planNames, code);
            PerfTargetPlan plan = new PerfTargetPlan();
            plan.setId(uuid32());
            plan.setPlanCode(code);
            plan.setPlanName(planName);
            plan.setKpiSchemeId("");
            plan.setTargetDim(DEFAULT_PLAN_TARGET_DIM);
            plan.setTargetCycle(DEFAULT_PLAN_TARGET_CYCLE);
            plan.setEffectiveDate(now.toLocalDate());
            plan.setStatus(DEFAULT_PLAN_STATUS);
            plan.setCreatedBy(operator);
            plan.setCreatedTime(now);
            plan.setUpdatedTime(now);
            targetPlanMapper.insert(plan);
            codeToPlanId.put(code, plan.getId());
        }
        return codeToPlanId;
    }

    /** 取某 planCode 首次出现行对应的方案名称. */
    private static String firstNameForCode(List<String> planCodes, List<String> planNames,
                                           String code) {
        for (int i = 0; i < planCodes.size(); i++) {
            if (code.equals(planCodes.get(i))) {
                return planNames.get(i);
            }
        }
        return code;
    }

    /** 32 位无横线 UUID. */
    private static String uuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
