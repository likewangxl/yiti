package com.bank.branch.platform.performance.service.importer.impl;

import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.TargetPlanService;
import com.bank.branch.platform.performance.service.TargetValueService;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 目标值导入策略（Task P5.1 骨架 / P5.2 实现）.
 *
 * <p>P5.2 Red：注入 {@link TargetValueService} / {@link TargetPlanService} 依赖（单元测试可
 * 直接 {@code new TargetImportStrategy(mockA, mockB)} 构造），execute 仍抛
 * {@code VALIDATION_FAILED} 占位，待 P5.2 Green 替换为 easyexcel 解析 + 行级 upsert。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TargetImportStrategy implements ImportStrategy {

    private final TargetValueService targetValueService;
    private final TargetPlanService targetPlanService;

    @Override
    public String importType() {
        return "TARGET";
    }

    @Override
    public ImportResult execute(PerfImportBatch batch, MultipartFile file) {
        log.warn("[TargetImportStrategy] P5.2 Red 阶段，execute 尚未实现，batchId={}, file={}",
                batch.getId(), file == null ? "null" : file.getOriginalFilename());
        throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                "TargetImportStrategy 待 Task P5.2 Green 实现");
    }
}
