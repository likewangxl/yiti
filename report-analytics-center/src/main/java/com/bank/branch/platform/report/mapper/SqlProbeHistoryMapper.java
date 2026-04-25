package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.SqlProbeHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * sql_probe_history Mapper —— SQL 探查历史（M0.5.1 + M4.3 增量）.
 */
@Mapper
public interface SqlProbeHistoryMapper {

    /** 新增一条探查历史（探查开始时 INSERT status=RUNNING 占位） */
    int insert(SqlProbeHistory e);

    /** 按 id 精确查询 */
    SqlProbeHistory selectById(@Param("id") String id);

    /** 更新终态（status / rowCount / executionTimeMs / errorMsg） */
    int updateTerminalStatus(SqlProbeHistory e);

    /**
     * 分页查询某员工的历史（M4.3.1 D.2，按 created_time DESC）.
     *
     * @param empId  员工 ID
     * @param offset 偏移
     * @param limit  分页大小
     * @return 该页数据
     */
    List<SqlProbeHistory> selectByEmpIdPaged(@Param("empId") String empId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    /** 统计某员工的历史总数（M4.3.1 D.2 配合分页）. */
    long countByEmpId(@Param("empId") String empId);
}
