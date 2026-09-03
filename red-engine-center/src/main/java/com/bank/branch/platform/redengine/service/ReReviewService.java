package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReScore;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.entity.ReUserPartyMap;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReScoreMapper;
import com.bank.branch.platform.redengine.mapper.ReSubmitMapper;
import com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 红色引擎-两级审核服务。
 * <p>移植自 redengine {@code BizReviewServiceImpl}（{@code business.service.impl}），承接支部/组织
 * 两级审核工作台：待审队列查询、上报详情预览、审核通过(含评分上限校验)、审核驳回。</p>
 * <p><b>reviewerId 补齐说明</b>：源系统 {@code approveSubmit}/{@code rejectSubmit} 均未记录审核人
 * (仅回填 status/reviewFeedback/reviewDate)，本次移植按 Task 9 简报要求，由 Controller 从
 * {@code CurrentUserApi.getCurrentEmpId()} 取当前登录人传入，本服务落到 RE_SUBMIT.reviewerId——
 * 是本次移植新增的能力，不是源系统行为的等价迁移。</p>
 * <p><b>status 前置校验说明</b>：源 {@code approveSubmit}/{@code rejectSubmit} 均未对
 * {@code existing.status} 做"仅 status=1(待审)可审核"的前置校验（对已通过/已驳回的记录重复调用
 * 审核接口不会报错，会被静默覆盖），本次移植原样保真、未新增该校验；如需收紧留待后续任务按产品
 * 决策补充，不在本任务范围内擅自加严。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReReviewService {

    /** 考核项未配置 maxScore 时的兜底满分上限（照抄源 approveSubmit 60-62 行的回退逻辑） */
    private static final BigDecimal DEFAULT_MAX_SCORE = new BigDecimal("100");

    /** 创建人与审核人相同时拒绝自审，避免多角色权限并集绕过业务职责分离。 */
    private static final String SELF_REVIEW_ERROR_CODE = "RE-40008";

    private static final String SELF_REVIEW_ERROR_MESSAGE = "禁止审核本人提交的记录";

    private static final String BRANCH_SECRETARY_ROLE = "R_RE_SECR";
    private static final String SYSTEM_ADMIN_ROLE = "SYS_ADMIN";
    private static final String DEFAULT_REVIEW_TAB = "PENDING";
    private static final String REVIEWING_TAB = "REVIEWING";

    private final ReSubmitMapper reSubmitMapper;
    private final ReScoreMapper reScoreMapper;
    private final RePartyOrgMapper partyOrgMapper;
    private final ReUserPartyMapMapper userPartyMapMapper;
    private final CurrentUserApi currentUserApi;

    /**
     * 查询审核待审队列。
     * <p>照抄源 {@code getReviewQueue}：status=1(已提交) 的记录按 submitDate 升序分页，让最早提交的
     * 材料优先进入审核视野。软删过滤由 {@code ReSubmit.deleted} 上的 MyBatis-Plus {@code @TableLogic}
     * 自动生效，无需手工拼条件。</p>
     *
     * @param pageNo   页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<ReSubmit> getReviewQueue(int pageNo, int pageSize) {
        return getReviewQueue(pageNo, pageSize, DEFAULT_REVIEW_TAB, currentUserApi.getCurrentEmpId());
    }

    /**
     * 查询当前支部书记可见的旧材料审核队列。
     *
     * <p>旧 {@code RE_SUBMIT} 记录没有任务 assignment，因此不能沿用任务工作台的查询；这里按
     * 当前支部书记可审核的支部集合限制 {@code orgId}，并在 Service 层再次校验，避免仅凭前端页签
     * 或 URL 泄露其他支部材料。REVIEWING 是任务工作台兼容页签，旧材料没有该状态，固定返回空页。</p>
     *
     * @param pageNo     页码（从1开始）
     * @param pageSize   每页大小
     * @param tab        PENDING/PASSED/REJECTED；空值默认为 PENDING，REVIEWING 返回空页
     * @param operatorId 当前登录支部书记平台用户 ID
     * @return 当前用户支部范围内的旧材料分页
     */
    public PageResult<ReSubmit> getReviewQueue(int pageNo, int pageSize, String tab, String operatorId) {
        requireCurrentUser(operatorId);
        requireBranchReviewer();
        Set<Long> branchIds = secretaryBranchIds(operatorId);
        String normalizedTab = normalizeReviewTab(tab);
        if (REVIEWING_TAB.equals(normalizedTab) || branchIds.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, List.of());
        }

        int status = statusForTab(normalizedTab);
        LambdaQueryWrapper<ReSubmit> wrapper = new LambdaQueryWrapper<ReSubmit>()
                .eq(ReSubmit::getStatus, status)
                .in(ReSubmit::getOrgId, branchIds)
                .orderByAsc(ReSubmit::getSubmitDate);
        IPage<ReSubmit> page = reSubmitMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ReSubmit> records = page == null || page.getRecords() == null
                ? List.of()
                : page.getRecords().stream()
                .filter(Objects::nonNull)
                .filter(item -> branchIds.contains(item.getOrgId()))
                .toList();
        return PageResult.of(pageNo, pageSize, page == null ? 0L : page.getTotal(), records);
    }

    /**
     * 查询上报详情供审核预览。
     * <p>与 {@code ReSubmitService.getDetail} 复用同一张 RE_SUBMIT 表，语义一致：不存在时返回 null，
     * 交由调用方(Controller)决定是否视为异常。端点层面复用 Task 4 已登记的 {@code P_RE_REVIEW_Q}
     * 资源（URL 由 {@code /api/re/reviews/queue} 调整为 {@code /api/re/reviews/**}），不单独登记新资源。</p>
     *
     * @param id 上报ID
     * @return 上报实体
     */
    public ReSubmit getPreview(Long id) {
        return getPreview(id, currentUserApi.getCurrentEmpId());
    }

    /** 查询旧材料详情，并按当前支部书记的实际支部范围执行实体级校验。 */
    public ReSubmit getPreview(Long id, String operatorId) {
        requireCurrentUser(operatorId);
        requireBranchReviewer();
        ReSubmit existing = reSubmitMapper.selectById(id);
        if (existing != null) {
            requireSecretaryForBranch(existing.getOrgId(), operatorId);
        }
        return existing;
    }

    /**
     * 审核通过（含评分上限校验）。
     * <p>照抄源 {@code approveSubmit} 52-110 行核心规则：
     * ① 上报不存在直接拒绝，不触碰 RE_SCORE/RE_SUBMIT；
     * ② score 非空且&gt;0 时，校验"同党组织(orgId)+同考核项(itemCode)+同年度(scoreYear=当年)"下
     * 已有 RE_SCORE 明细的 finalScore 累计值 + 本次 score 是否超出该上报的 maxScore（未配置则回退
     * {@link #DEFAULT_MAX_SCORE}），超限则拒绝且不落库、不推进状态；
     * ③ 校验通过后插入一条 RE_SCORE 明细(orgId/submitId/itemCode/scoreYear/finalScore/remark)，并把
     * RE_SUBMIT 状态推进为 2(已通过)，回填审核意见(reviewFeedback)/审核人(reviewerId)/审核时间
     * (reviewDate=now)。score 为空或&le;0 时跳过②③中的评分落库分支，但状态推进为 2 不受影响
     * （与源系统一致：状态推进在 score 分支之外，无条件执行）。</p>
     *
     * @param submitId 上报ID
     * @param score    本次评分（可为 null 或 &le;0，此时跳过评分落库，仅推进状态）
     * @param feedback 审核意见
     * @param empId    当前登录审核人平台工号（源系统未记录，本次补齐，见类注释）
     * @throws BizException code=RE-40003，上报记录不存在
     * @throws BizException code=RE-40004，累计得分超出该考核项上限
     * @throws BizException code=RE-40008，审核人与上报创建人相同
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long submitId, BigDecimal score, String feedback, String empId) {
        requireCurrentUser(empId);
        requireBranchReviewer();
        ReSubmit existing = reSubmitMapper.selectById(submitId);
        if (existing == null) {
            throw new BizException("RE-40003", "提交记录不存在");
        }
        requireSecretaryForBranch(existing.getOrgId(), empId);
        rejectSelfReview(existing, empId);

        if (score != null && score.compareTo(BigDecimal.ZERO) > 0) {
            insertScoreWithLimitCheck(existing, score, feedback);
        }

        ReSubmit update = new ReSubmit();
        update.setId(submitId);
        update.setStatus(2);
        update.setReviewFeedback(feedback);
        update.setReviewerId(empId);
        update.setReviewDate(LocalDateTime.now());
        reSubmitMapper.updateById(update);
        log.info("[ReReviewService.approve] submitId={}, score={}, empId={}", submitId, score, empId);
    }

    /**
     * 校验累计得分上限并落库本次评分明细（{@link #approve} 内部私有，仅在 score&gt;0 时调用）。
     * <p>累计求和用 {@code selectList} 取同 org+item_code+score_year 的既有 RE_SCORE 明细后在内存中
     * sum，而非下沉到 SQL 层 {@code SUM()} 聚合——数据量为"单个考核项单个组织单年度"的评分记录条数，
     * 属个位数量级，与源系统 {@code BizReviewServiceImpl} 的内存求和实现方式一致，且符合平台
     * MyBatis-Plus 规范"动态/简单条件查询优先 LambdaQueryWrapper"的要求。</p>
     *
     * @throws BizException code=RE-40004，累计得分超出上限
     */
    private void insertScoreWithLimitCheck(ReSubmit existing, BigDecimal score, String feedback) {
        BigDecimal maxScore = existing.getMaxScore() != null ? existing.getMaxScore() : DEFAULT_MAX_SCORE;
        Long orgId = existing.getOrgId();
        String itemCode = existing.getItemCode();
        int currentYear = LocalDate.now().getYear();

        List<ReScore> existingScores = reScoreMapper.selectList(
                new LambdaQueryWrapper<ReScore>()
                        .eq(ReScore::getOrgId, orgId)
                        .eq(itemCode != null, ReScore::getItemCode, itemCode)
                        .eq(ReScore::getScoreYear, currentYear));

        BigDecimal existingSum = BigDecimal.ZERO;
        for (ReScore s : existingScores) {
            if (s.getFinalScore() != null) {
                existingSum = existingSum.add(s.getFinalScore());
            }
        }

        if (existingSum.add(score).compareTo(maxScore) > 0) {
            BigDecimal allowedScore = maxScore.subtract(existingSum);
            throw new BizException("RE-40004", "考核项[" + itemCode + "]累计得分已达" + existingSum + "分，"
                    + "上限" + maxScore + "分，本次最多可计" + allowedScore + "分");
        }

        ReScore reScore = new ReScore();
        reScore.setOrgId(orgId);
        reScore.setSubmitId(existing.getId());
        reScore.setItemCode(itemCode);
        reScore.setScoreYear(currentYear);
        reScore.setFinalScore(score);
        reScore.setRemark(feedback);
        reScoreMapper.insert(reScore);
    }

    /**
     * 审核驳回。
     * <p>先读 RE_SUBMIT 做创建人与审核人二次校验；命中自审时以 RE-40008 fail-close，且不更新实体。
     * 不存在记录仍保留源系统契约：继续执行按 id 更新并静默影响 0 行，不新增不存在异常。
     * 非自审时也不增加 status 前置条件，直接推进为 3(已驳回)，回填审核意见(reviewFeedback)/
     * 审核人(reviewerId)/审核时间(reviewDate=now)。</p>
     *
     * @param submitId 上报ID
     * @param feedback 驳回原因
     * @param empId    当前登录审核人平台工号（源系统未记录，本次补齐，见类注释）
     * @throws BizException code=RE-40008，审核人与上报创建人相同
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long submitId, String feedback, String empId) {
        requireCurrentUser(empId);
        requireBranchReviewer();
        ReSubmit existing = reSubmitMapper.selectById(submitId);
        if (existing != null) {
            requireSecretaryForBranch(existing.getOrgId(), empId);
        }
        rejectSelfReview(existing, empId);

        ReSubmit update = new ReSubmit();
        update.setId(submitId);
        update.setStatus(3);
        update.setReviewFeedback(feedback);
        update.setReviewerId(empId);
        update.setReviewDate(LocalDateTime.now());
        reSubmitMapper.updateById(update);
        log.info("[ReReviewService.reject] submitId={}, empId={}", submitId, empId);
    }

    /**
     * 基于持久化实体而非角色/请求参数执行职责分离校验。
     * <p>多角色采用权限并集后，同一用户可能同时拥有上报和审核资源；RBAC 只能回答“能否调用审核接口”，
     * 不能回答“能否审核这条记录”，因此必须在写入 RE_SCORE/RE_SUBMIT 前按 submitterId 再次拒绝自审。</p>
     */
    private void rejectSelfReview(ReSubmit existing, String empId) {
        if (existing != null && Objects.equals(existing.getSubmitterId(), empId)) {
            throw new BizException(SELF_REVIEW_ERROR_CODE, SELF_REVIEW_ERROR_MESSAGE);
        }
    }

    /** 将旧审核页签归一化；未知值 fail-close，避免把未知状态误当作待审。 */
    private String normalizeReviewTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return DEFAULT_REVIEW_TAB;
        }
        String normalized = tab.trim().toUpperCase(java.util.Locale.ROOT);
        if (DEFAULT_REVIEW_TAB.equals(normalized) || "PASSED".equals(normalized)
                || "REJECTED".equals(normalized) || REVIEWING_TAB.equals(normalized)) {
            return normalized;
        }
        throw new BizException("RE-40020", "审核页签不支持");
    }

    private int statusForTab(String tab) {
        return switch (tab) {
            case DEFAULT_REVIEW_TAB -> 1;
            case "PASSED" -> 2;
            case "REJECTED" -> 3;
            default -> throw new BizException("RE-40020", "审核页签不支持");
        };
    }

    /** 与任务审核服务保持一致：书记按 secretaryId 或用户党组织映射获得实际支部范围。 */
    private Set<Long> secretaryBranchIds(String operatorId) {
        List<RePartyOrg> orgs = partyOrgMapper.selectList(null);
        if (orgs == null) {
            return Set.of();
        }
        Set<Long> mappedBranchIds = mappedPartyOrgIds(operatorId);
        return orgs.stream()
                .filter(this::isBranch)
                .filter(org -> isSystemAdmin()
                        || Objects.equals(normalize(operatorId), normalize(org.getSecretaryId()))
                        || mappedBranchIds.contains(org.getId()))
                .map(RePartyOrg::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 校验旧材料目标组织是当前书记可审核的支部。 */
    private void requireSecretaryForBranch(Long branchId, String operatorId) {
        if (branchId == null || !isBranchReviewer()) {
            throw new BizException("RE-40302", "无支部审核权限");
        }
        RePartyOrg branch = partyOrgMapper.selectById(branchId);
        if (!isBranch(branch)) {
            throw new BizException("RE-40302", "无权审核该党支部材料");
        }
        if (!isSystemAdmin()
                && !Objects.equals(normalize(operatorId), normalize(branch.getSecretaryId()))
                && !mappedPartyOrgIds(operatorId).contains(branchId)) {
            throw new BizException("RE-40302", "无权审核该党支部材料");
        }
    }

    private Set<Long> mappedPartyOrgIds(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) {
            return Set.of();
        }
        List<ReUserPartyMap> mappings = userPartyMapMapper.selectList(
                new LambdaQueryWrapper<ReUserPartyMap>().eq(ReUserPartyMap::getUserId, operatorId.trim()));
        if (mappings == null) {
            return Set.of();
        }
        return mappings.stream()
                .filter(Objects::nonNull)
                .map(ReUserPartyMap::getPartyOrgId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void requireBranchReviewer() {
        if (!isSystemAdmin() && !hasRole(BRANCH_SECRETARY_ROLE)) {
            throw new BizException("RE-40302", "无权执行该审核操作");
        }
    }

    private boolean isBranchReviewer() {
        return isSystemAdmin() || hasRole(BRANCH_SECRETARY_ROLE);
    }

    private boolean isSystemAdmin() {
        return currentUserApi.isSystemAdmin() || hasRole(SYSTEM_ADMIN_ROLE);
    }

    private boolean hasRole(String roleCode) {
        Set<String> roles = currentUserApi.getCurrentRoleCodes();
        return roles != null && roles.stream().filter(Objects::nonNull)
                .map(String::trim).anyMatch(roleCode::equalsIgnoreCase);
    }

    private void requireCurrentUser(String operatorId) {
        String current = currentUserApi.getCurrentEmpId();
        if (operatorId == null || operatorId.isBlank() || current == null || current.isBlank()
                || !operatorId.trim().equals(current.trim())) {
            throw new BizException("RE-40301", "当前用户与操作人不匹配");
        }
    }

    private boolean isBranch(RePartyOrg org) {
        return org != null && org.getId() != null && Integer.valueOf(2).equals(org.getOrgLevel());
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
