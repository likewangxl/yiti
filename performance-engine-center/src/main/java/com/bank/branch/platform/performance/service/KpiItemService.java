package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.api.dto.KpiItemDTO;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.facade.assembler.KpiAssembler;
import com.bank.branch.platform.performance.mapper.PerfKpiItemMapper;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.service.cmd.AddKpiItemCmd;
import com.bank.branch.platform.performance.service.cmd.UpdateKpiItemCmd;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * KPI 方案项服务.
 *
 * <p>职责: 管理 {@code perf_kpi_item} 的 CRUD。
 * <p>父子事务编排由 {@link KpiSchemeService#create} 通过 Spring REQUIRED 传播, 本 Service
 * 的写方法都在父事务内运行, 任何一项失败都会回滚整批。
 *
 * <p>业务约束:
 * <ul>
 *   <li>同 schemeId 下 metricCode 唯一 (由 UK + {@link #addItem} 预校验双保险)</li>
 *   <li>删除为高危操作, {@code reason} 必填</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiItemService {

    /** 缺省最高分, 对齐 DDL 默认值. */
    private static final BigDecimal DEFAULT_MAX_SCORE = new BigDecimal("999999.0000");

    /** 缺省最低分. */
    private static final BigDecimal DEFAULT_MIN_SCORE = new BigDecimal("0.0000");

    /** 缺省加倍系数. */
    private static final BigDecimal DEFAULT_MULTIPLIER = new BigDecimal("1.0000");

    private final PerfKpiItemMapper itemMapper;
    /**
     * 父方案表 Mapper: 仅用于 {@link #addItem} 的父方案存在性校验, 避免让 Controller 层兜底校验,
     * 也同步覆盖 V1.1 Facade 路径. 直接注入 Mapper 而非 {@code KpiSchemeService} 是为了规避
     * 循环依赖 ({@code KpiSchemeService.create} 已依赖本 Service 批量添加 item).
     */
    private final PerfKpiSchemeMapper schemeMapper;

    /**
     * 新增方案项.
     *
     * <p>先校验父方案存在 (防御 REST / Facade 两条路径), 再做 (schemeId, metricCode) UK 预校验;
     * 通过后补齐默认值并落库。重复时抛 {@link PerfErrorCode#KPI_ITEM_DUP} 而非让 UK 冲突冒泡,
     * 因为调用方在 create scheme 时会批量调用, 预校验更友好。
     *
     * @param cmd 新增命令 (schemeId + metricCode + weight 必填, 其余可空用默认值)
     * @return 新建方案项 (含生成的 id + 默认字段)
     */
    @Transactional(rollbackFor = Exception.class)
    public PerfKpiItem addItem(AddKpiItemCmd cmd) {
        // 父方案存在性校验: 避免 Controller 层兜底 + Facade 路径漏校双重问题
        if (schemeMapper.selectById(cmd.getSchemeId()) == null) {
            throw new PerfException(PerfErrorCode.KPI_SCHEME_NOT_FOUND, cmd.getSchemeId());
        }
        // UK 预校验: 同方案内 metric 必须唯一
        if (itemMapper.selectBySchemeAndMetric(cmd.getSchemeId(), cmd.getMetricCode()) != null) {
            throw new PerfException(PerfErrorCode.METRIC_CODE_DUP, cmd.getSchemeId(), cmd.getMetricCode());
        }
        PerfKpiItem item = new PerfKpiItem();
        item.setId(generateId());
        item.setSchemeId(cmd.getSchemeId());
        item.setMetricCode(cmd.getMetricCode());
        item.setWeight(cmd.getWeight());
        item.setMultiplier(cmd.getMultiplier() != null ? cmd.getMultiplier() : DEFAULT_MULTIPLIER);
        item.setMinScore(cmd.getMinScore() != null ? cmd.getMinScore() : DEFAULT_MIN_SCORE);
        item.setMaxScore(cmd.getMaxScore() != null ? cmd.getMaxScore() : DEFAULT_MAX_SCORE);
        item.setCreatedTime(LocalDateTime.now());
        itemMapper.insert(item);
        return item;
    }

    /**
     * 按主键选择性更新方案项.
     *
     * <p>只 patch 非空字段, 其余字段保留原值。对应 Mapper
     * {@code updateByIdSelective} 要求至少一个非 id 字段非空。
     *
     * @param id  项ID
     * @param cmd 更新命令
     * @return 更新后的方案项 (存量实体 merge 后的视图, 不重新 selectById)
     */
    @Transactional(rollbackFor = Exception.class)
    public PerfKpiItem updateItem(String id, UpdateKpiItemCmd cmd) {
        PerfKpiItem existing = itemMapper.selectById(id);
        if (existing == null) {
            throw new PerfException(PerfErrorCode.KPI_SCHEME_NOT_FOUND, id);
        }
        PerfKpiItem patch = new PerfKpiItem();
        patch.setId(id);
        patch.setWeight(cmd.getWeight());
        patch.setMultiplier(cmd.getMultiplier());
        patch.setMinScore(cmd.getMinScore());
        patch.setMaxScore(cmd.getMaxScore());
        itemMapper.updateByIdSelective(patch);

        // 内存视图同步 (便于 Facade 层免二次查询)
        if (cmd.getWeight() != null) {
            existing.setWeight(cmd.getWeight());
        }
        if (cmd.getMultiplier() != null) {
            existing.setMultiplier(cmd.getMultiplier());
        }
        if (cmd.getMinScore() != null) {
            existing.setMinScore(cmd.getMinScore());
        }
        if (cmd.getMaxScore() != null) {
            existing.setMaxScore(cmd.getMaxScore());
        }
        return existing;
    }

    /**
     * 删除方案项 (高危, reason 必填).
     *
     * <p>物理删除, 因 V1.0 尚未有 target_value 引用该 item 的场景;
     * 审计由 Controller 层 {@code @AuditLog(reasonRequired=true)} 保证。
     *
     * @param id       项ID
     * @param reason   删除原因 (用于审计, 不可空白)
     * @param operator 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(String id, String reason, String operator) {
        if (reason == null || reason.isBlank()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "reason 必填");
        }
        PerfKpiItem existing = itemMapper.selectById(id);
        if (existing == null) {
            throw new PerfException(PerfErrorCode.KPI_SCHEME_NOT_FOUND, id);
        }
        log.info("[KpiItemService.deleteItem] id={}, schemeId={}, metricCode={}, operator={}, reason={}",
                id, existing.getSchemeId(), existing.getMetricCode(), operator, reason);
        itemMapper.deleteById(id);
    }

    /**
     * 查询方案下所有项 (按插入顺序).
     *
     * @param schemeId 方案ID
     * @return 项列表 (可能为空)
     */
    @Transactional(readOnly = true)
    public List<PerfKpiItem> listBySchemeId(String schemeId) {
        return itemMapper.selectBySchemeId(schemeId);
    }

    /**
     * 按主键查询 (不存在抛 {@link PerfErrorCode#KPI_ITEM_NOT_FOUND}).
     *
     * @param id 主键
     * @return 方案项
     */
    @Transactional(readOnly = true)
    public PerfKpiItem getById(String id) {
        PerfKpiItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new PerfException(PerfErrorCode.KPI_SCHEME_NOT_FOUND, id);
        }
        return item;
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本 addItem.
     */
    @Transactional(rollbackFor = Exception.class)
    public KpiItemDTO addItemDto(AddKpiItemCmd cmd) {
        return KpiAssembler.toItemDto(addItem(cmd));
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本 updateItem.
     */
    @Transactional(rollbackFor = Exception.class)
    public KpiItemDTO updateItemDto(String id, UpdateKpiItemCmd cmd) {
        return KpiAssembler.toItemDto(updateItem(id, cmd));
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
