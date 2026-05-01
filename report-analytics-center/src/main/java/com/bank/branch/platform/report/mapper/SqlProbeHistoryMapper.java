package com.bank.branch.platform.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.report.entity.SqlProbeHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * sql_probe_history Mapper —— SQL 探查历史（M0.5.1 + M4.3 增量）.
 *
 * <p>MyBatis-Plus 接入：{@code insert(T)} / {@code selectById(Serializable)} 由
 * {@link BaseMapper} 提供，已从本接口删除。自定义业务查询方法继续保留。
 */
@Mapper
public interface SqlProbeHistoryMapper extends BaseMapper<SqlProbeHistory> {

    // insert / selectById 由 MyBatis-Plus BaseMapper 提供

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
