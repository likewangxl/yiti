package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.SqlProbeHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * sql_probe_history Mapper —— SQL 探查历史（M0.5.1 雏形）.
 *
 * <p>M4 阶段会追加 {@code selectByEmpIdPaged / updateTerminalStatus} 等方法.
 */
@Mapper
public interface SqlProbeHistoryMapper {

    /** 新增一条探查历史（探查开始时 INSERT status=RUNNING 占位） */
    int insert(SqlProbeHistory e);

    /** 按 id 精确查询 */
    SqlProbeHistory selectById(@Param("id") String id);

    /** 更新终态（status / rowCount / executionTimeMs / errorMsg） */
    int updateTerminalStatus(SqlProbeHistory e);
}
