package com.bank.branch.platform.bizapp.mapper;

import com.bank.branch.platform.bizapp.entity.LoanApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 资产投放申请 Mapper 接口，操作 loan_apply 表。
 * 所有查询默认过滤逻辑删除记录（deleted = 0）。
 */
@Mapper
public interface LoanApplyMapper {

    /** 按 id 查询（含逻辑删除过滤） */
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

    /** 插入 */
    int insert(LoanApply entity);

    /** 动态更新（仅更新非 null 字段） */
    int updateById(LoanApply entity);

    /** 更新状态 */
    int updateStatusById(@Param("id") String id,
                         @Param("status") String status,
                         @Param("updatedBy") String updatedBy);

    /** 按客户ID查询历史 */
    List<LoanApply> selectByCustId(@Param("custId") String custId);

    /** 批量按ID查询 */
    List<LoanApply> selectByIds(@Param("ids") List<String> ids);

    /** 按机构+时间范围统计已完成数量 */
    long countCompletedByOrg(@Param("orgId") String orgId,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    /** 按创建人+时间范围汇总授信金额 */
    BigDecimal sumCreditAmountByEmp(@Param("empId") String empId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);
}
