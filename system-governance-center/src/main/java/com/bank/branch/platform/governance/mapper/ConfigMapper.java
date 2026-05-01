package com.bank.branch.platform.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.governance.entity.SysConfigKv;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统配置 Mapper 接口，操作 sys_config_kv 表。
 * <p>
 * 提供按 configKey 精确查询、分页查询和更新能力。
 * </p>
 * <p>
 * insert / updateById 由 MyBatis-Plus BaseMapper 提供。
 * </p>
 */
@Mapper
public interface ConfigMapper extends BaseMapper<SysConfigKv> {

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
}
