package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfExportTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 绩效异步导出任务 Mapper（perf_export_task）.
 *
 * <p>V1.2 Task Q6.1 交付，支撑异步导出框架的最小集：
 * <ul>
 *   <li>创建任务（insert）</li>
 *   <li>按主键查询（selectById）</li>
 *   <li>状态机流转 + 结果回填（updateStatus / updateSuccess / updateFailed）</li>
 *   <li>按操作人查询任务列表（selectByOperator）</li>
 * </ul>
 *
 * <p>状态机由 Service 层保证（DDL 不加 CHECK），当前允许的转移：
 * {@code PENDING → RUNNING → SUCCESS | FAILED}。
 */
@Mapper
public interface PerfExportTaskMapper {

    /**
     * 新增导出任务（初始 status 通常为 PENDING）.
     *
     * @param task 任务实体
     * @return 受影响行数
     */
    int insert(PerfExportTask task);

    /**
     * 按主键查询.
     *
     * @param id 主键
     * @return 任务，不存在返回 null
     */
    PerfExportTask selectById(@Param("id") String id);

    /**
     * 更新状态（不涉及 file_key/row_count，通常用于 PENDING → RUNNING 过渡）.
     *
     * @param id        主键
     * @param newStatus 新状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id,
                     @Param("newStatus") String newStatus);

    /**
     * 标记为成功，回填 file_key / row_count / file_size / expire_at.
     *
     * @param id        主键
     * @param fileKey   MinIO object key
     * @param rowCount  导出行数
     * @param fileSize  文件大小
     * @param expireAt  过期时间（可空）
     * @return 受影响行数
     */
    int updateSuccess(@Param("id") String id,
                      @Param("fileKey") String fileKey,
                      @Param("rowCount") Integer rowCount,
                      @Param("fileSize") Long fileSize,
                      @Param("expireAt") java.time.LocalDateTime expireAt);

    /**
     * 标记为失败，回填 error_msg.
     *
     * @param id       主键
     * @param errorMsg 错误信息
     * @return 受影响行数
     */
    int updateFailed(@Param("id") String id,
                     @Param("errorMsg") String errorMsg);

    /**
     * 按 operatorId 倒序查询最近任务（不分页，调用方加 LIMIT）.
     *
     * @param operatorId 操作人员工号
     * @param limit      上限
     * @return 任务列表（可能为空）
     */
    List<PerfExportTask> selectByOperator(@Param("operatorId") String operatorId,
                                          @Param("limit") int limit);
}
