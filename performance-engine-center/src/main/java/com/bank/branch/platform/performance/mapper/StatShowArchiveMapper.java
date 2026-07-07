package com.bank.branch.platform.performance.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 统计展示表旬度归档 Mapper（XAN_M98_CUST/EMP_STAT_SHOW3 及其历史表 _H1/_H2/_H3）.
 *
 * <p>表名为固定白名单字面量（来自 StatShowArchiveJob.MAIN_TABLES + 后缀，非用户输入），用 ${} 拼接；
 * STATIS_DT 一律 #{} 占位。外部表无主键、非 MyBatis-Plus 实体，故用原生 XML。所有方法按单日操作，
 * 每次 ≤ 一天数据量（约 200 万），逐日提交避免巨型事务。
 */
public interface StatShowArchiveMapper {

    /** 统计某表在某 STATIS_DT 的行数（main/hist 通用，用于幂等 count 比对）. */
    long countByTableDate(@Param("table") String table, @Param("dt") String dt);

    /** 将主表某 STATIS_DT 的数据整行插入历史表（历史表与主表列同构，SELECT * 安全）. */
    int insertHistByDate(@Param("histTable") String histTable,
                         @Param("mainTable") String mainTable,
                         @Param("dt") String dt);

    /** 删除历史表某 STATIS_DT 的数据. */
    int deleteHistByDate(@Param("histTable") String histTable, @Param("dt") String dt);

    /** 取主表指定闭区间内的 MAX(STATIS_DT)（作为「该月最后一天」），无数据返回 null. */
    String selectMaxStatisDt(@Param("mainTable") String mainTable,
                             @Param("start") String start,
                             @Param("end") String end);

    /** 删除主表某 STATIS_DT 的数据（瘦身按天分批用）. */
    int deleteMainByDate(@Param("mainTable") String mainTable, @Param("dt") String dt);
}
