package com.bank.branch.platform.bizapp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 中场支持申请 Mapper 接口，操作 support_request 表。
 * 所有查询默认过滤逻辑删除记录（deleted = 0）。
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} /
 * {@code updateById(T)} / {@code deleteById(Serializable)} 由 BaseMapper 提供。
 * 注意：{@code selectById} 因原签名带 {@code @Param("id")} 与 BaseMapper 不兼容，
 * 保留自定义实现（含逻辑删除过滤）。
 * </p>
 */
@Mapper
public interface SupportRequestMapper extends BaseMapper<SupportRequest> {

    /** 按 id 查询（含逻辑删除过滤，自定义实现覆盖 BaseMapper.selectById） */
    SupportRequest selectById(@Param("id") String id);

    /** 按 id 查询并加 FOR UPDATE 行锁 */
    SupportRequest selectForUpdate(@Param("id") String id);

    /** 按 business_key 查询 */
    SupportRequest selectByBusinessKey(@Param("businessKey") String businessKey);

    /** 发起侧分页查询（SUPPORT 视图） */
    List<SupportRequest> selectPageForSupport(@Param("keyword") String keyword,
                                              @Param("status") String status,
                                              @Param("ownerOrgId") String ownerOrgId,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    /** 发起侧分页总数 */
    long countPageForSupport(@Param("keyword") String keyword,
                             @Param("status") String status,
                             @Param("ownerOrgId") String ownerOrgId);

    /** 承接侧分页查询（SUPPORT_DEPT 视图） */
    List<SupportRequest> selectPageForDept(@Param("supportDeptId") String supportDeptId,
                                           @Param("status") String status,
                                           @Param("assignedEmpId") String assignedEmpId,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /** 承接侧分页总数 */
    long countPageForDept(@Param("supportDeptId") String supportDeptId,
                          @Param("status") String status,
                          @Param("assignedEmpId") String assignedEmpId);

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
    List<SupportRequest> selectByCustId(@Param("custId") String custId);

    /** 按 submit_group_id 查询同组 */
    List<SupportRequest> selectBySubmitGroupId(@Param("submitGroupId") String submitGroupId);

    /** 批量按ID查询 */
    List<SupportRequest> selectByIds(@Param("ids") List<String> ids);

    /** 统计同客户同产品正在进行的申请数 */
    long countActiveByCustomerAndProduct(@Param("custId") String custId,
                                         @Param("productId") String productId);

    /** 按创建人+时间范围统计已完成数量 */
    long countCompletedByCreator(@Param("empId") String empId,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /** 按承接人+时间范围统计已完成数量 */
    long countCompletedByAssignee(@Param("empId") String empId,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    /** 统计客户正在运行的支持申请数 */
    long countRunningByCustomer(@Param("custId") String custId);
}
