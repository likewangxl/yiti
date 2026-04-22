package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfImportBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 绩效导入批次 Mapper（perf_import_batch）.
 *
 * <p>V1.1 Task P1.3 交付。支持导入流程需要的最小集：
 * <ul>
 *   <li>创建批次（insert）</li>
 *   <li>按主键 / 批次号读取（selectById / selectByBatchNo）</li>
 *   <li>状态机流转更新（updateStatus）</li>
 *   <li>行数汇总更新（updateCounts）</li>
 * </ul>
 *
 * <p>状态机由 Service 层保证（DDL 不加 CHECK），当前允许的转移：
 * {@code CREATED → RUNNING → SUCCESS | FAILED}。
 */
@Mapper
public interface PerfImportBatchMapper {

    /**
     * 新增导入批次（初始 status 通常为 CREATED）.
     *
     * <p>同 {@code batch_no} 第二次插入将抛 {@link org.springframework.dao.DuplicateKeyException}，
     * 调用方应先以 {@link #selectByBatchNo} 检测幂等。
     *
     * @param b 批次实体
     * @return 受影响行数
     */
    int insert(PerfImportBatch b);

    /**
     * 按主键查询.
     *
     * @param id 主键
     * @return 批次，不存在返回 null
     */
    PerfImportBatch selectById(@Param("id") String id);

    /**
     * 按批次号（业务唯一键）查询.
     *
     * @param batchNo 批次号
     * @return 批次，不存在返回 null
     */
    PerfImportBatch selectByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 更新状态 + 可选备注（状态机流转）.
     *
     * <p>调用方应在 Service 层校验原状态是否允许转移到 newStatus。
     *
     * @param id        主键
     * @param newStatus 新状态（RUNNING / SUCCESS / FAILED）
     * @param remark    备注（可为 null；通常存错误摘要或结果 JSON）
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id,
                     @Param("newStatus") String newStatus,
                     @Param("remark") String remark);

    /**
     * 更新导入行数汇总（导入结束时一次性设置）.
     *
     * @param id          主键
     * @param totalRows   总行数
     * @param successRows 成功行数
     * @param errorRows   失败行数
     * @return 受影响行数
     */
    int updateCounts(@Param("id") String id,
                     @Param("totalRows") int totalRows,
                     @Param("successRows") int successRows,
                     @Param("errorRows") int errorRows);
}
