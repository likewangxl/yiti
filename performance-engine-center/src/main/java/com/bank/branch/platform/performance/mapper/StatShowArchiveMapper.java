package com.bank.branch.platform.performance.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 统计展示表 T-1 归档 Mapper。
 *
 * <p>表名只由归档任务/数据就绪协调器的固定白名单和固定后缀生成，因此使用 ${} 拼接；
 * 日期条件一律使用 #{}。每次调用只处理 tmp 中的一个 STATIS_DT，不做数据边界预查询。
 */
public interface StatShowArchiveMapper {

    /** 清空旬边界对应的固定历史表。 */
    int truncateTable(@Param("table") String table);

    /** 删除目标表的单日数据，供普通日及 1 号主表写入幂等使用。 */
    int deleteByTableDate(@Param("table") String table, @Param("dt") String dt);

    /** 只读统计固定表中指定 STATIS_DT 的行数。 */
    long countByTableDate(@Param("table") String table, @Param("dt") String dt);

    /** 将 tmp 表单日 T-1 数据整行写入目标表。 */
    int insertFromTmpByDate(@Param("targetTable") String targetTable,
                            @Param("tmpTable") String tmpTable,
                            @Param("dt") String dt);
}
