package com.bank.branch.platform.governance.mapper;

import com.bank.branch.platform.governance.entity.SysJobRunLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
