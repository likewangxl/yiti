package com.bank.branch.platform.performance.service.importer;

import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.service.importer.impl.KpiSchemeImportStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * KPI 方案导入落库写入器（2026-06-17，KPI_SCHEME 策略专用）.
 *
 * <p>承载「全部行校验通过后」的 all-or-none 落库职责：单个 {@code @Transactional} 方法内
 * 先去重处理 KPI 方案（已存在则复用 id、忽略不改名不改标签范围；不存在则新建 ACTIVE 方案），
 * 再按 5000/批 chunk 把方案项 {@code upsertBatch} 写入。任一步 DB 异常自动回滚整批。
 *
 * <p>由 {@link KpiSchemeImportStrategy} 在所有行校验通过后调用；校验逻辑不在本类内。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiSchemeImportWriter {

    /** 单批 upsert 的最大行数（同一事务内仍保证 all-or-none）. */
    private static final int UPSERT_CHUNK = 5000;

    /** 新建 KPI 方案默认状态（规则1：已存在则忽略，新建则启用）. */
    private static final String DEFAULT_SCHEME_STATUS = "ACTIVE";

    /** 新建 KPI 方案默认周期类型. */
    private static final String DEFAULT_CYCLE_TYPE = "YEARLY";

    /** 方案项默认加倍系数. */
    private static final BigDecimal DEFAULT_MULTIPLIER = BigDecimal.ONE;

    /** 方案项默认最低分. */
    private static final BigDecimal DEFAULT_MIN_SCORE = BigDecimal.ZERO;

    /** 方案项默认最高分. */
    private static final BigDecimal DEFAULT_MAX_SCORE = new BigDecimal("999999");

    /** 方案项默认权重. */
    private static final BigDecimal DEFAULT_WEIGHT = BigDecimal.ZERO;

    private final PerfKpiSchemeMapper schemeMapper;
    private final PerfKpiItemMapper itemMapper;

    /**
     * 落库 KPI 方案 + 方案项（all-or-none，单事务）.
     *
     * <p>步骤：
     * <ol>
     *   <li>对每个去重 schemeCode：{@code selectBySchemeCode} 命中 → 复用其 id（忽略，不改名/不改标签范围）；
     *       未命中 → 新建 PerfKpiScheme（status=ACTIVE、cycleType=YEARLY、openDetail=0）并 insert。
     *       得到 schemeCode → schemeId 映射。</li>
     *   <li>逐项回填 id（新 UUID）、schemeId（映射）、multiplier/minScore/maxScore/weight 兜底默认、createdTime。</li>
     *   <li>按 {@link #UPSERT_CHUNK} chunk 调 {@code upsertBatch}。</li>
     * </ol>
     *
     * @param items       待落库方案项（除 id/schemeId/createdTime 及兜底默认外字段已就绪），与 schemeCodes 等长
     * @param schemeCodes 每项对应的方案编号（与 items 一一对应）
     * @param schemeInfos 按 schemeCode 去重的方案信息（schemeName + empTagScope，仅新建方案时取用）
     * @param operator    操作人（写 created_by/updated_by）
     */
    @Transactional(rollbackFor = Exception.class)
    public void write(List<PerfKpiItem> items, List<String> schemeCodes,
                      Map<String, KpiSchemeImportStrategy.SchemeInfo> schemeInfos, String operator) {
        LocalDateTime now = LocalDateTime.now();

        // 1) 解析/新建 KPI 方案，得到 schemeCode → schemeId 映射
        Map<String, String> codeToSchemeId = resolveSchemeIds(schemeCodes, schemeInfos, operator, now);

        // 2) 逐项回填 id / schemeId / 兜底默认 / 审计字段
        for (int i = 0; i < items.size(); i++) {
            PerfKpiItem item = items.get(i);
            item.setId(uuid32());
            item.setSchemeId(codeToSchemeId.get(schemeCodes.get(i)));
            if (item.getMultiplier() == null) {
                item.setMultiplier(DEFAULT_MULTIPLIER);
            }
            if (item.getMinScore() == null) {
                item.setMinScore(DEFAULT_MIN_SCORE);
            }
            if (item.getMaxScore() == null) {
                item.setMaxScore(DEFAULT_MAX_SCORE);
            }
            if (item.getWeight() == null) {
                item.setWeight(DEFAULT_WEIGHT);
            }
            item.setCreatedTime(now);
        }

        // 3) 按 chunk upsert（同一事务内，all-or-none）
        for (int from = 0; from < items.size(); from += UPSERT_CHUNK) {
            int to = Math.min(from + UPSERT_CHUNK, items.size());
            itemMapper.upsertBatch(items.subList(from, to));
        }
        log.info("[KpiSchemeImportWriter] 落库完成 schemes={}, items={}",
                codeToSchemeId.size(), items.size());
    }

    /** 去重 schemeCode → 复用既有 id 或新建 ACTIVE 方案，返回 schemeCode → schemeId 映射. */
    private Map<String, String> resolveSchemeIds(List<String> schemeCodes,
                                                 Map<String, KpiSchemeImportStrategy.SchemeInfo> schemeInfos,
                                                 String operator, LocalDateTime now) {
        LinkedHashSet<String> distinctCodes = new LinkedHashSet<>(schemeCodes);
        Map<String, String> codeToSchemeId = new HashMap<>(distinctCodes.size());
        for (String code : distinctCodes) {
            PerfKpiScheme exist = schemeMapper.selectBySchemeCode(code);
            if (exist != null) {
                // 规则1：已存在 → 复用 id，不重建、不改名、不改标签范围
                codeToSchemeId.put(code, exist.getId());
                continue;
            }
            KpiSchemeImportStrategy.SchemeInfo info = schemeInfos.get(code);
            PerfKpiScheme scheme = new PerfKpiScheme();
            scheme.setId(uuid32());
            scheme.setSchemeCode(code);
            scheme.setSchemeName(info == null ? code : info.schemeName());
            scheme.setEmpTagScope(info == null ? null : info.empTagScope());
            scheme.setStatus(DEFAULT_SCHEME_STATUS);
            scheme.setCycleType(DEFAULT_CYCLE_TYPE);
            scheme.setOpenDetail(0);
            scheme.setCreatedBy(operator);
            scheme.setUpdatedBy(operator);
            scheme.setCreatedTime(now);
            scheme.setUpdatedTime(now);
            schemeMapper.insert(scheme);
            codeToSchemeId.put(code, scheme.getId());
        }
        return codeToSchemeId;
    }

    /** 32 位无横线 UUID. */
    private static String uuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
