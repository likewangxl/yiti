package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.dto.resp.DataImportBatchVO;
import com.bank.branch.platform.report.entity.AmasDtImportSup;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据导入信息（表头）Mapper。
 * <p>批次列表按 DT_BATCHNUM 聚合（GROUP BY，BaseMapper 覆盖不到，落自定义 SQL）；
 * 表头列定义用 BaseMapper 条件查询（service 里 LambdaQueryWrapper）。</p>
 */
@Mapper
public interface AmasDtImportSupMapper extends BaseMapper<AmasDtImportSup> {

    /**
     * 批次列表分页（按批次号聚合，取每批次的名称/创建信息/列数，按创建时间倒序）。
     * 分页依赖 common-db 全局 PaginationInnerInterceptor。
     */
    @Select("<script>"
            + "SELECT DT_BATCHNUM AS batchNum, MAX(DT_NAME) AS dataName, "
            + "       MAX(DT_CREATE_TIME) AS createTime, MAX(DT_CREATE_USERNAME) AS createUsername, "
            + "       MAX(DT_CREATE_FULLNAME) AS createFullname, MAX(DT_EXPLAIN) AS dtExplain, "
            + "       COUNT(*) AS columnCount "
            + "FROM amas_dt_import_sup "
            + "<where> "
            + "  <if test=\"q.batchNum != null and q.batchNum != ''\"> AND DT_BATCHNUM LIKE CONCAT('%', #{q.batchNum}, '%') </if> "
            + "  <if test=\"q.dataName != null and q.dataName != ''\"> AND DT_NAME LIKE CONCAT('%', #{q.dataName}, '%') </if> "
            + "  <if test=\"q.createUsername != null and q.createUsername != ''\"> AND DT_CREATE_USERNAME = #{q.createUsername} </if> "
            + "  <if test=\"q.createTimeStart != null and q.createTimeStart != ''\"> AND DT_CREATE_TIME &gt;= #{q.createTimeStart} </if> "
            + "  <if test=\"q.createTimeEnd != null and q.createTimeEnd != ''\"> AND DT_CREATE_TIME &lt;= #{q.createTimeEnd} </if> "
            + "</where> "
            + "GROUP BY DT_BATCHNUM "
            + "ORDER BY MAX(DT_CREATE_TIME) DESC "
            + "</script>")
    IPage<DataImportBatchVO> pageBatches(IPage<DataImportBatchVO> page,
                                         @Param("q") com.bank.branch.platform.report.dto.req.DataImportQueryReqDTO q);
}
