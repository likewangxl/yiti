package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.dto.KpiItemDTO;
import com.bank.branch.platform.performance.api.dto.KpiSchemeDTO;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.facade.assembler.KpiAssembler;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.service.cmd.AddKpiItemCmd;
import com.bank.branch.platform.performance.service.cmd.CreateKpiSchemeCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateKpiSchemeCmd;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * KPI 方案服务.
 *
 * <p>职责:
 * <ul>
 *   <li>方案 + 方案项的聚合写入 (父子表单事务)</li>
 *   <li>发布校验: 一次批量读取所有 item 的 metricCode, 通过 {@link MetricDefService#getByCodes}
 *       校验 metric 存在且 status=ACTIVE, 任一不符抛 {@link PerfErrorCode#KPI_PUBLISH_METRIC_INVALID}</li>
 *   <li>方案禁用 (高危, reason 必填)</li>
 *   <li>只读查询 (按 id / 分页)</li>
 * </ul>
 *
 * <p>**事务策略**: 写方法统一 {@code @Transactional(rollbackFor=Exception.class)}.
 * {@link #create} 内对 {@code kpiItemService.addItem} 的调用依赖 Spring REQUIRED
 * 传播, 共享同一事务; 任一 item 失败, 整个方案连同已插入的 item 一起回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiSchemeService {

    /** 方案初始状态: DRAFT (与 DDL status 取值一致). */
    private static final String STATUS_DRAFT = "DRAFT";
    /** 方案已发布状态. */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 方案已禁用状态. */
    private static final String STATUS_DISABLED = "DISABLED";

    /** KpiApiImpl 的 {@code @Cacheable} 缓存名, 写方法 afterCommit 时定向 evict. */
    private static final String KPI_SCHEME_CACHE = "perf:kpi_scheme";

    private final PerfKpiSchemeMapper schemeMapper;
    private final KpiItemService kpiItemService;
    private final MetricDefService metricDefService;
    private final CacheManager cacheManager;

    /**
     * 新建方案 + 方案项 (单事务).
     *
     * <p>流程:
     * <ol>
     *   <li>预校验 schemeCode 不重复 (UK + 业务层双保险)</li>
     *   <li>构造并 INSERT scheme (status=DRAFT)</li>
     *   <li>遍历 items 调 {@link KpiItemService#addItem}, 每项的 schemeId 被覆写为本方案 id</li>
     * </ol>
     * <p>任一步骤异常都会使整个事务回滚 (依赖 {@code @Transactional(rollbackFor=Exception.class)}).
     *
     * @param cmd 新建命令
     * @return 新建的方案 (status=DRAFT)
     */
    @Transactional(rollbackFor = Exception.class)
    public PerfKpiScheme create(CreateKpiSchemeCmd cmd) {
        // schemeCode UK 预校验 (提前抛业务异常, 避免 DB 层语义含糊的 DuplicateKey 冒泡)
        // V1.1 P8.1 修正：KPI 方案编码重复不再复用 METRIC_CODE_DUP（"指标编码已存在"），
        // 改用语义准确的 KPI_SCHEME_CODE_EXISTS（PERF-40005）
        if (schemeMapper.selectBySchemeCode(cmd.getSchemeCode()) != null) {
            throw new PerfException(PerfErrorCode.KPI_SCHEME_CODE_EXISTS, cmd.getSchemeCode());
        }

        PerfKpiScheme scheme = new PerfKpiScheme();
        scheme.setId(generateId());
        scheme.setSchemeCode(cmd.getSchemeCode());
        scheme.setSchemeName(cmd.getSchemeName());
        scheme.setCycleType(cmd.getCycleType());
        scheme.setOpenDetail(cmd.getOpenDetail() != null ? cmd.getOpenDetail() : 0);
        scheme.setStatus(STATUS_DRAFT);
        LocalDateTime now = LocalDateTime.now();
        scheme.setCreatedBy(cmd.getOperator());
        scheme.setCreatedTime(now);
        scheme.setUpdatedBy(cmd.getOperator());
        scheme.setUpdatedTime(now);

        try {
            schemeMapper.insert(scheme);
        } catch (DuplicateKeyException ex) {
            // 并发场景 UK 兜底 (预校验与 insert 之间有其他事务抢先插入)
            // V1.1 P8.1 语义对齐：同 schemeCode 预校验抛 KPI_SCHEME_CODE_EXISTS
            throw new PerfException(PerfErrorCode.KPI_SCHEME_CODE_EXISTS, ex, cmd.getSchemeCode());
        }

        // 遍历写入方案项: addItem 在同一事务内运行 (REQUIRED 传播);
        // 任一项抛异常, 父事务整体回滚, scheme 也会被撤销。
        List<AddKpiItemCmd> items = cmd.getItems() == null ? Collections.emptyList() : cmd.getItems();
        for (AddKpiItemCmd itemCmd : items) {
            itemCmd.setSchemeId(scheme.getId());
            if (itemCmd.getOperator() == null) {
                itemCmd.setOperator(cmd.getOperator());
            }
            kpiItemService.addItem(itemCmd);
        }
        log.info("[KpiSchemeService.create] 新建方案 schemeCode={}, id={}, itemCount={}",
                scheme.getSchemeCode(), scheme.getId(), items.size());
        return scheme;
    }

    /**
     * 按主键选择性更新方案.
     *
     * <p>方案编码 (scheme_code) 作为 UK 不允许修改, 不在 cmd 内; 只 patch 基础信息。
     *
     * @param id  方案主键
     * @param cmd 更新命令
     * @return 更新后的方案 (存量实体 merge 视图)
     */
    @Transactional(rollbackFor = Exception.class)
    public PerfKpiScheme updateById(String id, UpdateKpiSchemeCmd cmd) {
        PerfKpiScheme existing = schemeMapper.selectById(id);
        if (existing == null) {
            throw new PerfException(PerfErrorCode.KPI_SCHEME_NOT_FOUND, id);
        }
        PerfKpiScheme patch = new PerfKpiScheme();
        patch.setId(id);
        patch.setSchemeName(cmd.getSchemeName());
        patch.setCycleType(cmd.getCycleType());
        patch.setOpenDetail(cmd.getOpenDetail());
        patch.setUpdatedBy(cmd.getOperator());
        schemeMapper.updateByIdSelective(patch);

        if (cmd.getSchemeName() != null) {
            existing.setSchemeName(cmd.getSchemeName());
        }
        if (cmd.getCycleType() != null) {
            existing.setCycleType(cmd.getCycleType());
        }
        if (cmd.getOpenDetail() != null) {
            existing.setOpenDetail(cmd.getOpenDetail());
        }
        existing.setUpdatedBy(cmd.getOperator());
        existing.setUpdatedTime(LocalDateTime.now());
        // 事务外 evict 缓存: 避免读到旧值, 且避免事务内 evict 后因事务回滚造成缓存空洞
        registerAfterCommitEvict(id);
        return existing;
    }

    /**
     * 禁用方案 (高危, reason 必填).
     *
     * <p>将状态流转为 DISABLED。审计由 Controller 层
     * {@code @AuditLog(reasonRequired=true)} 保证; 本 Service 层只做 reason 非空兜底。
     *
     * @param id       方案主键
     * @param reason   禁用原因 (不可空白)
     * @param operator 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void disable(String id, String reason, String operator) {
        if (reason == null || reason.isBlank()) {
            // 高危操作 reason 必填 - 这里做兜底, 正常应由 Controller DTO 层 @NotBlank 拦截
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "reason 必填");
        }
        PerfKpiScheme existing = schemeMapper.selectById(id);
        if (existing == null) {
            throw new PerfException(PerfErrorCode.KPI_SCHEME_NOT_FOUND, id);
        }
        if (STATUS_DISABLED.equals(existing.getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "方案已禁用: " + id);
        }
        log.info("[KpiSchemeService.disable] id={}, schemeCode={}, operator={}, reason={}",
                id, existing.getSchemeCode(), operator, reason);
        schemeMapper.updateStatusById(id, STATUS_DISABLED, operator);
        registerAfterCommitEvict(id);
    }

    /**
     * 发布方案: DRAFT/ACTIVE → ACTIVE.
     *
     * <p>**核心校验** (为什么): 方案发布即暴露给计算引擎, 必须保证所有引用指标都处于
     * ACTIVE 状态。考虑并发场景 (A 发布方案 B 同时禁用 metric), 发布动作每次都需
     * 重新校验, 不能依赖"添加 item 时校验过"的历史快照。
     *
     * <p>任一 item 的 metric 不存在 / 状态非 ACTIVE 均抛
     * {@link PerfErrorCode#KPI_PUBLISH_METRIC_INVALID}, 不允许部分发布。
     *
     * @param id       方案主键
     * @param operator 操作人
     * @return 发布后的方案 (status=ACTIVE)
     */
    @Transactional(rollbackFor = Exception.class)
    public PerfKpiScheme publish(String id, String operator) {
        PerfKpiScheme scheme = schemeMapper.selectById(id);
        if (scheme == null) {
            throw new PerfException(PerfErrorCode.KPI_SCHEME_NOT_FOUND, id);
        }
        if (STATUS_DISABLED.equals(scheme.getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "已禁用方案不可发布: " + id);
        }

        List<PerfKpiItem> items = kpiItemService.listBySchemeId(id);
        // 空方案也允许发布 (方案结构定型后再补 item 的使用场景),
        // 若业务要求必须 >= 1 item, 在 Controller DTO 层加校验更灵活。
        // 批量读取所有引用的 metric (N+1 优化): 100 个 item 由 100 次查询降为 1 次。
        List<String> metricCodes = items.stream()
                .map(PerfKpiItem::getMetricCode)
                .distinct()
                .collect(Collectors.toList());
        Map<String, PerfMetricDef> defMap = metricDefService.getByCodes(metricCodes).stream()
                .collect(Collectors.toMap(PerfMetricDef::getMetricCode, Function.identity()));
        for (PerfKpiItem item : items) {
            PerfMetricDef metricDef = defMap.get(item.getMetricCode());
            if (metricDef == null || !STATUS_ACTIVE.equals(metricDef.getStatus())) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, item.getMetricCode());
            }
        }

        schemeMapper.updateStatusById(id, STATUS_ACTIVE, operator);
        scheme.setStatus(STATUS_ACTIVE);
        scheme.setUpdatedBy(operator);
        scheme.setUpdatedTime(LocalDateTime.now());
        log.info("[KpiSchemeService.publish] 方案发布成功 id={}, schemeCode={}, itemCount={}, operator={}",
                id, scheme.getSchemeCode(), items.size(), operator);
        registerAfterCommitEvict(id);
        return scheme;
    }

    /**
     * 按主键查询, 不存在抛 {@link PerfErrorCode#KPI_SCHEME_NOT_FOUND}.
     *
     * @param id 主键
     * @return 方案实体
     */
    @Transactional(readOnly = true)
    public PerfKpiScheme getById(String id) {
        PerfKpiScheme scheme = schemeMapper.selectById(id);
        if (scheme == null) {
            throw new PerfException(PerfErrorCode.KPI_SCHEME_NOT_FOUND, id);
        }
        return scheme;
    }

    /**
     * 按主键查询, 不存在返回 {@link Optional#empty()} (供 Task 3 TargetPlan 引用校验).
     *
     * @param id 主键
     * @return Optional 包装的方案
     */
    @Transactional(readOnly = true)
    public Optional<PerfKpiScheme> getByIdOrNull(String id) {
        return Optional.ofNullable(schemeMapper.selectById(id));
    }

    /**
     * 按方案编码查询, 不存在返回 {@link Optional#empty()} (供 {@code KpiApi.getKpiScheme} 消费).
     *
     * @param schemeCode 方案编码
     * @return Optional 包装的方案
     */
    @Transactional(readOnly = true)
    public Optional<PerfKpiScheme> getBySchemeCodeOrNull(String schemeCode) {
        return Optional.ofNullable(schemeMapper.selectBySchemeCode(schemeCode));
    }

    /**
     * 列出所有 ACTIVE 状态方案（V1.7 已删除 DailyKpiCalcJob，本方法保留供事件驱动 KPI 重算使用）.
     *
     * <p>没有分页，基于方案通常 &lt; 100 的业务规模。若 V1.2 方案数量爆炸，
     * 可改为 {@link #page(String, String, String, int, int)} 分批驱动。
     *
     * @return ACTIVE 方案列表（可能为空）
     */
    @Transactional(readOnly = true)
    public List<PerfKpiScheme> listActiveSchemes() {
        return schemeMapper.selectByCondition(null, STATUS_ACTIVE, null, 0, 1000);
    }

    /**
     * 条件分页查询.
     *
     * @param cycleType 周期类型
     * @param status    状态
     * @param keyword   关键字 (编码/名称模糊)
     * @param pageNo    页码 (从 1 起)
     * @param pageSize  页大小
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<PerfKpiScheme> page(String cycleType, String status, String keyword, int pageNo, int pageSize) {
        int offset = Math.max(pageNo - 1, 0) * pageSize;
        long total = schemeMapper.countByCondition(cycleType, status, keyword);
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }
        List<PerfKpiScheme> records = schemeMapper.selectByCondition(cycleType, status, keyword, offset, pageSize);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本分页查询。列表视图不携带 items，
     * 与既有 Controller.list 行为一致（减少数据库压力）.
     */
    @Transactional(readOnly = true)
    public PageResult<KpiSchemeDTO> pageDto(String cycleType, String status, String keyword,
                                            int pageNo, int pageSize) {
        PageResult<PerfKpiScheme> raw = page(cycleType, status, keyword, pageNo, pageSize);
        List<KpiSchemeDTO> dtos = new java.util.ArrayList<>(raw.getRecords().size());
        for (PerfKpiScheme scheme : raw.getRecords()) {
            dtos.add(KpiAssembler.toDto(scheme, List.of()));
        }
        return PageResult.of(raw.getPageNo(), raw.getPageSize(), raw.getTotal(), dtos);
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本详情查询（含 items）.
     *
     * <p>不存在抛 {@link PerfErrorCode#KPI_SCHEME_NOT_FOUND}.
     */
    @Transactional(readOnly = true)
    public KpiSchemeDTO getByIdDto(String id) {
        PerfKpiScheme scheme = getById(id);
        List<PerfKpiItem> items = kpiItemService.listBySchemeId(id);
        return KpiAssembler.toDto(scheme, items);
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本创建（含 items 聚合）.
     */
    @Transactional(rollbackFor = Exception.class)
    public KpiSchemeDTO createDto(CreateKpiSchemeCmd cmd) {
        PerfKpiScheme scheme = create(cmd);
        List<PerfKpiItem> items = kpiItemService.listBySchemeId(scheme.getId());
        return KpiAssembler.toDto(scheme, items);
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本更新（含 items）.
     */
    @Transactional(rollbackFor = Exception.class)
    public KpiSchemeDTO updateByIdDto(String id, UpdateKpiSchemeCmd cmd) {
        PerfKpiScheme scheme = updateById(id, cmd);
        List<PerfKpiItem> items = kpiItemService.listBySchemeId(id);
        return KpiAssembler.toDto(scheme, items);
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本发布（含 items）.
     */
    @Transactional(rollbackFor = Exception.class)
    public KpiSchemeDTO publishDto(String id, String operator) {
        PerfKpiScheme scheme = publish(id, operator);
        List<PerfKpiItem> items = kpiItemService.listBySchemeId(id);
        return KpiAssembler.toDto(scheme, items);
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本 item 查询（补足 KpiItemDTO 列表消费者）.
     */
    @Transactional(readOnly = true)
    public List<KpiItemDTO> listItemsDto(String schemeId) {
        List<PerfKpiItem> items = kpiItemService.listBySchemeId(schemeId);
        List<KpiItemDTO> dtos = new java.util.ArrayList<>(items.size());
        for (PerfKpiItem item : items) {
            dtos.add(KpiAssembler.toItemDto(item));
        }
        return dtos;
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 注册事务提交后的缓存 evict 回调.
     *
     * <p>为什么 afterCommit 而非事务内 evict: 事务内 evict 若后续事务回滚, 缓存会出现
     * "已清空但 DB 未变" 的空洞 (下次读穿透到 DB 读回旧值再填回缓存), 违反一致性。
     * afterCommit 保证只在提交成功后执行, 且此时 DB 已写入最新值。
     *
     * <p>无活动事务 (测试 / 非 @Transactional 调用方) 时降级为立即 evict, 以免丢失清理。
     *
     * @param schemeId 方案主键
     */
    private void registerAfterCommitEvict(String schemeId) {
        Cache cache = cacheManager.getCache(KPI_SCHEME_CACHE);
        if (cache == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.evict(schemeId);
                }
            });
        } else {
            // 无事务场景 (理论上写方法都带 @Transactional 不会走到, 这里兜底)
            log.warn("[KpiSchemeService.registerAfterCommitEvict] 无活动事务, 立即 evict 缓存; id={}. "
                    + "此为反模式, 调用方应在 @Transactional 上下文中触发写操作.", schemeId);
            cache.evict(schemeId);
        }
    }
}
