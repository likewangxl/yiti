package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchRowDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;

/**
 * 批次写入事务边界。
 *
 * <p>开始任务和失败状态使用独立事务；成功路径把五个专用槽位、result_preview_json 和
 * SUCCESS 更新放在同一事务中，防止槽位半写却留下完整快照。</p>
 */
@Service
public class BranchDashboardBatchTaskStore {

    private final PerfRunTaskMapper runTaskMapper;
    private final OrgIndexResultMapper orgIndexResultMapper;

    @Autowired
    public BranchDashboardBatchTaskStore(PerfRunTaskMapper runTaskMapper,
                                         OrgIndexResultMapper orgIndexResultMapper,
                                         com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.runTaskMapper = runTaskMapper;
        this.orgIndexResultMapper = orgIndexResultMapper;
    }

    /** 单测兼容：直接使用任务 Mapper；生产构造器会注入结果 Mapper。 */
    public BranchDashboardBatchTaskStore(PerfRunTaskMapper runTaskMapper,
                                         com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this(runTaskMapper, null, objectMapper);
    }

    /** 独立提交初始 RUNNING 任务。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void start(PerfRunTask task) {
        runTaskMapper.insert(task);
    }

    /** 成功路径原子写入专用槽位和不可变快照。 */
    @Transactional(rollbackFor = Exception.class)
    public void commitSuccess(String batchId, BranchDashboardBatchDTO snapshot,
                              Map<String, PerfMetricDef> definitions, List<String> outputCodes,
                              String version,
                              String resultJson, String paramsJson) {
        if (orgIndexResultMapper == null) {
            throw new IllegalStateException("结果 Mapper 未装配");
        }
        if (outputCodes == null || outputCodes.size() != 5) {
            throw new IllegalStateException("批次输出指标必须恰有 5 个专用槽位");
        }
        for (BranchDashboardBatchRowDTO row : snapshot.getRows()) {
            for (String code : outputCodes) {
                PerfMetricDef def = definitions.get(code);
                int slot = def == null ? 0 : def.getValSlot();
                if (slot < 1 || slot > 400) {
                    throw new IllegalStateException("输出指标槽位越界: " + code);
                }
                orgIndexResultMapper.insertSlotValue(row.getOrgCode(), snapshot.getDataDate(), version,
                        slot, row.getMetricValues().get(code));
            }
        }
        runTaskMapper.updateBatchContext(batchId, snapshot.getDataDate(), version);
        runTaskMapper.updateResultPreviewJson(batchId, resultJson);
        runTaskMapper.updateStatusWithParams(batchId, "SUCCESS", null, paramsJson);
    }

    /** 独立记录 FAILED，保证失败可追溯。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(String batchId, String errorMessage, String paramsJson,
                           LocalDate dataDate, String dataVersion) {
        if (dataDate != null || dataVersion != null) {
            runTaskMapper.updateBatchContext(batchId, dataDate, dataVersion);
        }
        runTaskMapper.updateStatusWithParams(batchId, "FAILED", errorMessage, paramsJson);
    }

}
