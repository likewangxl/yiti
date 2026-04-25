package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.RptSnapshotTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * rpt_snapshot_task Mapper —— 快照任务配置（M0.5.1 雏形，V1 仅预留）.
 *
 * <p>V1 状态：仅 insert + selectById 雏形，实际启用推迟到 V2.
 */
@Mapper
public interface RptSnapshotTaskMapper {

    /** 新增一条快照任务 */
    int insert(RptSnapshotTask e);

    /** 按 id 精确查询 */
    RptSnapshotTask selectById(@Param("id") String id);
}
