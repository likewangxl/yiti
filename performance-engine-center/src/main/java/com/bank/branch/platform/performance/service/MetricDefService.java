package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateMetricDefCmd;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        def.setValSlot(metricSlotService.allocSlot(cmd.getBaseDim(), cmd.getMetricLevel(), cmd.getPreferredSlot()));
        def.setStatus("ACTIVE");
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
            throw new PerfException(PerfErrorCode.METRIC_CODE_DUP, ex, cmd.getMetricCode());
        }
        metricRefService.setRefs(cmd.getMetricCode(), refMetricCodes);
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
     * 软删除指标（Task B5）.
     *
     * <p>设置 deleted=1，删除后查询接口（selectByMetricCode/selectByCondition 等）
     * 均通过 AND deleted=0 过滤，对调用方不可见。
     *
     * @param id 指标主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMetric(String id) {
        mapper.softDelete(id);
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
                "created_by"    // ownerOrgCol (metric 表无 org_code, 降级为 created_by)
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
}
