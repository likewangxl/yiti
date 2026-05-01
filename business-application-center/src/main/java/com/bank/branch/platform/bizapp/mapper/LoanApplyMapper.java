package com.bank.branch.platform.bizapp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.bizapp.entity.LoanApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 资产投放申请 Mapper 接口，操作 loan_apply 表。
 * 所有查询默认过滤逻辑删除记录（deleted = 0）。
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} /
 * {@code updateById(T)} / {@code deleteById(Serializable)} 由 BaseMapper 提供。
 * 注意：{@code selectById} 因原签名带 {@code @Param("id")} 与 BaseMapper 不兼容，
 * 保留自定义实现（含逻辑删除过滤）。
 * </p>
 */
@Mapper
public interface LoanApplyMapper extends BaseMapper<LoanApply> {

    /** 按 id 查询（含逻辑删除过滤，自定义实现覆盖 BaseMapper.selectById） */
    LoanApply selectById(@Param("id") String id);

    /** 按 id 查询并加 FOR UPDATE 行锁 */
    LoanApply selectForUpdate(@Param("id") String id);

    /** 按 business_key 查询 */
    LoanApply selectByBusinessKey(@Param("businessKey") String businessKey);

    /** 分页查询 */
    List<LoanApply> selectPage(@Param("keyword") String keyword,
                               @Param("status") String status,
                               @Param("ownerOrgId") String ownerOrgId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    /** 分页总数 */
    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status,
                   @Param("ownerOrgId") String ownerOrgId);

    // insert(T) 由 BaseMapper 提供

    // updateById(T) 由 BaseMapper 提供（FieldStrategy.NOT_NULL，与原 XML 动态 SET 行为一致）

    /** 更新状态 */
    int updateStatusById(@Param("id") String id,
                         @Param("status") String status,
                         @Param("updatedBy") String updatedBy);

    /**
     * 条件更新状态（幂等专用）。
     * 仅当记录当前状态等于 expectedStatus 时才执行更新，返回影响行数。
     * Listener 使用此方法保证多实例环境下状态流转的幂等性。
     *
     * @param id             申请ID
     * @param expectedStatus 期望的当前状态（前置条件）
     * @param targetStatus   目标状态
     * @param updatedBy      操作人
     * @return 影响行数（0 表示状态已被其他实例处理，1 表示更新成功）
     */
    int conditionalUpdateStatus(@Param("id") String id,
                                @Param("expectedStatus") String expectedStatus,
                                @Param("targetStatus") String targetStatus,
                                @Param("updatedBy") String updatedBy);

    /** 按客户ID查询历史 */
    List<LoanApply> selectByCustId(@Param("custId") String custId);

    /** 批量按ID查询 */
    List<LoanApply> selectByIds(@Param("ids") List<String> ids);

    /** 按机构+时间范围统计已完成数量 */
    long countCompletedByOrg(@Param("orgId") String orgId,
                             @Param("startTime") LocalDateTime startTime,
                             @Param("endTime") LocalDateTime endTime);

    /** 按创建人+时间范围汇总授信金额 */
    BigDecimal sumCreditAmountByEmp(@Param("empId") String empId,
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);
}
