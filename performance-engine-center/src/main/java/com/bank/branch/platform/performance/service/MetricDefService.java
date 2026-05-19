package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.MetricCategoryDTO;
import com.bank.branch.platform.performance.controller.dto.MetricDefRespDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.facade.assembler.MetricAssembler;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateMetricDefCmd;
import com.bank.branch.platform.performance.service.result.BatchUpsertMetricDefResult;
import com.bank.branch.platform.performance.service.result.UpsertMetricDefResult;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 指标定义服务.
 */
@Service
@RequiredArgsConstructor
public class MetricDefService {

    private final PerfMetricDefMapper mapper;
    private final MetricRefService metricRefService;
    private final MetricSlotService metricSlotService;
    private final MetricCycleDetectService metricCycleDetectService;
    private final ObjectMapper objectMapper;
    /** Q7.3 新增: 当前用户读取. */
    private final CurrentUserApi currentUserApi;
    /** Q7.3 新增: 数据范围 SQL 片段生成器. */
    private final PerfScopeHelper perfScopeHelper;

    /**
     * V1.7：调度同步服务.
     * <p>构造器注入（无循环依赖：MetricSchedulerService 现依赖 PerfMetricDefMapper 而非 MetricDefService）.
     * <p>包级可见（非 private）供同包测试直接覆盖 mock.
     */
    final MetricSchedulerService metricSchedulerService;

    /**
     * V1.9：批量创建指标定义（整批 all-or-none 语义）.
     *
     * <p>用于 {@code MetricDefImportStrategy} 的 Excel 导入路径。本方法包裹外层事务，
     * 内部逐条调用 {@link #create(CreateMetricDefCmd)}。由于 {@code create} 默认
     * PROPAGATION_REQUIRED 会复用当前事务，任一行抛异常都会让整个批次回滚，DB 不留脏数据。
     *
     * <p>调用方契约：
     * <ul>
     *   <li>校验失败应在调用本方法前由 {@code MetricDefImportStrategy} 预扫描完成；
     *       本方法仅做"落库 + 复用 create 业务规则（slot 分配 / 循环依赖检测 等）"</li>
     *   <li>{@code cmds} 列表非 null 且 size &gt; 0，否则直接返回空列表</li>
     *   <li>任一 {@link PerfException}（含 {@code METRIC_CODE_DUP}）会让整批回滚并向上抛</li>
     * </ul>
     *
     * @param cmds     批量新建命令列表
     * @param operator 操作人（覆盖 cmd.operator 字段，确保审计一致）
     * @return 新建后的指标定义列表（与 cmds 同序）
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public List<PerfMetricDef> batchCreateMetricDefs(List<CreateMetricDefCmd> cmds, String operator) {
        if (cmds == null || cmds.isEmpty()) {
            return Collections.emptyList();
        }
        List<PerfMetricDef> created = new ArrayList<>(cmds.size());
        for (CreateMetricDefCmd cmd : cmds) {
            // 强制覆盖 operator，保持审计字段统一
            cmd.setOperator(operator);
            created.add(create(cmd));
        }
        return created;
    }

    /**
     * V1.11：按 {@code metric_name} 命中执行 upsert.
     *
     * <p>命中已存在指标（{@code deleted=0}） → 走 update 路径：
     * 仅更新业务字段（metric_desc / metricLevel / calcFreq / calcMode / calcLogicType /
     * sqlText / exprText / summaryRule / metricCategory / status），
     * <b>保留</b> DB 原 {@code id} / {@code metric_code} / {@code val_slot}.
     *
     * <p>未命中 → 走现有 {@link #create(CreateMetricDefCmd)} 路径.
     *
     * <p>注意：本方法 PROPAGATION_REQUIRED 复用调用方事务，
     * 由 {@link #batchUpsertByName} 包裹外层事务实现整批 all-or-none.
     *
     * @param cmd      命令
     * @param operator 操作人（覆盖 cmd.operator）
     * @return upsert 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public UpsertMetricDefResult upsertByName(CreateMetricDefCmd cmd, String operator) {
        cmd.setOperator(operator);
        PerfMetricDef existing = mapper.selectByMetricName(cmd.getMetricName());
        if (existing == null) {
            // 新增路径
            PerfMetricDef def = create(cmd);
            return new UpsertMetricDefResult(true, def);
        }
        // 更新路径：保留 id / metric_code / val_slot
        PerfMetricDef patch = new PerfMetricDef();
        patch.setId(existing.getId());
        patch.setMetricNameEn(cmd.getMetricNameEn());
        patch.setMetricDesc(cmd.getMetricDesc());
        patch.setMetricLevel(cmd.getMetricLevel());
        patch.setCalcFreq(cmd.getCalcFreq());
        patch.setCalcMode(cmd.getCalcMode());
        patch.setCalcLogicType(cmd.getCalcLogicType());
        patch.setSqlText(cmd.getSqlText());
        patch.setExprText(cmd.getExprText());
        patch.setSummaryRule(cmd.getSummaryRule());
        patch.setMetricCategory(cmd.getMetricCategory());
        // V1.11：状态字段透传，导入路径尊重 Excel 意图（cmd.status 可能为 null，不写）
        if (cmd.getStatus() != null && !cmd.getStatus().isBlank()) {
            patch.setStatus(cmd.getStatus());
        }
        patch.setUpdatedBy(operator);
        mapper.updateByIdSelective(patch);

        // 回填 existing 字段以便调用方读取最新视图
        if (cmd.getMetricNameEn() != null) {
            existing.setMetricNameEn(cmd.getMetricNameEn());
        }
        if (cmd.getMetricDesc() != null) {
            existing.setMetricDesc(cmd.getMetricDesc());
        }
        if (cmd.getMetricLevel() != null) {
            existing.setMetricLevel(cmd.getMetricLevel());
        }
        if (cmd.getCalcFreq() != null) {
            existing.setCalcFreq(cmd.getCalcFreq());
        }
        if (cmd.getCalcMode() != null) {
            existing.setCalcMode(cmd.getCalcMode());
        }
        if (cmd.getCalcLogicType() != null) {
            existing.setCalcLogicType(cmd.getCalcLogicType());
        }
        if (cmd.getSqlText() != null) {
            existing.setSqlText(cmd.getSqlText());
        }
        if (cmd.getExprText() != null) {
            existing.setExprText(cmd.getExprText());
        }
        if (cmd.getSummaryRule() != null) {
            existing.setSummaryRule(cmd.getSummaryRule());
        }
        if (cmd.getMetricCategory() != null) {
            existing.setMetricCategory(cmd.getMetricCategory());
        }
        if (cmd.getStatus() != null && !cmd.getStatus().isBlank()) {
            existing.setStatus(cmd.getStatus());
        }
        existing.setUpdatedBy(operator);
        existing.setUpdatedTime(LocalDateTime.now());

        // V1.7：状态变更可能触发调度注销/注册
        registerSchedulerHookIfNeeded(existing, false);
        return new UpsertMetricDefResult(false, existing);
    }

    /**
     * V1.11：批量按 {@code metric_name} upsert（整批事务）.
     *
     * <p>调用方契约：
     * <ul>
     *   <li>{@code cmds} 非 null 且 size &gt; 0，否则返回空结果</li>
     *   <li>任一行抛异常（含 {@link PerfException}）整批回滚</li>
     *   <li>结果与入参同序</li>
     * </ul>
     *
     * @param cmds     命令列表
     * @param operator 操作人
     * @return 批量结果
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchUpsertMetricDefResult batchUpsertByName(List<CreateMetricDefCmd> cmds, String operator) {
        if (cmds == null || cmds.isEmpty()) {
            return new BatchUpsertMetricDefResult(0, 0, Collections.emptyList());
        }
        int inserted = 0;
        int updated = 0;
        List<PerfMetricDef> defs = new ArrayList<>(cmds.size());
        for (CreateMetricDefCmd cmd : cmds) {
            UpsertMetricDefResult r = upsertByName(cmd, operator);
            if (r.isInserted()) {
                inserted++;
            } else {
                updated++;
            }
            defs.add(r.getDef());
        }
        return new BatchUpsertMetricDefResult(inserted, updated, defs);
    }

    /**
     * 新建指标定义.
     *
     * @param cmd 新建命令
     * @return 新建后的指标定义
     */
    @Transactional(rollbackFor = Exception.class)
    public PerfMetricDef create(CreateMetricDefCmd cmd) {
        if (mapper.selectByMetricCode(cmd.getMetricCode()) != null) {
            throw new PerfException(PerfErrorCode.METRIC_CODE_DUP, cmd.getMetricCode());
        }
        // V1.13：metric_name 预检对齐 V1.11 新增的 uk_metric_name_alive 唯一约束，
        // 避免 insert 阶段 DuplicateKeyException 被旧 catch 兜底为 METRIC_CODE_DUP 误导用户。
        if (mapper.selectByMetricName(cmd.getMetricName()) != null) {
            throw new PerfException(PerfErrorCode.METRIC_NAME_DUP, cmd.getMetricName());
        }
        List<String> refMetricCodes = parseRefMetricCodes(cmd.getRefMetricCodes());
        Map<String, Integer> refMetricLevels = loadRefMetricLevels(refMetricCodes);
        metricCycleDetectService.checkLevelConstraint(cmd.getMetricLevel(), refMetricLevels);
        metricCycleDetectService.checkNoCycle(metricRefService.loadFullGraph(), cmd.getMetricCode(), refMetricCodes);

        PerfMetricDef def = new PerfMetricDef();
        def.setId(generateId());
        def.setMetricCode(cmd.getMetricCode());
        def.setMetricName(cmd.getMetricName());
        def.setMetricNameEn(cmd.getMetricNameEn());
        def.setMetricDesc(cmd.getMetricDesc());
        def.setBaseDim(cmd.getBaseDim());
        def.setMetricLevel(cmd.getMetricLevel());
        def.setCalcFreq(cmd.getCalcFreq());
        def.setCalcMode(cmd.getCalcMode());
        def.setCalcLogicType(cmd.getCalcLogicType());
        def.setSqlText(cmd.getSqlText());
        def.setExprText(cmd.getExprText());
        def.setSummaryRule(cmd.getSummaryRule());
        def.setRefMetricCodes(toJson(refMetricCodes));
        // V1.9：状态字段优先取 cmd.status（导入路径透传 Excel statusFlag），未指定回落 ACTIVE
        String finalStatus = cmd.getStatus() != null && !cmd.getStatus().isBlank() ? cmd.getStatus() : "ACTIVE";
        // V1.9：维度无关型指标（baseDim=null）不占 slot、不入三大宽表
        // V1.13：DRAFT 草稿态不占 slot（不进宽表、不调度），DRAFT→ACTIVE 切换由 changeStatus 补分配；
        //        与 V1.9 维度无关型指标同思路：未到真正可执行态前不占用 slot 配额。
        if (cmd.getBaseDim() != null && !cmd.getBaseDim().isBlank()
                && "ACTIVE".equals(finalStatus)) {
            def.setValSlot(metricSlotService.allocSlot(cmd.getBaseDim(), cmd.getMetricLevel(), cmd.getPreferredSlot()));
        } else {
            def.setValSlot(null);
        }
        def.setMetricCategory(cmd.getMetricCategory());
        def.setStatus(finalStatus);
        // Q8.5b: 显式初始化 deleted=0（未删除）。entity 字段为 Integer（非基本类型），
        // 默认 null 会导致 selectByMetricCode（WHERE deleted=0）读不到刚插入的行。
        // DB 层虽然有 default 0，但 Mapper XML 使用 #{deleted} 会把 null 显式写入列，
        // 覆盖 DB default，造成"创建成功但查询不到"的假绿问题。
        def.setDeleted(0);
        def.setCreatedBy(cmd.getOperator());
        def.setCreatedTime(LocalDateTime.now());
        def.setUpdatedBy(cmd.getOperator());
        def.setUpdatedTime(LocalDateTime.now());

        try {
            mapper.insert(def);
        } catch (DuplicateKeyException ex) {
            // V1.13：区分 uk_metric_name_alive（metric_name 重复）与 uk_metric_code（编码重复），
            // 避免并发场景下漏掉预检窗口、错误码错位为"编码已存在"。
            String exMsg = ex.getMessage() == null ? "" : ex.getMessage();
            if (exMsg.contains("uk_metric_name_alive")) {
                throw new PerfException(PerfErrorCode.METRIC_NAME_DUP, ex, cmd.getMetricName());
            }
            throw new PerfException(PerfErrorCode.METRIC_CODE_DUP, ex, cmd.getMetricCode());
        }
        metricRefService.setRefs(cmd.getMetricCode(), refMetricCodes);
        // V1.7：指标创建后同步注册调度任务
        registerSchedulerHookIfNeeded(def, true);
        return def;
    }

    /**
     * 更新指标定义.
     *
     * @param cmd 更新命令
     * @return 更新后的指标定义
     */
    @Transactional(rollbackFor = Exception.class)
    public PerfMetricDef update(UpdateMetricDefCmd cmd) {
        PerfMetricDef existing = getByCode(cmd.getMetricCode());
        // V1.6 修复 Bug3：停用态禁止编辑（前端也已 disable 按钮，此处后端兜底）
        if ("DISABLED".equals(existing.getStatus()) || "INACTIVE".equals(existing.getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "指标已停用，禁止编辑：" + cmd.getMetricCode() + "（请先启用后再修改）");
        }
        boolean refMetricCodesProvided = cmd.getRefMetricCodes() != null && !cmd.getRefMetricCodes().isBlank();
        List<String> refMetricCodes = refMetricCodesProvided
                ? parseRefMetricCodes(cmd.getRefMetricCodes())
                : Collections.emptyList();
        if (refMetricCodesProvided) {
            Map<String, Integer> refMetricLevels = loadRefMetricLevels(refMetricCodes);
            metricCycleDetectService.checkLevelConstraint(existing.getMetricLevel(), refMetricLevels);
            metricCycleDetectService.checkNoCycle(metricRefService.loadFullGraph(), existing.getMetricCode(), refMetricCodes);
        }

        PerfMetricDef patch = new PerfMetricDef();
        patch.setId(existing.getId());
        patch.setMetricName(cmd.getMetricName());
        patch.setMetricNameEn(cmd.getMetricNameEn());
        patch.setMetricDesc(cmd.getMetricDesc());
        patch.setCalcFreq(cmd.getCalcFreq());
        patch.setCalcMode(cmd.getCalcMode());
        patch.setCalcLogicType(cmd.getCalcLogicType());
        patch.setSqlText(cmd.getSqlText());
        patch.setExprText(cmd.getExprText());
        patch.setSummaryRule(cmd.getSummaryRule());
        if (refMetricCodesProvided) {
            patch.setRefMetricCodes(toJson(refMetricCodes));
        }
        patch.setUpdatedBy(cmd.getOperator());
        mapper.updateByIdSelective(patch);
        if (refMetricCodesProvided) {
            metricRefService.setRefs(existing.getMetricCode(), refMetricCodes);
        }

        if (cmd.getMetricName() != null) {
            existing.setMetricName(cmd.getMetricName());
        }
        if (cmd.getMetricNameEn() != null) {
            existing.setMetricNameEn(cmd.getMetricNameEn());
        }
        if (cmd.getMetricDesc() != null) {
            existing.setMetricDesc(cmd.getMetricDesc());
        }
        if (cmd.getCalcFreq() != null) {
            existing.setCalcFreq(cmd.getCalcFreq());
        }
        if (cmd.getCalcMode() != null) {
            existing.setCalcMode(cmd.getCalcMode());
        }
        if (cmd.getCalcLogicType() != null) {
            existing.setCalcLogicType(cmd.getCalcLogicType());
        }
        if (cmd.getSqlText() != null) {
            existing.setSqlText(cmd.getSqlText());
        }
        if (cmd.getExprText() != null) {
            existing.setExprText(cmd.getExprText());
        }
        if (cmd.getSummaryRule() != null) {
            existing.setSummaryRule(cmd.getSummaryRule());
        }
        if (refMetricCodesProvided) {
            existing.setRefMetricCodes(toJson(refMetricCodes));
        }
        existing.setUpdatedBy(cmd.getOperator());
        existing.setUpdatedTime(LocalDateTime.now());
        // V1.7：指标更新后同步调度状态（状态变更可能触发 unregister）
        registerSchedulerHookIfNeeded(existing, false);
        return existing;
    }

    /**
     * 停用指标.
     *
     * @param metricCode 指标编码
     * @param reason     停用原因
     * @param operator   操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void disable(String metricCode, String reason, String operator) {
        PerfMetricDef existing = getByCode(metricCode);
        if (!"ACTIVE".equals(existing.getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "当前状态不可停用: " + existing.getStatus());
        }
        mapper.updateStatusById(existing.getId(), "DISABLED", operator);
    }

    /**
     * V1.6 通用状态切换：支持 ACTIVE / DRAFT / DISABLED 三向迁移。
     * Controller 收到 ChangeStatusReqDTO 后走这里，避免只有 disable 单向操作。
     *
     * <p>V1.13：DRAFT→ACTIVE 时若指标当前无 slot（V1.13 起 DRAFT 创建不再占 slot），
     * 在切状态前先补分配 slot；slot 区间耗尽时抛 PERF-42200，状态不切换。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(String metricCode, String targetStatus, String reason, String operator) {
        if (!"ACTIVE".equals(targetStatus) && !"DRAFT".equals(targetStatus) && !"DISABLED".equals(targetStatus)) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "无效目标状态: " + targetStatus);
        }
        PerfMetricDef existing = getByCode(metricCode);
        if (targetStatus.equals(existing.getStatus())) {
            return; // 幂等：状态相同直接返回，不抛错
        }
        // V1.13：DRAFT→ACTIVE 补分配 slot（DRAFT 阶段不占配额；维度无关型指标 baseDim=null 不分配）
        if ("ACTIVE".equals(targetStatus)
                && existing.getValSlot() == null
                && existing.getBaseDim() != null && !existing.getBaseDim().isBlank()) {
            int slot = metricSlotService.allocSlot(existing.getBaseDim(), existing.getMetricLevel(), null);
            PerfMetricDef slotPatch = new PerfMetricDef();
            slotPatch.setId(existing.getId());
            slotPatch.setValSlot(slot);
            slotPatch.setUpdatedBy(operator);
            mapper.updateByIdSelective(slotPatch);
            existing.setValSlot(slot);
        }
        mapper.updateStatusById(existing.getId(), targetStatus, operator);
    }

    /**
     * 软删除指标（Task B5）.
     *
     * <p>设置 deleted=1，删除后查询接口（selectByMetricCode/selectByCondition 等）
     * 均通过 AND deleted=0 过滤，对调用方不可见。
     *
     * @param id 指标主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMetric(String id) {
        // V1.7：删除前先查出 metricCode，供 afterCommit hook 注销调度任务
        PerfMetricDef existing = mapper.selectById(id);
        mapper.softDelete(id);
        if (existing != null) {
            unregisterSchedulerHook(existing.getMetricCode());
        }
    }

    /**
     * 按主键查询指标定义，不存在（含已软删除）时返回 null.
     *
     * @param id 指标主键 ID
     * @return 指标定义或 null
     */
    @Transactional(readOnly = true)
    public PerfMetricDef getMetricById(String id) {
        return mapper.selectById(id);
    }

    /**
     * 查询指标定义，不存在则抛异常.
     *
     * @param metricCode 指标编码
     * @return 指标定义
     */
    @Transactional(readOnly = true)
    public PerfMetricDef getByCode(String metricCode) {
        PerfMetricDef def = getByCodeOrNull(metricCode);
        if (def == null) {
            throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, metricCode);
        }
        return def;
    }

    /**
     * 查询指标定义，不存在时返回 null.
     *
     * @param metricCode 指标编码
     * @return 指标定义或 null
     */
    @Transactional(readOnly = true)
    public PerfMetricDef getByCodeOrNull(String metricCode) {
        return mapper.selectByMetricCode(metricCode);
    }

    /**
     * 分页查询指标定义.
     *
     * @param baseDim     基础维度
     * @param metricLevel 层级
     * @param status      状态
     * @param keyword     关键字
     * @param pageNo      页码
     * @param pageSize    每页大小
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<PerfMetricDef> page(String baseDim,
                                          Integer metricLevel,
                                          String status,
                                          String keyword,
                                          int pageNo,
                                          int pageSize) {
        int offset = Math.max(pageNo - 1, 0) * pageSize;
        long total = mapper.countByCondition(baseDim, metricLevel, status, keyword);
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }
        List<PerfMetricDef> records = mapper.selectByCondition(baseDim, metricLevel, status, keyword, offset, pageSize);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * Q7.3 示范方法：基于 {@link PerfScopeHelper} 的数据范围注入分页查询.
     *
     * <p>场景: SELF_CREATED → 只看到自己创建的 metric 定义.
     * 适用于"个人指标沙盒"或"部门隔离"的 metric 管理场景.
     *
     * <p>ScopeColumns 映射 (metric 表):
     * <ul>
     *   <li>createdByCol = "created_by" (SELF_CREATED 用)</li>
     *   <li>其他列复用 created_by (metric 表无 emp_id/org_code 业务列, 降级统一)</li>
     * </ul>
     *
     * <p>V1.3 规划: 根据实际业务场景可扩展 perf_metric_def 表增加 owner_org_id 字段,
     * 届时 ownerOrgCol 可映射到真实组织列.
     *
     * @return 经过数据范围过滤的分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<PerfMetricDef> pageWithScope(String baseDim,
                                                   Integer metricLevel,
                                                   String status,
                                                   String keyword,
                                                   int pageNo,
                                                   int pageSize) {
        String currentEmpId = currentUserApi.getCurrentEmpId();
        PerfScopeHelper.ScopeColumns columns = new PerfScopeHelper.ScopeColumns(
                "created_by",   // ownerEmpCol
                "created_by",   // assigneeCol (metric 无 assignee)
                "created_by",   // createdByCol (SELF_CREATED)
                "created_by",   // ownerOrgCol (metric 表无 org_code, 降级为 created_by)
                null            // bizKeyCol (metric 无 business_key, V1.4 WORKFLOW_PARTICIPANT 退化 fail-close)
        );
        PerfScopeHelper.Fragment frag = perfScopeHelper.getFragment(
                currentEmpId, BizType.PERF_CONFIG, BizAction.LIST, columns);

        int offset = Math.max(pageNo - 1, 0) * pageSize;
        long total = mapper.countByConditionWithScope(
                baseDim, metricLevel, status, keyword, frag.getSql(), frag.getParams());
        List<PerfMetricDef> records = mapper.selectByConditionWithScope(
                baseDim, metricLevel, status, keyword, offset, pageSize,
                frag.getSql(), frag.getParams());
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * V1.10：Controller 专用 DTO 版本一次性全量查询（去分页）.
     *
     * <p>取代 V1.3 的 {@code pageDto}：前端指标库页面工作模式是一次拉全集 + 客户端
     * 按 metric_category / metric_level 分组渲染树，分页反而需要前端额外合并多页，
     * 增加复杂度。V1.10 数据量在数千行内可控，DB 直接 ORDER BY metric_code 返回。
     *
     * @param baseDim     基础维度（可空）
     * @param metricLevel 指标层级（可空）
     * @param status      状态（可空）
     * @param keyword     编码或名称模糊（可空）
     * @return 指标定义 DTO 列表（升序）
     */
    @Transactional(readOnly = true)
    public List<MetricDefRespDTO> listAllDto(String baseDim, Integer metricLevel, String status,
                                             String keyword) {
        List<PerfMetricDef> raw = mapper.selectAllByCondition(baseDim, metricLevel, status, keyword);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<MetricDefRespDTO> dtos = new ArrayList<>(raw.size());
        for (PerfMetricDef def : raw) {
            dtos.add(MetricAssembler.toRespDTO(def));
        }
        return dtos;
    }

    /**
     * V1.10：列出所有非空 metric_category 的去重项，每项返回 {value, label}.
     *
     * <p>V1.9 列 metric_category 直接存中文（规模类/效益类/质量类等），未引入 sys_dict
     * 翻译，因此 {@code value == label}；保留双字段是为后续扩展字典翻译时不破坏前端契约.
     *
     * @return 指标分类下拉项列表（按 value 升序，空集合表示无分类数据）
     */
    @Transactional(readOnly = true)
    public List<MetricCategoryDTO> listCategories() {
        List<String> raw = mapper.selectDistinctCategories();
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<MetricCategoryDTO> dtos = new ArrayList<>(raw.size());
        for (String value : raw) {
            dtos.add(new MetricCategoryDTO(value, value));
        }
        return dtos;
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本 getByCode.
     */
    @Transactional(readOnly = true)
    public MetricDefRespDTO getByCodeDto(String metricCode) {
        return MetricAssembler.toRespDTO(getByCode(metricCode));
    }

    /**
     * 批量查询指标定义.
     *
     * @param metricCodes 指标编码列表
     * @return 指标定义列表
     */
    @Transactional(readOnly = true)
    public List<PerfMetricDef> getByCodes(List<String> metricCodes) {
        if (metricCodes == null || metricCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.selectByMetricCodes(metricCodes);
    }

    /**
     * 查询已启用指标定义列表.
     *
     * @param baseDim     基础维度
     * @param metricLevel 层级
     * @return 指标定义列表
     */
    @Transactional(readOnly = true)
    public List<PerfMetricDef> listActiveMetrics(String baseDim, Integer metricLevel) {
        return mapper.selectByCondition(baseDim, metricLevel, "ACTIVE", null, 0, 1000);
    }

    /**
     * V1.7：列出所有 ACTIVE+AUTO+未删除的指标，供启动同步使用.
     *
     * @return 可调度指标列表
     */
    @Transactional(readOnly = true)
    public List<PerfMetricDef> listSchedulable() {
        return mapper.selectSchedulable();
    }

    private List<String> parseRefMetricCodes(String refMetricCodesJson) {
        if (refMetricCodesJson == null || refMetricCodesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> refs = objectMapper.readValue(refMetricCodesJson, new TypeReference<List<String>>() {});
            return refs == null ? Collections.emptyList() : refs;
        } catch (IOException ex) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, ex, "refMetricCodes JSON 非法");
        }
    }

    private Map<String, Integer> loadRefMetricLevels(List<String> refMetricCodes) {
        if (refMetricCodes == null || refMetricCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PerfMetricDef> refDefs = mapper.selectByMetricCodes(refMetricCodes);
        Map<String, Integer> levelMap = new LinkedHashMap<>();
        if (refDefs != null) {
            for (PerfMetricDef refDef : refDefs) {
                levelMap.put(refDef.getMetricCode(), refDef.getMetricLevel());
            }
        }
        List<String> missing = new ArrayList<>();
        for (String refMetricCode : refMetricCodes) {
            if (!levelMap.containsKey(refMetricCode)) {
                missing.add(refMetricCode);
            }
        }
        if (!missing.isEmpty()) {
            throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, String.join(",", missing));
        }
        return levelMap;
    }

    private String toJson(List<String> refMetricCodes) {
        try {
            return objectMapper.writeValueAsString(refMetricCodes == null ? Collections.emptyList() : refMetricCodes);
        } catch (IOException ex) {
            throw new PerfException(PerfErrorCode.CALC_JOB_FAILED, ex);
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * V1.7：create/update 后注册或注销调度任务.
     *
     * <p>若有活跃事务则在 afterCommit 回调中执行，避免事务回滚后仍注册任务；
     * 无事务（测试/直接调用）场景直接执行.
     *
     * @param def      指标定义（含最新状态）
     * @param isCreate true=新建，false=更新
     */
    void registerSchedulerHookIfNeeded(PerfMetricDef def, boolean isCreate) {
        if (metricSchedulerService == null) return;
        boolean schedulable = metricSchedulerService.isSchedulable(def);
        Runnable action = () -> {
            if (schedulable) {
                metricSchedulerService.register(def);
            } else if (!isCreate) {
                // 更新后不可调度：注销已有任务（如 ACTIVE→DISABLED）
                metricSchedulerService.unregister(def.getMetricCode());
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    /**
     * V1.7：deleteMetric 后注销调度任务.
     *
     * <p>若有活跃事务则在 afterCommit 回调中执行.
     *
     * @param metricCode 指标编码
     */
    void unregisterSchedulerHook(String metricCode) {
        if (metricSchedulerService == null) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            metricSchedulerService.unregister(metricCode);
                        }
                    });
        } else {
            metricSchedulerService.unregister(metricCode);
        }
    }
}
