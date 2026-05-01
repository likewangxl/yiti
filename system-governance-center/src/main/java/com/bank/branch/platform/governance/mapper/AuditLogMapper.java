package com.bank.branch.platform.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.governance.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 审计日志 Mapper 接口，操作 audit_log 表。
 * <p>
 * audit_log 表为只追加（append-only）设计，无更新和删除操作。
 * 分页查询支持按操作人、业务类型、业务动作、时间范围和关键词筛选。
 * </p>
 * <p>
 * insert / selectById 由 MyBatis-Plus BaseMapper 提供。
 * </p>
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * 分页查询审计日志，支持多条件动态筛选。
     *
     * @param empId     操作人工号（精确匹配），为 null 时不过滤
     * @param bizType   业务类型（精确匹配），为 null 时不过滤
     * @param bizAction 业务动作（精确匹配），为 null 时不过滤
     * @param startDate 开始日期，为 null 时不过滤
     * @param endDate   结束日期，为 null 时不过滤
     * @param keyword   模糊搜索关键词，为 null 时不过滤
     * @param offset    偏移量
     * @param limit     每页大小
     * @return 审计日志列表
     */
    List<AuditLog> selectByPage(@Param("empId") String empId,
                                @Param("bizType") String bizType,
                                @Param("bizAction") String bizAction,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate,
                                @Param("keyword") String keyword,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    /**
     * 统计分页查询的总记录数，筛选条件同 selectByPage。
     *
     * @param empId     操作人工号
     * @param bizType   业务类型
     * @param bizAction 业务动作
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param keyword   模糊搜索关键词
     * @param offset    偏移量（占位，与 selectByPage 签名一致）
     * @param limit     每页大小（占位，与 selectByPage 签名一致）
     * @return 总记录数
     */
    long countByPage(@Param("empId") String empId,
                     @Param("bizType") String bizType,
                     @Param("bizAction") String bizAction,
                     @Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate,
                     @Param("keyword") String keyword,
                     @Param("offset") int offset,
                     @Param("limit") int limit);
}
