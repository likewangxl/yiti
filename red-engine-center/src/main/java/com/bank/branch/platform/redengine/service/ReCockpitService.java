package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.ReCockpitOverviewDTO;
import com.bank.branch.platform.redengine.api.dto.ReOverdueItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReRankingItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReWarningItemDTO;
import com.bank.branch.platform.redengine.entity.ReAnnualResult;
import com.bank.branch.platform.redengine.entity.ReOverdueDeduction;
import com.bank.branch.platform.redengine.entity.ReScore;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.mapper.ReAnnualResultMapper;
import com.bank.branch.platform.redengine.mapper.ReOverdueDeductionMapper;
import com.bank.branch.platform.redengine.mapper.ReScoreMapper;
import com.bank.branch.platform.redengine.mapper.ReSubmitMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 红色引擎-驾驶舱/预警/年度归档服务。
 * <p>移植自 redengine {@code BizCockpitServiceImpl}（{@code business.service.impl}），承接全局数据
 * 驾驶舱总览、组织排名、逾期上报池、红黄牌预警池、期间结算归档、年度考核结果生成。</p>
 *
 * <p><b>红黄牌阈值口径以 Java 代码为准</b>：源系统前端/文档注释写"黄牌上限75分"，但源
 * {@code BizCockpitServiceImpl.getYellowWarning} 实际代码是 {@code ge(60).lt(80)}——本次移植照抄
 * 代码行为：final_score&lt;60 红牌，60&le;final_score&lt;80 黄牌，&ge;80 不预警。两处口径判定不在本
 * 任务范围内改文档，记入 Task 18 勘误。</p>
 *
 * <p><b>逾期口径以 Java 代码为准</b>：源系统 spec 文档口径是"迟1天-1分、迟&ge;3天清零"的自动累进扣分，
 * 但源 {@code BizCockpitServiceImpl} 实际代码是"逾期列表=status∈{0,1}且submitDate+7天早于今天的上报
 * 记录，由人工在预警池里逐条调用 executeOverdue 执行扣分(默认5分)"——本次移植同样照抄代码行为，
 * 不实现文档描述的自动累进扣分。</p>
 *
 * <p><b>generateAnnualResult 维度聚合补全说明（简报已授权的行为变更）</b>：源
 * {@code BizCockpitServiceImpl.generateAnnualResult} 的维度聚合是残缺实现——只用
 * {@code orgDimensionScores.putIfAbsent(orgId, new HashMap<>())} 建了个"组织->空维度Map"的键，
 * 从未把任何 {@code RE_SCORE} 记录的实际分值写入该 Map，导致 dim1~dim4 永远读到
 * {@code getOrDefault(..., ZERO)} 的兜底零值，total/final/isQualified 因此恒为0/0/不合格，是一个
 * 从未真正生效的"死代码"。RE_SCORE 实体本身不带 dimension 字段（该字段落在 RE_SUBMIT 上），本次
 * 移植按简报授权补全为：① 取全部未软删 RE_SCORE；② 取这些 RE_SCORE 关联的 RE_SUBMIT（按
 * submitId 批量 in 查询，二次查询后内存 join，避免 N+1，也避免为此单独写 XML 跨表 JOIN——数据量
 * 为党组织级别的个位数量级，符合平台"动态/简单条件查询优先 LambdaQueryWrapper"规范）取其
 * dimension；③ 按 (orgId, dimension) 分组对 finalScore 求和，得每个组织 dim1~dim4 的真实合计；
 * ④ total=四维之和，final=total*0.4，isQualified=total&ge;60；⑤ 按 (orgId, evalYear) upsert
 * （唯一键 uk_org_year：存在则 updateById，否则 insert）。年度范围沿用源码行为，不额外按
 * {@code scoreYear} 二次过滤 RE_SCORE（源码 {@code scoreWrapper} 本就只按 deleted=0 取全量，未按
 * 年份过滤——本任务只补全"残缺聚合"这一项被简报明确授权的缺陷，不在本任务内顺手改动未被点名的
 * 其它口径，避免超出授权范围）。</p>
 *
 * <p><b>executeOverdue "上报不存在"处理（相对源码的判断调用）</b>：源码 {@code submit == null} 时
 * 直接 {@code return false}，静默失败。按 T2 变换规则(不再用布尔/异常吞掉失败结果)，并参照 Task 9
 * {@code ReReviewService.approve} 对同类"关联记录不存在"场景的处理方式，本次移植改为抛
 * {@code BizException(RE-40005)}，由 {@code GlobalExceptionHandler} 统一映射为失败响应，不是源系统
 * 行为的逐字复刻，是本次移植按平台既有惯例做的等价加固。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReCockpitService {

    /** 红牌阈值：final_score < 60 */
    private static final BigDecimal RED_THRESHOLD = new BigDecimal("60");
    /** 黄牌阈值上界(不含)：60 <= final_score < 80，照抄源码 80(非源注释 75) */
    private static final BigDecimal YELLOW_UPPER_THRESHOLD = new BigDecimal("80");
    /** 年度归档合格线：total_score >= 60 */
    private static final BigDecimal QUALIFIED_THRESHOLD = new BigDecimal("60");
    /** 年度归档折算系数：final_score = total_score * 0.4 */
    private static final BigDecimal ANNUAL_FINAL_FACTOR = new BigDecimal("0.4");
    /** 逾期未结的默认扣分分值 */
    private static final BigDecimal DEFAULT_DEDUCTION_POINTS = new BigDecimal("5");
    /** 逾期判定天数：submitDate + 7天 早于今天视为逾期 */
    private static final long OVERDUE_DAYS = 7L;

    private final ReSubmitMapper reSubmitMapper;
    private final ReScoreMapper reScoreMapper;
    private final ReOverdueDeductionMapper reOverdueDeductionMapper;
    private final ReAnnualResultMapper reAnnualResultMapper;

    /**
     * 查询驾驶舱总览统计（上报总数/已通过/待审/已驳回四计数）。
     * <p>四次计数均基于 RE_SUBMIT，{@code @TableLogic} 自动排除软删记录，无需手工拼 deleted 条件。</p>
     *
     * @return 总览统计 DTO
     */
    public ReCockpitOverviewDTO getOverview() {
        long totalSubmits = reSubmitMapper.selectCount(new LambdaQueryWrapper<>());
        long approvedCount = reSubmitMapper.selectCount(
                new LambdaQueryWrapper<ReSubmit>().eq(ReSubmit::getStatus, 2));
        long pendingCount = reSubmitMapper.selectCount(
                new LambdaQueryWrapper<ReSubmit>().eq(ReSubmit::getStatus, 1));
        long rejectedCount = reSubmitMapper.selectCount(
                new LambdaQueryWrapper<ReSubmit>().eq(ReSubmit::getStatus, 3));

        ReCockpitOverviewDTO overview = new ReCockpitOverviewDTO();
        overview.setTotalSubmits(totalSubmits);
        overview.setApprovedCount(approvedCount);
        overview.setPendingCount(pendingCount);
        overview.setRejectedCount(rejectedCount);
        log.info("[ReCockpitService.getOverview] total={}, approved={}, pending={}, rejected={}",
                totalSubmits, approvedCount, pendingCount, rejectedCount);
        return overview;
    }

    /**
     * 查询组织排名（按 RE_SCORE.final_score 降序，rank 为排序后序号）。
     *
     * @return 排名列表
     */
    public List<ReRankingItemDTO> getRanking() {
        List<ReScore> scores = reScoreMapper.selectList(
                new LambdaQueryWrapper<ReScore>().orderByDesc(ReScore::getFinalScore));

        List<ReRankingItemDTO> ranking = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            ReScore score = scores.get(i);
            ReRankingItemDTO item = new ReRankingItemDTO();
            item.setRank(i + 1);
            item.setOrgId(score.getOrgId());
            item.setFinalScore(score.getFinalScore());
            item.setPeriod(score.getScorePeriod());
            ranking.add(item);
        }
        log.info("[ReCockpitService.getRanking] size={}", ranking.size());
        return ranking;
    }

    /**
     * 查询逾期上报池。
     * <p>逾期口径：RE_SUBMIT.status∈{0,1}(草稿/已提交，尚未终审) 且 submitDate+7天 严格早于今天。
     * status 粗筛下沉到 SQL(wrapper)，7天边界用 {@link LocalDate#plusDays}+{@link LocalDate#isBefore}
     * 在内存精确判定(照抄源码语义，目标实体 submitDate 已是 LocalDate，无需再做 java.sql.Date 转换)。</p>
     *
     * @return 逾期条目列表
     */
    public List<ReOverdueItemDTO> getOverdueList() {
        List<ReSubmit> candidates = reSubmitMapper.selectList(
                new LambdaQueryWrapper<ReSubmit>()
                        .in(ReSubmit::getStatus, 0, 1)
                        .orderByAsc(ReSubmit::getSubmitDate));

        LocalDate today = LocalDate.now();
        List<ReOverdueItemDTO> overdueList = new ArrayList<>();
        for (ReSubmit submit : candidates) {
            LocalDate submitDate = submit.getSubmitDate();
            if (submitDate != null && submitDate.plusDays(OVERDUE_DAYS).isBefore(today)) {
                ReOverdueItemDTO item = new ReOverdueItemDTO();
                item.setId(submit.getId());
                item.setOrgId(submit.getOrgId());
                item.setProjectName(submit.getProjectName());
                item.setSubmitDate(submitDate);
                item.setStatus(submit.getStatus());
                overdueList.add(item);
            }
        }
        log.info("[ReCockpitService.getOverdueList] candidateCount={}, overdueCount={}",
                candidates.size(), overdueList.size());
        return overdueList;
    }

    /**
     * 人工执行逾期扣分（预警池逐条操作，高危）。
     * <p>未提供扣分分值时兜底默认5分（照抄源 Controller 层的默认值逻辑，下沉到 Service 便于单测覆盖）。
     * 上报记录不存在时抛 {@code BizException(RE-40005)}，不静默返回失败（见类注释）。</p>
     *
     * @param submitId        上报ID
     * @param deductionPoints 扣分分值（可为空，兜底默认5分）
     * @throws BizException code=RE-40005，上报记录不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeOverdue(Long submitId, BigDecimal deductionPoints) {
        ReSubmit submit = reSubmitMapper.selectById(submitId);
        if (submit == null) {
            throw new BizException("RE-40005", "上报记录不存在");
        }

        BigDecimal points = deductionPoints != null ? deductionPoints : DEFAULT_DEDUCTION_POINTS;

        ReOverdueDeduction deduction = new ReOverdueDeduction();
        deduction.setOrgId(submit.getOrgId());
        deduction.setSubmitId(submitId);
        deduction.setDeductionReason("逾期未上报");
        deduction.setDeductionPoints(points);
        deduction.setDeductionDate(LocalDate.now());
        reOverdueDeductionMapper.insert(deduction);
        log.info("[ReCockpitService.executeOverdue] submitId={}, orgId={}, points={}",
                submitId, submit.getOrgId(), points);
    }

    /**
     * 查询红牌预警池：final_score &lt; 60。
     *
     * @return 红牌预警列表
     */
    public List<ReWarningItemDTO> getRedWarning() {
        List<ReScore> scores = reScoreMapper.selectList(new LambdaQueryWrapper<>());
        List<ReWarningItemDTO> warnings = new ArrayList<>();
        for (ReScore score : scores) {
            if (score.getFinalScore() != null && score.getFinalScore().compareTo(RED_THRESHOLD) < 0) {
                warnings.add(toWarningItem(score, "red"));
            }
        }
        log.info("[ReCockpitService.getRedWarning] size={}", warnings.size());
        return warnings;
    }

    /**
     * 查询黄牌预警池：60 &le; final_score &lt; 80（照抄源码阈值80，非源注释75）。
     *
     * @return 黄牌预警列表
     */
    public List<ReWarningItemDTO> getYellowWarning() {
        List<ReScore> scores = reScoreMapper.selectList(new LambdaQueryWrapper<>());
        List<ReWarningItemDTO> warnings = new ArrayList<>();
        for (ReScore score : scores) {
            BigDecimal finalScore = score.getFinalScore();
            if (finalScore != null
                    && finalScore.compareTo(RED_THRESHOLD) >= 0
                    && finalScore.compareTo(YELLOW_UPPER_THRESHOLD) < 0) {
                warnings.add(toWarningItem(score, "yellow"));
            }
        }
        log.info("[ReCockpitService.getYellowWarning] size={}", warnings.size());
        return warnings;
    }

    private ReWarningItemDTO toWarningItem(ReScore score, String level) {
        ReWarningItemDTO item = new ReWarningItemDTO();
        item.setOrgId(score.getOrgId());
        item.setFinalScore(score.getFinalScore());
        item.setPeriod(score.getScorePeriod());
        item.setLevel(level);
        return item;
    }

    /**
     * 期间结算归档（语义照源码保真）。
     * <p>源码实现只是"查询该期间是否存在 RE_SCORE 记录"，并未真正落任何"已结算"标记，方法名与实际行为
     * 不符——本次移植不改行为，原样保真（是否需要真正实现结算标记留待后续任务按产品决策处理，不在
     * 本任务范围内擅自加戏）。</p>
     *
     * @param period 考核期间(YYYY-MM)
     * @return 该期间是否存在评分记录
     */
    public boolean archiveSettlement(String period) {
        List<ReScore> scores = reScoreMapper.selectList(
                new LambdaQueryWrapper<ReScore>().eq(ReScore::getScorePeriod, period));
        boolean result = !scores.isEmpty();
        log.info("[ReCockpitService.archiveSettlement] period={}, scoreCount={}, result={}",
                period, scores.size(), result);
        return result;
    }

    /**
     * 生成年度考核归档结果（按组织聚合四维度分数，upsert 到 RE_ANNUAL_RESULT）。
     * <p>维度聚合补全说明见类注释。聚合步骤：① 取全部未软删 RE_SCORE；② 收集这些 RE_SCORE 的
     * submitId，批量 in 查询对应 RE_SUBMIT 取 dimension（内存 Map 缓存，避免 N+1）；③ 按
     * (orgId, dimension) 分组对 finalScore 求和；④ dim1~dim4 取自求和结果(缺省0)，
     * total=四维之和，final=total*0.4，isQualified=total&ge;60 ? 1 : 0；⑤ 按 (orgId, evalYear)
     * upsert。RE_SCORE 找不到对应 RE_SUBMIT(如已被物理清理)时该分不计入任何维度，跳过，不抛异常。</p>
     *
     * @param year 考核年度，为空时取当前年度
     */
    @Transactional(rollbackFor = Exception.class)
    public void generateAnnualResult(Integer year) {
        int evalYear = year != null ? year : LocalDate.now().getYear();

        List<ReScore> allScores = reScoreMapper.selectList(new LambdaQueryWrapper<>());

        Set<Long> submitIds = new HashSet<>();
        for (ReScore score : allScores) {
            if (score.getSubmitId() != null) {
                submitIds.add(score.getSubmitId());
            }
        }
        Map<Long, String> submitIdToDimension = new HashMap<>();
        if (!submitIds.isEmpty()) {
            List<ReSubmit> submits = reSubmitMapper.selectList(
                    new LambdaQueryWrapper<ReSubmit>().in(ReSubmit::getId, submitIds));
            for (ReSubmit submit : submits) {
                submitIdToDimension.put(submit.getId(), submit.getDimension());
            }
        }

        // 按 (orgId, dimension) 分组求和：先按 orgId 建组织维度表，再按 dimension 累加 finalScore
        Map<Long, Map<String, BigDecimal>> orgDimensionSums = new HashMap<>();
        for (ReScore score : allScores) {
            String dimension = submitIdToDimension.get(score.getSubmitId());
            if (dimension == null || score.getOrgId() == null) {
                // 找不到对应 RE_SUBMIT 的维度归属(如已被物理清理)，该分不计入聚合，跳过
                continue;
            }
            BigDecimal finalScore = score.getFinalScore() != null ? score.getFinalScore() : BigDecimal.ZERO;
            orgDimensionSums.computeIfAbsent(score.getOrgId(), k -> new HashMap<>())
                    .merge(dimension, finalScore, BigDecimal::add);
        }

        int upsertCount = 0;
        for (Map.Entry<Long, Map<String, BigDecimal>> entry : orgDimensionSums.entrySet()) {
            Long orgId = entry.getKey();
            Map<String, BigDecimal> dims = entry.getValue();

            BigDecimal dim1 = dims.getOrDefault("dim1", BigDecimal.ZERO);
            BigDecimal dim2 = dims.getOrDefault("dim2", BigDecimal.ZERO);
            BigDecimal dim3 = dims.getOrDefault("dim3", BigDecimal.ZERO);
            BigDecimal dim4 = dims.getOrDefault("dim4", BigDecimal.ZERO);
            BigDecimal totalScore = dim1.add(dim2).add(dim3).add(dim4);
            BigDecimal finalScore = totalScore.multiply(ANNUAL_FINAL_FACTOR);
            int isQualified = totalScore.compareTo(QUALIFIED_THRESHOLD) >= 0 ? 1 : 0;

            ReAnnualResult existing = reAnnualResultMapper.selectOne(
                    new LambdaQueryWrapper<ReAnnualResult>()
                            .eq(ReAnnualResult::getOrgId, orgId)
                            .eq(ReAnnualResult::getEvalYear, evalYear));

            ReAnnualResult result = new ReAnnualResult();
            result.setOrgId(orgId);
            result.setDim1Score(dim1);
            result.setDim2Score(dim2);
            result.setDim3Score(dim3);
            result.setDim4Score(dim4);
            result.setTotalScore(totalScore);
            result.setFinalScore(finalScore);
            result.setIsQualified(isQualified);
            result.setEvalYear(evalYear);

            if (existing != null) {
                result.setId(existing.getId());
                reAnnualResultMapper.updateById(result);
            } else {
                reAnnualResultMapper.insert(result);
            }
            upsertCount++;
        }
        log.info("[ReCockpitService.generateAnnualResult] evalYear={}, orgCount={}", evalYear, upsertCount);
    }
}
