package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.AmasPerfAdjustApproval;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业绩调整审批表（主表）Mapper：列表分页 + 详情主记录，全部走 MyBatis-Plus 条件查询.
 */
@Mapper
public interface AmasPerfAdjustApprovalMapper extends BaseMapper<AmasPerfAdjustApproval> {
}
