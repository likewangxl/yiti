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

    /** 批次列表（文件级全员公开）：includeDisabled=false 时过滤掉 STATUS='DISABLED' 的文件 */
    List<RptFreeReportBatch> selectBatches(
            @Param("keyword") String keyword,
            @Param("dateFrom") java.time.LocalDateTime dateFrom,
            @Param("dateTo") java.time.LocalDateTime dateTo,
            @Param("includeDisabled") boolean includeDisabled);

    /** 更新批次状态 */
    int updateStatus(@Param("batchId") String batchId, @Param("status") String status);
}
