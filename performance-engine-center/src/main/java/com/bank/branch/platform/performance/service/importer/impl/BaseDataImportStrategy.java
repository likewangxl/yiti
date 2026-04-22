package com.bank.branch.platform.performance.service.importer.impl;

import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 基础数据导入策略（Task P5.1 骨架 / P5.3 实现）.
 *
 * <p>P5.1 本期：{@link #importType} 固定返回 {@code "BASE_DATA"}；execute 抛
 * {@code VALIDATION_FAILED} 提示"P5.3 实现中"，待 Task P5.3 替换为指标原始值 UPSERT 逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaseDataImportStrategy implements ImportStrategy {

    @Override
    public String importType() {
        return "BASE_DATA";
    }

    @Override
    public ImportResult execute(PerfImportBatch batch, MultipartFile file) {
        log.warn("[BaseDataImportStrategy] P5.1 骨架，execute 尚未实现，batchId={}, file={}",
                batch.getId(), file == null ? "null" : file.getOriginalFilename());
        throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                "BaseDataImportStrategy 待 Task P5.3 实现");
    }
}
