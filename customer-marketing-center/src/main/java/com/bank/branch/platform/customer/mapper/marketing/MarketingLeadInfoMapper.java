package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** MARKETING_LEAD_INFO 数据访问。 */
@Mapper
public interface MarketingLeadInfoMapper extends BaseMapper<MarketingLeadInfo> {

    List<MarketingLeadInfo> selectManualPage(@Param("keyword") String keyword,
                                             @Param("status") String status,
                                             @Param("entryEmpId") String entryEmpId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    long countManualPage(@Param("keyword") String keyword,
                         @Param("status") String status,
                         @Param("entryEmpId") String entryEmpId);

    MarketingLeadInfo selectActiveByCreditCode(@Param("creditCode") String creditCode);

    MarketingLeadInfo selectActiveById(@Param("id") Long id);

    MarketingLeadInfo selectForUpdate(@Param("id") Long id);

    MarketingLeadInfo selectForUpdateByCreditCode(@Param("creditCode") String creditCode);

    int updateStatusIf(@Param("id") Long id,
                       @Param("expectedStatus") String expectedStatus,
                       @Param("targetStatus") String targetStatus,
                       @Param("updatedBy") String updatedBy,
                       @Param("rejectReason") String rejectReason);

    /**
     * 审批状态 CAS 成功后，使用递增后的版本号原子回写客户主档关联和池状态。
     *
     * @param id                  线索ID
     * @param expectedLockVersion {@link #updateStatusIf} 执行后的版本号
     * @param custId              装配后的营销客户主档ID
     * @param poolStatus          PUBLIC 为 AVAILABLE，定向分配为 CLAIMED
     * @param updatedBy           审批人工号
     * @return 更新行数，必须为1
     */
    int finalizeApproval(@Param("id") Long id,
                         @Param("expectedLockVersion") int expectedLockVersion,
                         @Param("custId") Long custId,
                         @Param("poolStatus") String poolStatus,
                         @Param("updatedBy") String updatedBy);

    /**
     * 将已退回线索重新保存为草稿时，显式清空上一轮审批元数据。
     *
     * @param id        线索ID
     * @param updatedBy 修改人工号
     * @return 更新行数
     */
    int clearApprovalMetadataForDraft(@Param("id") Long id,
                                       @Param("updatedBy") String updatedBy);

    /**
     * 直接从已退回状态重新提交时，写入本轮提交信息并清空上一轮流程结果。
     *
     * @param id             线索ID
     * @param submittedBy   本轮提交人工号
     * @param submittedTime 本轮提交时间
     * @param businessKey   本轮流程业务键
     * @param activeDedupKey 在途去重键
     * @param updatedBy     修改人工号
     * @return 更新行数
     */
    int prepareRejectedResubmission(@Param("id") Long id,
                                    @Param("submittedBy") String submittedBy,
                                    @Param("submittedTime") LocalDateTime submittedTime,
                                    @Param("businessKey") String businessKey,
                                    @Param("activeDedupKey") String activeDedupKey,
                                    @Param("updatedBy") String updatedBy);

    /**
     * 工作流启动成功后显式回写流程实例ID，避免依赖实体更新策略写入非空值。
     *
     * @param id                线索ID
     * @param processInstanceId 新流程实例ID
     * @param expectedStatus    启动期间预期的线索状态
     * @param updatedBy         修改人工号
     * @return 更新行数
     */
    int updateProcessInstanceId(@Param("id") Long id,
                                @Param("processInstanceId") String processInstanceId,
                                @Param("expectedStatus") String expectedStatus,
                                @Param("updatedBy") String updatedBy);

    List<MarketingLeadInfo> selectByImportBatchId(@Param("batchId") Long batchId);
}
