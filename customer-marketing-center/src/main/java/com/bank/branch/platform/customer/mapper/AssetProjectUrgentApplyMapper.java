package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.AssetProjectUrgentApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AssetProjectUrgentApplyMapper extends BaseMapper<AssetProjectUrgentApply> {
    List<AssetProjectUrgentApply> selectByAssetProjectId(@Param("assetProjectId") Long assetProjectId);
    AssetProjectUrgentApply selectActiveByAssetProjectId(@Param("assetProjectId") Long assetProjectId);
    int complete(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                 @Param("targetStatus") String targetStatus, @Param("reviewedBy") String reviewedBy,
                 @Param("approvalComment") String approvalComment,
                 @Param("updatedTime") LocalDateTime updatedTime);
}
