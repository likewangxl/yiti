package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.TouchTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** MARKETING_TOUCH_TASK Mapper。 */
@Mapper
public interface TouchTaskMapper extends BaseMapper<TouchTask> {
    @Update("UPDATE MARKETING_TOUCH_TASK SET task_status='CANCELLED', cancel_time=#{cancelTime}, "
            + "cancel_reason=#{reason}, updated_by=#{updatedBy}, updated_time=#{cancelTime}, lock_version=lock_version+1 "
            + "WHERE cust_id=#{custId} AND task_status IN ('PENDING','IN_PROGRESS')")
    int cancelActiveByCust(@Param("custId") String custId,
                           @Param("cancelTime") LocalDateTime cancelTime,
                           @Param("reason") String reason,
                           @Param("updatedBy") String updatedBy);

    TouchTask selectByIdForUpdate(@Param("id") String id);
    TouchTask selectById(@Param("id") String id);
    TouchTask selectByTaskNo(@Param("taskNo") String taskNo);
    TouchTask selectBySource(@Param("sourceType") String sourceType, @Param("sourceBizId") Long sourceBizId);
    List<TouchTask> selectPage(@Param("keyword") String keyword, @Param("status") String status,
                               @Param("assigneeEmpId") String assigneeEmpId,
                               @Param("offset") int offset, @Param("limit") int limit);
    long countPage(@Param("keyword") String keyword, @Param("status") String status,
                   @Param("assigneeEmpId") String assigneeEmpId);
    /**
     * 查询当前员工可见的触达任务：主执行人，或同机构且已在有效日志中登记的协同参与人。
     * 列表展示字段在 SQL 中一次聚合，避免服务层按任务逐条查询日志和参与人。
     */
    List<TouchTask> selectPageForViewer(@Param("keyword") String keyword, @Param("status") String status,
                                        @Param("viewerEmpId") String viewerEmpId,
                                        @Param("viewerOrgId") String viewerOrgId,
                                        @Param("offset") int offset, @Param("limit") int limit);
    long countPageForViewer(@Param("keyword") String keyword, @Param("status") String status,
                            @Param("viewerEmpId") String viewerEmpId,
                            @Param("viewerOrgId") String viewerOrgId);
    /** 查询带页面展示聚合字段的任务详情，不用于写入锁定。 */
    TouchTask selectViewById(@Param("id") String id);
    List<TouchTask> selectPendingForSlaRefresh();
    Long countActiveByCust(@Param("custId") String custId);
    List<TouchTask> selectActiveByCust(@Param("custId") String custId);
    List<TouchTask> selectActiveByCustAndAssignee(@Param("custId") String custId,
                                                   @Param("assigneeEmpId") String assigneeEmpId);
    List<TouchTask> selectByEmp(@Param("empId") String empId);
    List<TouchTask> selectByEmpAndStatus(@Param("empId") String empId, @Param("status") String status);
    Long countByEmpAndStatuses(@Param("empId") String empId, @Param("statuses") List<String> statuses);
    List<TouchTask> selectByCustOrderByCreatedDesc(@Param("custId") String custId);
    List<TouchTask> selectByCustAndOrg(@Param("custId") String custId, @Param("orgCode") String orgCode);
    Long countFirstTouchSuccess(@Param("custId") String custId, @Param("orgCode") String orgCode);
    Long countByOrgBetween(@Param("orgCode") String orgCode, @Param("startDate") String startDate,
                           @Param("endDate") String endDate, @Param("status") String status);
    Long countSlaWarningByOrgBetween(@Param("orgCode") String orgCode,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate);
    Double avgDurationHoursByOrgBetween(@Param("orgCode") String orgCode,
                                        @Param("startDate") String startDate,
                                        @Param("endDate") String endDate);
    List<TouchTask> selectAdminPage(@Param("keyword") String keyword, @Param("status") String status,
                                    @Param("assigneeEmpId") String assigneeEmpId, @Param("orgId") String orgId,
                                    @Param("offset") int offset, @Param("limit") int limit);
    Long countAdminPage(@Param("keyword") String keyword, @Param("status") String status,
                        @Param("assigneeEmpId") String assigneeEmpId, @Param("orgId") String orgId);

    @Update("UPDATE MARKETING_TOUCH_TASK SET task_status='IN_PROGRESS', updated_by=#{updatedBy}, "
            + "updated_time=#{updatedTime}, lock_version=lock_version+1 "
            + "WHERE id=#{id} AND task_status='PENDING'")
    int markInProgressIfPending(@Param("id") Long id, @Param("updatedBy") String updatedBy,
                                @Param("updatedTime") LocalDateTime updatedTime);

    @Update("UPDATE MARKETING_TOUCH_TASK SET task_status=#{targetStatus}, success_time=#{successTime}, "
            + "cancel_time=#{cancelTime}, cancel_reason=#{cancelReason}, updated_by=#{updatedBy}, "
            + "updated_time=#{updatedTime}, lock_version=lock_version+1 "
            + "WHERE id=#{id} AND task_status=#{expectedStatus}")
    int updateStatusCas(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                        @Param("targetStatus") String targetStatus,
                        @Param("successTime") LocalDateTime successTime,
                        @Param("cancelTime") LocalDateTime cancelTime,
                        @Param("cancelReason") String cancelReason,
                        @Param("updatedBy") String updatedBy,
                        @Param("updatedTime") LocalDateTime updatedTime);
}
