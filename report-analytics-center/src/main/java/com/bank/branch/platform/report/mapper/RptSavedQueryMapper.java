package com.bank.branch.platform.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.report.entity.RptSavedQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * rpt_saved_query Mapper —— 动态查询保存方案.
 *
 * <p>M0.5.1 雏形 4 方法 + M1.4/M1.5 增量 3 方法（list / oldest / 乐观锁删除）.
 *
 * <p>MyBatis-Plus 接入：{@code insert(T)} / {@code selectById(Serializable)} /
 * {@code deleteById(Serializable)} 由 {@link BaseMapper} 提供，已从本接口删除。
 * 自定义业务方法继续保留。
 */
@Mapper
public interface RptSavedQueryMapper extends BaseMapper<RptSavedQuery> {

    // insert / selectById / deleteById 由 MyBatis-Plus BaseMapper 提供

    /** 按 empId 计数（用于 M1.5 最多 10 条方案上限判定） */
    int countByEmpId(@Param("empId") String empId);

    /** 选择更新（name / subjectIds / metricCodes / version / updatedTime 按需） */
    int updateByIdSelective(RptSavedQuery e);

    /**
     * 按 empId + 可选 dim 列表查询（M1.4 B.1 新增）.
     * dim 为 null 时不过滤，按 created_time DESC 排序.
     */
    List<RptSavedQuery> listByEmpAndDim(@Param("empId") String empId, @Param("dim") String dim);

    /**
     * 查询某用户最旧一条方案的 id（M1.5 自动删最旧用）.
     * 按 created_time ASC LIMIT 1.
     */
    String findOldestId(@Param("empId") String empId);

    /**
     * 乐观锁更新（M1.5 PUT 用）：仅当 id+expectedVersion 匹配时才更新，否则返回 0.
     */
    int updateWithOptimisticLock(RptSavedQuery e, @Param("expectedVersion") int expectedVersion);
}
