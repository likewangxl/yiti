package com.bank.branch.platform.performance.service.export.impl;

import com.bank.branch.platform.performance.entity.PerfExportTask;
import com.bank.branch.platform.performance.mapper.PerfExportTaskMapper;
import com.bank.branch.platform.performance.service.export.ExportStrategy;
import com.bank.branch.platform.performance.service.export.PerfExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 绩效异步导出统一入口实现（Task Q6.1 Red 占位）.
 *
 * <p>当前为 Red 阶段占位实现：所有方法抛 UnsupportedOperationException，
 * 保证 Q6.1 Red 测试全部失败。Green 阶段填充正式实现。
 */
@Slf4j
@Service
public class PerfExportServiceImpl implements PerfExportService {

    private final PerfExportTaskMapper taskMapper;
    private final Map<String, ExportStrategy> strategyMap;

    public PerfExportServiceImpl(PerfExportTaskMapper taskMapper,
                                 List<ExportStrategy> strategies) {
        this.taskMapper = taskMapper;
        this.strategyMap = new HashMap<>();
        for (ExportStrategy s : strategies) {
            String type = s.exportType();
            if (type == null || type.isBlank()) {
                throw new IllegalStateException("ExportStrategy " + s.getClass().getName()
                        + " 返回空 exportType");
            }
            if (this.strategyMap.putIfAbsent(type, s) != null) {
                throw new IllegalStateException("ExportStrategy exportType 冲突: " + type);
            }
        }
        log.info("[PerfExportService] 已装配 {} 个导出策略: {}",
                strategyMap.size(), strategyMap.keySet());
    }

    @Override
    public String createTask(String exportType, Map<String, Object> params, String operatorId) {
        throw new UnsupportedOperationException("Q6.1 Green 交付");
    }

    @Override
    public PerfExportTask getTask(String taskId) {
        throw new UnsupportedOperationException("Q6.1 Green 交付");
    }

    @Override
    public PerfExportTask getTaskForOwner(String taskId, String operatorId) {
        throw new UnsupportedOperationException("Q6.1 Green 交付");
    }
}
