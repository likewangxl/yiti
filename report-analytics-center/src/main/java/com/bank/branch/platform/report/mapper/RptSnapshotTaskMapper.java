package com.bank.branch.platform.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.report.entity.RptSnapshotTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * rpt_snapshot_task Mapper —— 快照任务配置（M0.5.1 雏形，V1 仅预留）.
 *
 * <p>V1 状态：仅 insert + selectById 雏形，实际启用推迟到 V2.
 *
 * <p>MyBatis-Plus 接入：继承 {@link BaseMapper} 提供通用 CRUD。
 * {@code insert(T)} 由 BaseMapper 提供；{@code selectById(@Param("id") String)} 签名与
 * BaseMapper 的 {@code selectById(Serializable)} 不同（带命名参数），保留原方法以维持兼容性。
 */
@Mapper
public interface RptSnapshotTaskMapper extends BaseMapper<RptSnapshotTask> {

    // insert 由 MyBatis-Plus BaseMapper 提供

    /** 按 id 精确查询（保留：@Param("id") 签名与 BaseMapper.selectById(Serializable) 不同） */
    RptSnapshotTask selectById(@Param("id") String id);
}
