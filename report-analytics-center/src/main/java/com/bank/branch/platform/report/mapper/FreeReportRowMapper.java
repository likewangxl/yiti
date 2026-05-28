package com.bank.branch.platform.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.report.entity.RptFreeReportRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FreeReportRowMapper extends BaseMapper<RptFreeReportRow> {

    long countByBatch(@Param("batchId") String batchId,
                      @Param("keyword") String keyword,
                      @Param("empNo") String empNo,
                      @Param("empName") String empName,
                      @Param("empId") String empId,
                      @Param("orgCodes") List<String> orgCodes);

    List<RptFreeReportRow> selectByBatch(@Param("batchId") String batchId,
                                         @Param("keyword") String keyword,
                                         @Param("empNo") String empNo,
                                         @Param("empName") String empName,
                                         @Param("empId") String empId,
                                         @Param("orgCodes") List<String> orgCodes,
                                         @Param("offset") int offset,
                                         @Param("pageSize") int pageSize);

    void deleteByBatchId(@Param("batchId") String batchId);
}
