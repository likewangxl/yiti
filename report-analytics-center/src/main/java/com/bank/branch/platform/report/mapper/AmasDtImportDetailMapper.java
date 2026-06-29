package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.AmasDtImportDetail;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 数据导入明细（单元格）Mapper：按批次取全部单元格（service 里用 LambdaQueryWrapper），透视成行。
 * <p>查看分页按逻辑行 DT_FLAG 分页（GROUP BY，BaseMapper 覆盖不到，落自定义 SQL）。</p>
 * <p>列值过滤：filters 为 {DT_TITLE_NO -> 关键字}，用 HAVING + 条件聚合按整行（DT_FLAG）模糊匹配，多条件 AND。</p>
 */
@Mapper
public interface AmasDtImportDetailMapper extends BaseMapper<AmasDtImportDetail> {

    /** 批次的逻辑行数（DT_FLAG 去重，按列值过滤后）. */
    @Select("<script>"
            + "SELECT COUNT(*) FROM ("
            + "  SELECT DT_FLAG FROM amas_dt_import_details WHERE DT_BATCHNUM = #{batchNum}"
            + "  GROUP BY DT_FLAG"
            + "  <if test='filters != null and !filters.isEmpty()'>"
            + "    HAVING"
            + "    <foreach collection='filters' index='titleNo' item='kw' separator=' AND '>"
            + "      MAX(CASE WHEN DT_TITLE_NO = #{titleNo} THEN DT_DETAILS END) LIKE CONCAT('%', #{kw}, '%')"
            + "    </foreach>"
            + "  </if>"
            + ") t"
            + "</script>")
    long countFlags(@Param("batchNum") String batchNum,
                    @Param("filters") Map<String, String> filters);

    /** 按 DT_FLAG 分页取一页的行标识（按行首次出现顺序，按列值过滤后）. */
    @Select("<script>"
            + "SELECT DT_FLAG FROM amas_dt_import_details WHERE DT_BATCHNUM = #{batchNum}"
            + " GROUP BY DT_FLAG"
            + " <if test='filters != null and !filters.isEmpty()'>"
            + "   HAVING"
            + "   <foreach collection='filters' index='titleNo' item='kw' separator=' AND '>"
            + "     MAX(CASE WHEN DT_TITLE_NO = #{titleNo} THEN DT_DETAILS END) LIKE CONCAT('%', #{kw}, '%')"
            + "   </foreach>"
            + " </if>"
            + " ORDER BY MIN(DT_DETAILS_SNO) LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<String> pageFlags(@Param("batchNum") String batchNum,
                           @Param("filters") Map<String, String> filters,
                           @Param("offset") int offset, @Param("size") int size);
}
