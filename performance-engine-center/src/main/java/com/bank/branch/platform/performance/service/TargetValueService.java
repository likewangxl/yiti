package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.dto.TargetSubjectDTO;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.facade.assembler.TargetAssembler;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.service.cmd.UpsertTargetValueBatchCmd;
import com.bank.branch.platform.performance.service.cmd.UpsertTargetValueCmd;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 目标值服务.
 *
 * <p>职责:
 * <ul>
 *   <li>基于 UK (plan_id, subject_type, subject_id, cycle_key, metric_code) 的批量 upsert</li>
 *   <li>入参校验: size 上限 500 (PERF-40910)、空列表 early return、planId 非空</li>
 *   <li>createdBy 强制覆盖为当前操作人 (安全契约 I-2: 冲突行的 updated_by = VALUES(created_by),
 *       若不覆盖, 调用方传入任意值会成为审计字段的篡改入口)</li>
 *   <li>只读查询 (UK / 按方案分页)</li>
 * </ul>
 *
 * <p>**事务策略**: 写方法 {@code @Transactional(rollbackFor=Exception.class)},
 * Mapper XML 单语句 upsertBatch 已在 DB 层原子; 事务主要用于与调用方其他 DB 操作共用。
 * 本 Service **不**缓存目标值 (量大且按 UK 查询, 缓存收益低), 因此**不**实现 afterCommit evict。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TargetValueService {

    /** 批量 upsert 单批上限 (对齐 PERF-40910 错误码与 spec §5.3 DoD). */
    private static final int BATCH_UPPER_LIMIT = 500;

    private final PerfTargetValueMapper targetValueMapper;
    /** V1.3 R1.1 新增：当前用户读取（数据范围注入路径使用）. */
    private final CurrentUserApi currentUserApi;
    /** V1.3 R1.1 新增：数据范围 SQL 片段生成器. */
    private final PerfScopeHelper perfScopeHelper;
    /** 2026-06-15：EMP 维度工号存在性校验（PT_USER.username）. */
    private final UserApi userApi;
    /** 2026-06-15：ORG 维度机构存在性校验 + 部门编号归一（EXT_ORG_INFO）. */
    private final OrgApi orgApi;
    /** 2026-06-15：解析目标方案 → 关联 KPI 方案（校验导入/录入指标是否在方案内）. */
    private final PerfTargetPlanMapper targetPlanMapper;
    /** 2026-06-15：取 KPI 方案定义的指标项集合. */
    private final PerfKpiItemMapper kpiItemMapper;

    /**
     * 批量 upsert 目标值.
     *
     * <p>流程:
     * <ol>
     *   <li>空 / null 列表 early return 0 (I-3): 若直接下沉到 Mapper, MyBatis
     *       {@code <foreach>} 会生成 {@code VALUES } (空 VALUES 列表) 触发语法错误</li>
     *   <li>size > 500 抛 TARGET_BATCH_TOO_BIG (PERF-40910): 避免超大批次阻塞 DB</li>
     *   <li>遍历覆写 entity.createdBy = operator (I-2): XML
     *       {@code ON DUPLICATE KEY UPDATE updated_by = VALUES(created_by)} 依赖此约定,
     *       确保审计字段不可被调用方伪造</li>
     *   <li>为未带 id 的 entity 补 UUID (允许调用方传裸 entity)</li>
     *   <li>调 Mapper.upsertBatch, 返回 MySQL 语义受影响行数 (新增 1 / 更新 2)</li>
     * </ol>
     *
     * @param list     目标值列表 (可空)
     * @param operator 操作人 (必填, 强制覆盖 createdBy)
     * @return 受影响行数 (MySQL 语义), 空列表返回 0
     */
    @Transactional(rollbackFor = Exception.class)
    public int upsertBatch(List<PerfTargetValue> list, String operator) {
        if (list == null || list.isEmpty()) {
            // early return 避免 XML <foreach> 生成非法 VALUES ()
            return 0;
        }
        if (list.size() > BATCH_UPPER_LIMIT) {
            throw new PerfException(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT, list.size());
        }
        // 强制覆盖 createdBy 为当前操作人 (I-2 安全契约) + 主体存在性后端校验（2026-06-15）
        // 方案/KPI 指标集合按 planId/schemeId 缓存，一批内复用，避免逐行 DB 往返
        Map<String, PerfTargetPlan> planCache = new HashMap<>();
        Map<String, Set<String>> schemeMetricCache = new HashMap<>();
        for (PerfTargetValue v : list) {
            validateAndNormalizeSubject(v);
            validateMetricInKpiScheme(v, planCache, schemeMetricCache);
            v.setCreatedBy(operator);
            if (v.getId() == null || v.getId().isBlank()) {
                v.setId(generateId());
            }
        }
        int affected = targetValueMapper.upsertBatch(list);
        log.info("[TargetValueService.upsertBatch] operator={}, inputSize={}, affected={}",
                operator, list.size(), affected);
        return affected;
    }

    /**
     * 主体存在性校验 + ORG 归一化（2026-06-15）.
     *
     * <p>目标值导入/录入的工号、部门编号一律由后端直连数据库校验，<b>不再依赖前端缓存</b>
     * （前端缓存受 pageSize 上限截断，会把真实存在的员工误判为"不存在"）。
     * <ul>
     *   <li>EMP：subjectId=工号，必须存在于 PT_USER.username（{@code userApi.getUsersByUsernames}）。</li>
     *   <li>ORG：subjectId 可为部门编号(EXT_ORG_INFO.DEPT_NO)或内部机构编码；命中部门编号则
     *       归一为内部机构编码入库（与单条录入/对象列展示/KPI 计算的 subject_id 口径一致），
     *       否则按内部编码兜底校验（{@code orgApi.getOrg}）。</li>
     *   <li>CUST/其他维度：不校验。</li>
     * </ul>
     * 校验不通过先 {@code log.warn} 留痕（便于运维排查"哪个工号/机构被拦"），再抛
     * {@link PerfErrorCode#VALIDATION_FAILED}；单条导入由调用方 catch 累计到 errorSummary，
     * 批量端点则整批失败。
     *
     * @param v 待校验目标值（ORG 命中部门编号时其 subjectId 会被原地归一为内部机构编码）
     */
    private void validateAndNormalizeSubject(PerfTargetValue v) {
        String type = v.getSubjectType();
        String subject = v.getSubjectId();
        if (subject == null || subject.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "对象编号必填");
        }
        if ("EMP".equals(type)) {
            List<UserDTO> users = userApi.getUsersByUsernames(List.of(subject));
            if (users == null || users.isEmpty()) {
                log.warn("[TargetValueService] 目标值主体校验失败：员工工号在系统中不存在 subjectId={}, planId={}",
                        subject, v.getPlanId());
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "员工工号在系统中不存在: " + subject);
            }
        } else if ("ORG".equals(type)) {
            // 部门编号优先：命中则归一为内部机构编码入库
            OrgDTO byDept = orgApi.getOrgByDeptNo(subject);
            if (byDept != null) {
                if (byDept.getOrgCode() != null && !byDept.getOrgCode().isBlank()) {
                    v.setSubjectId(byDept.getOrgCode());
                }
                return;
            }
            // 兜底：subjectId 本身可能已是内部机构编码（单条录入从下拉选择）
            if (orgApi.getOrg(subject) == null) {
                log.warn("[TargetValueService] 目标值主体校验失败：机构(部门编号/编码)在系统中不存在 subjectId={}, planId={}",
                        subject, v.getPlanId());
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "机构(部门编号)在系统中不存在: " + subject);
            }
        }
        // CUST/null 等其他维度不做主体存在性校验
    }

    /**
     * 指标合法性校验：目标值的指标必须属于目标方案关联的 KPI 方案中定义的指标（2026-06-15）.
     *
     * <p>业务规则：「不是 KPI 方案中的指标，不能添加到目标值」。覆盖目标值管理页的单条/批量录入
     * 与 Excel 导入（导入策略 {@code TargetImportStrategy} 已先行校验，此处作为统一兜底，
     * 保证任何写入路径都不会落入非方案指标）。
     *
     * <p>取数：planId → {@code perf_target_plan.kpi_scheme_id} → {@code perf_kpi_item.metric_code} 集合；
     * 方案不存在或未关联 KPI 方案（{@code kpiSchemeId} 为空）时跳过（与导入策略一致，不在此处新增方案存在性失败模式）。
     * 指标集合按 schemeId 缓存，一批内仅查一次。
     *
     * @param v                 待校验目标值
     * @param planCache         planId → 方案 缓存
     * @param schemeMetricCache schemeId → 指标编码集合 缓存
     * @throws PerfException {@link PerfErrorCode#IMPORT_METRIC_NOT_IN_KPI} 指标不在方案内
     */
    private void validateMetricInKpiScheme(PerfTargetValue v,
                                           Map<String, PerfTargetPlan> planCache,
                                           Map<String, Set<String>> schemeMetricCache) {
        String planId = v.getPlanId();
        if (planId == null || planId.isBlank()) {
            return;
        }
        PerfTargetPlan plan = planCache.computeIfAbsent(planId, targetPlanMapper::selectById);
        if (plan == null) {
            return;
        }
        String schemeId = plan.getKpiSchemeId();
        if (schemeId == null || schemeId.isBlank()) {
            return;
        }
        Set<String> codes = schemeMetricCache.computeIfAbsent(schemeId, sid -> {
            Set<String> set = new HashSet<>();
            List<PerfKpiItem> items = kpiItemMapper.selectBySchemeId(sid);
            if (items != null) {
                for (PerfKpiItem it : items) {
                    if (it != null && it.getMetricCode() != null) {
                        set.add(it.getMetricCode());
                    }
                }
            }
            return set;
        });
        if (!codes.contains(v.getMetricCode())) {
            log.warn("[TargetValueService] 指标不在目标方案关联的 KPI 方案中 metricCode={}, planId={}, schemeId={}",
                    v.getMetricCode(), planId, schemeId);
            throw new PerfException(PerfErrorCode.IMPORT_METRIC_NOT_IN_KPI, v.getMetricCode());
        }
    }

    /**
     * 单值 upsert.
     *
     * <p>内部委托 {@link #upsertBatch} (复用校验 + createdBy 覆盖逻辑).
     *
     * @param cmd 单值命令
     * @return 受影响行数 (新增 1 / 更新 2)
     */
    @Transactional(rollbackFor = Exception.class)
    public int upsertOne(UpsertTargetValueCmd cmd) {
        PerfTargetValue v = new PerfTargetValue();
        v.setId(generateId());
        v.setPlanId(cmd.getPlanId());
        v.setSubjectType(cmd.getSubjectType());
        v.setSubjectId(cmd.getSubjectId());
        v.setCycleKey(cmd.getCycleKey());
        v.setMetricCode(cmd.getMetricCode());
        v.setTargetValue(cmd.getTargetValue());
        v.setBaseValue(cmd.getBaseValue());
        // V1.4 S2.2: 透传 owner 字段（可空）
        v.setOwnerEmpId(cmd.getOwnerEmpId());
        v.setOwnerOrgCode(cmd.getOwnerOrgCode());
        // 2026-06-17: 透传 阶段名称 / 起止日期（可空）
        v.setStageName(cmd.getStageName());
        v.setStartDate(cmd.getStartDate());
        v.setEndDate(cmd.getEndDate());
        // createdBy 在 upsertBatch 内部强制覆盖, 此处不设置
        return upsertBatch(Collections.singletonList(v), cmd.getOperator());
    }

    /**
     * 按 UK 查询单条目标值.
     *
     * @param planId      方案ID
     * @param subjectType 对象类型
     * @param subjectId   对象ID
     * @param cycleKey    周期键
     * @param metricCode  指标编码
     * @return Optional 包装的目标值
     */
    @Transactional(readOnly = true)
    public Optional<PerfTargetValue> getByUniqueKey(String planId, String subjectType, String subjectId,
                                                    String cycleKey, String metricCode) {
        return Optional.ofNullable(
                targetValueMapper.selectByUniqueKey(planId, subjectType, subjectId, cycleKey, metricCode));
    }

    /**
     * 按方案分页查询目标值.
     *
     * <p>为什么强制 planId 非空 (I-3): Mapper XML {@code <where>} 的 {@code AND plan_id = #{planId}}
     * 不在 {@code <if>} 内, 传入 null 会生成 {@code plan_id IS NOT NULL} 的意外语义 (或 NPE);
     * 且 "遍历全库目标值" 无业务价值, Service 层提前拦截。
     *
     * @param planId      方案ID (必填)
     * @param subjectType 对象类型 (可空)
     * @param subjectId   对象ID (可空)
     * @param cycleKey    周期键 (可空)
     * @param pageNo      页码 (从 1 起)
     * @param pageSize    页大小
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<PerfTargetValue> listByPlan(String planId, String subjectType, String subjectId,
                                                  String cycleKey, int pageNo, int pageSize) {
        if (planId == null || planId.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "planId 必填");
        }
        long total = targetValueMapper.countByPlan(planId, subjectType, subjectId, cycleKey);
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }
        int offset = Math.max(pageNo - 1, 0) * pageSize;
        List<PerfTargetValue> records = targetValueMapper.listByPlan(
                planId, subjectType, subjectId, cycleKey, offset, pageSize);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * V1.3 R1.1 新增：基于 {@link PerfScopeHelper} 的数据范围注入分页查询.
     *
     * <p>场景:
     * <ul>
     *   <li>普通绩效配置员 SELF_CREATED → 只看到自己创建的目标值</li>
     *   <li>绩效查询员 ALL → 可见全部目标值（scope=ALL 无片段）</li>
     *   <li>无权限 / fail-close → 返回 total=0 的空页</li>
     * </ul>
     *
     * <p>ScopeColumns 映射（V1.4 S2.3 从 V1.3 全部 created_by 精化为独立 owner 字段）:
     * <ul>
     *   <li>ownerEmpCol   = "owner_emp_id" (SELF 精确匹配归属员工, V1.4 S2.1 引入字段)</li>
     *   <li>assigneeCol   = "owner_emp_id" (SELF_ASSIGNED, 复用同列)</li>
     *   <li>createdByCol  = "created_by" (SELF_CREATED 语义保持)</li>
     *   <li>ownerOrgCol   = "owner_org_code" (ORG 精确匹配归属机构, V1.4 S2.1 引入字段)</li>
     *   <li>bizKeyCol     = null (TargetValue 无 business_key, WORKFLOW_PARTICIPANT fail-close)</li>
     * </ul>
     *
     * <p>与既有 {@link #listByPlan} 的差异：
     * <ul>
     *   <li>listByPlan: 强制 planId 非空（Controller 侧手动指定方案上下文）, 不带数据范围</li>
     *   <li>pageWithScope: planId 可空（V1.3 为向后兼容保留可选）, 内部注入数据范围片段</li>
     * </ul>
     *
     * <p>V1.4 S2.3 演进：V1.3 R1.1 因 DDL 无 owner_emp_id / owner_org_code 列,
     * SELF / ORG 全部降级到 created_by；V1.4 S2.1 引入独立字段后, 本方法升级
     * ScopeColumns 切到精确列, 模块 CLAUDE.md 技术债 #5 标记已消化.
     *
     * @param planId      目标方案ID (可空，空则按 scope 跨方案查询)
     * @param subjectType 对象类型 EMP/ORG (可空)
     * @param subjectId   对象ID (可空)
     * @param cycleKey    周期键 (可空)
     * @param pageNo      页码 (从 1 起)
     * @param pageSize    页大小
     * @return 经过数据范围过滤的分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<PerfTargetValue> pageWithScope(String planId, String subjectType, String subjectId,
                                                     String cycleKey, int pageNo, int pageSize) {
        String currentEmpId = currentUserApi.getCurrentEmpId();
        PerfScopeHelper.ScopeColumns columns = new PerfScopeHelper.ScopeColumns(
                "owner_emp_id",   // ownerEmpCol (SELF, V1.4 S2.3 从 created_by 切到精确列)
                "owner_emp_id",   // assigneeCol (SELF_ASSIGNED, 复用归属员工列)
                "created_by",     // createdByCol (SELF_CREATED 语义保持)
                "owner_org_code", // ownerOrgCol (ORG, V1.4 S2.3 从 created_by 切到精确列)
                null              // bizKeyCol (TargetValue 无 business_key, WORKFLOW_PARTICIPANT fail-close)
        );
        PerfScopeHelper.Fragment frag = perfScopeHelper.getFragment(
                currentEmpId, BizType.PERF_CONFIG, BizAction.LIST, columns);

        int offset = Math.max(pageNo - 1, 0) * pageSize;
        long total = targetValueMapper.countByConditionWithScope(
                planId, subjectType, subjectId, cycleKey, frag.getSql(), frag.getParams());
        List<PerfTargetValue> records = targetValueMapper.selectByConditionWithScope(
                planId, subjectType, subjectId, cycleKey, offset, pageSize,
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
    public PageResult<TargetValueDTO> pageWithScopeDto(String planId, String subjectType, String subjectId,
                                                       String cycleKey, int pageNo, int pageSize) {
        PageResult<PerfTargetValue> raw = pageWithScope(planId, subjectType, subjectId, cycleKey, pageNo, pageSize);
        List<TargetValueDTO> dtos = new java.util.ArrayList<>(raw.getRecords().size());
        for (PerfTargetValue v : raw.getRecords()) {
            dtos.add(TargetAssembler.toDto(v));
        }
        return PageResult.of(raw.getPageNo(), raw.getPageSize(), raw.getTotal(), dtos);
    }

    /**
     * 按主键物理删除单条目标值（目标值管理页"删除"操作，前端二次确认后调用）。
     *
     * <p>PERF_TARGET_VALUE 无逻辑删除列，{@code BaseMapper.deleteById} 直接 {@code DELETE FROM}。
     * 高危操作：Controller 侧标 {@code @AuditLog}；id 不存在返回 0（幂等，不抛异常）。
     *
     * @param id 目标值主键
     * @return 受影响行数（0 表示该 id 不存在）
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(String id) {
        int affected = targetValueMapper.deleteById(id);
        log.info("[TargetValueService.deleteById] id={}, affected={}", id, affected);
        return affected;
    }

    /**
     * V1.3 R4.1：批量 upsert Cmd 版本，内部完成 entity 构造 + 复用既有 {@link #upsertBatch}.
     *
     * <p>Controller 只传 {@link UpsertTargetValueBatchCmd}，避免 Controller 层 {@code new PerfTargetValue()}.
     *
     * @param batchCmd 批量命令（含 items 与 operator）
     * @return 受影响行数（MySQL 语义：新增 1 / 更新 2）
     */
    @Transactional(rollbackFor = Exception.class)
    public int upsertBatchFromCmd(UpsertTargetValueBatchCmd batchCmd) {
        if (batchCmd == null || batchCmd.getItems() == null || batchCmd.getItems().isEmpty()) {
            return 0;
        }
        List<PerfTargetValue> list = new java.util.ArrayList<>(batchCmd.getItems().size());
        for (UpsertTargetValueCmd item : batchCmd.getItems()) {
            PerfTargetValue v = new PerfTargetValue();
            v.setPlanId(item.getPlanId());
            v.setSubjectType(item.getSubjectType());
            v.setSubjectId(item.getSubjectId());
            v.setCycleKey(item.getCycleKey());
            v.setMetricCode(item.getMetricCode());
            v.setTargetValue(item.getTargetValue());
            v.setBaseValue(item.getBaseValue());
            // V1.4 S2.2: 透传 owner 字段（可空）
            v.setOwnerEmpId(item.getOwnerEmpId());
            v.setOwnerOrgCode(item.getOwnerOrgCode());
            // 2026-06-17: 透传 阶段名称 / 起止日期（可空）
            v.setStageName(item.getStageName());
            v.setStartDate(item.getStartDate());
            v.setEndDate(item.getEndDate());
            // createdBy 在 upsertBatch 内部强制覆盖为 operator (I-2), 此处不设置
            list.add(v);
        }
        return upsertBatch(list, batchCmd.getOperator());
    }

    /**
     * 查询某目标方案下「对象」下拉项（方案内目标值去重 + 标签解析，2026-06-15）.
     *
     * <p>用于目标值管理页查询区的对象下拉。EMP 维度展示「工号 姓名」，ORG 维度展示
     * 「部门编号 机构名称」。{@code subjectId} 保持入库口径（EMP=工号 / ORG=内部机构编码），
     * 供前端作为列表过滤值回传。
     *
     * <ul>
     *   <li>EMP：批量 {@link UserApi#getUsersByUsernames} 解析工号→姓名；解析不到则姓名留空、label 退化为工号。</li>
     *   <li>ORG：subjectId 为内部机构编码，逐个 {@link OrgApi#getOrg} 解析部门编号+机构名称（按编码缓存去重）。</li>
     * </ul>
     *
     * @param planId 目标方案ID（必填）
     * @return 去重并解析标签后的对象列表（EMP 在前、ORG 在后，按 subjectId 排序由 SQL 保证）
     */
    @Transactional(readOnly = true)
    public List<TargetSubjectDTO> listSubjects(String planId) {
        if (planId == null || planId.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "planId 必填");
        }
        List<PerfTargetValue> rows = targetValueMapper.selectDistinctSubjectsByPlan(planId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        // EMP 工号批量解析姓名
        List<String> empIds = new java.util.ArrayList<>();
        for (PerfTargetValue r : rows) {
            if ("EMP".equals(r.getSubjectType()) && r.getSubjectId() != null) {
                empIds.add(r.getSubjectId());
            }
        }
        Map<String, String> empNameByNo = new HashMap<>();
        if (!empIds.isEmpty()) {
            List<UserDTO> users = userApi.getUsersByUsernames(empIds);
            if (users != null) {
                for (UserDTO u : users) {
                    if (u != null && u.getUsername() != null) {
                        empNameByNo.put(u.getUsername(), u.getDisplayName());
                    }
                }
            }
        }
        // ORG 内部编码 → OrgDTO 缓存（避免同编码重复查）
        Map<String, OrgDTO> orgByCode = new HashMap<>();
        List<TargetSubjectDTO> result = new java.util.ArrayList<>(rows.size());
        for (PerfTargetValue r : rows) {
            String type = r.getSubjectType();
            String subjectId = r.getSubjectId();
            if (subjectId == null) {
                continue;
            }
            if ("ORG".equals(type)) {
                // subject_id 多为部门编号(DEPT_NO)，少数为内部机构编码：先按部门编号解析，兜底按编码
                OrgDTO org = orgByCode.computeIfAbsent(subjectId, sid -> {
                    OrgDTO byDept = orgApi.getOrgByDeptNo(sid);
                    return byDept != null ? byDept : orgApi.getOrg(sid);
                });
                String deptNo = org != null && org.getDeptNo() != null ? org.getDeptNo() : subjectId;
                String orgName = org != null ? org.getOrgName() : null;
                result.add(TargetSubjectDTO.builder()
                        .subjectType("ORG").subjectId(subjectId)
                        .displayId(deptNo).name(orgName)
                        .label(buildLabel(deptNo, orgName))
                        .build());
            } else {
                String name = empNameByNo.get(subjectId);
                result.add(TargetSubjectDTO.builder()
                        .subjectType(type).subjectId(subjectId)
                        .displayId(subjectId).name(name)
                        .label(buildLabel(subjectId, name))
                        .build());
            }
        }
        return result;
    }

    /** 下拉标签：有名称则「编号 名称」，否则仅编号. */
    private static String buildLabel(String id, String name) {
        return (name == null || name.isBlank()) ? id : id + " " + name;
    }

    /**
     * 2026-06-17：某目标方案下所有目标值的「阶段名称」去重列表（非空），供查询区下拉。
     *
     * @param planId 目标方案ID（必填）
     * @return 去重的非空阶段名称列表（可能为空）
     */
    @Transactional(readOnly = true)
    public List<String> listStageNames(String planId) {
        if (planId == null || planId.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "planId 必填");
        }
        List<String> names = targetValueMapper.selectDistinctStageNamesByPlan(planId);
        return names == null ? Collections.emptyList() : names;
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
