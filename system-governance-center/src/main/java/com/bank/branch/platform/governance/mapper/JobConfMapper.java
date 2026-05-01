package com.bank.branch.platform.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.governance.entity.SysJobConf;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务调度配置 Mapper 接口，操作 sys_job_conf 表。
 * <p>
 * selectById / insert / updateById 由 MyBatis-Plus BaseMapper 提供。
 * </p>
 */
@Mapper
public interface JobConfMapper extends BaseMapper<SysJobConf> {

    /**
     * 根据任务KEY查询任务配置。
     *
     * @param jobKey 任务唯一标识
     * @return 任务配置实体，不存在时返回 null
     */
    SysJobConf selectByJobKey(String jobKey);

    /**
     * 分页查询任务配置列表，支持关键词模糊搜索。
     *
     * @param keyword 关键词（模糊匹配 job_key / job_name），为 null 时不过滤
     * @param offset  偏移量
     * @param limit   每页大小
     * @return 任务配置列表
     */
    List<SysJobConf> selectByPage(@Param("keyword") String keyword,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    /**
     * 统计分页查询的总记录数。
     *
     * @param keyword 关键词，为 null 时不过滤
     * @return 总记录数
     */
    long countByPage(@Param("keyword") String keyword);

    /**
     * 根据 jobKey 同步更新 last_run_time（V1.6 quartz 整合 P1.5 引入）。
     *
     * <p>由 JobExecutionLogger.jobWasExecuted 在每次任务执行结束后调用，
     * 记录最近一次实际触发时间。</p>
     *
     * @param jobKey      任务唯一标识
     * @param lastRunTime 最近一次执行结束时间
     * @return 受影响行数
     */
    int updateLastRunTime(@Param("jobKey") String jobKey,
                          @Param("lastRunTime") LocalDateTime lastRunTime);

    /**
     * 按状态查询任务配置列表（V1.6 quartz 整合 P3.1 引入）。
     *
     * <p>用于 JobService.@PostConstruct.syncJobsOnStartup 启动时同步：
     * 遍历 status='ACTIVE' 的所有任务并注册到 Quartz Scheduler。</p>
     *
     * @param status 任务状态：ACTIVE / PAUSED
     * @return 任务配置列表，按 id 升序
     */
    List<SysJobConf> selectByStatus(@Param("status") String status);

    /**
     * 按主键单独更新任务状态（V1.6 quartz 整合 P3.3 引入）。
     *
     * <p>用于 JobService.pauseJob / resumeJob：在调用 Quartz scheduler 之前
     * 先持久化数据库状态变更（PAUSED / ACTIVE），与 scheduler.pauseJob / resumeJob
     * 在 {@code @Transactional} 范围内一起执行——若 mapper 写库失败则不会调用 scheduler，
     * 保证两边状态一致。</p>
     *
     * @param id     任务ID
     * @param status 目标状态：ACTIVE / PAUSED
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id, @Param("status") String status);
}
