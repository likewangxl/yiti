package com.bank.branch.platform.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.report.entity.RptExportTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * rpt_export_task Mapper —— 报表异步导出任务（M0.5.1 基线 + M5.2.1 扩展）.
 *
 * <p>M5.2.1 新增 4 个状态机方法 + 1 个查询：
 * <ul>
 *   <li>updateStatus —— PENDING → RUNNING / RUNNING → CANCELLED 等单字段过渡</li>
 *   <li>updateSuccess —— 终态 SUCCESS + 回填 file_key/row_count/file_size/expire_at</li>
 *   <li>updateFailed —— 终态 FAILED + 回填 error_msg</li>
 *   <li>selectByOperator —— 按 operator_id（+ 可选 status）按时间倒序查询</li>
 * </ul>
 *
 * <p>MyBatis-Plus 接入：{@code insert(T)} / {@code selectById(Serializable)} 由
 * {@link BaseMapper} 提供，已从本接口删除。自定义状态机方法继续保留。
 */
@Mapper
public interface RptExportTaskMapper extends BaseMapper<RptExportTask> {

    // insert / selectById 由 MyBatis-Plus BaseMapper 提供

    /**
     * 更新单字段 status（PENDING → RUNNING / CANCELLED 等过渡）.
     *
     * @return 影响行数（id 不存在返回 0）
     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 终态 SUCCESS：status='SUCCESS' + 回填 file_key/row_count/file_size/expire_at.
     *
     * @return 影响行数
     */
    int updateSuccess(@Param("id") String id,
                      @Param("fileKey") String fileKey,
                      @Param("rowCount") Integer rowCount,
                      @Param("fileSize") Long fileSize,
                      @Param("expireAt") LocalDateTime expireAt);

    /**
     * 终态 FAILED：status='FAILED' + 回填 error_msg.
     *
     * @return 影响行数
     */
    int updateFailed(@Param("id") String id, @Param("errorMsg") String errorMsg);

    /**
     * 按 operator_id 查询任务（status 可选过滤），按 created_time 倒序.
     *
     * @param operatorId 操作人员工号（必填）
     * @param status     状态过滤，null 表示不过滤
     */
    List<RptExportTask> selectByOperator(@Param("operatorId") String operatorId,
                                         @Param("status") String status);
}
