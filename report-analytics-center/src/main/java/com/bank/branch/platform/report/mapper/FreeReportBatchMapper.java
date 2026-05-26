package com.bank.branch.platform.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.report.entity.RptFreeReportBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FreeReportBatchMapper extends BaseMapper<RptFreeReportBatch> {

    List<RptFreeReportBatch> selectByReportName(@Param("reportName") String reportName);

    List<RptFreeReportBatch> selectAllOrderByImportTimeDesc();
}
