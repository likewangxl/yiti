package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.KpiResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * KPI 结果表 Mapper（kpi_result）.
 *
 * <p>V1.1 Task P1.2 交付。表结构与宽表不同：
 * <ul>
 *   <li>主键 bigint AUTO_INCREMENT（insert 后 id 由 JDBC 回填）</li>
 *   <li>业务唯一键 uk_emp_cycle_asof(emp_id, cycle_type, cycle_date, as_of_date) 四列</li>
 *   <li>无 val_* 值槽；核心业务字段 kpi_total_score(decimal) + detail_json(longtext)</li>
 * </ul>
 *
 * <p>V1.1 仅暴露最小写入 + 基础读查询，后续 V1.2 计算 Job/查询 API 将按需扩展。
 */
@Mapper
public interface KpiResultMapper {

    /**
     * 新增 KPI 结果记录（id 由 AUTO_INCREMENT 回填到实体）.
     *
     * <p>相同 (emp_id, cycle_type, cycle_date, as_of_date) 第二次插入将抛
     * {@link org.springframework.dao.DuplicateKeyException}，由 Service 层决策重算策略。
     *
     * @param r KPI 结果
     * @return 受影响行数
     */
    int insert(KpiResult r);

    /**
     * 按主键查询.
     *
     * @param id 主键
     * @return KPI 结果，不存在返回 null
     */
    KpiResult selectById(@Param("id") Long id);

    /**
     * 查询指定员工在某周期类型下的所有 KPI 历史记录.
     *
     * <p>按 as_of_date 降序返回，方便"查最新"场景。
     *
     * @param empId     员工工号
     * @param cycleType 周期类型（MONTHLY/QUARTERLY）
     * @return KPI 历史列表（可能为空）
     */
    List<KpiResult> selectByEmpCycle(@Param("empId") String empId,
                                     @Param("cycleType") String cycleType);
}
