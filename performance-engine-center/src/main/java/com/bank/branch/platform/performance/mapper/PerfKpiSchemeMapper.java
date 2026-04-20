package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * KPI 方案表 Mapper.
 *
 * <p>负责 perf_kpi_scheme 父表的 CRUD。方案项由 {@link PerfKpiItemMapper} 管理，
 * 父子同事务由 Service 层编排（见 Task 2.2 KpiSchemeService）。
 */
@Mapper
public interface PerfKpiSchemeMapper {

    /**
     * 新增 KPI 方案.
     *
     * @param scheme KPI 方案
     * @return 受影响行数
     */
    int insert(PerfKpiScheme scheme);

    /**
     * 按主键选择性更新（非空字段才更新，updated_time 固定写入 NOW()）.
     *
     * <p>不接受 patch created_by/created_time; 若传值将被忽略（XML 刻意不提供对应 {@code <if>} 分支）.
     *
     * @param scheme KPI 方案
     * @return 受影响行数
     */
    int updateByIdSelective(PerfKpiScheme scheme);

    /**
     * 按主键更新状态（用于发布 / 禁用流转）.
     *
     * @param id        主键
     * @param status    新状态
     * @param updatedBy 更新人
     * @return 受影响行数
     */
    int updateStatusById(@Param("id") String id,
                         @Param("status") String status,
                         @Param("updatedBy") String updatedBy);

    /**
     * 按主键查询.
     *
     * @param id 主键
     * @return 方案，不存在返回 null
     */
    PerfKpiScheme selectById(@Param("id") String id);

    /**
     * 按方案编码查询（UK 支撑）.
     *
     * @param schemeCode 方案编码
     * @return 方案，不存在返回 null
     */
    PerfKpiScheme selectBySchemeCode(@Param("schemeCode") String schemeCode);

    /**
     * 分页条件查询.
     *
     * @param cycleType 周期类型
     * @param status    状态
     * @param keyword   关键字（编码或名称模糊匹配）
     * @param offset    偏移量
     * @param limit     每页大小
     * @return 方案列表
     */
    List<PerfKpiScheme> selectByCondition(@Param("cycleType") String cycleType,
                                          @Param("status") String status,
                                          @Param("keyword") String keyword,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    /**
     * 条件计数（与 selectByCondition 保持一致）.
     *
     * @param cycleType 周期类型
     * @param status    状态
     * @param keyword   关键字
     * @return 总数
     */
    long countByCondition(@Param("cycleType") String cycleType,
                          @Param("status") String status,
                          @Param("keyword") String keyword);

    /**
     * 按主键删除（用于测试清理或受控下线；生产软删走 updateStatusById）.
     *
     * @param id 主键
     * @return 受影响行数
     */
    int deleteById(@Param("id") String id);
}
