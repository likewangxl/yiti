package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.service.cmd.UpsertTargetValueCmd;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
        // 强制覆盖 createdBy 为当前操作人 (I-2 安全契约)
        for (PerfTargetValue v : list) {
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
     * <p>ScopeColumns 映射（perf_target_value 业务列仅 created_by 可用）:
     * <ul>
     *   <li>ownerEmpCol   = "created_by" (SELF 降级到创建人, 该表无独立 owner/emp_id 列)</li>
     *   <li>assigneeCol   = "created_by" (同上, 无 assignee 列)</li>
     *   <li>createdByCol  = "created_by" (SELF_CREATED 语义准确)</li>
     *   <li>ownerOrgCol   = "created_by" (perf_target_value 无 org_code, ORG scope 降级)</li>
     * </ul>
     *
     * <p>与既有 {@link #listByPlan} 的差异：
     * <ul>
     *   <li>listByPlan: 强制 planId 非空（Controller 侧手动指定方案上下文）, 不带数据范围</li>
     *   <li>pageWithScope: planId 可空（V1.3 为向后兼容保留可选）, 内部注入数据范围片段</li>
     * </ul>
     *
     * <p>V1.3 规划：可在 V1.4 考虑扩展 perf_target_value 增加 owner_emp_id / owner_org_code
     * 字段，届时 SELF / ORG 不再降级到 created_by.
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
                "created_by",   // ownerEmpCol (SELF)
                "created_by",   // assigneeCol (无 assignee)
                "created_by",   // createdByCol (SELF_CREATED)
                "created_by"    // ownerOrgCol (无 org_code, 降级)
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

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
