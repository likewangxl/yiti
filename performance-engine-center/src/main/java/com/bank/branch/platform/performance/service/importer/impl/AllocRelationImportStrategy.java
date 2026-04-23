package com.bank.branch.platform.performance.service.importer.impl;

import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分配关系导入策略（Task P5.1 骨架 / P5.4 实现）.
 *
 * <p>P5.4 Red：注入 {@link CustAllocRelationMapper} 依赖（V1.1 新增 insert 方法，
 * 见 Mapper 类 Javadoc），execute 仍抛 {@code VALIDATION_FAILED} 占位，
 * 待 P5.4 Green 替换为 easyexcel 解析 + 行级 insert + DuplicateKey 捕获。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllocRelationImportStrategy implements ImportStrategy {

    private final CustAllocRelationMapper custAllocRelationMapper;

    @Override
    public String importType() {
        return "ALLOC";
    }

    @Override
    public ImportResult execute(PerfImportBatch batch, MultipartFile file) {
        log.warn("[AllocRelationImportStrategy] P5.4 Red 阶段，execute 尚未实现，batchId={}, file={}",
                batch.getId(), file == null ? "null" : file.getOriginalFilename());
        throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                "AllocRelationImportStrategy 待 Task P5.4 Green 实现");
    }
}
