package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.dto.asset.AssetProjectQuery;
import com.bank.branch.platform.customer.entity.AssetProjectApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AssetProjectApplyMapper extends BaseMapper<AssetProjectApply> {
    AssetProjectApply selectActiveById(@Param("id") Long id);
    AssetProjectApply selectForUpdate(@Param("id") Long id);
    AssetProjectApply selectByBusinessKey(@Param("businessKey") String businessKey);
    List<AssetProjectApply> selectPage(@Param("query") AssetProjectQuery query,
                                       @Param("empId") String empId,
                                       @Param("bizIds") List<Long> bizIds,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);
    long countPage(@Param("query") AssetProjectQuery query,
                   @Param("empId") String empId,
                   @Param("bizIds") List<Long> bizIds);
    int updateDraftCas(@Param("entity") AssetProjectApply entity,
                       @Param("expectedVersion") Integer expectedVersion);
    int markDeleted(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                    @Param("updatedBy") String updatedBy, @Param("updatedTime") LocalDateTime updatedTime);
    int markSubmitted(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                      @Param("businessKey") String businessKey,
                      @Param("processInstanceId") String processInstanceId,
                      @Param("updatedBy") String updatedBy, @Param("updatedTime") LocalDateTime updatedTime);
    int conditionalUpdateStatus(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                                @Param("targetStatus") String targetStatus,
                                @Param("updatedBy") String updatedBy,
                                @Param("updatedTime") LocalDateTime updatedTime);
    int markUrgentApproved(@Param("id") Long id, @Param("updatedBy") String updatedBy,
                           @Param("updatedTime") LocalDateTime updatedTime);
    long countRunningByCustomer(@Param("custId") Long custId);
    long countByApplicant(@Param("empId") String empId);
    long countCompletedByApplicant(@Param("empId") String empId,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);
    BigDecimal sumCompletedCreditByApplicant(@Param("empId") String empId,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);
}
