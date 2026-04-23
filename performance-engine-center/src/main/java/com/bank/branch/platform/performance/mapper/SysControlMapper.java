package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.SysControl;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * sys_control 数据版本控制表 Mapper.
 *
 * <p>V1.0.3 对齐：表新增 5 字段（remark/updated_by/publish_source/publish_by/publish_time）。
 * <p>v1.2 版本控制表采用生产 DDL, 不走审计字段填充, 禁止使用 common-db 的 AuditFieldFiller.
 * <p>UK: (scope_dim, latest_data_date, current_version)（V1.0.3 扩展），索引: (scope_dim, is_valid).
 */
@Mapper
public interface SysControlMapper {

    /**
     * 新增一条版本控制记录.
     *
     * @param sysControl 实体
     * @return 受影响行数
     */
    int insert(SysControl sysControl);

    /**
     * 按主键查询.
     *
     * @param id 主键
     * @return 实体, 不存在时返回 null
     */
    SysControl selectById(@Param("id") String id);

    /**
     * 查询指定维度下当前生效 (is_valid=1) 的版本, 最多一条.
     *
     * @param scopeDim 维度
     * @return 实体, 不存在时返回 null
     */
    SysControl selectByScopeAndValid(@Param("scopeDim") String scopeDim);

    /**
     * 查询指定维度下全部历史版本, 按 latest_data_date 倒序.
     *
     * @param scopeDim 维度
     * @param limit    最多返回条数
     * @return 实体列表
     */
    List<SysControl> listByScope(@Param("scopeDim") String scopeDim,
                                 @Param("limit") int limit);

    /**
     * 按主键更新 is_valid 标志.
     *
     * @param id      主键
     * @param isValid 1 有效 / 0 失效
     * @return 受影响行数
     */
    int updateIsValid(@Param("id") String id,
                      @Param("isValid") int isValid);

    /**
     * 选择性更新（V1.0.3：含发布元数据字段）.
     *
     * @param sysControl 实体（只更新非 null 字段）
     * @return 受影响行数
     */
    int updateByIdSelective(SysControl sysControl);

    /**
     * 条件查询 (支持 scopeDim / isValid 过滤, 分页).
     *
     * @param scopeDim 维度, null 不过滤
     * @param isValid  有效标志, null 不过滤
     * @param offset   分页偏移
     * @param limit    每页大小
     * @return 实体列表
     */
    List<SysControl> selectByCondition(@Param("scopeDim") String scopeDim,
                                       @Param("isValid") Integer isValid,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /**
     * 按条件统计记录数.
     */
    long countByCondition(@Param("scopeDim") String scopeDim,
                          @Param("isValid") Integer isValid);

    // ===== V1.2 Task Q5.1：历史版本清理新增 3 个方法 =====

    /**
     * 列出 sys_control 表中出现过的全部 scope_dim（distinct）.
     *
     * <p>Q5.1 SysControlCleanupJob 启动时据此拿到需要清理的维度清单，
     * 避免把"清理策略"硬编码为 EMP/ORG/CUST 三档.
     *
     * @return 所有 distinct 的 scope_dim，可能为空列表
     */
    List<String> selectScopeDims();

    /**
     * 查询某 scope_dim 下应被清理的历史版本 id 列表（V1.2 Task Q5.1）.
     *
     * <p>语义：
     * <ul>
     *   <li>仅考虑 {@code is_valid = 0} 的历史行，当前生效行 {@code is_valid = 1} 永不返回；</li>
     *   <li>按 {@code publish_time DESC, id DESC} 倒序，保留最新 {@code keepCount} 条；</li>
     *   <li>返回超出 keepCount 部分的 id 列表（OFFSET keepCount）.</li>
     * </ul>
     *
     * @param scopeDim  维度
     * @param keepCount 保留的历史条数
     * @return 应清理的 id 列表（不含当前生效行）
     */
    List<String> selectOldVersionIdsForCleanup(@Param("scopeDim") String scopeDim,
                                               @Param("keepCount") int keepCount);

    /**
     * 按 id 列表批量硬删除 sys_control 记录（V1.2 Task Q5.1）.
     *
     * <p>调用方 <strong>必须</strong> 保证 ids 全部来自 {@link #selectOldVersionIdsForCleanup}
     * 的返回值，以避免误删当前生效版本.
     *
     * @param ids id 列表；为空时不执行 SQL，返回 0
     * @return 实际删除的行数
     */
    int deleteByIds(@Param("ids") List<String> ids);
}
