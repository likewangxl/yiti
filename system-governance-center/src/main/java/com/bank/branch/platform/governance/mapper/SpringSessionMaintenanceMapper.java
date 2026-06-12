package com.bank.branch.platform.governance.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * Spring Session 表维护 Mapper。
 * <p>GoldenDB 不支持外键、已去掉 SPRING_SESSION_ATTRIBUTES → SPRING_SESSION 的
 * {@code ON DELETE CASCADE}，Spring Session 过期清理只删 SPRING_SESSION 主表、不再级联删属性，
 * 导致 SPRING_SESSION_ATTRIBUTES 出现孤儿行并无限增长。由定时任务调用本 Mapper 清理。</p>
 */
@Mapper
public interface SpringSessionMaintenanceMapper {

    /**
     * 删除孤儿 session 属性（其所属 session 已不在 SPRING_SESSION 主表）。
     *
     * @return 删除行数
     */
    @Delete("DELETE FROM SPRING_SESSION_ATTRIBUTES "
            + "WHERE SESSION_PRIMARY_ID NOT IN (SELECT PRIMARY_ID FROM SPRING_SESSION)")
    int deleteOrphanAttributes();
}
