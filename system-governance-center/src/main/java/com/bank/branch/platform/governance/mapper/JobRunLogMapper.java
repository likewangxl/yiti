package com.bank.branch.platform.governance.mapper;

import com.bank.branch.platform.governance.entity.SysJobRunLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务执行日志 Mapper 接口，操作 sys_job_run_log 表。
 */
@Mapper
public interface JobRunLogMapper {

    /**
     * 新增执行日志记录。
     *
     * @param log 执行日志实体
     * @return 受影响行数
     */
    int insert(SysJobRunLog log);

    /**
     * 根据主键查询执行日志。
     *
     * @param id 日志ID
     * @return 执行日志实体，不存在时返回 null
     */
    SysJobRunLog selectById(String id);

    /**
     * 按主键动态更新执行日志。
     *
     * @param log 包含 id 及待更新字段的日志实体
     * @return 受影响行数
     */
    int updateById(SysJobRunLog log);

    /**
     * 判断指定任务是否存在 RUNNING 状态的执行日志（并发防控）。
     *
     * @param jobId 任务ID
     * @return 存在返回 true，否则返回 false
     */
    boolean existsRunningByJobId(String jobId);

    /**
     * 分页查询指定任务的执行日志，按创建时间倒序。
     *
     * @param jobId  任务ID
     * @param offset 偏移量
     * @param limit  每页大小
     * @return 执行日志列表
     */
    List<SysJobRunLog> selectByJobId(@Param("jobId") String jobId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 统计指定任务的执行日志总数。
     *
     * @param jobId 任务ID
     * @return 总记录数
     */
    long countByJobId(String jobId);

    /**
     * 标记执行日志为 SUCCESS（V1.6 quartz 整合 P1.5 引入）。
     *
     * <p>由 JobExecutionLogger.jobWasExecuted 在 jobException == null 时调用。
     * 仅更新 status / end_time，不写 error_msg。</p>
     *
     * @param id      日志主键
     * @param endTime 结束时间
     * @return 受影响行数
     */
    int updateSuccess(@Param("id") String id,
                      @Param("endTime") LocalDateTime endTime);

    /**
     * 标记执行日志为 FAILED 并写入错误信息（V1.6 quartz 整合 P1.5 引入）。
     *
     * <p>由 JobExecutionLogger.jobWasExecuted 在 jobException != null 时调用。
     * errorMsg 调用前已截断到 4000 字符以内，防止 TEXT 列爆炸。</p>
     *
     * @param id       日志主键
     * @param endTime  结束时间
     * @param errorMsg 异常堆栈（截断后，长度 ≤ 4000）
     * @return 受影响行数
     */
    int updateFailed(@Param("id") String id,
                     @Param("endTime") LocalDateTime endTime,
                     @Param("errorMsg") String errorMsg);
}
