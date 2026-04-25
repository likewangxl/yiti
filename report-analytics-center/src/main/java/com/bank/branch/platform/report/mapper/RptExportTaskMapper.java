package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.RptExportTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * rpt_export_task Mapper —— 报表异步导出任务（M0.5.1 雏形，M5 启用）.
 *
 * <p>M5 阶段会追加 {@code selectPendingPaged / updateTerminalStatus / updateFileInfo} 等方法.
 */
@Mapper
public interface RptExportTaskMapper {

    /** 新增一条导出任务（status=PENDING） */
    int insert(RptExportTask e);

    /** 按 id 精确查询 */
    RptExportTask selectById(@Param("id") String id);
}
