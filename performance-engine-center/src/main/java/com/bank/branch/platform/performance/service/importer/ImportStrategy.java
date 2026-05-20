package com.bank.branch.platform.performance.service.importer;

import com.bank.branch.platform.performance.entity.PerfImportBatch;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据导入策略（V1.1 Task P5.1；V1.12 微调扩展 ImportContext 参数）.
 *
 * <p>每种 importType 对应一个策略 Bean，{@link PerfImportService} 根据 importType
 * 分发到具体策略执行。
 *
 * <p>策略实现要求：
 * <ul>
 *   <li>保持无状态（允许被并发调用）</li>
 *   <li>execute 内部仅负责"解析 + 行级校验 + 落库"，**不负责**批次状态机流转
 *       （状态机由 {@link PerfImportService} 统一管理）</li>
 *   <li>返回 {@link ImportResult}；整文件级错误（文件为空、列头不对齐）
 *       应抛 {@link com.bank.branch.platform.performance.exception.PerfException}
 *       （推荐错误码：{@code IMPORT_COLUMN_MAPPING_INVALID / VALIDATION_FAILED}）</li>
 *   <li>行级错误累计到 {@link ImportResult#setErrorSummary}，不要抛异常</li>
 *   <li>{@link ImportContext} 仅 METRIC_RESULT 使用，其他 4 个策略忽略 {@code ctx}</li>
 * </ul>
 */
public interface ImportStrategy {

    /**
     * 本策略处理的 importType，与 {@code PerfImportBatch.importType} 一致.
     *
     * @return 类型标识，如 {@code "TARGET" / "BASE_DATA" / "ALLOC"}
     */
    String importType();

    /**
     * 执行导入.
     *
     * @param batch 已落库的批次（status=RUNNING），id/batchNo 可用
     * @param file  上传的 Excel 文件
     * @param ctx   跨 strategy 共享的整文件级参数；不需要时由 Service 传入 {@link ImportContext#EMPTY}
     * @return 导入结果（非 null）
     */
    ImportResult execute(PerfImportBatch batch, MultipartFile file, ImportContext ctx);
}
