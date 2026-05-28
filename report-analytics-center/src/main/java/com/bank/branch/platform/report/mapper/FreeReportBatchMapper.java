package com.bank.branch.platform.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.report.entity.RptFreeReportBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FreeReportBatchMapper extends BaseMapper<RptFreeReportBatch> {

    List<RptFreeReportBatch> selectByReportName(@Param("reportName") String reportName);

    List<RptFreeReportBatch> selectByFileName(@Param("fileName") String fileName);

    List<RptFreeReportBatch> selectAllOrderByImportTimeDesc(
            @Param("keyword") String keyword,
            @Param("dateFrom") java.time.LocalDateTime dateFrom,
            @Param("dateTo") java.time.LocalDateTime dateTo);

    /** 带数据范围的批次查询：scopeEmpId 非空=只看该人；scopeOrgCodes 非空=只看这些机构下的人；都空=全量 */
    List<RptFreeReportBatch> selectBatchesWithScope(
            @Param("keyword") String keyword,
            @Param("dateFrom") java.time.LocalDateTime dateFrom,
            @Param("dateTo") java.time.LocalDateTime dateTo,
            @Param("scopeEmpId") String scopeEmpId,
            @Param("scopeOrgCodes") java.util.List<String> scopeOrgCodes);
}
