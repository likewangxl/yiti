package com.bank.branch.platform.performance.service.importer.impl;

import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.ImportStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 基础数据导入策略（Task P5.1 骨架 / P5.3 实现）.
 *
 * <p>P5.3 Red：注入 metric_def + emp/org/cust index result 4 个 Mapper 依赖（单元测试可
 * {@code new BaseDataImportStrategy(mockA, mockB, mockC, mockD)} 构造），execute 仍抛
 * {@code VALIDATION_FAILED} 占位，待 P5.3 Green 替换为 easyexcel 解析 + 按 baseDim 路由落库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaseDataImportStrategy implements ImportStrategy {

    private final PerfMetricDefMapper metricDefMapper;
    private final EmpIndexResultMapper empIndexResultMapper;
    private final OrgIndexResultMapper orgIndexResultMapper;
    private final CustIndexResultMapper custIndexResultMapper;

    @Override
    public String importType() {
        return "BASE_DATA";
    }

    @Override
    public ImportResult execute(PerfImportBatch batch, MultipartFile file) {
        log.warn("[BaseDataImportStrategy] P5.3 Red 阶段，execute 尚未实现，batchId={}, file={}",
                batch.getId(), file == null ? "null" : file.getOriginalFilename());
        throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                "BaseDataImportStrategy 待 Task P5.3 Green 实现");
    }
}
