package com.bank.branch.platform.governance.mapper;

import com.bank.branch.platform.governance.entity.SysJobConf;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务调度配置 Mapper 接口，操作 sys_job_conf 表。
 */
@Mapper
public interface JobConfMapper {

    /**
     * 根据任务KEY查询任务配置。
     *
     * @param jobKey 任务唯一标识
     * @return 任务配置实体，不存在时返回 null
     */
    SysJobConf selectByJobKey(String jobKey);

    /**
     * 根据主键查询任务配置。
     *
     * @param id 任务ID
     * @return 任务配置实体，不存在时返回 null
     */
    SysJobConf selectById(String id);

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
     * 按主键动态更新任务配置。
     *
     * @param conf 包含 id 及待更新字段的任务配置实体
     * @return 受影响行数
     */
    int updateById(SysJobConf conf);

    /**
     * 新增任务配置记录。
     *
     * @param conf 任务配置实体
     * @return 受影响行数
     */
    int insert(SysJobConf conf);
}
