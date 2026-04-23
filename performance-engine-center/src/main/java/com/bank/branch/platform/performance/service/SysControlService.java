package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.SysControlMapper;
import com.bank.branch.platform.performance.service.cmd.SwitchVersionCmd;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 数据版本控制 Service.
 *
 * <p>职责:
 * <ul>
 *   <li>查询当前生效版本 (单条 is_valid=1)</li>
 *   <li>查询维度下历史版本</li>
 *   <li>原子切换版本 (旧版 is_valid=0 + 新版 insert, 事务)</li>
 *   <li>幂等初始化 (无记录时 insert, 有记录时返回既有)</li>
 * </ul>
 *
 * <p>**分布式锁不在此层**, 由 Facade 层申请/释放 (事务外). 本 Service 仅负责事务边界内的原子操作.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysControlService {

    private final SysControlMapper sysControlMapper;

    /**
     * 查询指定维度当前生效版本.
     *
     * @param scopeDim 维度 (EMP / ORG / CUST)
     * @return 实体
     * @throws PerfException PERF-40406 当该维度无生效记录
     */
    public SysControl getCurrentVersion(String scopeDim) {
        SysControl sc = sysControlMapper.selectByScopeAndValid(scopeDim);
        if (sc == null) {
            log.warn("[SysControlService.getCurrentVersion] 当前版本不存在, scopeDim={}", scopeDim);
            throw new PerfException(PerfErrorCode.SYS_CONTROL_VERSION_NOT_FOUND, scopeDim);
        }
        return sc;
    }

    /**
     * 查询指定维度历史版本 (按 latest_data_date 倒序).
     *
     * @param scopeDim 维度
     * @param limit    最多返回条数
     * @return 历史版本列表 (不抛空, 可能返回空 list)
     */
    public List<SysControl> listVersionHistory(String scopeDim, int limit) {
        return sysControlMapper.listByScope(scopeDim, limit);
    }

    /**
     * 原子切换版本 (事务内): 旧版 is_valid=0 → 新版 insert is_valid=1.
     *
     * <p>并发场景: 由 Facade 层申请 Redis 分布式锁包裹本方法.
     * 若多实例绕过锁同时执行, 依赖 DB UK 兜底 (scope_dim, latest_data_date).
     *
     * @param cmd 切换命令
     * @return 新入库的 SysControl 实体
     * @throws PerfException PERF-40406 当前无生效版本
     * @throws PerfException PERF-40904 DB UK 冲突表明另一并发请求已完成切换
     */
    @Transactional(rollbackFor = Exception.class)
    public SysControl doSwitchVersion(SwitchVersionCmd cmd) {
        log.info("[SysControlService.doSwitchVersion] scopeDim={}, dataDate={}, newVersion={}, operator={}",
                cmd.getScopeDim(), cmd.getDataDate(), cmd.getNewVersion(), cmd.getOperator());

        // 1) 定位当前生效版本 (如果没有, 不允许切换, 业务要求先 init)
        SysControl curr = sysControlMapper.selectByScopeAndValid(cmd.getScopeDim());
        if (curr == null) {
            throw new PerfException(PerfErrorCode.SYS_CONTROL_VERSION_NOT_FOUND, cmd.getScopeDim());
        }

        // 2) 将旧版本置失效
        sysControlMapper.updateIsValid(curr.getId(), 0);

        // 3) 插入新版本 is_valid=1; 若 UK 冲突说明另一并发请求先完成了切换
        SysControl newSc = new SysControl();
        newSc.setId(generateId());
        newSc.setScopeDim(cmd.getScopeDim());
        newSc.setLatestDataDate(cmd.getDataDate());
        newSc.setCurrentVersion(cmd.getNewVersion());
        newSc.setIsValid(1);
        LocalDateTime now = LocalDateTime.now();
        newSc.setCreatedTime(now);
        newSc.setUpdatedTime(now);
        // V1.0.3 Task B7：写入发布元数据
        newSc.setRemark(cmd.getReason());
        newSc.setPublishBy(cmd.getOperator());
        newSc.setPublishSource(cmd.getPublishSource());
        newSc.setPublishTime(now);
        newSc.setUpdatedBy(cmd.getOperator());

        try {
            sysControlMapper.insert(newSc);
        } catch (DuplicateKeyException e) {
            log.warn("[SysControlService.doSwitchVersion] UK 冲突, 判定为并发切换: scopeDim={}, dataDate={}",
                    cmd.getScopeDim(), cmd.getDataDate());
            throw new PerfException(PerfErrorCode.SYS_CONTROL_VERSION_CONFLICT, e);
        }
        return newSc;
    }

    /**
     * 回滚到指定历史版本（V1.2 Q1.2）.
     *
     * <p>流程：
     * <ol>
     *   <li>校验 (scopeDim, rollbackTo) 历史记录存在，不存在则抛 PERF-40012</li>
     *   <li>查询当前生效版本，不存在则抛 PERF-40012</li>
     *   <li>将当前生效记录 is_valid=0</li>
     *   <li>新建一条 current_version=rollbackTo 的 is_valid=1 记录，publishSource=ROLLBACK</li>
     * </ol>
     *
     * <p>latestDataDate 取 {@link LocalDate#now()} —— 回滚也是一次发布事件，需要新的数据日期。
     *
     * <p>高危操作：Controller 层必须带 {@code @AuditLog(reasonRequired=true)}。
     *
     * @param scopeDim   维度（EMP / ORG / CUST）
     * @param rollbackTo 回滚到的历史版本号
     * @param reason     回滚原因（必填，写入 remark）
     * @param operatorId 操作人 empId
     * @return 回滚后新入库的 SysControl 实体
     * @throws PerfException PERF-40012 当 rollbackTo 不存在或当前无生效版本
     * @throws PerfException PERF-40903 并发 UK 冲突
     */
    @Transactional(rollbackFor = Exception.class)
    public SysControl rollback(String scopeDim, String rollbackTo, String reason, String operatorId) {
        log.info("[SysControlService.rollback] scopeDim={}, rollbackTo={}, operator={}, reason={}",
                scopeDim, rollbackTo, operatorId, reason);

        // 1) 校验 rollbackTo 版本存在于历史记录（不论 is_valid）
        List<SysControl> history = sysControlMapper.listByScope(scopeDim, Integer.MAX_VALUE);
        boolean targetExists = history.stream()
                .anyMatch(sc -> rollbackTo.equals(sc.getCurrentVersion()));
        if (!targetExists) {
            log.warn("[SysControlService.rollback] rollbackTo 版本不存在: scopeDim={}, rollbackTo={}",
                    scopeDim, rollbackTo);
            throw new PerfException(PerfErrorCode.SYS_CONTROL_VERSION_NOT_FOUND, scopeDim, rollbackTo);
        }

        // 2) 当前生效版本必须存在（业务前置条件）
        SysControl curr = sysControlMapper.selectByScopeAndValid(scopeDim);
        if (curr == null) {
            throw new PerfException(PerfErrorCode.SYS_CONTROL_VERSION_NOT_FOUND, scopeDim);
        }

        // 3) 旧版置失效
        sysControlMapper.updateIsValid(curr.getId(), 0);

        // 4) 插入 rollbackTo 版本 is_valid=1，publishSource=ROLLBACK
        SysControl newSc = new SysControl();
        newSc.setId(generateId());
        newSc.setScopeDim(scopeDim);
        // latestDataDate 使用今日：回滚也是一次新发布，避免与旧记录 UK 冲突
        newSc.setLatestDataDate(LocalDate.now());
        newSc.setCurrentVersion(rollbackTo);
        newSc.setIsValid(1);
        LocalDateTime now = LocalDateTime.now();
        newSc.setCreatedTime(now);
        newSc.setUpdatedTime(now);
        newSc.setRemark(reason);
        newSc.setPublishBy(operatorId);
        newSc.setPublishSource("ROLLBACK");
        newSc.setPublishTime(now);
        newSc.setUpdatedBy(operatorId);

        try {
            sysControlMapper.insert(newSc);
        } catch (DuplicateKeyException e) {
            log.warn("[SysControlService.rollback] UK 冲突, scopeDim={}, rollbackTo={}",
                    scopeDim, rollbackTo);
            throw new PerfException(PerfErrorCode.SYS_CONTROL_VERSION_CONFLICT, e);
        }
        return newSc;
    }

    /**
     * 幂等初始化: 若该 scope_dim 已有记录则返回既有 (优先生效版本, 否则返回最近一条),
     * 无任何记录时才 insert 一条新基线.
     *
     * @param scopeDim 维度
     * @param dataDate 数据日期
     * @param version  版本号
     * @return 实体 (新建或既有, 永不为 null)
     */
    @Transactional(rollbackFor = Exception.class)
    public SysControl initIfAbsent(String scopeDim, LocalDate dataDate, String version) {
        long count = sysControlMapper.countByCondition(scopeDim, null);
        if (count > 0) {
            // 优先返回当前生效版本; 若无生效版本 (历史记录均已失效) 则返回最近一条
            SysControl curr = sysControlMapper.selectByScopeAndValid(scopeDim);
            if (curr != null) {
                return curr;
            }
            List<SysControl> latest = sysControlMapper.listByScope(scopeDim, 1);
            // count>0 理论上一定有至少一条, 但防御式编程再核查一次
            if (!latest.isEmpty()) {
                return latest.get(0);
            }
        }
        SysControl sc = new SysControl();
        sc.setId(generateId());
        sc.setScopeDim(scopeDim);
        sc.setLatestDataDate(dataDate);
        sc.setCurrentVersion(version);
        sc.setIsValid(1);
        LocalDateTime now = LocalDateTime.now();
        sc.setCreatedTime(now);
        sc.setUpdatedTime(now);
        sysControlMapper.insert(sc);
        log.info("[SysControlService.initIfAbsent] 新建版本记录 scopeDim={}, dataDate={}, version={}",
                scopeDim, dataDate, version);
        return sc;
    }

    /** 生成 varchar(32) 主键. */
    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
