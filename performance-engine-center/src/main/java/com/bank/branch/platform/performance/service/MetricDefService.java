package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateMetricDefCmd;
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
        List<String> refMetricCodes = parseRefMetricCodes(cmd.getRefMetricCodes());
        Map<String, Integer> refMetricLevels = loadRefMetricLevels(refMetricCodes);
        metricCycleDetectService.checkLevelConstraint(existing.getMetricLevel(), refMetricLevels);
        metricCycleDetectService.checkNoCycle(metricRefService.loadFullGraph(), existing.getMetricCode(), refMetricCodes);

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
        patch.setRefMetricCodes(toJson(refMetricCodes));
        patch.setUpdatedBy(cmd.getOperator());
        mapper.updateByIdSelective(patch);
        metricRefService.setRefs(existing.getMetricCode(), refMetricCodes);

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
        existing.setRefMetricCodes(toJson(refMetricCodes));
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
            throw new PerfException(PerfErrorCode.INVALID_STATE, "当前状态不可停用: " + existing.getStatus());
        }
        mapper.updateStatusById(existing.getId(), "DISABLED", operator);
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
            throw new PerfException(PerfErrorCode.PARAM_INVALID, ex, "refMetricCodes JSON 非法");
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
            throw new PerfException(PerfErrorCode.INTERNAL_ERROR, ex);
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
