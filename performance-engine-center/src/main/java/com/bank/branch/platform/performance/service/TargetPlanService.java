package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.dto.TargetPlanDTO;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.facade.assembler.TargetAssembler;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.service.cmd.CreateTargetPlanCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateTargetPlanCmd;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 目标方案服务.
 *
 * <p>职责:
 * <ul>
 *   <li>目标方案 CRUD (单表, 无子表聚合; 目标值由 TargetValueService 独立管理)</li>
 *   <li>创建时校验 effectiveDate 非空; 校验 kpiSchemeId 引用的 KPI 方案为 ACTIVE</li>
 *   <li>方案禁用 (高危, reason 必填)</li>
 *   <li>只读查询 (按 id / 按 code / 分页)</li>
 * </ul>
 *
 * <p>事务策略: 写方法 @Transactional(rollbackFor=Exception.class).
 * 所有缓存 evict 通过 TransactionSynchronizationManager 的 afterCommit 回调触发,
 * 避免事务回滚时缓存已清但 DB 未变的一致性空洞.
 *
 * <p>expire_date 歧义说明: 当前 DDL 只有 effective_date, 没有 expire_date.
 * plan 文档 L1366-1367 钦定的 effectiveAfterExpire / effectiveEqualsExpire
 * 场景退化为 effectiveDate 非空校验; expire_date 列入 V1.1 DDL follow-up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TargetPlanService {

    /** 方案激活状态 (对齐 DDL 仅 ACTIVE/DISABLED). */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 方案禁用状态. */
    private static final String STATUS_DISABLED = "DISABLED";

    /** KPI 方案激活状态 (引用校验阈值, 对齐 KpiSchemeService 语义). */
    private static final String KPI_STATUS_ACTIVE = "ACTIVE";

    /** TargetApiImpl 的 @Cacheable 缓存名, 写方法 afterCommit 定向 evict. */
    private static final String TARGET_PLAN_CACHE = "perf:target_plan";

    private final PerfTargetPlanMapper targetPlanMapper;
    private final KpiSchemeService kpiSchemeService;
    private final CacheManager cacheManager;
    /** V1.3 R1.2 新增：当前用户读取（数据范围注入路径使用）. */
    private final CurrentUserApi currentUserApi;
    /** V1.3 R1.2 新增：数据范围 SQL 片段生成器. */
    private final PerfScopeHelper perfScopeHelper;

    /**
     * 新建目标方案.
     *
     * <p>流程:
     * 1. 校验 effectiveDate 非空 (PARAM_INVALID)
     * 2. 校验 planCode UK 不重复 (TARGET_PLAN_CODE_DUP); UK 预校验先行以提前拒绝
     * 3. 校验 kpiSchemeId 对应方案存在且 status=ACTIVE (TARGET_PLAN_KPI_SCHEME_INVALID)
     * 4. 构造实体 (status=ACTIVE) 并 INSERT
     * 5. 注册 afterCommit 缓存 evict
     *
     * @param cmd 新建命令
     * @return 新建的方案 (status=ACTIVE)
     */
    @Transactional(rollbackFor = Exception.class)
    public PerfTargetPlan create(CreateTargetPlanCmd cmd) {
        if (cmd.getEffectiveDate() == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "effectiveDate 必填");
        }
        // planCode UK 预校验先行, 提前拒绝避免后续 kpiScheme 查询浪费;
        // 预校验与 insert 之间的并发窗口由 DuplicateKeyException 兜底.
        // V1.1 P8.1 修正：目标方案编码重复不再复用 METRIC_CODE_DUP（"指标编码已存在"），
        // 改用语义准确的 TARGET_PLAN_CODE_EXISTS（PERF-40006）
        if (targetPlanMapper.selectByPlanCode(cmd.getPlanCode()) != null) {
            throw new PerfException(PerfErrorCode.TARGET_PLAN_CODE_EXISTS, cmd.getPlanCode());
        }

        // 引用校验: KPI 方案必须 ACTIVE (发布态), DRAFT/DISABLED 均拒绝
        Optional<PerfKpiScheme> schemeOpt = kpiSchemeService.getByIdOrNull(cmd.getKpiSchemeId());
        if (schemeOpt.isEmpty() || !KPI_STATUS_ACTIVE.equals(schemeOpt.get().getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, cmd.getKpiSchemeId());
        }

        PerfTargetPlan plan = new PerfTargetPlan();
        plan.setId(generateId());
        plan.setPlanCode(cmd.getPlanCode());
        plan.setPlanName(cmd.getPlanName());
        plan.setKpiSchemeId(cmd.getKpiSchemeId());
        plan.setTargetDim(cmd.getTargetDim());
        plan.setTargetCycle(cmd.getTargetCycle());
        plan.setEffectiveDate(cmd.getEffectiveDate());
        plan.setStatus(STATUS_ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        plan.setCreatedBy(cmd.getOperator());
        plan.setCreatedTime(now);
        plan.setUpdatedBy(cmd.getOperator());
        plan.setUpdatedTime(now);

        try {
            targetPlanMapper.insert(plan);
        } catch (DuplicateKeyException ex) {
            // V1.1 P8.1 语义对齐：并发 UK 兜底同步改为 TARGET_PLAN_CODE_EXISTS
            throw new PerfException(PerfErrorCode.TARGET_PLAN_CODE_EXISTS, ex, cmd.getPlanCode());
        }
        log.info("[TargetPlanService.create] 新建目标方案 planCode={}, id={}, kpiSchemeId={}",
                plan.getPlanCode(), plan.getId(), plan.getKpiSchemeId());
        registerAfterCommitEvict(plan.getId());
        return plan;
    }

    /**
     * 按主键选择性更新目标方案.
     *
     * <p>方案编码 (plan_code) 作为 UK 不允许修改; kpiSchemeId 为安全起见也不支持 patch
     * (若需切换应走"禁用旧方案+新建方案"组合操作, 保持审计清晰).
     *
     * @param id  方案主键
     * @param cmd 更新命令
     * @return 更新后的方案 (merge 视图)
     */
    @Transactional(rollbackFor = Exception.class)
    public PerfTargetPlan updateById(String id, UpdateTargetPlanCmd cmd) {
        PerfTargetPlan existing = targetPlanMapper.selectById(id);
        if (existing == null) {
            throw new PerfException(PerfErrorCode.TARGET_PLAN_NOT_FOUND, id);
        }
        PerfTargetPlan patch = new PerfTargetPlan();
        patch.setId(id);
        patch.setPlanName(cmd.getPlanName());
        patch.setTargetDim(cmd.getTargetDim());
        patch.setTargetCycle(cmd.getTargetCycle());
        patch.setEffectiveDate(cmd.getEffectiveDate());
        patch.setUpdatedBy(cmd.getOperator());
        targetPlanMapper.updateByIdSelective(patch);

        if (cmd.getPlanName() != null) {
            existing.setPlanName(cmd.getPlanName());
        }
        if (cmd.getTargetDim() != null) {
            existing.setTargetDim(cmd.getTargetDim());
        }
        if (cmd.getTargetCycle() != null) {
            existing.setTargetCycle(cmd.getTargetCycle());
        }
        if (cmd.getEffectiveDate() != null) {
            existing.setEffectiveDate(cmd.getEffectiveDate());
        }
        existing.setUpdatedBy(cmd.getOperator());
        existing.setUpdatedTime(LocalDateTime.now());
        registerAfterCommitEvict(id);
        return existing;
    }

    /**
     * 禁用目标方案 (高危, reason 必填).
     *
     * <p>将状态流转为 DISABLED. 审计由 Controller 层
     * {@code @AuditLog(reasonRequired=true)} 保证; Service 层兜底校验 reason 非空.
     *
     * @param id       方案主键
     * @param reason   禁用原因 (不可空白)
     * @param operator 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void disable(String id, String reason, String operator) {
        if (reason == null || reason.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "reason 必填");
        }
        PerfTargetPlan existing = targetPlanMapper.selectById(id);
        if (existing == null) {
            throw new PerfException(PerfErrorCode.TARGET_PLAN_NOT_FOUND, id);
        }
        if (STATUS_DISABLED.equals(existing.getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "目标方案已禁用: " + id);
        }
        log.info("[TargetPlanService.disable] id={}, planCode={}, operator={}, reason={}",
                id, existing.getPlanCode(), operator, reason);
        targetPlanMapper.updateStatusById(id, STATUS_DISABLED, operator);
        registerAfterCommitEvict(id);
    }

    /**
     * 按主键查询, 不存在抛 TARGET_PLAN_NOT_FOUND.
     *
     * @param id 主键
     * @return 方案实体
     */
    @Transactional(readOnly = true)
    public PerfTargetPlan getById(String id) {
        PerfTargetPlan plan = targetPlanMapper.selectById(id);
        if (plan == null) {
            throw new PerfException(PerfErrorCode.TARGET_PLAN_NOT_FOUND, id);
        }
        return plan;
    }

    /**
     * 按主键查询, 不存在返回 Optional.empty (供 Task 3.3 TargetApiImpl 的 @Cacheable 消费).
     *
     * @param id 主键
     * @return Optional 包装的方案
     */
    @Transactional(readOnly = true)
    public Optional<PerfTargetPlan> getByIdOrNull(String id) {
        return Optional.ofNullable(targetPlanMapper.selectById(id));
    }

    /**
     * 按方案编码查询, 不存在返回 Optional.empty (plan L1369 DoD).
     *
     * @param planCode 方案编码
     * @return Optional 包装的方案
     */
    @Transactional(readOnly = true)
    public Optional<PerfTargetPlan> getByCodeOrNull(String planCode) {
        return Optional.ofNullable(targetPlanMapper.selectByPlanCode(planCode));
    }

    /**
     * 条件分页查询.
     *
     * @param kpiSchemeId 关联 KPI 方案ID
     * @param status      状态
     * @param keyword     关键字 (编码/名称模糊)
     * @param pageNo      页码 (从 1 起)
     * @param pageSize    页大小
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<PerfTargetPlan> page(String kpiSchemeId, String status, String keyword,
                                           int pageNo, int pageSize) {
        long total = targetPlanMapper.countByCondition(kpiSchemeId, status, keyword);
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }
        int offset = Math.max(pageNo - 1, 0) * pageSize;
        List<PerfTargetPlan> records = targetPlanMapper.selectByCondition(
                kpiSchemeId, status, keyword, offset, pageSize);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * V1.3 R1.2 新增：基于 {@link PerfScopeHelper} 的数据范围注入分页查询.
     *
     * <p>场景:
     * <ul>
     *   <li>普通绩效配置员 SELF_CREATED → 只看到自己创建的目标方案</li>
     *   <li>绩效查询员 ALL → 可见全部方案（scope=ALL 无片段）</li>
     *   <li>无权限 / fail-close → 返回 total=0 的空页</li>
     * </ul>
     *
     * <p>ScopeColumns 映射（perf_target_plan 业务列仅 created_by 可用）:
     * <ul>
     *   <li>ownerEmpCol   = "created_by" (SELF 降级到创建人, 该表无独立 emp_id / owner 列)</li>
     *   <li>assigneeCol   = "created_by"</li>
     *   <li>createdByCol  = "created_by" (SELF_CREATED 语义准确)</li>
     *   <li>ownerOrgCol   = "created_by" (perf_target_plan 无 org_code, ORG scope 降级)</li>
     * </ul>
     *
     * <p>与既有 {@link #page} 的差异：page 不带数据范围（向后兼容老调用方）,
     * pageWithScope 内部注入数据范围片段。V1.3 R1.3 的 Controller 改造会切到本方法。
     *
     * <p>V1.3 规划：可在 V1.4 考虑扩展 perf_target_plan 增加 owner_emp_id / owner_org_code
     * 字段, 届时 SELF / ORG 不再降级到 created_by。
     *
     * @param kpiSchemeId 关联 KPI 方案ID (可空)
     * @param status      状态 (可空)
     * @param keyword     关键字 (编码/名称模糊, 可空)
     * @param pageNo      页码 (从 1 起)
     * @param pageSize    页大小
     * @return 经过数据范围过滤的分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<PerfTargetPlan> pageWithScope(String kpiSchemeId, String status, String keyword,
                                                    int pageNo, int pageSize) {
        String currentEmpId = currentUserApi.getCurrentEmpId();
        PerfScopeHelper.ScopeColumns columns = new PerfScopeHelper.ScopeColumns(
                "created_by",   // ownerEmpCol (SELF)
                "created_by",   // assigneeCol
                "created_by",   // createdByCol (SELF_CREATED)
                "created_by"    // ownerOrgCol (无 org_code, 降级)
        );
        PerfScopeHelper.Fragment frag = perfScopeHelper.getFragment(
                currentEmpId, BizType.PERF_CONFIG, BizAction.LIST, columns);

        int offset = Math.max(pageNo - 1, 0) * pageSize;
        long total = targetPlanMapper.countByConditionWithScope(
                kpiSchemeId, status, keyword, frag.getSql(), frag.getParams());
        List<PerfTargetPlan> records = targetPlanMapper.selectByConditionWithScope(
                kpiSchemeId, status, keyword, offset, pageSize,
                frag.getSql(), frag.getParams());
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本分页查询（带数据范围注入）.
     *
     * <p>内部委托 {@link #pageWithScope}，再通过 {@link TargetAssembler#toDto}
     * 装配，使 Controller 不再感知 entity。
     */
    @Transactional(readOnly = true)
    public PageResult<TargetPlanDTO> pageWithScopeDto(String kpiSchemeId, String status, String keyword,
                                                      int pageNo, int pageSize) {
        PageResult<PerfTargetPlan> raw = pageWithScope(kpiSchemeId, status, keyword, pageNo, pageSize);
        List<TargetPlanDTO> dtos = new java.util.ArrayList<>(raw.getRecords().size());
        for (PerfTargetPlan plan : raw.getRecords()) {
            dtos.add(TargetAssembler.toDto(plan));
        }
        return PageResult.of(raw.getPageNo(), raw.getPageSize(), raw.getTotal(), dtos);
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本详情查询（不存在抛 TARGET_PLAN_NOT_FOUND）.
     */
    @Transactional(readOnly = true)
    public TargetPlanDTO getByIdDto(String id) {
        return TargetAssembler.toDto(getById(id));
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本创建，内部调用 {@link #create}.
     */
    @Transactional(rollbackFor = Exception.class)
    public TargetPlanDTO createDto(CreateTargetPlanCmd cmd) {
        return TargetAssembler.toDto(create(cmd));
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本更新.
     */
    @Transactional(rollbackFor = Exception.class)
    public TargetPlanDTO updateByIdDto(String id, UpdateTargetPlanCmd cmd) {
        return TargetAssembler.toDto(updateById(id, cmd));
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 注册事务提交后的缓存 evict 回调 (复用 KpiSchemeService 的同名模式).
     *
     * <p>为什么 afterCommit 而非事务内 evict: 事务内 evict 若后续回滚, 缓存会出现
     * "已清空但 DB 未变" 的空洞, 下次读穿透到 DB 读回旧值再填回缓存, 违反一致性.
     * afterCommit 保证只在提交成功后执行, 此时 DB 已写入最新值.
     *
     * <p>无活动事务 (测试 / 非 @Transactional 调用方) 时降级为立即 evict, 避免丢失清理.
     *
     * @param planId 目标方案主键
     */
    private void registerAfterCommitEvict(String planId) {
        Cache cache = cacheManager.getCache(TARGET_PLAN_CACHE);
        if (cache == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.evict(planId);
                }
            });
        } else {
            log.warn("[TargetPlanService.registerAfterCommitEvict] 无活动事务, 立即 evict 缓存; id={}. "
                    + "此为反模式, 调用方应在 @Transactional 上下文中触发写操作.", planId);
            cache.evict(planId);
        }
    }
}
