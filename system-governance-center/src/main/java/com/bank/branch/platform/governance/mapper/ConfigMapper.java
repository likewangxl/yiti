package com.bank.branch.platform.governance.mapper;

import com.bank.branch.platform.governance.entity.SysConfigKv;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统配置 Mapper 接口，操作 sys_config_kv 表。
 * <p>
 * 提供按 configKey 精确查询、分页查询和更新能力。
 * </p>
 */
@Mapper
public interface ConfigMapper {

    /**
     * 根据配置键查询配置项（不限状态）。
     *
     * @param configKey 配置键
     * @return 配置实体，不存在时返回 null
     */
    SysConfigKv selectByConfigKey(String configKey);

    /**
     * 分页查询配置列表，支持按状态过滤。
     *
     * @param status 状态过滤，为 null 时不过滤
     * @param offset 偏移量
     * @param limit  每页大小
     * @return 配置列表
     */
    List<SysConfigKv> selectAll(@Param("status") String status,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    /**
     * 统计配置总记录数，支持按状态过滤。
     *
     * @param status 状态过滤，为 null 时不过滤
     * @return 总记录数
     */
    long countAll(@Param("status") String status);

    /**
     * 按主键更新配置信息。
     *
     * @param config 包含 id 及待更新字段的配置实体
     * @return 受影响行数
     */
    int updateById(SysConfigKv config);

    /**
     * 新增配置记录。
     *
     * @param config 配置实体
     * @return 受影响行数
     */
    int insert(SysConfigKv config);
}
