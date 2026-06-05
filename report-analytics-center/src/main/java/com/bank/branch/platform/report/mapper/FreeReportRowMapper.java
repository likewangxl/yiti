package com.bank.branch.platform.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.report.entity.RptFreeReportRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FreeReportRowMapper extends BaseMapper<RptFreeReportRow> {

    // 行级数据权限：rowMode = ALL / ORG_SUBTREE / BRANCH_EMP / SELF
    //   ALL         不限制（自由报表操作人 / SYS_ADMIN）
    //   ORG_SUBTREE 行工号映射到的机构 ∈ orgCodes（支行领导/机构负责人/分行行长）
    //   BRANCH_EMP  行工号映射到网点(ORG_LEVEL=3) 或 工号+姓名=本人（分行员工）
    //   SELF        工号+姓名=本人（支行员工/默认）
    long countByBatch(@Param("batchId") String batchId,
                      @Param("keyword") String keyword,
                      @Param("empNo") String empNo,
                      @Param("empName") String empName,
                      @Param("rowMode") String rowMode,
                      @Param("selfEmpNo") String selfEmpNo,
                      @Param("selfName") String selfName,
                      @Param("orgCodes") List<String> orgCodes);

    List<RptFreeReportRow> selectByBatch(@Param("batchId") String batchId,
                                         @Param("keyword") String keyword,
                                         @Param("empNo") String empNo,
                                         @Param("empName") String empName,
                                         @Param("rowMode") String rowMode,
                                         @Param("selfEmpNo") String selfEmpNo,
                                         @Param("selfName") String selfName,
                                         @Param("orgCodes") List<String> orgCodes,
                                         @Param("offset") int offset,
                                         @Param("pageSize") int pageSize);

    void deleteByBatchId(@Param("batchId") String batchId);
}
