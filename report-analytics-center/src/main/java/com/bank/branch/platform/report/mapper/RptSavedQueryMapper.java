package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.RptSavedQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * rpt_saved_query Mapper —— 动态查询保存方案（M0.5.1 雏形）.
 *
 * <p>M1.4 会追加 {@code selectByEmpIdPaged / selectOldest}（删除最旧方案场景）等方法.
 */
@Mapper
public interface RptSavedQueryMapper {

    /** 新增一条方案 */
    int insert(RptSavedQuery e);

    /** 按 id 精确查询 */
    RptSavedQuery selectById(@Param("id") String id);

    /** 按 empId 计数（用于 M1.4 最多 10 条方案上限判定） */
    int countByEmpId(@Param("empId") String empId);

    /** 选择更新（name / subjectIds / metricCodes / version / updatedTime 按需） */
    int updateByIdSelective(RptSavedQuery e);

    /** 按 id 物理删除（M1.4 超限时删除最旧一条） */
    int deleteById(@Param("id") String id);
}
