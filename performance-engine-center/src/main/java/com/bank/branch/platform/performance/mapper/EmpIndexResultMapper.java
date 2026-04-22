package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.EmpIndexResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 员工指标结果宽表 Mapper（emp_index_result）.
 *
 * <p>V1.1 Task P1.1 交付。宽表共 200 个值槽（{@code val_1 .. val_200}），
 * 本 Mapper 以"单 slot 读写"为核心抽象，屏蔽 200 列带来的代码爆炸：
 * <ul>
 *   <li>{@link #insertSlotValue} 采用 "INSERT ... ON DUPLICATE KEY UPDATE" 语义，
 *       保证首次写入插入新行、后续不同 slot 写入累加到同一行；</li>
 *   <li>{@link #selectSlotValue} 单值回读；</li>
 *   <li>{@link #selectSlotValuesByEmps} 按 empIds 批量查同一 slot；</li>
 *   <li>{@link #insertRow} 保留"行粒度"插入入口（供单测校验 UK 冲突，生产代码优先走 insertSlotValue）。</li>
 * </ul>
 *
 * <p><strong>安全（SQL 注入）声明</strong>：
 * <ul>
 *   <li>为实现 "按 slot 动态切换列名"，{@code insertSlotValue} / {@code selectSlotValue}
 *       / {@code selectSlotValuesByEmps} 在 XML 中使用 {@code val_${slot}} 拼接列名
 *       （列名不能用 #{} 参数化，属 common-dev-guide §5 允许的合法例外）；</li>
 *   <li>调用方 <strong>必须</strong> 在 Service 层强制校验 {@code slot ∈ [1, 200]}（例如
 *       {@code @Range(min=1, max=200)} 或 {@code Assert.isTrue}），否则构成 SQL 注入漏洞；</li>
 *   <li>其余全部参数一律使用 {@code #{}} 预编译占位。</li>
 * </ul>
 */
@Mapper
public interface EmpIndexResultMapper {

    /**
     * 插入/更新员工在指定 slot 上的指标值（UPSERT 语义）.
     *
     * <p>实现细节：SQL 采用 {@code INSERT INTO ... (emp_id, data_date, version, val_{slot}) VALUES (...)
     * ON DUPLICATE KEY UPDATE val_{slot}=VALUES(val_{slot})}，依赖 uk_subject_date_ver 保证幂等。
     *
     * @param empId    员工工号
     * @param dataDate 数据日期
     * @param version  数据版本
     * @param slot     值槽（1..200，<strong>调用方必须校验</strong>）
     * @param value    指标值（可为 null，表示清空该槽）
     */
    void insertSlotValue(@Param("empId") String empId,
                         @Param("dataDate") LocalDate dataDate,
                         @Param("version") String version,
                         @Param("slot") Integer slot,
                         @Param("value") BigDecimal value);

    /**
     * 查询员工在指定 slot 上的指标值.
     *
     * @param empId    员工工号
     * @param dataDate 数据日期
     * @param version  数据版本
     * @param slot     值槽（1..200，<strong>调用方必须校验</strong>）
     * @return slot 值，行不存在或该槽未赋值返回 null
     */
    BigDecimal selectSlotValue(@Param("empId") String empId,
                               @Param("dataDate") LocalDate dataDate,
                               @Param("version") String version,
                               @Param("slot") Integer slot);

    /**
     * 按 empIds 批量查询同一 slot 值.
     *
     * @param empIds   员工工号列表（非空；调用方应控制 &le; 500 批量上限）
     * @param dataDate 数据日期
     * @param version  数据版本
     * @param slot     值槽（1..200，<strong>调用方必须校验</strong>）
     * @return 每个命中员工对应的 (empId, metricValue) 行列表；未命中员工不返回
     */
    List<EmpMetricValueRow> selectSlotValuesByEmps(@Param("empIds") List<String> empIds,
                                                   @Param("dataDate") LocalDate dataDate,
                                                   @Param("version") String version,
                                                   @Param("slot") Integer slot);

    /**
     * 纯行粒度插入（不写 val_*，仅建立唯一维度行）.
     *
     * <p>用途：单元测试构造 UK 冲突场景；生产代码一般直接使用 {@link #insertSlotValue}。
     * 同 (emp_id, data_date, version) 第二次调用将抛 {@link org.springframework.dao.DuplicateKeyException}。
     *
     * @param row Entity（empId/dataDate/version 必填，其余字段可空）
     * @return 受影响行数
     */
    int insertRow(EmpIndexResult row);
}
