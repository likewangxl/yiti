package com.bank.branch.platform.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.report.entity.SqlProbeExportTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * SQL_PROBE_EXPORT_TASK Mapper —— SQL 探查异步导出任务.
 *
 * <p>仅用 MyBatis-Plus {@link BaseMapper} 内置方法：insert / updateById / selectById；
 * 列表查询用 LambdaQueryWrapper（按 emp + createdTime DESC，显式排除 FILE_CONTENT 大字段）。</p>
 */
@Mapper
public interface SqlProbeExportTaskMapper extends BaseMapper<SqlProbeExportTask> {
}
